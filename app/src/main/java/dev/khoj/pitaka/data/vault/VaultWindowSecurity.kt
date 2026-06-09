package dev.khoj.pitaka.data.vault

/**
 * Pure policy for whether the app window should carry `FLAG_SECURE`.
 *
 * F-12 (audit): when the vault is unlocked, borrower names, loan lists, and
 * other vault-derived PII render on screen. Without `FLAG_SECURE` the Recents
 * / Overview screenshot captures that PII, and screen-cast / accessibility
 * services can read the pixels. The fix is to set the flag whenever the vault
 * is unlocked and clear it when locked.
 *
 * Kept Android-free and pure so the *decision* is unit-tested; the actual
 * `window.addFlags` / `clearFlags` call lives in [MainActivity] (an Android
 * boundary that can't be exercised without flaky instrumentation).
 */
object VaultWindowSecurity {

    /** True when the window must be FLAG_SECURE for the given vault [state]. */
    fun shouldSecure(state: VaultState): Boolean = state is VaultState.Unlocked
}
