package dev.khoj.pitaka.di

import okhttp3.CertificatePinner

/**
 * F-08 (audit): certificate pinning for the GitHub hosts the publish + OAuth
 * features talk to. Pure + Android-free so the pin-set construction is
 * unit-testable; the actual TLS enforcement happens on the [okhttp3.OkHttpClient]
 * built in NetworkModule.
 *
 * Strategy — Option A (root + intermediate, NO leaf), decided with the user:
 *   - GitHub's leaf cert rotates roughly every 90 days. Pinning it would mean
 *     shipping a Play Store update every quarter or the publish/OAuth flows
 *     hard-fail (there is no developer backend to hot-fix — §1.1). So the leaf
 *     is intentionally NOT pinned.
 *   - We pin the long-lived intermediate + root instead. Refresh cadence is
 *     measured in years; routine leaf rotation is covered transparently.
 *   - Slightly weaker than leaf-pinning (any Sectigo-issued cert for the host
 *     validates), but Sectigo participates in Certificate Transparency, so
 *     mis-issuance is detectable. Acceptable for an app on an irregular
 *     release cadence.
 *
 * Captured live 2026-06-01 (github.com + api.github.com share the SAME chain
 * below the leaf). GitHub migrated to Sectigo — earlier GTS/DigiCert pins are
 * stale. Re-capture with the cert-pin-refresh skill before any refresh; never
 * copy these forward blind.
 *
 * PIN MAINTENANCE: when the intermediate (2036-03-21) or root (2038-01-18)
 * approaches expiry, OR if GitHub switches CA again, re-run capture_pins.py
 * against both hosts and update the constants below. Until then the leaf may
 * rotate freely with no app change.
 */
object GitHubCertificatePins {

    /** Sectigo Public Server Authentication CA DV E36 — intermediate. Exp 2036-03-21. */
    const val INTERMEDIATE_SECTIGO_E36 =
        "sha256/ZSagvDzjltLkewXEBuDxIzpW/dpVw1Juvvmd0hhkzdY="

    /** Sectigo Public Server Authentication Root E46 — root. Exp 2038-01-18. */
    const val ROOT_SECTIGO_E46 =
        "sha256/sLVjNUaFYfW7n6EtgBeEpjOlcnBdNPMrZDRF36iwBdE="

    /** Hosts the publish + OAuth flows reach. Both share the chain above. */
    val PINNED_HOSTS: List<String> = listOf("github.com", "api.github.com")

    /**
     * Builds an OkHttp [CertificatePinner] pinning each host to the
     * intermediate + root (OR semantics: a connection succeeds if either pin
     * matches, so the leaf can rotate under the same intermediate freely).
     */
    fun pinner(): CertificatePinner {
        val builder = CertificatePinner.Builder()
        PINNED_HOSTS.forEach { host ->
            builder.add(host, INTERMEDIATE_SECTIGO_E36, ROOT_SECTIGO_E46)
        }
        return builder.build()
    }
}
