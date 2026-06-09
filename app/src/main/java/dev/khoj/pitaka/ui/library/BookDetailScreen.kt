package dev.khoj.pitaka.ui.library

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.Note
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.domain.model.Book
import dev.khoj.pitaka.ui.contribute.LocalizedText

/**
 * Book detail screen.
 *
 * D9: no hero action. Lend / Edit / Note / Share / Delete sit as an equal row of
 *     icons in the top app bar. Phase 1 fully wires Share (D28 plain-text) and
 *     Delete; Edit reuses the AddBook form (Phase 1.5 polish — for now it shows
 *     a "coming next" placeholder); Lend and Note surface "coming in Phase 4"
 *     and "coming next" messages respectively.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    onEdit: () -> Unit,
    onLend: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var transientMessage by remember { mutableStateOf<String?>(null) }
    var showCoverMenu by remember { mutableStateOf(false) }

    // State for a pending camera capture (the temp URI we asked the camera to
    // write to; remembered so the TakePicture result can route it onward).
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // --- Manual crop step (F-Droid variant: vanniktech/canhub cropper,
    // Apache-2.0, no Play Services). Both a camera capture and a gallery pick
    // are routed through the cropper FIRST; the cropper writes the cropped
    // result to its own content:// URI, which we then feed through the SAME
    // onPickCover -> ImageStore.importBookCover path, so the cropped image is
    // downscaled to 400x600 JPEG q80 exactly like any other cover.
    //
    // Crop is FREE (fixAspectRatio=false) but the window OPENS on a 2:3
    // book-cover rectangle; the user can adjust or rotate freely from there. ---
    val cropLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = com.canhub.cropper.CropImageContract(),
    ) { result ->
        if (result.isSuccessful) {
            result.uriContent?.let { viewModel.onPickCover(it) }
        } else if (result.error != null) {
            transientMessage = ctx.getString(
                R.string.book_detail_crop_failed, result.error?.message ?: "",
            )
        }
        pendingCameraUri = null
    }

    fun launchCrop(source: Uri) {
        val options = com.canhub.cropper.CropImageOptions().apply {
            fixAspectRatio = false          // free crop — user may override
            aspectRatioX = 2                // but the window opens on 2:3,
            aspectRatioY = 3                // the natural book-cover portrait
            allowRotation = true
            allowFlipping = false
            guidelines = com.canhub.cropper.CropImageView.Guidelines.ON
            outputCompressFormat = android.graphics.Bitmap.CompressFormat.JPEG
            // Label the action-bar confirm item explicitly so "save crop" is
            // obvious (the cropper hosts this control as an action-bar menu
            // item — see Theme.Pitaka.Cropper).
            cropMenuCropButtonTitle = ctx.getString(R.string.book_detail_crop_done)
        }
        cropLauncher.launch(com.canhub.cropper.CropImageContractOptions(source, options))
    }

    // Gallery pick -> crop -> downscale.
    val pickCoverLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) launchCrop(uri)
    }

    // --- Camera capture for a cover photo (F-Droid variant: system camera
    // intent, no ML Kit). The full-size capture is written to a FileProvider
    // content:// URI under cacheDir/camera/, then routed through the crop step
    // and finally the same downscale path as any other cover. ---
    val takePictureLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            launchCrop(uri)
        } else {
            pendingCameraUri = null
        }
    }

    // Mints a fresh temp file + content:// URI and launches the camera.
    fun launchCamera() {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        pendingCameraUri = uri
        takePictureLauncher.launch(uri)
    }

    // The app declares CAMERA in its manifest, so the OS requires the runtime
    // permission to be held before ACTION_IMAGE_CAPTURE; request it if needed.
    val cameraPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCamera()
        } else {
            transientMessage = ctx.getString(R.string.book_detail_camera_denied)
        }
    }

    fun onTakePhoto() {
        val hasPermission = ContextCompat.checkSelfPermission(
            ctx, Manifest.permission.CAMERA,
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) launchCamera() else cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                // Soft-remove (PLAN-merge.md): navigate back as before; the book
                // stays in the DB (flagged removed), it does not vanish.
                BookDetailEvent.Removed -> onDeleted()
                BookDetailEvent.Restored ->
                    transientMessage = ctx.getString(R.string.book_detail_restored)
            }
        }
    }

    Scaffold(
        topBar = {
            // Slim top bar — back button only. The book title gets its own
            // banner below, so long bilingual titles (esp. Devanagari) have
            // room to breathe.
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> {} // brief; data source is local
                state.notFound -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            stringResource(R.string.book_detail_not_found),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onBack) {
                            Text(stringResource(R.string.book_detail_back_to_library))
                        }
                    }
                }
                state.book != null -> {
                    val book = state.book!!
                    BookDetailBody(
                        book = book,
                        availableCount = state.availableCount,
                        activeLoanCount = state.activeLoanCount,
                        onLend = onLend,
                        onEdit = onEdit,
                        onNote = { transientMessage = ctx.getString(R.string.book_detail_note_unavailable) },
                        onShare = { shareBook(ctx, book) },
                        onDelete = { showDeleteDialog = true },
                        onRestore = { viewModel.onRestore() },
                        onCoverTap = { showCoverMenu = true },
                    )
                }
            }
        }
    }

    if (showCoverMenu) {
        AlertDialog(
            onDismissRequest = { showCoverMenu = false },
            title = { Text(stringResource(R.string.book_detail_cover_dialog_title)) },
            text = {
                Column {
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showCoverMenu = false
                            onTakePhoto()
                        },
                    ) { Text(stringResource(R.string.book_detail_cover_scan)) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showCoverMenu = false
                            pickCoverLauncher.launch("image/*")
                        },
                    ) { Text(stringResource(R.string.book_detail_cover_pick)) }
                    TextButton(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            showCoverMenu = false
                            viewModel.onClearCover()
                        },
                    ) { Text(stringResource(R.string.book_detail_cover_clear)) }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCoverMenu = false }) { Text(stringResource(R.string.add_book_cancel)) }
            },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.book_detail_delete_confirm_title)) },
            text = { Text(stringResource(R.string.book_detail_delete_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteDialog = false
                    viewModel.onRemoveConfirmed()
                }) { Text(stringResource(R.string.book_detail_delete_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        )
    }

    transientMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = { transientMessage = null },
            confirmButton = {
                TextButton(onClick = { transientMessage = null }) { Text(stringResource(R.string.common_ok)) }
            },
            text = { Text(msg) },
        )
    }
}

@Composable
private fun BookDetailBody(
    book: Book,
    availableCount: Int?,
    activeLoanCount: Int?,
    onLend: () -> Unit,
    onEdit: () -> Unit,
    onNote: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
    onCoverTap: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // --- Title banner with cover thumbnail ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
        ) {
            dev.khoj.pitaka.ui.common.BookCoverImage(
                coverUrl = book.coverUrl,
                title = book.title,
                modifier = Modifier
                    .width(96.dp)
                    .padding(end = 16.dp)
                    .clickable { onCoverTap() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    book.title,
                    style = MaterialTheme.typography.headlineSmall,
                )
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
        }

        // --- D9 equal-icon action row ---
        // D39: a removed book is inert — Lend/Edit/Note/Delete are suppressed.
        // Only Share + Restore remain (view + restore, per Q-REMOVED-VIS).
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (book.removed) {
                ActionButton(
                    icon = Icons.Filled.Share,
                    labelRes = R.string.book_detail_share,
                    onClick = onShare,
                )
                ActionButton(
                    icon = Icons.Filled.Refresh,
                    labelRes = R.string.book_detail_restore,
                    onClick = onRestore,
                )
            } else {
                ActionButton(
                    icon = Icons.AutoMirrored.Filled.Send,
                    labelRes = R.string.book_detail_lend,
                    onClick = onLend,
                )
                ActionButton(
                    icon = Icons.Filled.Edit,
                    labelRes = R.string.book_detail_edit,
                    onClick = onEdit,
                )
                ActionButton(
                    icon = Icons.AutoMirrored.Filled.Note,
                    labelRes = R.string.book_detail_note,
                    onClick = onNote,
                )
                ActionButton(
                    icon = Icons.Filled.Share,
                    labelRes = R.string.book_detail_share,
                    onClick = onShare,
                )
                ActionButton(
                    icon = Icons.Filled.Delete,
                    labelRes = R.string.book_detail_delete,
                    onClick = onDelete,
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // --- Detail rows ---
        Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            DetailRow(R.string.book_detail_row_isbn, book.isbn)
            DetailRow(R.string.book_detail_row_publisher, book.publisher)
            DetailRow(R.string.book_detail_row_published, book.publishedYear?.toString())
            DetailRow(R.string.book_detail_row_genre, book.genre)
            DetailRow(R.string.book_detail_row_language, book.language)
            DetailRow(R.string.book_detail_row_pages, book.pageCount?.toString())
            DetailRow(R.string.book_detail_row_shelf, book.location)
            // Quantity (copyCount) is always shown. Available = quantity − active
            // loans is shown only when the vault is unlocked (availableCount
            // non-null); a count while locked would leak loan state (D4).
            DetailRow(R.string.book_detail_row_quantity, book.copyCount.toString())
            if (availableCount != null) {
                DetailRow(
                    R.string.book_detail_row_available,
                    stringResource(
                        R.string.book_detail_available_value,
                        availableCount,
                        book.copyCount,
                        activeLoanCount ?: 0,
                    ),
                )
            }
            // Source provenance: category and/or free-form detail (either may be set).
            book.sourceType?.let { DetailRow(R.string.book_detail_row_source, sourceTypeLabel(it)) }
            DetailRow(R.string.book_detail_row_source_detail, book.sourceDetail)
            book.ageGroup?.let { DetailRow(R.string.book_detail_row_age_group, ageGroupLabel(it)) }
            DetailRow(
                R.string.book_detail_row_date_added,
                dev.khoj.pitaka.ui.loans.formatDate(book.addedDate),
            )
            // Maintainer attribution (D41) — shown when set.
            DetailRow(R.string.book_detail_row_added_by, book.addedBy)

            if (!book.notes.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                LocalizedText(R.string.detail_section_notes, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(book.notes, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(vertical = 8.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = stringResource(labelRes))
        }
        LocalizedText(
            labelRes,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DetailRow(labelRes: Int, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        LocalizedText(
            labelRes,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * D28: share action emits plain text only.
 * Format:
 *   - Title, author, ISBN  → "{title} by {author} (ISBN {isbn})"
 *   - Title, author, no ISBN → "{title} by {author}"
 *   - Title only, ISBN     → "{title} (ISBN {isbn})"
 *   - Title only           → "{title}"
 */
private fun shareBook(ctx: android.content.Context, book: Book) {
    val text = when {
        !book.author.isNullOrBlank() && !book.isbn.isNullOrBlank() ->
            ctx.getString(R.string.book_detail_share_template_with_isbn, book.title, book.author, book.isbn)
        !book.author.isNullOrBlank() ->
            ctx.getString(R.string.book_detail_share_template, book.title, book.author)
        !book.isbn.isNullOrBlank() ->
            ctx.getString(R.string.book_detail_share_template_no_author_with_isbn, book.title, book.isbn)
        else ->
            ctx.getString(R.string.book_detail_share_template_no_author, book.title)
    }
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    val chooser = Intent.createChooser(send, ctx.getString(R.string.book_detail_share_chooser))
    chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    ctx.startActivity(chooser)
}
