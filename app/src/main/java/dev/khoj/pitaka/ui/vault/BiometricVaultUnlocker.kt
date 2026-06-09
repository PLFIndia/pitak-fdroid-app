package dev.khoj.pitaka.ui.vault

import androidx.fragment.app.FragmentActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import dev.khoj.pitaka.domain.usecase.EnableVaultUseCase
import dev.khoj.pitaka.domain.usecase.UnlockVaultUseCase

/**
 * Thin wrapper around AndroidX BiometricPrompt, shared by two callers with
 * DIFFERENT authenticator needs:
 *
 *  - **App Lock** (the lighter UI gate, PLAN decision A): the key it protects
 *    is NOT auth-bound, so it can use the fast [APPLOCK_AUTHENTICATORS] =
 *    BIOMETRIC_WEAK | DEVICE_CREDENTIAL (fingerprint/face, PIN fallback). This
 *    is the DEFAULT.
 *
 *  - **Vault unlock** (audit F-06 / decision A3): the vault key IS auth-bound
 *    with a time-bound validity window, advanced ONLY by DEVICE_CREDENTIAL or a
 *    Class-3 STRONG biometric — a WEAK auth does NOT advance it. So the vault
 *    call site passes [VAULT_AUTHENTICATORS] = DEVICE_CREDENTIAL only. (STRONG
 *    is deliberately omitted: pairing STRONG+DEVICE_CREDENTIAL is the documented
 *    Android-14 `mTokenEscrow is null` silent-fail.)
 *
 * The authenticator set is a per-call parameter, NOT a shared constant —
 * collapsing both into one set is the bug that made enabling App Lock biometric
 * silently route through the device-credential sheet and bypass the PIN gate.
 */
object BiometricVaultUnlocker {

    fun canAuthenticate(
        activity: FragmentActivity,
        authenticators: Int = APPLOCK_AUTHENTICATORS,
    ): CanAuthenticate {
        val manager = BiometricManager.from(activity)
        return when (manager.canAuthenticate(authenticators)) {
            BiometricManager.BIOMETRIC_SUCCESS -> CanAuthenticate.Yes
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> CanAuthenticate.NoHardware
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> CanAuthenticate.NotEnrolled
            else -> CanAuthenticate.Unavailable
        }
    }

    fun prompt(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        errorFormat: String,
        onSuccess: () -> Unit,
        onError: (CharSequence) -> Unit,
        onCancel: () -> Unit,
        authenticators: Int = APPLOCK_AUTHENTICATORS,
        negativeButtonText: String? = null,
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                when (errorCode) {
                    BiometricPrompt.ERROR_USER_CANCELED,
                    BiometricPrompt.ERROR_NEGATIVE_BUTTON -> onCancel()
                    else -> onError(String.format(errorFormat, errorCode, errString.toString()))
                }
            }
            override fun onAuthenticationFailed() {
                // Wrong fingerprint — prompt stays up; user can retry. No action.
            }
        })
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(authenticators)
        // AndroidX rule: a negative button is REQUIRED unless DEVICE_CREDENTIAL
        // is among the allowed authenticators (which supplies its own cancel).
        // Omitting it with a biometric-only set throws IllegalArgumentException
        // ("Negative text must be set and non-empty") at build().
        val hasDeviceCredential =
            authenticators and BiometricManager.Authenticators.DEVICE_CREDENTIAL != 0
        if (!hasDeviceCredential) {
            require(!negativeButtonText.isNullOrEmpty()) {
                "negativeButtonText is required when DEVICE_CREDENTIAL is not allowed"
            }
            builder.setNegativeButtonText(negativeButtonText)
        }
        prompt.authenticate(builder.build())
    }

    /**
     * App Lock auto-prompt — biometric ONLY (no DEVICE_CREDENTIAL). Cancelling
     * or failing must drop the user to the app's OWN Pitaka-PIN field, NOT the
     * system device-credential sheet. Mixing in DEVICE_CREDENTIAL here is what
     * stranded the PIN field behind the system PIN sheet.
     */
    const val APPLOCK_BIOMETRIC_ONLY =
        BiometricManager.Authenticators.BIOMETRIC_WEAK

    /**
     * App Lock gate (full set). BIOMETRIC_WEAK | DEVICE_CREDENTIAL — used where
     * a device-credential fallback IS wanted (e.g. the Forgot-PIN reset).
     */
    const val APPLOCK_AUTHENTICATORS =
        BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL

    enum class CanAuthenticate { Yes, NoHardware, NotEnrolled, Unavailable }
}