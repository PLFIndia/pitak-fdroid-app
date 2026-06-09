package dev.khoj.pitaka.data.backup

import android.content.Context
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapter
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.data.crypto.BackupPassphraseWrapper
import dev.khoj.pitaka.data.images.CoverPaths
import dev.khoj.pitaka.data.local.books.BooksDatabase
import dev.khoj.pitaka.data.local.borrowers.BorrowersDatabase
import dev.khoj.pitaka.data.local.wishlist.WishlistDatabase
import java.io.File
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a Pitaka backup archive to the supplied [OutputStream].
 *
 * Archive layout (see [BackupManifest]):
 *   manifest.json
 *   books.db, wishlist.db, borrowers.db  (verbatim file copies)
 *   backup_blob                          (passphrase-wrapped vault key)
 *
 * Notes / non-goals:
 *  - cache.db is excluded (not user data; re-derivable).
 *  - The borrowers.db file is already SQLCipher-encrypted; its inclusion
 *    is safe as long as the wrapped blob is also present (otherwise the
 *    archive is unrecoverable on a new device).
 *  - We snapshot files; we do NOT lock the SQLite write path. For Pitaka's
 *    user pattern this is fine — the user runs backup manually and isn't
 *    typically writing at the same time. If we ever need crash-consistency
 *    we'd use SQLite's `VACUUM INTO` (also works under SQLCipher).
 */
@Singleton
class BackupArchive @Inject constructor(
    @ApplicationContext private val context: Context,
    private val moshi: Moshi,
    private val backupWrapper: BackupPassphraseWrapper,
    private val booksDb: BooksDatabase,
    private val wishlistDb: WishlistDatabase,
    private val vaultSession: dev.khoj.pitaka.data.vault.VaultSession,
) {
    @OptIn(ExperimentalStdlibApi::class)
    fun write(output: OutputStream, now: Long): BackupManifest {
        // Room runs in WAL mode: freshly-committed rows live in the `<db>-wal`
        // sidecar until a checkpoint folds them into the main `.db` file. The
        // backup copies the main `.db` file by raw bytes (it does NOT bundle the
        // -wal sidecar), so without a checkpoint here the backup captures a stale
        // base file and just-added rows are silently lost on restore. Force a
        // TRUNCATE checkpoint on each DB so its file is complete and
        // self-contained before we read it. Best-effort: a checkpoint failure
        // must not abort the backup.
        checkpoint(booksDb)
        checkpoint(wishlistDb)
        // borrowers.db is SQLCipher-encrypted; a checkpoint can only run while
        // the vault is UNLOCKED (the key is loaded). If the vault is unlocked at
        // backup time it may have uncommitted-to-main WAL rows (the user just
        // added a borrower/loan) — checkpoint them in. If locked, it is not
        // being written to, so its main file is already current and we skip it.
        vaultSession.currentDatabase()?.let { checkpoint(it) }

        val manifestAdapter = moshi.adapter<BackupManifest>()
        val blobBytes = backupWrapper.rawBlobBytes()
        val coverFiles = coverFiles()
        val manifest = BackupManifest(
            exportedAt = now,
            hasBooks = dbFile(BooksDatabase.NAME).exists(),
            hasWishlist = dbFile(WishlistDatabase.NAME).exists(),
            hasBorrowers = dbFile(BorrowersDatabase.NAME).exists(),
            hasBackupBlob = blobBytes != null,
            hasCovers = coverFiles.isNotEmpty(),
            backupHint = backupWrapper.getHint(),
        )

        ZipOutputStream(output).use { zos ->
            putEntry(zos, "manifest.json", manifestAdapter.indent("  ").toJson(manifest).toByteArray(Charsets.UTF_8))
            if (manifest.hasBooks) putEntry(zos, "books.db", dbFile(BooksDatabase.NAME).readBytes())
            if (manifest.hasWishlist) putEntry(zos, "wishlist.db", dbFile(WishlistDatabase.NAME).readBytes())
            if (manifest.hasBorrowers) putEntry(zos, "borrowers.db", dbFile(BorrowersDatabase.NAME).readBytes())
            blobBytes?.let { putEntry(zos, "backup_blob", it) }
            // User-supplied cover images (PLAN-covers.md D2). The archive
            // (and BoundedZipExtractor) is intentionally FLAT — no nested
            // directories — so each cover is stored as a flat entry named
            // `cover_<leaf>` and routed back to filesDir/covers/<leaf> on
            // restore. The leaf is already the on-disk filename (a UUID for
            // post-D1 covers, a legacy id for older ones); we bundle whatever
            // is present verbatim and let restore + the Wave-4 healer
            // rationalise references.
            for (f in coverFiles) {
                putEntry(zos, COVER_ENTRY_PREFIX + f.name, f.readBytes())
            }
        }
        return manifest
    }

    /** Regular files under filesDir/covers/, or empty when none exist. */
    private fun coverFiles(): List<File> {
        val dir = File(context.filesDir, CoverPaths.COVERS_DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()
    }

    private fun dbFile(name: String): File = context.getDatabasePath(name)

    /**
     * Force a WAL TRUNCATE checkpoint so all committed rows are folded from the
     * `<db>-wal` sidecar into the main `.db` file, making the file we copy
     * complete and self-contained. Best-effort: any failure is swallowed so a
     * checkpoint problem can never abort an otherwise-valid backup.
     */
    private fun checkpoint(db: androidx.room.RoomDatabase) {
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        }
    }

    private fun putEntry(zos: ZipOutputStream, name: String, bytes: ByteArray) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(bytes)
        zos.closeEntry()
    }

    companion object {
        /**
         * Flat-entry prefix for bundled cover images. The archive format and
         * [BoundedZipExtractor] are intentionally flat (no nested dirs), so a
         * cover that lives at `filesDir/covers/<leaf>` is stored as the flat
         * entry `cover_<leaf>` and routed back on restore. Chosen so it can
         * never collide with the fixed top-level entries (manifest.json,
         * books.db, wishlist.db, borrowers.db, backup_blob).
         */
        const val COVER_ENTRY_PREFIX = "cover_"
    }
}
