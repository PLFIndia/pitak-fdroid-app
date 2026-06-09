package dev.khoj.pitaka.ui.wishlist

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.WishlistBook
import dev.khoj.pitaka.ui.contribute.LocalizedText
import dev.khoj.pitaka.ui.contribute.contributorLongPress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WishlistScreen(
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenLibraryBook: (Long) -> Unit,
    viewModel: WishlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var purchaseDialogFor by remember { mutableStateOf<WishlistBook?>(null) }
    var alreadyInLibrary by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is WishlistEvent.Purchased -> Unit
                is WishlistEvent.AlreadyInLibrary -> alreadyInLibrary = event.existingBookId
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wishlist_title)) },
                actions = {
                    IconButton(onClick = onScanBook) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = stringResource(R.string.wishlist_scan_cd))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBook) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.wishlist_add_cd))
            }
        },
    ) { padding ->
        WishlistContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onFilterChange = viewModel::onFilterChange,
            onOpenBook = onOpenBook,
            onMarkPurchasedRequest = { book -> purchaseDialogFor = book },
            onAddBook = onAddBook,
            onScanBook = onScanBook,
            contentPadding = padding,
        )
    }

    purchaseDialogFor?.let { book ->
        AlertDialog(
            onDismissRequest = { purchaseDialogFor = null },
            title = { Text(stringResource(R.string.wishlist_purchased_dialog_title)) },
            text = { Text(stringResource(R.string.wishlist_purchased_dialog_body)) },
            confirmButton = {
                TextButton(onClick = {
                    purchaseDialogFor = null
                    viewModel.onMarkPurchased(book.id, moveToLibrary = true)
                }) { Text(stringResource(R.string.wishlist_move_to_library)) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        purchaseDialogFor = null
                        viewModel.onMarkPurchased(book.id, moveToLibrary = false)
                    }) { Text(stringResource(R.string.wishlist_purchased_only)) }
                    TextButton(onClick = { purchaseDialogFor = null }) {
                        Text(stringResource(R.string.add_book_cancel))
                    }
                }
            },
        )
    }

    alreadyInLibrary?.let { existingId ->
        AlertDialog(
            onDismissRequest = { alreadyInLibrary = null },
            title = { Text(stringResource(R.string.wishlist_already_in_library_title)) },
            text = { Text(stringResource(R.string.wishlist_already_in_library_body)) },
            confirmButton = {
                TextButton(onClick = {
                    alreadyInLibrary = null
                    onOpenLibraryBook(existingId)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WishlistContent(
    state: WishlistUiState,
    onQueryChange: (String) -> Unit,
    onFilterChange: (WishlistFilter) -> Unit,
    onOpenBook: (Long) -> Unit,
    onMarkPurchasedRequest: (WishlistBook) -> Unit,
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.wishlist_search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                modifier = Modifier.contributorLongPress(R.string.wishlist_filter_active),
                selected = state.filter == WishlistFilter.Active,
                onClick = { onFilterChange(WishlistFilter.Active) },
                label = { LocalizedText(R.string.wishlist_filter_active, passthroughTap = true) },
            )
            FilterChip(
                modifier = Modifier.contributorLongPress(R.string.wishlist_filter_purchased),
                selected = state.filter == WishlistFilter.Purchased,
                onClick = { onFilterChange(WishlistFilter.Purchased) },
                label = { LocalizedText(R.string.wishlist_filter_purchased, passthroughTap = true) },
            )
        }

        Spacer(Modifier.height(8.dp))

        if (state.books.isEmpty() && !state.isLoading) {
            EmptyWishlistState(
                onAddBook = onAddBook,
                onScanBook = onScanBook,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = state.books, key = { it.id }) { book ->
                    WishlistRow(
                        book = book,
                        onClick = { onOpenBook(book.id) },
                        onMarkPurchased = { onMarkPurchasedRequest(book) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun EmptyWishlistState(
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Filled.Bookmark,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            LocalizedText(
                R.string.wishlist_empty_headline,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            LocalizedText(
                R.string.wishlist_empty_body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onScanBook) {
                    LocalizedText(R.string.library_empty_card_scan)
                }
                FilledTonalButton(onClick = onAddBook) {
                    LocalizedText(R.string.library_empty_card_type)
                }
            }
        }
    }
}

@Composable
private fun WishlistRow(
    book: WishlistBook,
    onClick: () -> Unit,
    onMarkPurchased: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!book.author.isNullOrBlank()) {
                Text(
                    book.author,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        PriorityChip(priority = book.priority)
        if (book.purchased) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.wishlist_purchased_chip),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        } else {
            TextButton(onClick = onMarkPurchased, modifier = Modifier.padding(start = 4.dp)) {
                Text(
                    stringResource(R.string.wishlist_mark_purchased),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun PriorityChip(priority: Int) {
    val (label, container, content) = when (priority) {
        WishlistBook.PRIORITY_HIGH -> Triple(
            R.string.wishlist_priority_high,
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
        WishlistBook.PRIORITY_MED -> Triple(
            R.string.wishlist_priority_med,
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        else -> Triple(
            R.string.wishlist_priority_low,
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Surface(
        color = container,
        contentColor = content,
        shape = RoundedCornerShape(50),
    ) {
        LocalizedText(
            label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
