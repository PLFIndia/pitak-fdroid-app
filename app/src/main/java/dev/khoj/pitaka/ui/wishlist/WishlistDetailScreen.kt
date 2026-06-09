package dev.khoj.pitaka.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import dev.khoj.pitaka.domain.model.WishlistBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistDetailScreen(
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit,
    onOpenLibraryBook: (Long) -> Unit,
    viewModel: WishlistDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var purchaseDialog by remember { mutableStateOf(false) }
    var deleteDialog by remember { mutableStateOf(false) }
    var alreadyInLibrary by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                WishlistDetailEvent.Deleted -> onDeleted()
                WishlistDetailEvent.Purchased -> onBack()
                is WishlistDetailEvent.AlreadyInLibrary -> alreadyInLibrary = event.bookId
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.book_detail_edit))
                    }
                    IconButton(onClick = { deleteDialog = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.book_detail_delete))
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {}
                state.notFound -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.wishlist_detail_not_found), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                    }
                }
                state.book != null -> {
                    WishlistDetailBody(
                        book = state.book!!,
                        onMarkPurchased = { purchaseDialog = true },
                    )
                }
            }
        }
    }

    if (deleteDialog) {
        AlertDialog(
            onDismissRequest = { deleteDialog = false },
            title = { Text(stringResource(R.string.wishlist_detail_delete_confirm_title)) },
            confirmButton = {
                TextButton(onClick = {
                    deleteDialog = false
                    viewModel.onDeleteConfirmed()
                }) { Text(stringResource(R.string.book_detail_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialog = false }) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        )
    }

    if (purchaseDialog) {
        AlertDialog(
            onDismissRequest = { purchaseDialog = false },
            title = { Text(stringResource(R.string.wishlist_purchased_dialog_title)) },
            text = { Text(stringResource(R.string.wishlist_purchased_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    purchaseDialog = false
                    viewModel.onMarkPurchased(moveToLibrary = true)
                }) { Text(stringResource(R.string.wishlist_move_to_library)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        purchaseDialog = false
                        viewModel.onMarkPurchased(moveToLibrary = false)
                    }) { Text(stringResource(R.string.wishlist_purchased_only)) }
                    TextButton(onClick = { purchaseDialog = false }) {
                        Text(stringResource(R.string.add_book_cancel))
                    }
                }
            },
        )
    }

    alreadyInLibrary?.let { existing ->
        AlertDialog(
            onDismissRequest = { alreadyInLibrary = null },
            title = { Text(stringResource(R.string.wishlist_already_in_library_title)) },
            text = { Text(stringResource(R.string.wishlist_already_in_library_body)) },
            confirmButton = {
                TextButton(onClick = {
                    alreadyInLibrary = null
                    onOpenLibraryBook(existing)
                }) { Text(stringResource(R.string.wishlist_already_in_library_open)) }
            },
            dismissButton = {
                TextButton(onClick = { alreadyInLibrary = null }) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        )
    }
}

@Composable
private fun WishlistDetailBody(book: WishlistBook, onMarkPurchased: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(book.title, style = MaterialTheme.typography.headlineMedium)
            if (!book.titleTransliteration.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    book.titleTransliteration,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!book.author.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    book.author,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
            if (!book.purchased) {
                Button(onClick = onMarkPurchased) {
                    Text(stringResource(R.string.wishlist_mark_purchased))
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            DetailRow(stringResource(R.string.book_detail_row_isbn), book.isbn)
            DetailRow(stringResource(R.string.book_detail_row_publisher), book.publisher)
            DetailRow(stringResource(R.string.book_detail_row_published), book.publishedYear?.toString())
            DetailRow(stringResource(R.string.wishlist_detail_row_priority), when (book.priority) {
                WishlistBook.PRIORITY_HIGH -> stringResource(R.string.wishlist_priority_high)
                WishlistBook.PRIORITY_MED  -> stringResource(R.string.wishlist_priority_med)
                else                       -> stringResource(R.string.wishlist_priority_low)
            })
            DetailRow(stringResource(R.string.wishlist_detail_row_price), book.priceEstimate?.toString())
            if (book.purchased) {
                DetailRow(stringResource(R.string.wishlist_detail_row_purchased), stringResource(R.string.common_yes))
            }
            if (!book.notes.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.detail_section_notes), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(book.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
