package dev.khoj.pitaka.ui.publish

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.usecase.PublishLibraryUseCase
import dev.khoj.pitaka.ui.contribute.LocalizedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishScreen(
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenCfWizard: () -> Unit,
    viewModel: PublishViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.publish_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.publish_intro), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            if (state.signedIn) {
                Text(stringResource(R.string.publish_auth_status_signed_in, state.username ?: "…"),
                    style = MaterialTheme.typography.bodyLarge)
            } else {
                Text(stringResource(R.string.publish_auth_status_signed_out),
                    style = MaterialTheme.typography.bodyLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onSignIn) {
                    Text(stringResource(R.string.publish_sign_in))
                }
                if (state.signedIn) {
                    TextButton(onClick = { viewModel.onSignOut() }) {
                        Text(stringResource(R.string.publish_sign_out))
                    }
                }
            }

            HorizontalDivider()
            Text(
                state.targetRepo?.let { stringResource(R.string.publish_target_repo, it) }
                    ?: stringResource(R.string.publish_target_repo_none),
                style = MaterialTheme.typography.bodyLarge,
            )
            OutlinedButton(
                onClick = { viewModel.onPickRepo() },
                enabled = state.signedIn,
            ) { Text(stringResource(R.string.publish_pick_repo)) }

            HorizontalDivider()
            // Optional contact info rendered on the PUBLIC page (typed fields:
            // location/GPS, email, phone). Persisted immediately; every publish
            // overwrites the live values, so clearing a field removes it. Each
            // uses EditableField: shows the saved value with an Edit button,
            // opens an input box on edit, collapses on save.
            LocalizedText(R.string.publish_contact_title,
                style = MaterialTheme.typography.titleSmall)
            LocalizedText(R.string.publish_contact_caution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            dev.khoj.pitaka.ui.components.EditableField(
                labelRes = R.string.publish_contact_location_label,
                value = state.contactLocation,
                onSave = viewModel::onContactLocationChange,
                placeholder = stringResource(R.string.publish_contact_location_hint),
            )
            dev.khoj.pitaka.ui.components.EditableField(
                labelRes = R.string.publish_contact_email_label,
                value = state.contactEmail,
                onSave = viewModel::onContactEmailChange,
                placeholder = stringResource(R.string.publish_contact_email_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
            )
            dev.khoj.pitaka.ui.components.EditableField(
                labelRes = R.string.publish_contact_phone_label,
                value = state.contactPhone,
                onSave = viewModel::onContactPhoneChange,
                placeholder = stringResource(R.string.publish_contact_phone_hint),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone,
            )

            HorizontalDivider()
            Button(
                onClick = { viewModel.onPublishNow() },
                enabled = state.signedIn && state.targetRepo != null && !state.publishing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.publishing) {
                    Text(phaseLabel(state.phase))
                } else {
                    Text(stringResource(R.string.publish_now))
                }
            }
            state.lastResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            state.pagesUrl?.let { url ->
                TextButton(onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                }) { Text(stringResource(R.string.publish_open_in_browser)) }
                Text(url, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.publish_pages_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider()
            TextButton(onClick = onOpenCfWizard) {
                Text(stringResource(R.string.publish_cf_wizard_link))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (state.repos.isNotEmpty() || state.isLoadingRepos) {
        AlertDialog(
            onDismissRequest = { viewModel.refresh() },
            confirmButton = { TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.common_close)) } },
            title = { Text(stringResource(R.string.publish_pick_repo)) },
            text = {
                if (state.isLoadingRepos) {
                    Row { CircularProgressIndicator() }
                } else {
                    LazyColumn {
                        items(state.repos, key = { it.fullName ?: it.name ?: "" }) { repo ->
                            TextButton(
                                onClick = { viewModel.onChooseRepo(repo) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text(repo.fullName ?: repo.name ?: stringResource(R.string.publish_repo_unnamed)) }
                        }
                    }
                }
            },
        )
    }
}

/** Maps the coarse publish phase to a button label (falls back to a generic one). */
@Composable
private fun phaseLabel(phase: PublishLibraryUseCase.Phase?): String = stringResource(
    when (phase) {
        PublishLibraryUseCase.Phase.PREPARING -> R.string.publish_phase_preparing
        PublishLibraryUseCase.Phase.UPLOADING -> R.string.publish_phase_uploading
        PublishLibraryUseCase.Phase.COMMITTING -> R.string.publish_phase_committing
        PublishLibraryUseCase.Phase.PAGES_BUILDING -> R.string.publish_phase_pages_building
        null -> R.string.publish_progress
    }
)
