package dev.khoj.pitaka.ui.borrowers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Loan
import dev.khoj.pitaka.ui.loans.formatDate
import dev.khoj.pitaka.ui.vault.VaultGate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BorrowerProfileScreen(
    onBack: () -> Unit,
    viewModel: BorrowerProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.borrower?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        VaultGate(contentPadding = padding) {
            if (state.notFound) {
                Box(modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.borrower_profile_not_found), style = MaterialTheme.typography.titleMedium)
                }
                return@VaultGate
            }
            val borrower = state.borrower ?: return@VaultGate
            Column(
                modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!borrower.contact.isNullOrBlank()) {
                    Text(
                        borrower.contact,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (!borrower.notes.isNullOrBlank()) {
                    Text(borrower.notes, style = MaterialTheme.typography.bodyMedium)
                }

                state.stats?.let { stats ->
                    HorizontalDivider()
                    StatsBlock(stats)
                }

                HorizontalDivider()
                SectionHeader(stringResource(R.string.borrower_section_active))
                if (state.active.isEmpty()) {
                    Text(
                        "—",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.active.forEach { (loan, book) -> LoanLine(loan, book?.title ?: "(book removed)") }
                }

                HorizontalDivider()
                SectionHeader(stringResource(R.string.borrower_section_history))
                if (state.returned.isEmpty()) {
                    Text(stringResource(R.string.borrower_no_returns_yet),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    state.returned.forEach { (loan, book) -> LoanLine(loan, book?.title ?: "(book removed)") }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun StatsBlock(stats: dev.khoj.pitaka.domain.model.BorrowerStats) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.borrower_total_loans, stats.totalLoans),
            style = MaterialTheme.typography.bodyMedium)
        if (stats.averageReturnDays != null) {
            Text(
                stringResource(R.string.borrower_avg_return, stats.averageReturnDays),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.borrower_overdue_rate, (stats.overdueRate * 100).toInt()),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun LoanLine(loan: Loan, title: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (loan.isReturned) {
                Text(
                    stringResource(R.string.loan_returned_date, formatDate(loan.returnedDate ?: 0L)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    stringResource(R.string.loan_lent_date, formatDate(loan.lentDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
