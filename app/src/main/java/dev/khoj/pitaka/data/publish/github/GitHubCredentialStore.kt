package dev.khoj.pitaka.data.publish.github

import android.content.Context
import dev.khoj.pitaka.data.crypto.aead.AeadPreferenceStore
import dev.khoj.pitaka.data.crypto.aead.AeadStores
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the user's GitHub access token + OAuth Client ID + target repo
 * encrypted at rest (audit F-03: migrated off the deprecated
 * `androidx.security.crypto` to the app's own [AeadPreferenceStore]).
 *
 * Per §1.1 every value here belongs to the user; the app holds it on the
 * user's behalf and never sends it anywhere except api.github.com.
 *
 * D14: the same store holds either an OAuth-derived token (Device Flow,
 * D13) or a user-pasted PAT. Downstream code uses [currentToken] and
 * doesn't care which.
 */
@Singleton
class GitHubCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val store: AeadPreferenceStore by lazy {
        AeadStores.create(context, PREF_FILE, KEY_ALIAS)
    }

    fun currentToken(): String? = store.getString(KEY_TOKEN)?.takeIf { it.isNotBlank() }
    fun isAuthenticated(): Boolean = !currentToken().isNullOrBlank()

    fun setToken(token: String) { store.putString(KEY_TOKEN, token) }
    fun clearToken() { store.remove(KEY_TOKEN) }

    fun clientId(): String? = store.getString(KEY_CLIENT_ID)?.takeIf { it.isNotBlank() }
    fun setClientId(id: String) { store.putString(KEY_CLIENT_ID, id) }

    /** "owner/repo" — picked from /user/repos, persisted for the next publish. */
    fun targetRepo(): String? = store.getString(KEY_REPO)?.takeIf { it.isNotBlank() }
    fun setTargetRepo(ownerSlashRepo: String) { store.putString(KEY_REPO, ownerSlashRepo) }
    fun clearTargetRepo() { store.remove(KEY_REPO) }

    companion object {
        // F-18: own Keystore alias (was the shared "pitaka_publish_master_v1").
        private const val KEY_ALIAS = "pitaka_gh_credentials_key_v1"
        private const val PREF_FILE = "pitaka_publish_secrets"
        private const val KEY_TOKEN = "gh_token"
        private const val KEY_CLIENT_ID = "gh_client_id"
        private const val KEY_REPO = "gh_target_repo"
    }
}
