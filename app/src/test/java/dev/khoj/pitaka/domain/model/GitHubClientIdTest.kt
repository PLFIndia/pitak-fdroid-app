package dev.khoj.pitaka.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GitHubClientIdTest {

    // --- accepted (real-world shapes) ---

    @Test
    fun accepts_legacy_20_hex_client_id() {
        // Legacy OAuth App Client IDs are 20 hex characters.
        assertThat(GitHubClientId.isValid("1234567890abcdef1234")).isTrue()
    }

    @Test
    fun accepts_github_app_iv1_prefixed_id() {
        // GitHub App client IDs use an `Iv1.` prefix.
        assertThat(GitHubClientId.isValid("Iv1.1234567890abcdef")).isTrue()
    }

    @Test
    fun accepts_newer_iv23_prefixed_id() {
        // Newer App client IDs use other `Iv23...` prefixes.
        assertThat(GitHubClientId.isValid("Iv23liABCDEFghij1234")).isTrue()
    }

    @Test
    fun trims_surrounding_whitespace() {
        assertThat(GitHubClientId.normalizeOrNull("  1234567890abcdef1234  "))
            .isEqualTo("1234567890abcdef1234")
    }

    @Test
    fun preserves_case_of_id() {
        // Client IDs are case-sensitive; normalisation must not lowercase them.
        assertThat(GitHubClientId.normalizeOrNull("Iv1.AbCdEf1234567890"))
            .isEqualTo("Iv1.AbCdEf1234567890")
    }

    // --- rejected ---

    @Test
    fun rejects_blank() {
        assertThat(GitHubClientId.normalizeOrNull("")).isNull()
        assertThat(GitHubClientId.normalizeOrNull("   ")).isNull()
    }

    @Test
    fun rejects_too_short() {
        assertThat(GitHubClientId.isValid("abc123")).isFalse()
    }

    @Test
    fun rejects_too_long() {
        assertThat(GitHubClientId.isValid("a".repeat(65))).isFalse()
    }

    @Test
    fun rejects_pasted_full_url() {
        assertThat(GitHubClientId.isValid("https://github.com/settings/applications/12345"))
            .isFalse()
    }

    @Test
    fun rejects_client_id_query_fragment() {
        assertThat(GitHubClientId.isValid("client_id=1234567890abcdef1234")).isFalse()
    }

    @Test
    fun rejects_embedded_whitespace() {
        assertThat(GitHubClientId.isValid("1234567890 abcdef1234")).isFalse()
    }

    @Test
    fun rejects_path_or_query_chars() {
        assertThat(GitHubClientId.isValid("1234567890abcdef/1234")).isFalse()
        assertThat(GitHubClientId.isValid("1234567890abcdef?1234")).isFalse()
        assertThat(GitHubClientId.isValid("1234567890abcdef&1234")).isFalse()
    }

    @Test
    fun rejects_non_ascii_or_symbol_chars() {
        assertThat(GitHubClientId.isValid("1234567890abcdef@234")).isFalse()
        assertThat(GitHubClientId.isValid("1234567890abcdeféää1")).isFalse()
    }
}
