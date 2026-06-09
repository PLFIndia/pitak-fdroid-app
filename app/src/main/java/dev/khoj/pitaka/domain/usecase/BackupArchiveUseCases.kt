package dev.khoj.pitaka.domain.usecase

import dev.khoj.pitaka.data.backup.BackupArchive
import dev.khoj.pitaka.data.backup.BackupManifest
import dev.khoj.pitaka.data.backup.BackupRestore
import dev.khoj.pitaka.data.vault.VaultPreferences
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

/**
 * D15 backup creation. User picks the destination via SAF; we write the
 * archive to the resulting OutputStream and update the last-backup
 * timestamp so the staleness banner resets.
 */
class CreateBackupArchiveUseCase(
    private val archive: BackupArchive,
    private val prefs: VaultPreferences,
    private val clock: () -> Long,
) {
    @Inject
    constructor(
        archive: BackupArchive,
        prefs: VaultPreferences,
    ) : this(archive, prefs, clock = System::currentTimeMillis)

    sealed interface Result {
        data class Success(val manifest: BackupManifest) : Result
        data class Failed(val reason: String) : Result
    }

    suspend operator fun invoke(out: OutputStream): Result = try {
        val now = clock()
        val manifest = archive.write(out, now)
        prefs.setLastBackupAtMs(now)
        Result.Success(manifest)
    } catch (t: Throwable) {
        Result.Failed(t.message ?: "Backup write failed")
    }
}

/**
 * D5 restore. After Success the caller must restart the process so Room
 * + Hilt pick up the replaced DB files.
 */
class RestoreBackupArchiveUseCase @Inject constructor(
    private val restore: BackupRestore,
) {
    sealed interface Result {
        data class Success(val manifest: BackupManifest) : Result
        data object WrongPassphrase : Result
        data object MissingBackupBlob : Result
        data class SchemaTooNew(val schemaVersion: Int) : Result
        data class Failed(val reason: String) : Result
    }

    operator fun invoke(input: InputStream, passphrase: CharArray): Result {
        return when (val r = restore.restore(input, passphrase)) {
            is BackupRestore.Result.Success -> Result.Success(r.manifest)
            BackupRestore.Result.WrongPassphrase -> Result.WrongPassphrase
            BackupRestore.Result.MissingBackupBlob -> Result.MissingBackupBlob
            is BackupRestore.Result.SchemaTooNew -> Result.SchemaTooNew(r.schemaVersion)
            is BackupRestore.Result.Failed -> Result.Failed(r.reason)
        }
    }
}
