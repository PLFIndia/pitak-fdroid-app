package dev.khoj.pitaka.ui.applock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.security.AppLockManager
import dev.khoj.pitaka.ui.vault.BiometricVaultUnlocker
import kotlinx.coroutines.delay

/**
 * Full-screen App Lock gate. Shown as an opaque overlay above the app whenever
 * App Lock is enabled and the session is locked.
 *
 * Biometric-first when enabled (auto-prompts on appear), with "Use PIN" as the
 * always-available fallback. PIN entry shows the escalating-throttle countdown
 * after too many wrong attempts. Forgot-PIN resets via device credential.
 */
@Composable
fun AppLockScreen(
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var remainingMs by remember { mutableStateOf(0L) }

    val wrongPinMsg = stringResource(R.string.applock_wrong_pin)
    val biometricSubtitle = stringResource(R.string.applock_unlock_biometric_subtitle)
    val biometricTitle = stringResource(R.string.applock_locked_title)
    val biometricErrorFmt = stringResource(R.string.vault_biometric_auth_error)

    fun showBiometric() {
        val act = activity ?: return
        BiometricVaultUnlocker.prompt(
            activity = act,
            title = biometricTitle,
            subtitle = biometricSubtitle,
            errorFormat = biometricErrorFmt,
            onSuccess = { viewModel.unlockViaBiometric() },
            onError = {},   // fall back to the always-visible Pitaka-PIN field
            onCancel = {},  // fall back to the always-visible Pitaka-PIN field
            // Biometric ONLY — cancelling must reveal the app's PIN field,
            // not the system device-credential sheet.
            authenticators = BiometricVaultUnlocker.APPLOCK_BIOMETRIC_ONLY,
            // Required by AndroidX when DEVICE_CREDENTIAL is not allowed.
            negativeButtonText = context.getString(R.string.applock_cancel),
        )
    }

    // Auto-prompt biometric once on first appearance if enabled.
    LaunchedEffect(Unit) {
        if (ui.biometricEnabled && activity != null) showBiometric()
    }

    // Throttle countdown ticker.
    LaunchedEffect(remainingMs) {
        if (remainingMs > 0) {
            while (remainingMs > 0) {
                delay(1000)
                remainingMs = (remainingMs - 1000).coerceAtLeast(0)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        ) {
            Text(
                stringResource(R.string.applock_locked_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))

            val throttled = remainingMs > 0
            OutlinedTextField(
                value = pin,
                onValueChange = { new -> if (new.all { it.isDigit() }) { pin = new; error = null } },
                label = { Text(stringResource(R.string.applock_enter_pin_title)) },
                singleLine = true,
                enabled = !throttled,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(8.dp))
                Text(error!!, color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall)
            }
            if (throttled) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.applock_throttle_wait, (remainingMs / 1000).toInt()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    viewModel.verifyAtGate(pin.toCharArray()) { result ->
                        when (result) {
                            is AppLockManager.Result.Success -> { pin = "" }
                            is AppLockManager.Result.Wrong -> { error = wrongPinMsg; pin = "" }
                            is AppLockManager.Result.Throttled -> { remainingMs = result.remainingMs; pin = "" }
                            is AppLockManager.Result.NotSet -> { /* gate shouldn't show */ }
                        }
                    }
                },
                enabled = !throttled && pin.length >= AppLockManager.MIN_PIN_LENGTH,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.applock_continue)) }

            if (ui.biometricEnabled && activity != null) {
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = { showBiometric() }) {
                    Text(stringResource(R.string.applock_use_biometric))
                }
            }

            Spacer(Modifier.height(24.dp))
            // Forgot PIN → device-credential reset (no data loss).
            TextButton(onClick = {
                val act = activity ?: return@TextButton
                BiometricVaultUnlocker.prompt(
                    activity = act,
                    title = biometricTitle,
                    subtitle = biometricSubtitle,
                    errorFormat = biometricErrorFmt,
                    onSuccess = { viewModel.resetAfterDeviceAuth() },
                    onError = {},
                    onCancel = {},
                )
            }) {
                Text(stringResource(R.string.applock_forgot_pin))
            }
        }
    }
}
