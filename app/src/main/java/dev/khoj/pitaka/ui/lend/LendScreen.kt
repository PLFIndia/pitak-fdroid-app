package dev.khoj.pitaka.ui.lend

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.ui.vault.VaultGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LendScreen(
    onBack: () -> Unit,
    onLent: () -> Unit,
    viewModel: LendViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is LendEvent.Lent -> onLent()
                LendEvent.BorrowerRequired ->
                    Toast.makeText(context, context.getString(R.string.lend_no_borrower), Toast.LENGTH_SHORT).show()
                LendEvent.VaultLocked ->
                    Toast.makeText(context, context.getString(R.string.lend_vault_locked_toast), Toast.LENGTH_SHORT).show()
                is LendEvent.Error ->
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.lend_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        VaultGate(contentPadding = padding) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.lend_book_label), style = MaterialTheme.typography.titleSmall)
                Text(form.book?.title ?: "—", style = MaterialTheme.typography.bodyLarge)
                form.book?.author?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                HorizontalDivider()
                Text(stringResource(R.string.lend_borrower_existing), style = MaterialTheme.typography.titleSmall)
                if (form.borrowers.isEmpty()) {
                    Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        form.borrowers.take(10).forEach { borrower ->
                            FilterChip(
                                selected = form.selectedBorrowerId == borrower.id,
                                onClick = {
                                    viewModel.onPickExisting(
                                        if (form.selectedBorrowerId == borrower.id) null else borrower.id
                                    )
                                },
                                label = { Text(borrower.name) },
                            )
                        }
                    }
                }

                Text(stringResource(R.string.lend_borrower_new), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = form.newBorrowerName,
                    onValueChange = viewModel::onNewNameChange,
                    label = { Text(stringResource(R.string.lend_borrower_name)) },
                    singleLine = true,
                    enabled = form.selectedBorrowerId == null,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = form.newBorrowerContact,
                    onValueChange = viewModel::onNewContactChange,
                    label = { Text(stringResource(R.string.lend_borrower_contact)) },
                    singleLine = true,
                    enabled = form.selectedBorrowerId == null,
                    modifier = Modifier.fillMaxWidth(),
                )

                HorizontalDivider()
                Text(
                    if (form.dueDate == null) stringResource(R.string.lend_due_date_label)
                    else stringResource(R.string.lend_due_value, dev.khoj.pitaka.ui.loans.formatDate(form.dueDate!!)),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        // 7 days from now
                        viewModel.onDueDateChange(System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000)
                    }) { Text(stringResource(R.string.lend_quick_7_days)) }
                    TextButton(onClick = {
                        viewModel.onDueDateChange(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000)
                    }) { Text(stringResource(R.string.lend_quick_30_days)) }
                    TextButton(onClick = { viewModel.onDueDateChange(null) }) { Text(stringResource(R.string.lend_clear_due_date)) }
                }

                OutlinedTextField(
                    value = form.notes,
                    onValueChange = viewModel::onNotesChange,
                    label = { Text(stringResource(R.string.lend_notes_label)) },
                    supportingText = { Text(stringResource(R.string.lend_notes_support)) },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.add_book_cancel))
                    }
                    Button(onClick = viewModel::onSave, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.lend_save))
                    }
                }
            }
        }
    }
}
