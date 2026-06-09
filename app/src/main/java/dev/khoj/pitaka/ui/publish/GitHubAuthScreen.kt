package dev.khoj.pitaka.ui.publish

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.publish.github.GitHubDeviceFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubAuthScreen(
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    viewModel: GitHubAuthViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current

    LaunchedEffect(state.flowState) {
        if (state.flowState is GitHubDeviceFlow.FlowState.Success) onSignedIn()
    }
    LaunchedEffect(state.patSaved) {
        if (state.patSaved) onSignedIn()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.gh_auth_title)) },
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
            Text(stringResource(R.string.gh_auth_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            OutlinedTextField(
                value = state.clientId,
                onValueChange = viewModel::onClientIdChange,
                label = { Text(stringResource(R.string.gh_auth_client_id)) },
                isError = state.clientIdError,
                supportingText = if (state.clientIdError) {
                    { Text(stringResource(R.string.gh_auth_client_id_error)) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { viewModel.onStartOauth() },
                enabled = state.clientId.trim().isNotEmpty() && state.flowState !is GitHubDeviceFlow.FlowState.AwaitingUser,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.gh_auth_use_oauth))
            }

            FlowStatusBlock(
                flowState = state.flowState,
                onOpenVerification = { uri ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uri)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    ctx.startActivity(intent)
                },
            )

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Text(stringResource(R.string.gh_auth_use_pat),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(
                value = state.patInput,
                onValueChange = viewModel::onPatChange,
                label = { Text(stringResource(R.string.gh_auth_pat)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                onClick = viewModel::onSavePat,
                enabled = state.patInput.trim().isNotEmpty(),
            ) { Text(stringResource(R.string.gh_auth_save_pat)) }
        }
    }
}

@Composable
private fun FlowStatusBlock(
    flowState: GitHubDeviceFlow.FlowState?,
    onOpenVerification: (String) -> Unit,
) {
    when (flowState) {
        null,
        GitHubDeviceFlow.FlowState.Starting -> Unit
        is GitHubDeviceFlow.FlowState.AwaitingUser -> {
            Text(stringResource(R.string.gh_auth_user_code_label),
                style = MaterialTheme.typography.bodyMedium)
            Text(flowState.userCode, style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold)
            Text(flowState.verificationUri, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onOpenVerification(flowState.verificationUri) }) {
                    Text(stringResource(R.string.gh_auth_user_code_open))
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(16.dp))
                Spacer(Modifier.height(0.dp))
                Text("  " + stringResource(R.string.gh_auth_polling),
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        is GitHubDeviceFlow.FlowState.Success ->
            Text(stringResource(R.string.gh_auth_signed_in), style = MaterialTheme.typography.titleMedium)
        GitHubDeviceFlow.FlowState.Denied ->
            Text(stringResource(R.string.gh_auth_denied), color = MaterialTheme.colorScheme.error)
        GitHubDeviceFlow.FlowState.Expired ->
            Text(stringResource(R.string.gh_auth_expired), color = MaterialTheme.colorScheme.error)
        is GitHubDeviceFlow.FlowState.Failed ->
            Text(stringResource(R.string.gh_auth_failed, flowState.reason),
                color = MaterialTheme.colorScheme.error)
    }
}
