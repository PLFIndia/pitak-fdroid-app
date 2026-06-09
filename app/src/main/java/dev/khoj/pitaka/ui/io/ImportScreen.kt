package dev.khoj.pitaka.ui.io

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ExportImportViewModel = hiltViewModel(),
) {
    val state by viewModel.importState.collectAsStateWithLifecycle()

    val openDocumentLauncher = rememberLauncherForActivityResult(
        // ACTION_OPEN_DOCUMENT, broad MIME so the user can pick JSON or CSV.
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.onFilePicked(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_title)) },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.import_supported), style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = {
                    openDocumentLauncher.launch(
                        arrayOf(
                            "application/json", "text/csv", "text/plain",
                            "application/zip", "text/*", "*/*",
                        ),
                    )
                },
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.import_pick))
            }

            if (state.running) {
                Spacer(Modifier.height(8.dp))
                CircularProgressIndicator()
            }

            state.errorText?.let { msg ->
                Text(msg, color = MaterialTheme.colorScheme.error)
            }

            state.summary?.let { summary ->
                Spacer(Modifier.height(8.dp))
                if (summary.isFailure) {
                    Text(stringResource(R.string.import_failure), style = MaterialTheme.typography.titleMedium)
                } else {
                    Text(stringResource(R.string.import_summary_done), style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.import_summary_format, summary.format?.name.orEmpty()))
                    Text(stringResource(R.string.import_summary_books, summary.booksAdded, summary.booksSkipped))
                    Text(stringResource(R.string.import_summary_wishlist, summary.wishlistAdded, summary.wishlistReplaced))
                    if (summary.parseErrors.isNotEmpty()) {
                        Text(stringResource(R.string.import_summary_errors, summary.parseErrors.size))
                    }
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text(stringResource(R.string.import_close))
                }
            }
        }
    }
}
