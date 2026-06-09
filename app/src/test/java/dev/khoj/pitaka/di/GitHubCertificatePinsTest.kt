package dev.khoj.pitaka.di

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F-08: verifies the GitHub certificate pin-set is constructed correctly.
 * The actual TLS handshake / pin enforcement can only be checked against the
 * live host (a device smoke test); this guards the pure construction —
 * which hosts are pinned, which SPKI hashes, and that the leaf is NOT pinned.
 */
class GitHubCertificatePinsTest {

    @Test
    fun pins_both_github_hosts() {
        // The OAuth flow hits github.com; the contents/publish API hits
        // api.github.com. Both must be pinned.
        assertThat(GitHubCertificatePins.PINNED_HOSTS)
            .containsExactly("github.com", "api.github.com")
    }

    @Test
    fun pinner_returns_intermediate_and_root_for_each_host() {
        val pinner = GitHubCertificatePins.pinner()
        for (host in GitHubCertificatePins.PINNED_HOSTS) {
            val pins = pinner.findMatchingPins(host).map { it.toString() }
            // Two pins per host: intermediate + root. No leaf (Option A).
            assertThat(pins).hasSize(2)
            assertThat(pins.any { it.contains("ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY=") }).isTrue()
            assertThat(pins.any { it.contains("sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE=") }).isTrue()
        }
    }

    @Test
    fun pins_use_sha256_scheme() {
        assertThat(GitHubCertificatePins.INTERMEDIATE_SECTIGO_E36).startsWith("sha256/")
        assertThat(GitHubCertificatePins.ROOT_SECTIGO_E46).startsWith("sha256/")
    }

    @Test
    fun does_not_pin_the_rotating_leaf() {
        // The leaf SPKI captured 2026-06-01 for github.com. Pinning it would
        // force a quarterly APK update — Option A deliberately excludes it.
        val leafPin = "Ry0vLQcAM0ZpwjfCIday3P4budz0fLwe34EWXN1ZWdk="
        assertThat(GitHubCertificatePins.INTERMEDIATE_SECTIGO_E36).doesNotContain(leafPin)
        assertThat(GitHubCertificatePins.ROOT_SECTIGO_E46).doesNotContain(leafPin)
    }
}
