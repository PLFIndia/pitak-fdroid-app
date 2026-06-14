package dev.khoj.pitaka.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.ui.contribute.LocalizedText

/**
 * Library screen.
 *
 * Composition rules in play:
 * - D17: dismissible onboarding card surfaces when the library is empty and the
 *        user hasn't dismissed it this session.
 * - D22: list sort defaults to recently-added newest first (handled in the VM).
 * - D26: empty state has an action row (scan / type / import) so it's never a
 *        terminal dead-end.
 * - D26b: Pending button lives in the top app bar; in Phase 1 it's a stub that
 *         shows a "vault coming in Phase 4" message.
 *
 * @param onAddBook navigate to the manual-entry screen
 * @param onOpenBook navigate to the book detail screen for a given id
 * @param onOpenBookmarks navigate to the Bookmarks screen (links to other
 *        people's published library pages)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    onImportBook: () -> Unit,
    onOpenBook: (Long) -> Unit,
    onOpenBookmarks: () -> Unit,
    onPublish: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.libraryLogoUri.isNotBlank()) {
                            coil.compose.AsyncImage(
                                model = state.libraryLogoUri,
                                contentDescription = null,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(6.dp)),
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(8.dp))
                        }
                        Text(state.libraryName.ifBlank { stringResource(R.string.library_title) })
                    }
                },
                actions = {
                    IconButton(onClick = onScanBook) {
                        Icon(
                            Icons.Filled.PhotoCamera,
                            contentDescription = stringResource(R.string.library_scan_cd),
                        )
                    }
                    IconButton(onClick = onPublish) {
                        Icon(
                            Icons.Filled.Public,
                            contentDescription = stringResource(R.string.library_publish_cd),
                        )
                    }
                    IconButton(onClick = onOpenBookmarks) {
                        Icon(
                            Icons.Filled.Bookmarks,
                            contentDescription = stringResource(R.string.bookmarks_open_cd),
                        )
                    }
                },
                // Transparent container — see SettingsScreen: avoids M3
                // TopAppBar's internal container-color animation lagging behind
                // the body on a theme switch. Background == surface here, so it
                // looks identical.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddBook) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.library_add_cd),
                )
            }
        },
    ) { padding ->
        LibraryContent(
            state = state,
            onQueryChange = viewModel::onQueryChange,
            onDismissOnboardingCard = viewModel::onDismissOnboardingCard,
            onSortChange = viewModel::onSortChange,
            onLanguageFilterChange = viewModel::onLanguageFilterChange,
            onClearFilters = viewModel::onClearFilters,
            onAddBook = onAddBook,
            onScanBook = onScanBook,
            onImportBook = onImportBook,
            onOpenBook = onOpenBook,
            contentPadding = padding,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryContent(
    state: LibraryUiState,
    onQueryChange: (String) -> Unit,
    onDismissOnboardingCard: () -> Unit,
    onSortChange: (dev.khoj.pitaka.domain.repository.BookSort) -> Unit,
    onLanguageFilterChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
    onAddBook: () -> Unit,
    onScanBook: () -> Unit,
    onImportBook: () -> Unit,
    onOpenBook: (Long) -> Unit,
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
            placeholder = { Text(stringResource(R.string.library_search_hint),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        // Sort + genre/language filter controls (feature 3). Hidden on an empty
        // library (nothing to sort/filter, onboarding card is showing instead).
        if (!state.showOnboardingCard) {
            LibraryControlsRow(
                state = state,
                onSortChange = onSortChange,
                onLanguageFilterChange = onLanguageFilterChange,
                onClearFilters = onClearFilters,
            )
        }

        // Crash banner: zero pixels on a healthy install, a dismissable
        // alert when an unsent crash report sits on disk from the previous
        // launch.
        dev.khoj.pitaka.ui.crash.CrashBanner()

        if (state.showOnboardingCard) {
            OnboardingCard(
                onScan = onScanBook,
                onType = onAddBook,
                onImport = onImportBook,
                onDismiss = onDismissOnboardingCard,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        when {
            state.isLoading -> {
                // Loading is brief because the data source is local Room — keep this minimal.
                Spacer(Modifier.height(0.dp))
            }
            state.books.isEmpty() -> {
                EmptyLibraryState(
                    isSearching = state.query.isNotBlank(),
                    query = state.query,
                    onAddBook = onAddBook,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(items = state.books, key = { it.id }) { book ->
                        BookRow(
                            book = book,
                            unavailable = state.unavailableBookIds?.contains(book.id) == true,
                            onClick = { onOpenBook(book.id) },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/**
 * Feature 3 (Revision R): sort selector + language filter facets.
 *
 * Sort dropdown = {Date added, Language, Age group}. Language is also a filter
 * facet (chips that narrow the list). Genre is NOT here — it's search-only.
 * Horizontally scrollable so a long language list never wraps. A "Clear" chip
 * appears only when a facet is active.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibraryControlsRow(
    state: LibraryUiState,
    onSortChange: (BookSort) -> Unit,
    onLanguageFilterChange: (String?) -> Unit,
    onClearFilters: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(androidx.compose.foundation.rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SortDropdownChip(sort = state.sort, onSortChange = onSortChange)

        if (state.filter.isActive) {
            FilterChip(
                selected = false,
                onClick = onClearFilters,
                label = { Text(stringResource(R.string.library_filter_clear)) },
                leadingIcon = {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                },
            )
        }

        // Language facets.
        state.availableLanguages.forEach { language ->
            val selected = state.filter.language.equals(language, ignoreCase = true)
            FilterChip(
                selected = selected,
                onClick = { onLanguageFilterChange(if (selected) null else language) },
                label = { Text(language) },
                colors = FilterChipDefaults.filterChipColors(),
                leadingIcon = if (selected) {
                    { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) }
                } else null,
            )
        }
    }
}

/** Sort dropdown as a chip-styled button. Date added / Language / Age group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortDropdownChip(
    sort: BookSort,
    onSortChange: (BookSort) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = when (sort) {
        BookSort.RecentlyAdded -> stringResource(R.string.library_sort_recent)
        BookSort.LanguageAsc -> stringResource(R.string.library_sort_language)
        BookSort.AgeGroupAsc -> stringResource(R.string.library_sort_age_group)
    }
    Box {
        FilterChip(
            selected = true,
            onClick = { expanded = true },
            label = { Text(stringResource(R.string.library_sort_prefix, label)) },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_sort_recent)) },
                onClick = { onSortChange(BookSort.RecentlyAdded); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_sort_language)) },
                onClick = { onSortChange(BookSort.LanguageAsc); expanded = false },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.library_sort_age_group)) },
                onClick = { onSortChange(BookSort.AgeGroupAsc); expanded = false },
            )
        }
    }
}

@Composable
private fun OnboardingCard(
    onScan: () -> Unit,
    onType: () -> Unit,
    onImport: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        tonalElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocalizedText(
                    R.string.library_empty_headline,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.library_empty_card_dismiss),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    LocalizedText(R.string.library_empty_card_scan, maxLines = 1)
                }
                FilledTonalButton(onClick = onType, modifier = Modifier.fillMaxWidth()) {
                    LocalizedText(R.string.library_empty_card_type, maxLines = 1)
                }
                FilledTonalButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    LocalizedText(R.string.library_empty_card_import, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun EmptyLibraryState(
    isSearching: Boolean,
    query: String,
    onAddBook: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.AutoMirrored.Filled.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            if (isSearching) {
                Text(
                    stringResource(R.string.library_no_matches, query),
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddBook) {
                    Text(stringResource(R.string.library_add_as_new, query))
                }
            } else {
                LocalizedText(
                    R.string.library_empty_headline,
                    style = MaterialTheme.typography.titleMedium,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onAddBook) {
                    LocalizedText(R.string.library_empty_card_type)
                }
            }
        }
    }
}

@Composable
private fun BookRow(book: Book, unavailable: Boolean, onClick: () -> Unit) {
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
        dev.khoj.pitaka.ui.common.BookCoverImage(
            coverUrl = book.coverUrl,
            title = book.title,
            modifier = Modifier
                .width(40.dp)
                .padding(end = 12.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                book.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                // Removed books are dimmed (visible-but-inert, D39).
                color = if (book.removed) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
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
        if (book.removed) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.book_detail_removed_badge),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (unavailable && !book.removed) {
            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.library_book_not_available_badge),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (book.needsMetadata) {
            Surface(
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                shape = RoundedCornerShape(50),
                modifier = Modifier.padding(start = 8.dp),
            ) {
                Text(
                    stringResource(R.string.library_row_needs_metadata),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        if (book.copyCount > 1) {
            Text(
                "×${book.copyCount}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
