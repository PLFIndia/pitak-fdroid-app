package dev.khoj.pitaka.data.scanner

/**
 * Result of a barcode detection attempt.
 *
 * - [Accept] — the barcode is an EAN-13 with the Bookland 978/979 prefix (per
 *   pitaka.md §6.3), and the same value hasn't been seen within the debounce
 *   window. The screen should treat this as the user's confirmed scan.
 * - [Reject] — the barcode is non-ISBN or didn't pass the debounce. Discard.
 */
sealed interface BarcodeDecision {
    data class Accept(val isbn: String) : BarcodeDecision
    data object Reject : BarcodeDecision
}

/**
 * Pure detection-and-debounce policy. Lives in the data layer so it can be
 * exercised by JVM unit tests — no Android framework, no ML Kit types.
 *
 * The Compose scanner wraps an ML Kit `Barcode` analyzer that forwards raw
 * values + format codes here; this class decides whether to surface them.
 */
class BarcodeAnalyzer(
    private val debounceMillis: Long = 2_000L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private var lastValue: String? = null
    private var lastAtMillis: Long = 0L

    /**
     * @param rawValue   the digits returned by the barcode reader (whitespace
     *                   should already be stripped by the caller).
     * @param format     0 = unknown; the ML Kit constants are passed through
     *                   verbatim. EAN-13 is `Barcode.FORMAT_EAN_13`. Other
     *                   formats are rejected.
     * @param eanFormatCode the platform's EAN-13 constant (so callers don't
     *                   leak ML Kit types into this class). Defaults to
     *                   `Barcode.FORMAT_EAN_13` = 32.
     */
    fun analyze(rawValue: String?, format: Int, eanFormatCode: Int = EAN_13_FORMAT): BarcodeDecision {
        if (rawValue == null) return BarcodeDecision.Reject
        if (format != eanFormatCode) return BarcodeDecision.Reject

        val cleaned = rawValue.filter { it.isDigit() }
        if (cleaned.length != 13) return BarcodeDecision.Reject
        val prefix = cleaned.substring(0, 3)
        if (prefix != "978" && prefix != "979") return BarcodeDecision.Reject

        val now = clock()
        if (cleaned == lastValue && now - lastAtMillis < debounceMillis) {
            return BarcodeDecision.Reject
        }
        lastValue = cleaned
        lastAtMillis = now
        return BarcodeDecision.Accept(cleaned)
    }

    companion object {
        /**
         * EAN-13 format marker. Value 32 historically mirrored ML Kit's
         * `Barcode.FORMAT_EAN_13`; the F-Droid variant decodes via ZXing, so
         * this is now just an internal sentinel the [ZxingBarcodeAnalyzer]
         * passes verbatim (ZXing reports format by `BarcodeFormat.EAN_13`, not
         * an int). Kept stable so the pure analyze() contract is unchanged.
         */
        const val EAN_13_FORMAT: Int = 32
    }
}
