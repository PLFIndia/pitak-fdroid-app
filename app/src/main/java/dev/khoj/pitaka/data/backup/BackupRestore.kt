package dev.khoj.pitaka.data.backup

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.crypto.BackupPassphraseWrapper
import dev.khoj.pitaka.data.crypto.KeystoreVault
import dev.khoj.pitaka.data.crypto.VaultPassphrase
import dev.khoj.pitaka.data.images.CoverPaths
import dev.khoj.pitaka.data.local.books.BooksDatabase
import dev.khoj.pitaka.data.local.borrowers.BorrowersDatabase
import dev.khoj.pitaka.data.local.wishlist.WishlistDatabase
import dev.khoj.pitaka.data.vault.VaultSession
import java.io.File
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads a Pitaka backup archive and applies it to the device.
 *
 * Restore proceeds in stages:
 *   1. Parse manifest, refuse `schemaVersion > KNOWN_SCHEMA_VERSION`.
 *   2. Unwrap `backup_blob` with the user-supplied passphrase. On wrong
 *      passphrase → [Result.WrongPassphrase].
 *   3. Lock vault, move DB files into the app's database dir.
 *   4. Re-wrap the daily passphrase under THIS device's Keystore.
 *      Re-persist the raw backup blob into encrypted-at-rest prefs so
 *      the user's existing passphrase keeps working on the new device.
 *   5. Surface RestartRequired — the caller kills the process and the
 *      activity stack restarts from the launcher icon, picking up fresh
 *      Room / Hilt state.
 */
@Singleton
class BackupRestore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val keystore: KeystoreVault,
    private val backupWrapper: BackupPassphraseWrapper,
    private val session: VaultSession,
) {
    sealed interface Result {
        data class Success(val manifest: BackupManifest) : Result
        data object WrongPassphrase : Result
        data object MissingBackupBlob : Result
        data class SchemaTooNew(val schemaVersion: Int) : Result
        data class Failed(val reason: String) : Result
    }

    @OptIn(ExperimentalStdlibApi::class)
    fun restore(input: InputStream, passphrase: CharArray): Result {
        val tempDir = File(context.cacheDir, "restore-${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            // F-02 (audit): bounded extraction. Caps on per-entry size,
            // total size, and entry count protect against zip-bombs and
            // tampered archives. Zip-slip is enforced by the extractor's
            // canonical-path check.
            val files: Map<String, File> = try {
                BoundedZipExtractor.extract(input, tempDir)
            } catch (e: BoundedExtractionException) {
                return Result.Failed(e.message ?: "Archive rejected")
            }

            val manifestFile = files["manifest.json"]
                ?: return Result.Failed("Archive missing manifest.json")
            val manifestAdapter = moshi.adapter<BackupManifest>()
            val manifest = runCatching { manifestAdapter.fromJson(manifestFile.readText(Charsets.UTF_8)) }
                .getOrNull() ?: return Result.Failed("Invalid manifest.json")
            if (manifest.schemaVersion > BackupManifest.KNOWN_SCHEMA_VERSION) {
                return Result.SchemaTooNew(manifest.schemaVersion)
            }

            val blobFile = files["backup_blob"]
                ?: return Result.MissingBackupBlob
            val blobBytes = blobFile.readBytes()

            // F-07 (audit): validate the archive's backup_blob BEFORE we
            // touch the on-device state. A corrupt blob now surfaces as
            // "archive is corrupt" instead of being silently mangled into
            // a wrong-passphrase outcome.
            val previousBlob = backupWrapper.rawBlobBytes()
            val previousHint = backupWrapper.getHint()
            when (val r = backupWrapper.setRawBlobBytes(blobBytes, manifest.backupHint)) {
                is BackupPassphraseWrapper.SetBlobResult.Success -> Unit
                is BackupPassphraseWrapper.SetBlobResult.InvalidFormat ->
                    return Result.Failed("Backup archive is corrupt: ${r.reason}")
            }

            val unwrapped: VaultPassphrase? = backupWrapper.unwrapWithBackupPassphrase(passphrase)
            if (unwrapped == null) {
                // Wrong passphrase: restore previous state so user can retry.
                if (previousBlob != null) {
                    // The prior blob came from our own rawBlobBytes() and is
                    // therefore valid by construction; ignore the (impossible)
                    // InvalidFormat case to keep the call site simple.
                    backupWrapper.setRawBlobBytes(previousBlob, previousHint)
                } else {
                    backupWrapper.clear()
                }
                return Result.WrongPassphrase
            }

            try {
                // Phase 2: lock + close any open vault session.
                session.lock()

                // Phase 3: move DB files into place.
                files["books.db"]?.let { copyOver(it, context.getDatabasePath(BooksDatabase.NAME)) }
                files["wishlist.db"]?.let { copyOver(it, context.getDatabasePath(WishlistDatabase.NAME)) }
                files["borrowers.db"]?.let { copyOver(it, context.getDatabasePath(BorrowersDatabase.NAME)) }

                // Phase 3b: restore bundled cover images (PLAN-covers.md D2).
                // Entries named `cover_<leaf>` route to filesDir/covers/<leaf>.
                // Relative `covers/<leaf>` references in the restored books.db
                // then resolve on THIS device regardless of the exporting
                // device's package-qualified path.
                restoreCovers(files)

                // Phase 4: re-wrap daily passphrase under THIS device's Keystore.
                keystore.wipeAndReset()
                keystore.setExplicitDailyPassphrase(unwrapped)
            } finally {
                unwrapped.close()
            }

            return Result.Success(manifest)
        } catch (t: Throwable) {
            return Result.Failed(t.message ?: "Restore failed")
        } finally {
            runCatching { tempDir.deleteRecursively() }
        }
    }

    /**
     * Writes bundled `cover_<leaf>` entries into filesDir/covers/<leaf>.
     * A restore replaces device state, so we clear any existing covers dir
     * first to avoid leaving covers that belong to the pre-restore library.
     * Best-effort: a single unreadable cover entry must not fail the whole
     * restore (the row's reference will simply resolve to a placeholder).
     */
    private fun restoreCovers(files: Map<String, File>) {
        val coversDir = File(context.filesDir, CoverPaths.COVERS_DIR)
        runCatching { coversDir.deleteRecursively() }
        coversDir.mkdirs()
        for ((name, file) in files) {
            if (!name.startsWith(BackupArchive.COVER_ENTRY_PREFIX)) continue
            val leaf = name.removePrefix(BackupArchive.COVER_ENTRY_PREFIX)
            // Defence in depth: leaf is already sanitised by the extractor
            // (no '/', no '\', no NUL), but re-validate via CoverPaths so a
            // future extractor change can't let a traversal name through.
            if (CoverPaths.leafOf(CoverPaths.PREFIX + leaf) != leaf) continue
            runCatching { file.copyTo(File(coversDir, leaf), overwrite = true) }
        }
    }

    private fun copyOver(src: File, dst: File) {
        // Wipe any sidecar files Room/SQLite may have created (-shm, -wal)
        // so the new DB opens cleanly.
        listOf(dst, File(dst.path + "-shm"), File(dst.path + "-wal"), File(dst.path + "-journal"))
            .forEach { if (it.exists()) it.delete() }
        dst.parentFile?.mkdirs()
        src.copyTo(dst, overwrite = true)
    }
}
