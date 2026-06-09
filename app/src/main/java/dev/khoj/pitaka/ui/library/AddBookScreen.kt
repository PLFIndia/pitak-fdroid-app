package dev.khoj.pitaka.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.domain.model.TitleSearchResult
import dev.khoj.pitaka.ui.contribute.LocalizedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(
    onBack: () -> Unit,
    onSaved: (Long) -> Unit,
    onOpenExistingBook: (Long) -> Unit,
    viewModel: AddBookViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var duplicateExistingId by remember { mutableStateOf<Long?>(null) }
    var lookupFailure by remember { mutableStateOf<LookupFailureState?>(null) }
    var titleSearchState by remember { mutableStateOf<TitleSearchUiState>(TitleSearchUiState.Idle) }
    var titleSearchQuery by remember { mutableStateOf("") }
    var showIsbnChangedConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddBookEvent.Saved              -> onSaved(event.id)
                is AddBookEvent.DuplicateIsbn      -> duplicateExistingId = event.existingId
                AddBookEvent.LookupNotFound        -> lookupFailure = LookupFailureState.NotFound
                AddBookEvent.LookupNetworkError    -> lookupFailure = LookupFailureState.NetworkError
                AddBookEvent.TitleSearchProgress   -> titleSearchState = TitleSearchUiState.Loading
                AddBookEvent.TitleSearchEmpty      -> titleSearchState = TitleSearchUiState.Empty
                AddBookEvent.TitleSearchError      -> titleSearchState = TitleSearchUiState.Error
                is AddBookEvent.TitleSearchResults -> titleSearchState = TitleSearchUiState.Results(event.results)
                AddBookEvent.IsbnChangedConfirmRequested -> showIsbnChangedConfirm = true
                AddBookEvent.NotFound              -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    LocalizedText(
                        if (form.mode == AddBookMode.Edit) R.string.edit_book_title
                        else R.string.add_book_title
                    )
                },
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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = form.title,
                onValueChange = viewModel::onTitleChange,
                label = { Text(stringResource(R.string.add_book_title_label)) },
                isError = form.titleError,
                supportingText = if (form.titleError) {
                    { LocalizedText(R.string.add_book_title_required) }
                } else null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.titleTransliteration,
                onValueChange = viewModel::onTransliterationChange,
                label = { Text(stringResource(R.string.add_book_transliteration_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.author,
                onValueChange = viewModel::onAuthorChange,
                label = { Text(stringResource(R.string.add_book_author_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // ISBN field with Lookup button (Phase 2)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = form.isbn,
                    onValueChange = viewModel::onIsbnChange,
                    label = { Text(stringResource(R.string.add_book_isbn_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(
                    onClick = viewModel::onLookupIsbn,
                    enabled = !form.isLookingUp && form.isbn.isNotBlank(),
                ) {
                    if (form.isLookingUp) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        LocalizedText(R.string.add_book_isbn_lookup)
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.publisher,
                    onValueChange = viewModel::onPublisherChange,
                    label = { Text(stringResource(R.string.add_book_publisher_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = form.publishedYear,
                    onValueChange = viewModel::onYearChange,
                    label = { Text(stringResource(R.string.add_book_year_label)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = form.genre,
                    onValueChange = viewModel::onGenreChange,
                    label = { Text(stringResource(R.string.add_book_genre_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = form.language,
                    onValueChange = viewModel::onLanguageChange,
                    label = { Text(stringResource(R.string.add_book_language_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }
            OutlinedTextField(
                value = form.pageCount,
                onValueChange = viewModel::onPagesChange,
                label = { Text(stringResource(R.string.add_book_pages_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.location,
                onValueChange = viewModel::onLocationChange,
                label = { Text(stringResource(R.string.add_book_location_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Quantity (copyCount) — how many copies the user owns. Min 1.
            OutlinedTextField(
                value = form.quantity,
                onValueChange = viewModel::onQuantityChange,
                label = { Text(stringResource(R.string.add_book_quantity_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            // Source: provenance category (dropdown) + free-form detail.
            SourceTypeDropdown(
                selected = form.sourceType,
                onSelected = viewModel::onSourceTypeChange,
            )
            OutlinedTextField(
                value = form.sourceDetail,
                onValueChange = viewModel::onSourceDetailChange,
                label = { Text(stringResource(R.string.add_book_source_detail_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            // Age group: reader age band (dropdown, nullable).
            AgeGroupDropdown(
                selected = form.ageGroup,
                onSelected = viewModel::onAgeGroupChange,
            )
            // Date added: defaults to today, user-editable via a date picker.
            DateAddedField(
                epochMillis = form.addedDate,
                onPick = viewModel::onAddedDateChange,
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.add_book_notes_label)) },
                supportingText = { Text(stringResource(R.string.add_book_notes_support)) },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    LocalizedText(R.string.add_book_cancel)
                }
                Button(onClick = viewModel::onSave, modifier = Modifier.weight(1f)) {
                    LocalizedText(R.string.add_book_save)
                }
            }
        }
    }

    // D2 duplicate-ISBN dialog
    duplicateExistingId?.let { existingId ->
        AlertDialog(
            onDismissRequest = { duplicateExistingId = null },
            title = { LocalizedText(R.string.add_book_duplicate_title) },
            text = {
                LocalizedText(R.string.add_book_duplicate_body)
            },
            confirmButton = {
                TextButton(onClick = {
                    duplicateExistingId = null
                    onOpenExistingBook(existingId)
                }) { LocalizedText(R.string.common_open_existing) }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = {
                        viewModel.onAddAsDuplicateCopy(existingId)
                        duplicateExistingId = null
                    }) { LocalizedText(R.string.add_book_duplicate_add_copy) }
                    TextButton(onClick = { duplicateExistingId = null }) {
                        LocalizedText(R.string.add_book_cancel)
                    }
                }
            },
        )
    }

    // D7 lookup-failure dialog (NotFound or NetworkError)
    lookupFailure?.let { failure ->
        val (titleRes, bodyRes) = when (failure) {
            LookupFailureState.NotFound -> R.string.add_book_isbn_lookup_not_found_title to
                    R.string.add_book_isbn_lookup_not_found_body
            LookupFailureState.NetworkError -> R.string.add_book_isbn_lookup_network_title to
                    R.string.add_book_isbn_lookup_network_body
        }
        AlertDialog(
            onDismissRequest = { lookupFailure = null },
            title = { LocalizedText(titleRes) },
            text = { LocalizedText(bodyRes) },
            // All actions stacked full-width in one column. With 3–4 options
            // (Search / Retry / Save-skeleton / Fill-manually) the default
            // confirm+dismiss horizontal FlowRow can't fit the longer labels and
            // they overflow the dialog. Full-width stacked buttons (Material's
            // pattern for 3+ actions) keep every label inside the card.
            confirmButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End,
                ) {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            lookupFailure = null
                            titleSearchQuery = form.title.ifBlank { form.isbn }
                            titleSearchState = TitleSearchUiState.Loading
                            viewModel.onTitleSearch(titleSearchQuery)
                        },
                    ) { LocalizedText(R.string.add_book_isbn_lookup_search_title) }
                    if (failure == LookupFailureState.NetworkError) {
                        TextButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                lookupFailure = null
                                viewModel.onLookupIsbn()
                            },
                        ) { LocalizedText(R.string.add_book_isbn_lookup_retry) }
                    }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            lookupFailure = null
                            viewModel.onSaveSkeletonWithIsbn()
                        },
                    ) { LocalizedText(R.string.add_book_isbn_lookup_save_skeleton) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = { lookupFailure = null },
                    ) { LocalizedText(R.string.add_book_isbn_lookup_fill_manually) }
                }
            },
        )
    }

    // D30 ISBN-changed-on-edit confirmation
    if (showIsbnChangedConfirm) {
        AlertDialog(
            onDismissRequest = { showIsbnChangedConfirm = false },
            title = { LocalizedText(R.string.add_book_isbn_changed_dialog_title) },
            text = { LocalizedText(R.string.edit_book_save_warning_isbn_changed) },
            confirmButton = {
                TextButton(onClick = {
                    showIsbnChangedConfirm = false
                    viewModel.onConfirmSaveAfterIsbnChange()
                }) { LocalizedText(R.string.edit_book_save_warning_isbn_changed_confirm) }
            },
            dismissButton = {
                TextButton(onClick = { showIsbnChangedConfirm = false }) {
                    LocalizedText(R.string.add_book_cancel)
                }
            },
        )
    }

    // Title search results dialog (D7 fallback path)
    val currentSearch = titleSearchState
    if (currentSearch !is TitleSearchUiState.Idle) {
        AlertDialog(
            onDismissRequest = { titleSearchState = TitleSearchUiState.Idle },
            title = { LocalizedText(R.string.title_search_title) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = titleSearchQuery,
                        onValueChange = { titleSearchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    when (val s = currentSearch) {
                        TitleSearchUiState.Idle -> Unit
                        TitleSearchUiState.Loading -> {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                            ) { CircularProgressIndicator() }
                        }
                        TitleSearchUiState.Empty -> {
                            LocalizedText(R.string.title_search_empty,
                                modifier = Modifier.padding(top = 24.dp))
                        }
                        TitleSearchUiState.Error -> {
                            LocalizedText(R.string.title_search_error,
                                modifier = Modifier.padding(top = 24.dp))
                        }
                        is TitleSearchUiState.Results -> {
                            LazyColumn(modifier = Modifier.padding(top = 12.dp)) {
                                items(items = s.results, key = { it.sourceKey }) { result ->
                                    TitleSearchRow(
                                        result = result,
                                        onPick = {
                                            viewModel.onPickTitleSearchResult(result)
                                            titleSearchState = TitleSearchUiState.Idle
                                        },
                                    )
                                    HorizontalDivider()
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onTitleSearch(titleSearchQuery) }) {
                    LocalizedText(R.string.add_book_isbn_lookup_search_title)
                }
            },
            dismissButton = {
                TextButton(onClick = { titleSearchState = TitleSearchUiState.Idle }) {
                    LocalizedText(R.string.title_search_close)
                }
            },
        )
    }
}

@Composable
private fun TitleSearchRow(result: TitleSearchResult, onPick: () -> Unit) {
    val isbnPrefix = stringResource(R.string.add_book_search_result_isbn_prefix)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        TextButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(result.title, style = MaterialTheme.typography.titleSmall)
                val sub = buildString {
                    result.author?.let { append(it) }
                    result.publishedYear?.let {
                        if (isNotEmpty()) append(" · ")
                        append(it.toString())
                    }
                    result.isbn?.let {
                        if (isNotEmpty()) append(" · ")
                        append(isbnPrefix).append(it)
                    }
                }
                if (sub.isNotBlank()) {
                    Text(
                        sub,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private enum class LookupFailureState { NotFound, NetworkError }

private sealed interface TitleSearchUiState {
    data object Idle : TitleSearchUiState
    data object Loading : TitleSearchUiState
    data object Empty : TitleSearchUiState
    data object Error : TitleSearchUiState
    data class Results(val results: List<TitleSearchResult>) : TitleSearchUiState
}

/** Localized label for a source-type option (null = "Not set"). */
@Composable
internal fun sourceTypeLabel(type: Book.SourceType?): String = stringResource(
    when (type) {
        Book.SourceType.PURCHASED -> R.string.source_type_purchased
        Book.SourceType.GIFT -> R.string.source_type_gift
        Book.SourceType.DONATED -> R.string.source_type_donated
        Book.SourceType.INHERITED -> R.string.source_type_inherited
        Book.SourceType.OTHER -> R.string.source_type_other
        null -> R.string.source_type_unset
    }
)

/** Localized label for an age-group option (null = "Any / not set"). */
@Composable
internal fun ageGroupLabel(group: Book.AgeGroup?): String = stringResource(
    when (group) {
        Book.AgeGroup.ABOVE_3 -> R.string.age_group_above_3
        Book.AgeGroup.ABOVE_6 -> R.string.age_group_above_6
        Book.AgeGroup.ABOVE_10 -> R.string.age_group_above_10
        Book.AgeGroup.ABOVE_15 -> R.string.age_group_above_15
        Book.AgeGroup.ADVANCED -> R.string.age_group_advanced
        null -> R.string.age_group_unset
    }
)

/**
 * Age-group dropdown (Above 3 / Above 6 / Above 10 / Above 15 / Advanced), with
 * a "Not set" option mapping to null. Options are in enum (= sort) order.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AgeGroupDropdown(
    selected: Book.AgeGroup?,
    onSelected: (Book.AgeGroup?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val options: List<Book.AgeGroup?> = listOf(null) + Book.AgeGroup.entries
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = ageGroupLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.add_book_age_group_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(ageGroupLabel(opt)) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * Source-type provenance dropdown (Purchased / Gift / Donated / Inherited /
 * Other), with a "Not set" option mapping to null. Paired with the free-form
 * source-detail text field below it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SourceTypeDropdown(
    selected: Book.SourceType?,
    onSelected: (Book.SourceType?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    // null first ("Not set"), then the five categories in enum order.
    val options: List<Book.SourceType?> = listOf(null) + Book.SourceType.entries
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = sourceTypeLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.add_book_source_type_label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            options.forEach { opt ->
                DropdownMenuItem(
                    text = { Text(sourceTypeLabel(opt)) },
                    onClick = {
                        onSelected(opt)
                        expanded = false
                    },
                )
            }
        }
    }
}

/**
 * "Date added" field: a read-only text field showing the chosen date that
 * opens a Material3 date picker on tap. Defaults to today (the form state's
 * default); the user can back-date to the real acquisition date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateAddedField(
    epochMillis: Long,
    onPick: (Long) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = dev.khoj.pitaka.ui.loans.formatDate(epochMillis),
        onValueChange = {},
        readOnly = true,
        label = { Text(stringResource(R.string.add_book_date_added_label)) },
        trailingIcon = {
            TextButton(onClick = { showPicker = true }) {
                Text(stringResource(R.string.add_book_date_added_change))
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )

    if (showPicker) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let(onPick)
                    showPicker = false
                }) { Text(stringResource(R.string.add_book_date_added_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        ) {
            DatePicker(state = state)
        }
    }
}
