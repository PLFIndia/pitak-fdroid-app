package dev.khoj.pitaka.ui.merge

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.usecase.MergeLibraryUseCase

/**
 * Community-library merge screen (PLAN-merge.md D40). Opens a file picker, runs
 * the merge use case, and renders one of: result summary, the Join/Overwrite
 * decision (different library), or an error. Add-only auto-applies; conflicts +
 * possible-duplicates are surfaced as counts for the user's awareness (the
 * per-row resolution screen is a later refinement).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MergeScreen(
    onBack: () -> Unit,
    viewModel: MergeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickerLaunched by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.onFilePicked(uri) else onBack()
    }

    // Auto-open the picker once on entry.
    LaunchedEffect(Unit) {
        if (!pickerLaunched) {
            pickerLaunched = true
            picker.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_merge_from_file)) },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (val s = state) {
                is MergeViewModel.UiState.Idle -> {
                    Text(stringResource(R.string.settings_merge_help))
                    OutlinedButton(
                        onClick = { picker.launch(arrayOf("application/json", "text/plain", "*/*")) },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.settings_merge_from_file)) }
                }

                is MergeViewModel.UiState.Running -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) { CircularProgressIndicator() }
                }

                is MergeViewModel.UiState.Merged -> {
                    Text(
                        stringResource(R.string.merge_result_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val reviewCount = s.result.conflicts.size + s.result.possibleDuplicates.size
                    Text(
                        stringResource(
                            R.string.merge_result_summary,
                            s.result.added, s.result.identical, reviewCount,
                        )
                    )
                    if (reviewCount > 0) {
                        Text(
                            "Items that differ were left as-is for now — your existing books were not changed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_book_cancel))
                    }
                }

                is MergeViewModel.UiState.Differ -> DifferDecision(
                    decision = s.decision,
                    onJoin = { viewModel.onJoin(s.decision) },
                    onOverwrite = { viewModel.onOverwrite(s.decision) },
                    onCancel = onBack,
                )

                is MergeViewModel.UiState.Error -> {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_book_cancel))
                    }
                }
            }
        }
    }
}

@Composable
private fun DifferDecision(
    decision: MergeLibraryUseCase.Outcome.DiffersDecision,
    onJoin: () -> Unit,
    onOverwrite: () -> Unit,
    onCancel: () -> Unit,
) {
    var showOverwriteConfirm by remember { mutableStateOf(false) }

    Text(
        stringResource(R.string.merge_differ_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    val body = if (decision.incomingLibraryName.isNotBlank()) {
        stringResource(
            R.string.merge_differ_body,
            decision.incomingLibraryName,
            decision.localLibraryName.ifBlank { stringResource(R.string.settings_library_id_label) },
        )
    } else {
        stringResource(R.string.merge_differ_body_unknown)
    }
    Text(body)

    // Join is the default (filled, primary).
    Button(onClick = onJoin, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.merge_action_join))
    }
    // Overwrite is the guarded secondary; confirm before destroying local data.
    OutlinedButton(onClick = { showOverwriteConfirm = true }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.merge_action_overwrite))
    }
    TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.merge_cancel))
    }

    if (showOverwriteConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showOverwriteConfirm = false },
            title = { Text(stringResource(R.string.merge_overwrite_confirm_title)) },
            text = { Text(stringResource(R.string.merge_overwrite_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showOverwriteConfirm = false
                    onOverwrite()
                }) { Text(stringResource(R.string.merge_action_overwrite)) }
            },
            dismissButton = {
                TextButton(onClick = { showOverwriteConfirm = false }) {
                    Text(stringResource(R.string.merge_cancel))
                }
            },
        )
    }
}
