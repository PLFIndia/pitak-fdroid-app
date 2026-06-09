package dev.khoj.pitaka.ui.wishlist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.WishlistBook

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddWishlistScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddWishlistViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    var lookupFailure by remember { mutableStateOf<String?>(null) }
    var duplicate by remember { mutableStateOf<Long?>(null) }

    val lookupNotFoundMsg = stringResource(R.string.add_wishlist_lookup_not_found)
    val lookupNetworkMsg = stringResource(R.string.add_wishlist_lookup_network_error)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AddWishlistEvent.Saved          -> onSaved()
                is AddWishlistEvent.DuplicateIsbn  -> duplicate = event.existingId
                AddWishlistEvent.LookupNotFound    -> lookupFailure = lookupNotFoundMsg
                AddWishlistEvent.LookupNetworkError -> lookupFailure = lookupNetworkMsg
                AddWishlistEvent.NotFound          -> onBack()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (form.mode == AddWishlistMode.Edit) stringResource(R.string.add_wishlist_edit_title)
                        else stringResource(R.string.wishlist_add_cd)
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
                    { Text(stringResource(R.string.add_book_title_required)) }
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

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text(stringResource(R.string.add_book_isbn_lookup))
                    }
                }
            }

            OutlinedTextField(
                value = form.publisher,
                onValueChange = viewModel::onPublisherChange,
                label = { Text(stringResource(R.string.add_book_publisher_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.publishedYear,
                onValueChange = viewModel::onYearChange,
                label = { Text(stringResource(R.string.add_book_year_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.priceEstimate,
                onValueChange = viewModel::onPriceChange,
                label = { Text(stringResource(R.string.wishlist_price_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Text(stringResource(R.string.wishlist_priority_label))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = form.priority == WishlistBook.PRIORITY_LOW,
                    onClick = { viewModel.onPriorityChange(WishlistBook.PRIORITY_LOW) },
                    label = { Text(stringResource(R.string.wishlist_priority_low)) },
                )
                FilterChip(
                    selected = form.priority == WishlistBook.PRIORITY_MED,
                    onClick = { viewModel.onPriorityChange(WishlistBook.PRIORITY_MED) },
                    label = { Text(stringResource(R.string.wishlist_priority_med)) },
                )
                FilterChip(
                    selected = form.priority == WishlistBook.PRIORITY_HIGH,
                    onClick = { viewModel.onPriorityChange(WishlistBook.PRIORITY_HIGH) },
                    label = { Text(stringResource(R.string.wishlist_priority_high)) },
                )
            }

            OutlinedTextField(
                value = form.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.add_book_notes_label)) },
                minLines = 3,
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
                    Text(stringResource(R.string.add_book_save))
                }
            }
        }
    }

    lookupFailure?.let { msg ->
        AlertDialog(
            onDismissRequest = { lookupFailure = null },
            confirmButton = { TextButton(onClick = { lookupFailure = null }) { Text(stringResource(R.string.common_ok)) } },
            text = { Text(msg) },
        )
    }

    duplicate?.let { existing ->
        AlertDialog(
            onDismissRequest = { duplicate = null },
            title = { Text(stringResource(R.string.add_wishlist_duplicate_title)) },
            text = { Text(stringResource(R.string.add_wishlist_duplicate_body)) },
            confirmButton = {
                TextButton(onClick = { duplicate = null; onSaved() }) {
                    Text(stringResource(R.string.common_open_existing))
                }
            },
            dismissButton = {
                TextButton(onClick = { duplicate = null }) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        )
    }
}
