package dev.khoj.pitaka.ui.bookmarks

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Bookmark
import dev.khoj.pitaka.domain.model.BookmarkUrl
import dev.khoj.pitaka.ui.contribute.LocalizedText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookmarksScreen(
    onBack: () -> Unit,
    viewModel: BookmarksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var showAdd by rememberSaveable { mutableStateOf(false) }
    // Index of the bookmark being renamed, or -1 when none.
    var renameIndex by rememberSaveable { mutableStateOf(-1) }
    // Index pending delete confirmation, or -1 when none.
    var deleteIndex by rememberSaveable { mutableStateOf(-1) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { LocalizedText(R.string.bookmarks_title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    IconButton(onClick = { showAdd = true }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.bookmarks_add_cd),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.bookmarks.isEmpty()) {
                FloatingActionButton(onClick = { showAdd = true }) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.bookmarks_add_cd),
                    )
                }
            }
        },
    ) { padding ->
        if (state.bookmarks.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(24.dp),
            ) {
                LocalizedText(
                    R.string.bookmarks_empty,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                itemsIndexed(state.bookmarks) { index, bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onOpen = { openInBrowser(context, bookmark.url) },
                        onRename = { renameIndex = index },
                        onDelete = { deleteIndex = index },
                    )
                }
            }
        }
    }

    if (showAdd) {
        AddBookmarkDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, url ->
                val ok = viewModel.add(name, url)
                if (ok) showAdd = false
                ok
            },
        )
    }

    if (renameIndex >= 0) {
        val current = state.bookmarks.getOrNull(renameIndex)
        if (current == null) {
            renameIndex = -1
        } else {
            RenameBookmarkDialog(
                initialName = current.name,
                onDismiss = { renameIndex = -1 },
                onConfirm = { newName ->
                    viewModel.rename(renameIndex, newName)
                    renameIndex = -1
                },
            )
        }
    }

    if (deleteIndex >= 0) {
        val current = state.bookmarks.getOrNull(deleteIndex)
        if (current == null) {
            deleteIndex = -1
        } else {
            AlertDialog(
                onDismissRequest = { deleteIndex = -1 },
                title = { Text(stringResource(R.string.bookmarks_delete_title)) },
                text = { Text(stringResource(R.string.bookmarks_delete_message, current.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.delete(deleteIndex)
                        deleteIndex = -1
                    }) { Text(stringResource(R.string.bookmarks_delete_confirm)) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteIndex = -1 }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: Bookmark,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(bookmark.name) },
        supportingContent = {
            Text(
                bookmark.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.bookmarks_row_menu_cd),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks_rename)) },
                        onClick = { menuOpen = false; onRename() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bookmarks_delete)) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
    )
}

@Composable
private fun AddBookmarkDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Boolean,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var showError by rememberSaveable { mutableStateOf(false) }

    val nameOk = name.isNotBlank()
    val urlOk = BookmarkUrl.isValid(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_add_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; showError = false },
                    label = { Text(stringResource(R.string.bookmarks_field_name)) },
                    singleLine = true,
                    isError = showError && !nameOk,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; showError = false },
                    label = { Text(stringResource(R.string.bookmarks_field_url)) },
                    placeholder = { Text("https://") },
                    singleLine = true,
                    isError = showError && !urlOk,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (showError) {
                    Text(
                        stringResource(R.string.bookmarks_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (nameOk && urlOk) {
                    if (!onConfirm(name, url)) showError = true
                } else {
                    showError = true
                }
            }) { Text(stringResource(R.string.bookmarks_add_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

@Composable
private fun RenameBookmarkDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.bookmarks_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.bookmarks_field_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank(),
            ) { Text(stringResource(R.string.common_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
        },
    )
}

/**
 * Opens [url] in an external browser. Wrapped so a device with no browser (or a
 * malformed URI that slipped past validation) shows a toast rather than
 * crashing — graceful failure per the design.
 */
private fun openInBrowser(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(
            context,
            context.getString(R.string.bookmarks_open_failed),
            Toast.LENGTH_SHORT,
        ).show()
    }
}
