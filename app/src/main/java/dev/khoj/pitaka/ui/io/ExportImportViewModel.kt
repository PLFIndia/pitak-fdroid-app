package dev.khoj.pitaka.ui.io

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.bundle.LibraryBundle
import dev.khoj.pitaka.data.import_.ImportFormat
import dev.khoj.pitaka.domain.usecase.ExportUseCase
import dev.khoj.pitaka.domain.usecase.ImportLibraryUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import javax.inject.Inject

@HiltViewModel
class ExportImportViewModel @Inject constructor(
    private val app: Application,
    private val exportUseCase: ExportUseCase,
    private val importUseCase: ImportLibraryUseCase,
    private val bundle: LibraryBundle,
    private val prefs: dev.khoj.pitaka.data.prefs.AppPreferences,
) : AndroidViewModel(app) {

    // --- Export ---
    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    init {
        // Reflect persisted PDF column selection into the UI state.
        viewModelScope.launch {
            prefs.pdfColumns().collect { cols ->
                _exportState.update { it.copy(pdfColumns = cols.toSet()) }
            }
        }
    }

    private val _exportEvents = Channel<ExportEvent>(Channel.BUFFERED)
    val exportEvents = _exportEvents.receiveAsFlow()

    fun onScopeChange(scope: ExportUseCase.Scope) = _exportState.update { it.copy(scope = scope) }
    fun onFormatChange(format: ExportUseCase.Format) = _exportState.update { it.copy(format = format) }

    /** Toggle a PDF column on/off. Title is mandatory and ignored if toggled. */
    fun onPdfColumnToggle(column: dev.khoj.pitaka.data.export.PdfColumn) {
        if (column == dev.khoj.pitaka.data.export.PdfColumn.MANDATORY) return
        viewModelScope.launch {
            val current = prefs.pdfColumns().first().toMutableSet()
            if (column in current) current.remove(column) else current.add(column)
            prefs.setPdfColumns(current.toList())
        }
    }

    fun onRequestSave() {
        viewModelScope.launch {
            val prepared = exportUseCase(_exportState.value.scope, _exportState.value.format)
            // Stream the export into a private cache file (P6 streaming invariant
            // preserved — writeTo never materializes the whole library as a String),
            // then hand a content:// URI to the share sheet. Android "Sharing files"
            // FileProvider pattern: app-private bytes, granted per-Intent, no storage
            // permission.
            val uri = runCatching {
                val dir = java.io.File(app.cacheDir, "exports").apply { mkdirs() }
                // Overwrite any stale file of the same name; the timestamp in the
                // filename already makes collisions practically impossible.
                val file = java.io.File(dir, prepared.filename)
                java.io.FileOutputStream(file).use { out ->
                    prepared.writeTo(out)
                    out.flush()
                }
                androidx.core.content.FileProvider.getUriForFile(
                    app,
                    "${app.packageName}.fileprovider",
                    file,
                )
            }.getOrNull()

            if (uri != null) {
                _exportEvents.trySend(ExportEvent.ShareReady(uri, prepared.mime))
            } else {
                _exportEvents.trySend(ExportEvent.SaveFailed)
            }
        }
    }

    // --- Import ---
    private val _importState = MutableStateFlow(ImportUiState())
    val importState: StateFlow<ImportUiState> = _importState.asStateFlow()

    fun onFilePicked(uri: Uri) {
        viewModelScope.launch {
            _importState.value = _importState.value.copy(running = true, summary = null, errorText = null)
            val summary = withContext(Dispatchers.IO) { importFrom(uri) }
            if (summary == null) {
                _importState.value = _importState.value.copy(
                    running = false,
                    errorText = app.getString(R.string.import_read_failed),
                )
            } else {
                _importState.value = _importState.value.copy(running = false, summary = summary)
            }
        }
    }

    /**
     * Reads [uri] and imports it. A bundle (.zip) is detected by the ZIP local-
     * file-header magic (`PK\x03\x04`) peeked off the front of the stream — we
     * route on content, not on filename/MIME, because a shared file's reported
     * type is unreliable. Anything else is treated as a text import (JSON / CSV),
     * matching the existing sniffer. Returns null only when the source could not
     * be read at all.
     */
    private suspend fun importFrom(uri: Uri): ImportLibraryUseCase.Summary? {
        return runCatching {
            app.contentResolver.openInputStream(uri)?.use { raw ->
                val input = BufferedInputStream(raw)
                val magic = ByteArray(4)
                input.mark(4)
                val read = input.read(magic, 0, 4)
                input.reset()
                val isZip = read == 4 &&
                    magic[0] == 0x50.toByte() && magic[1] == 0x4B.toByte() && // "PK"
                    magic[2] == 0x03.toByte() && magic[3] == 0x04.toByte()

                if (isZip) {
                    when (val r = bundle.read(input)) {
                        is LibraryBundle.ReadResult.Success ->
                            importUseCase.apply(r.payload, ImportFormat.PitakaBundle)
                        is LibraryBundle.ReadResult.Failed ->
                            ImportLibraryUseCase.Summary(null, 0, 0, 0, 0, listOf(r.reason))
                    }
                } else {
                    val text = BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                    if (text.isEmpty()) null else importUseCase(text)
                }
            }
        }.getOrNull()
    }
}

private fun <T> MutableStateFlow<T>.update(block: (T) -> T) { value = block(value) }

data class ExportUiState(
    val scope: ExportUseCase.Scope = ExportUseCase.Scope.Both,
    val format: ExportUseCase.Format = ExportUseCase.Format.Json,
    val pdfColumns: Set<dev.khoj.pitaka.data.export.PdfColumn> =
        dev.khoj.pitaka.data.export.PdfColumn.DEFAULT.toSet(),
)

sealed interface ExportEvent {
    /** Export written to a cache file; launch the share sheet with this URI. */
    data class ShareReady(val uri: Uri, val mime: String) : ExportEvent
    data object SaveFailed : ExportEvent
}

data class ImportUiState(
    val running: Boolean = false,
    val summary: ImportLibraryUseCase.Summary? = null,
    val errorText: String? = null,
)
