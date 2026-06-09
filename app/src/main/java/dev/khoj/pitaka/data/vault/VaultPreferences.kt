package dev.khoj.pitaka.data.vault

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * DataStore-backed user preferences for the vault. Phase 4 only exposes the
 * two values it actually needs (auto-lock timeout, backup-banner-acked).
 * Phase 7 Settings reads/writes these via a richer UI.
 *
 * D1 default: 60_000ms (60 seconds).
 *
 * D18 banner: once the user acknowledges the "your data won't survive
 * device loss without a backup passphrase" warning, this flag is set so
 * the banner stops nagging.
 */
@Singleton
class VaultPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = "pitaka_vault_prefs")

    private val autoLockKey = longPreferencesKey("auto_lock_timeout_ms")
    private val backupBannerAckedKey = stringPreferencesKey("backup_banner_acked")
    private val lastBackupAtKey = longPreferencesKey("last_backup_at_ms")
    private val backupStalenessThresholdKey = longPreferencesKey("backup_staleness_threshold_ms")

    fun autoLockTimeoutMs(): Long = runBlocking {
        context.dataStore.data.first()[autoLockKey] ?: DEFAULT_AUTO_LOCK_MS
    }

    suspend fun setAutoLockTimeoutMs(ms: Long) {
        context.dataStore.edit { it[autoLockKey] = ms }
    }

    fun observeBackupBannerAcked(): Flow<Boolean> =
        context.dataStore.data.map { (it[backupBannerAckedKey] ?: "0") == "1" }

    suspend fun setBackupBannerAcked(acked: Boolean) {
        context.dataStore.edit { it[backupBannerAckedKey] = if (acked) "1" else "0" }
    }

    /** Epoch-ms of the last successful backup write; 0 if never. */
    fun lastBackupAtMs(): Long = runBlocking {
        context.dataStore.data.first()[lastBackupAtKey] ?: 0L
    }

    suspend fun setLastBackupAtMs(ms: Long) {
        context.dataStore.edit { it[lastBackupAtKey] = ms }
    }

    /** Milliseconds since which a backup is considered stale (banner fires). */
    fun backupStalenessThresholdMs(): Long = runBlocking {
        context.dataStore.data.first()[backupStalenessThresholdKey] ?: DEFAULT_STALENESS_MS
    }

    suspend fun setBackupStalenessThresholdMs(ms: Long) {
        context.dataStore.edit { it[backupStalenessThresholdKey] = ms }
    }

    companion object {
        const val DEFAULT_AUTO_LOCK_MS: Long = 60_000L // D1
        const val DEFAULT_STALENESS_MS: Long = 14L * 24 * 60 * 60 * 1000 // 14 days
    }
}
