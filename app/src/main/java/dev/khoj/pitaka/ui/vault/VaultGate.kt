package dev.khoj.pitaka.ui.vault

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R

/**
 * Gates [content] behind a vault unlock. The gate is a full-screen panel
 * shown when locked; once unlocked, [content] composes underneath.
 *
 * D18: enabling the vault is one biometric tap. The screen explains what
 * the vault is before prompting.
 */
@Composable
fun VaultGate(
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: VaultViewModel = hiltViewModel(),
    content: @Composable () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                VaultEvent.Unlocked -> Unit
                is VaultEvent.Error -> Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    if (state.isUnlocked) {
        content()
        return
    }

    val activity = context as? FragmentActivity ?: run {
        Text("Vault requires a FragmentActivity host.")
        return
    }

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(8.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.vault_locked_headline),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    if (state.isInitialized) R.string.vault_locked_body_existing
                    else R.string.vault_locked_body_new
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            // Be explicit about WHICH credential unlocks the vault: the device
            // credential / biometric, NOT the in-app Pitaka PIN. The warning
            // half is rendered in the error colour for emphasis.
            Text(
                buildAnnotatedString {
                    append(stringResource(R.string.vault_unlock_method_note))
                    append("  ")
                    withStyle(
                        SpanStyle(
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Bold,
                        )
                    ) {
                        append(stringResource(R.string.vault_unlock_method_warning_not))
                    }
                    append(" ")
                    append(stringResource(R.string.vault_unlock_method_warning_rest))
                },
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = {
                    val title = context.getString(R.string.vault_prompt_title)
                    val subtitle = context.getString(
                        if (state.isInitialized) R.string.vault_prompt_subtitle_unlock
                        else R.string.vault_prompt_subtitle_enable
                    )
                    BiometricVaultUnlocker.prompt(
                        activity = activity,
                        title = title,
                        subtitle = subtitle,
                        errorFormat = context.getString(R.string.vault_biometric_auth_error),
                        onSuccess = {
                            if (state.isInitialized) viewModel.onUnlockConfirmed()
                            else viewModel.onEnableConfirmed()
                        },
                        onError = { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        },
                        onCancel = {},
                        // Vault key is non-auth-bound (F-06 software guard), so
                        // the prompt can use biometric + device-credential
                        // fallback — fingerprint works again.
                        authenticators = BiometricVaultUnlocker.APPLOCK_AUTHENTICATORS,
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (state.isInitialized) R.string.vault_unlock_button
                        else R.string.vault_enable_button
                    )
                )
            }
        }
    }
}
