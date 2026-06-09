package dev.khoj.pitaka.ui.io

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.usecase.ExportUseCase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(
    onBack: () -> Unit,
    viewModel: ExportImportViewModel = hiltViewModel(),
) {
    val state by viewModel.exportState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    val shareFailedMessage = stringResource(R.string.export_share_failed)
    val shareChooserTitle = stringResource(R.string.export_share_chooser)

    LaunchedEffect(Unit) {
        viewModel.exportEvents.collect { event ->
            when (event) {
                is ExportEvent.ShareReady -> {
                    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = event.mime
                        putExtra(android.content.Intent.EXTRA_STREAM, event.uri)
                        // Per-Intent read grant — the receiving app can read this one
                        // file and nothing else (least privilege).
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(send, shareChooserTitle)
                    chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(chooser)
                }
                ExportEvent.SaveFailed -> snackbarHostState.showSnackbar(shareFailedMessage)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(stringResource(R.string.export_scope_title), style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = state.scope == ExportUseCase.Scope.LibraryOnly,
                    onClick = { viewModel.onScopeChange(ExportUseCase.Scope.LibraryOnly) },
                    label = { Text(stringResource(R.string.export_scope_library)) },
                )
                FilterChip(
                    selected = state.scope == ExportUseCase.Scope.WishlistOnly,
                    onClick = { viewModel.onScopeChange(ExportUseCase.Scope.WishlistOnly) },
                    label = { Text(stringResource(R.string.export_scope_wishlist)) },
                )
                FilterChip(
                    selected = state.scope == ExportUseCase.Scope.Both,
                    onClick = { viewModel.onScopeChange(ExportUseCase.Scope.Both) },
                    label = { Text(stringResource(R.string.export_scope_both)) },
                )
            }

            Text(stringResource(R.string.export_format_title), style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterChip(
                    selected = state.format == ExportUseCase.Format.Json,
                    onClick = { viewModel.onFormatChange(ExportUseCase.Format.Json) },
                    label = { Text(stringResource(R.string.export_format_json)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = state.format == ExportUseCase.Format.Csv,
                    onClick = { viewModel.onFormatChange(ExportUseCase.Format.Csv) },
                    label = { Text(stringResource(R.string.export_format_csv)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = state.format == ExportUseCase.Format.Bundle,
                    onClick = { viewModel.onFormatChange(ExportUseCase.Format.Bundle) },
                    label = { Text(stringResource(R.string.export_format_bundle)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                FilterChip(
                    selected = state.format == ExportUseCase.Format.Pdf,
                    onClick = { viewModel.onFormatChange(ExportUseCase.Format.Pdf) },
                    label = { Text(stringResource(R.string.export_format_pdf)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            // PDF column picker — only relevant when PDF is the chosen format.
            if (state.format == ExportUseCase.Format.Pdf) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.export_pdf_columns_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    stringResource(R.string.export_pdf_columns_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PdfColumnPicker(
                    selected = state.pdfColumns,
                    onToggle = viewModel::onPdfColumnToggle,
                )
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = viewModel::onRequestSave, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.export_share))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun PdfColumnPicker(
    selected: Set<dev.khoj.pitaka.data.export.PdfColumn>,
    onToggle: (dev.khoj.pitaka.data.export.PdfColumn) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        dev.khoj.pitaka.data.export.PdfColumn.ORDER.forEach { col ->
            val mandatory = col == dev.khoj.pitaka.data.export.PdfColumn.MANDATORY
            FilterChip(
                selected = col in selected || mandatory,
                onClick = { if (!mandatory) onToggle(col) },
                label = { Text(pdfColumnLabel(col)) },
            )
        }
    }
}

@Composable
private fun pdfColumnLabel(col: dev.khoj.pitaka.data.export.PdfColumn): String = stringResource(
    when (col) {
        dev.khoj.pitaka.data.export.PdfColumn.TITLE -> R.string.pdf_col_title
        dev.khoj.pitaka.data.export.PdfColumn.AUTHOR -> R.string.pdf_col_author
        dev.khoj.pitaka.data.export.PdfColumn.YEAR -> R.string.pdf_col_year
        dev.khoj.pitaka.data.export.PdfColumn.ISBN -> R.string.pdf_col_isbn
        dev.khoj.pitaka.data.export.PdfColumn.PUBLISHER -> R.string.pdf_col_publisher
        dev.khoj.pitaka.data.export.PdfColumn.GENRE -> R.string.pdf_col_genre
        dev.khoj.pitaka.data.export.PdfColumn.LANGUAGE -> R.string.pdf_col_language
        dev.khoj.pitaka.data.export.PdfColumn.PAGES -> R.string.pdf_col_pages
        dev.khoj.pitaka.data.export.PdfColumn.AGE_GROUP -> R.string.pdf_col_age_group
        dev.khoj.pitaka.data.export.PdfColumn.QUANTITY -> R.string.pdf_col_quantity
        dev.khoj.pitaka.data.export.PdfColumn.ADDED_DATE -> R.string.pdf_col_added_date
        dev.khoj.pitaka.data.export.PdfColumn.LOCATION -> R.string.pdf_col_location
        dev.khoj.pitaka.data.export.PdfColumn.SOURCE -> R.string.pdf_col_source
        dev.khoj.pitaka.data.export.PdfColumn.SOURCE_DETAIL -> R.string.pdf_col_source_detail
    }
)
