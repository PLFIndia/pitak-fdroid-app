package dev.khoj.pitaka.ui.loans

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Borrower
import dev.khoj.pitaka.ui.contribute.LocalizedText
import dev.khoj.pitaka.ui.contribute.contributorLongPress
import dev.khoj.pitaka.ui.vault.VaultGate
import dev.khoj.pitaka.ui.vault.VaultViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Top-level Loans surface (replaces the Phase 1 placeholder). Wraps the three
 * tabs in a [VaultGate] so unlock happens once for the whole area.
 *
 * Includes a small "lock" affordance for users who want to lock the vault
 * before leaving the screen — supplementing D1's automatic backgrounding lock.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoansScreen(
    onOpenBorrower: (Long) -> Unit,
    onOpenPending: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.loans_title)) },
                actions = {
                    val vm: VaultViewModel = hiltViewModel()
                    val state by vm.state.collectAsStateWithLifecycle()
                    IconButton(onClick = onOpenPending) {
                        Icon(
                            Icons.Filled.NotificationsActive,
                            contentDescription = stringResource(R.string.library_pending_cd),
                        )
                    }
                    if (state.isUnlocked) {
                        IconButton(onClick = { vm.onLock() }) {
                            Icon(Icons.Filled.Lock, contentDescription = stringResource(R.string.vault_lock_action))
                        }
                    }
                },
            )
        },
    ) { padding ->
        VaultGate(contentPadding = padding) {
            LoansBody(
                contentPadding = padding,
                onOpenBorrower = onOpenBorrower,
                onOpenPending = onOpenPending,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LoansBody(
    contentPadding: PaddingValues,
    onOpenBorrower: (Long) -> Unit,
    onOpenPending: () -> Unit,
    viewModel: LoansViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
    ) {
        SecondaryTabRow(selectedTabIndex = tab) {
            Tab(
                modifier = Modifier.contributorLongPress(R.string.loans_tab_active),
                selected = tab == 0,
                onClick = { tab = 0 },
                text = { LocalizedText(R.string.loans_tab_active, passthroughTap = true) },
            )
            Tab(
                modifier = Modifier.contributorLongPress(R.string.loans_tab_history),
                selected = tab == 1,
                onClick = { tab = 1 },
                text = { LocalizedText(R.string.loans_tab_history, passthroughTap = true) },
            )
            Tab(
                modifier = Modifier.contributorLongPress(R.string.loans_tab_borrowers),
                selected = tab == 2,
                onClick = { tab = 2 },
                text = { LocalizedText(R.string.loans_tab_borrowers, passthroughTap = true) },
            )
        }

        when (tab) {
            0 -> ActiveTab(state, viewModel::onReturnLoan)
            1 -> HistoryTab(state)
            2 -> BorrowersTab(state, onOpenBorrower)
        }
    }
}

@Composable
private fun ActiveTab(state: LoansUiState, onMarkReturned: (Long) -> Unit) {
    if (state.active.isEmpty()) {
        EmptyHero(
            headline = R.string.loans_empty_headline,
            body = R.string.loans_empty_body,
        )
        return
    }
    val now = remember { System.currentTimeMillis() }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.active, key = { it.loan.id }) { row ->
            ActiveLoanRow(row, now, onMarkReturned)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun HistoryTab(state: LoansUiState) {
    if (state.returned.isEmpty()) {
        EmptyHero(headline = R.string.loans_history_empty, body = null)
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(state.returned, key = { it.loan.id }) { row ->
            ReturnedLoanRow(row)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun BorrowersTab(state: LoansUiState, onOpenBorrower: (Long) -> Unit) {
    if (state.borrowers.isEmpty()) {
        EmptyHero(
            headline = null,
            body = R.string.borrowers_empty,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(state.borrowers, key = { it.id }) { borrower ->
            BorrowerRow(borrower) { onOpenBorrower(borrower.id) }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun ActiveLoanRow(row: ResolvedLoan, now: Long, onMarkReturned: (Long) -> Unit) {
    val loan = row.loan
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(row.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(
            row.borrowerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.loan_lent_date, formatDate(loan.lentDate)),
                    style = MaterialTheme.typography.bodySmall,
                )
                if (loan.dueDate != null) {
                    val isOverdue = now > loan.dueDate
                    val txt = if (isOverdue) stringResource(R.string.loan_overdue)
                              else stringResource(R.string.loan_due_date, formatDate(loan.dueDate))
                    Text(
                        txt,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isOverdue) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = { onMarkReturned(loan.id) }) {
                Text(stringResource(R.string.loan_mark_returned))
            }
        }
    }
}

@Composable
private fun ReturnedLoanRow(row: ResolvedLoan) {
    val loan = row.loan
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(row.bookTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
        Text(
            row.borrowerName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.loan_returned_date, formatDate(loan.returnedDate ?: 0L)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BorrowerRow(borrower: Borrower, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
    ) {
        Column {
            Text(borrower.name, style = MaterialTheme.typography.titleMedium)
            if (!borrower.contact.isNullOrBlank()) {
                Text(
                    borrower.contact,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun EmptyHero(headline: Int?, body: Int?) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (headline != null) {
                LocalizedText(headline, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
            }
            if (body != null) {
                LocalizedText(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private val DATE_FMT = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
internal fun formatDate(epochMs: Long): String =
    if (epochMs <= 0L) "—" else DATE_FMT.format(Date(epochMs))
