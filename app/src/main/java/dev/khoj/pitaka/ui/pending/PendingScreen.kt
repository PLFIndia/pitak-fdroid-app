package dev.khoj.pitaka.ui.pending

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.ui.loans.formatDate
import dev.khoj.pitaka.ui.vault.VaultGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingScreen(
    onBack: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onSetBackupPassphrase: () -> Unit,
    onOpenBackup: () -> Unit,
    viewModel: PendingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pending_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        VaultGate(contentPadding = padding) {
            LaunchedEffect(Unit) { viewModel.refresh() }
            PendingBody(state, padding, onOpenBook, onSetBackupPassphrase, onOpenBackup)
        }
    }
}

@Composable
private fun PendingBody(
    state: PendingUiState,
    contentPadding: PaddingValues,
    onOpenBook: (Long) -> Unit,
    onSetBackupPassphrase: () -> Unit,
    onOpenBackup: () -> Unit,
) {
    val snap = state.snapshot
    if (snap == null && !state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.pending_empty), style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    snap ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snap.backupPassphraseNeeded) {
            BackupPassphraseBanner(onSetBackupPassphrase)
        }
        snap.backupStaleDays?.let { days ->
            BackupStaleBanner(days = days, onOpenBackup = onOpenBackup)
        }
        if (snap.overdue.isNotEmpty()) {
            Section(stringResource(R.string.pending_section_overdue), MaterialTheme.colorScheme.errorContainer) {
                snap.overdue.forEach { loan ->
                    SmallRow(
                        stringResource(R.string.pending_loan_id_label, loan.id),
                        stringResource(R.string.pending_loan_lent, formatDate(loan.lentDate)),
                    )
                }
            }
        }
        if (snap.dueSoon.isNotEmpty()) {
            Section(stringResource(R.string.pending_section_due_soon), MaterialTheme.colorScheme.tertiaryContainer) {
                snap.dueSoon.forEach { loan ->
                    SmallRow(
                        stringResource(R.string.pending_loan_id_label, loan.id),
                        stringResource(R.string.pending_loan_due, formatDate(loan.dueDate ?: 0L)),
                    )
                }
            }
        }
        if (snap.staleMetadataBooks.isNotEmpty()) {
            Section(stringResource(R.string.pending_section_stale), MaterialTheme.colorScheme.surfaceVariant) {
                snap.staleMetadataBooks.forEach { book ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(book.title, style = MaterialTheme.typography.bodyLarge)
                            if (!book.isbn.isNullOrBlank()) {
                                Text(
                                    stringResource(R.string.pending_isbn_value, book.isbn),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onOpenBook(book.id) }) { Text(stringResource(R.string.common_open)) }
                    }
                }
            }
        }
        if (snap.isEmpty) {
            Text(stringResource(R.string.pending_empty), style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun BackupPassphraseBanner(onSet: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.pending_backup_banner_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pending_backup_banner_body), style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSet) { Text(stringResource(R.string.pending_backup_banner_action)) }
        }
    }
}

@Composable
private fun BackupStaleBanner(days: Int, onOpenBackup: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.pending_backup_stale_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.pending_backup_stale_body, days),
                style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenBackup) {
                Text(stringResource(R.string.pending_backup_stale_action))
            }
        }
    }
}

@Composable
private fun Section(title: String, container: androidx.compose.ui.graphics.Color, content: @Composable () -> Unit) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Surface(color = container, shape = RoundedCornerShape(8.dp)) {
            Column(modifier = Modifier.padding(12.dp)) { content() }
        }
    }
    HorizontalDivider()
}

@Composable
private fun SmallRow(headline: String, body: String) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(headline, style = MaterialTheme.typography.bodyLarge)
        Text(body, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
