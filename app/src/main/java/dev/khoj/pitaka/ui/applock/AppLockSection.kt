package dev.khoj.pitaka.ui.applock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import dev.khoj.pitaka.ui.contribute.LocalizedText
import dev.khoj.pitaka.ui.contribute.contributorLongPress
import dev.khoj.pitaka.ui.vault.BiometricVaultUnlocker

/**
 * App Lock section for the Security tab: enable (set PIN), biometric opt-in,
 * change PIN, re-lock timeout, and disable. Self-contained — owns its
 * [AppLockViewModel]. PIN never leaves these dialogs in plaintext beyond the
 * CharArray handed to the manager (which zeroes it).
 */
@Composable
fun AppLockSection(
    viewModel: AppLockViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    var showSetPin by remember { mutableStateOf(false) }
    var showChangePin by remember { mutableStateOf(false) }
    var showDisablePin by remember { mutableStateOf(false) }

    LocalizedText(R.string.applock_title, style = MaterialTheme.typography.titleMedium,
        fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
    LocalizedText(R.string.applock_help,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(4.dp))

    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        LocalizedText(R.string.applock_enable, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium)
        Switch(
            checked = ui.enabled,
            onCheckedChange = { want ->
                if (want) showSetPin = true else showDisablePin = true
            },
        )
    }

    if (ui.enabled) {
        // Biometric opt-in.
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                LocalizedText(R.string.applock_biometric_toggle,
                    style = MaterialTheme.typography.bodyMedium)
                LocalizedText(R.string.applock_biometric_help,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(
                checked = ui.biometricEnabled,
                onCheckedChange = { want ->
                    if (!want) {
                        viewModel.setBiometricEnabled(false)
                    } else {
                        // Confirm a biometric works before enabling it.
                        val act = activity
                        if (act != null) {
                            BiometricVaultUnlocker.prompt(
                                activity = act,
                                title = context.getString(R.string.applock_biometric_toggle),
                                subtitle = context.getString(R.string.applock_unlock_biometric_subtitle),
                                errorFormat = context.getString(R.string.vault_biometric_auth_error),
                                onSuccess = { viewModel.setBiometricEnabled(true) },
                                onError = {},
                                onCancel = {},
                                // Confirm a real biometric is enrolled — biometric only.
                                authenticators = BiometricVaultUnlocker.APPLOCK_BIOMETRIC_ONLY,
                                // Required by AndroidX when DEVICE_CREDENTIAL is not allowed.
                                negativeButtonText = context.getString(R.string.applock_cancel),
                            )
                        }
                    }
                },
            )
        }

        // Re-lock timeout.
        Spacer(Modifier.height(4.dp))
        LocalizedText(R.string.applock_autolock_label, style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(
                1L to R.string.applock_timeout_immediate,
                30_000L to R.string.applock_timeout_30s,
                60_000L to R.string.settings_auto_lock_60s,
                300_000L to R.string.settings_auto_lock_5m,
            ).forEach { (ms, label) ->
                FilterChip(
                    modifier = Modifier.contributorLongPress(label),
                    selected = ui.autoLockTimeoutMs == ms,
                    onClick = { viewModel.setAutoLockTimeout(ms) },
                    label = { LocalizedText(label, passthroughTap = true) },
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { showChangePin = true }, modifier = Modifier.fillMaxWidth()) {
            LocalizedText(R.string.applock_change_pin)
        }
    }

    // --- Dialogs ---

    if (showSetPin) {
        PinDialog(
            titleRes = R.string.applock_set_pin_title,
            requireConfirm = true,
            onDismiss = { showSetPin = false },
            onConfirm = { pin, _ ->
                viewModel.enableWithPin(pin) { /* state refreshes via flow */ }
                showSetPin = false
            },
        )
    }

    if (showChangePin) {
        ChangePinDialog(
            onDismiss = { showChangePin = false },
            onConfirm = { current, new ->
                viewModel.changePin(current, new) { }
                showChangePin = false
            },
        )
    }

    if (showDisablePin) {
        PinDialog(
            titleRes = R.string.applock_current_pin_title,
            requireConfirm = false,
            onDismiss = { showDisablePin = false },
            onConfirm = { pin, _ ->
                viewModel.disableWithPin(pin) { }
                showDisablePin = false
            },
        )
    }
}

/**
 * Generic PIN-entry dialog. When [requireConfirm] is true it shows a second
 * "confirm PIN" field and only enables Continue when the two match and meet the
 * minimum length. Returns (pin, confirm) — confirm is the same as pin when not
 * required.
 */
@Composable
private fun PinDialog(
    titleRes: Int,
    requireConfirm: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (CharArray, CharArray) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = pin.length < AppLockManager.MIN_PIN_LENGTH
    val mismatch = requireConfirm && pin != confirm
    val canConfirm = !tooShort && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(titleRes)) },
        text = {
            Column {
                PinField(label = stringResource(R.string.applock_enter_pin_title), value = pin, onChange = { pin = it })
                if (requireConfirm) {
                    Spacer(Modifier.height(8.dp))
                    PinField(label = stringResource(R.string.applock_confirm_pin_title), value = confirm, onChange = { confirm = it })
                    if (confirm.isNotEmpty() && mismatch) {
                        Text(stringResource(R.string.applock_pin_mismatch),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (pin.isNotEmpty() && tooShort) {
                    Text(stringResource(R.string.applock_pin_too_short),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(pin.toCharArray(), confirm.toCharArray()) },
                enabled = canConfirm,
            ) { Text(stringResource(R.string.applock_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.applock_cancel)) }
        },
    )
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onConfirm: (CharArray, CharArray) -> Unit,
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val tooShort = new.length < AppLockManager.MIN_PIN_LENGTH
    val mismatch = new != confirm
    val canConfirm = current.length >= AppLockManager.MIN_PIN_LENGTH && !tooShort && !mismatch

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.applock_change_pin)) },
        text = {
            Column {
                PinField(label = stringResource(R.string.applock_current_pin_title), value = current, onChange = { current = it })
                Spacer(Modifier.height(8.dp))
                PinField(label = stringResource(R.string.applock_set_pin_title), value = new, onChange = { new = it })
                Spacer(Modifier.height(8.dp))
                PinField(label = stringResource(R.string.applock_confirm_pin_title), value = confirm, onChange = { confirm = it })
                if (confirm.isNotEmpty() && mismatch) {
                    Text(stringResource(R.string.applock_pin_mismatch),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(current.toCharArray(), new.toCharArray()) },
                enabled = canConfirm,
            ) { Text(stringResource(R.string.applock_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.applock_cancel)) }
        },
    )
}

@Composable
private fun PinField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> if (new.all { it.isDigit() }) onChange(new) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
}
