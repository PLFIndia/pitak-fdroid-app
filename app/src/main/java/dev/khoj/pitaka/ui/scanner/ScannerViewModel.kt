package dev.khoj.pitaka.ui.scanner

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.khoj.pitaka.data.scanner.BarcodeAnalyzer
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import javax.inject.Inject

@HiltViewModel
class ScannerViewModel @Inject constructor() : ViewModel() {

    // BarcodeAnalyzer holds debounce state; one instance per scanner session.
    val analyzer = BarcodeAnalyzer()

    private val _events = Channel<ScannerEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    fun onIsbnDetected(isbn: String) {
        _events.trySend(ScannerEvent.IsbnScanned(isbn))
    }
}

sealed interface ScannerEvent {
    data class IsbnScanned(val isbn: String) : ScannerEvent
}
