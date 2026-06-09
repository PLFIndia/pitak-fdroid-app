package dev.khoj.pitaka.ui.common

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes a short string (a library ID, PLAN-merge.md D40) as a QR-code [Bitmap]
 * for in-person pairing. Pure ZXing — no UI deps, no network. Reading reuses the
 * existing ML Kit scanner.
 */
object QrEncoder {

    /**
     * @param content the text to encode (e.g. "pitaka-lib:<id>").
     * @param sizePx the square pixel size of the output bitmap.
     * @return an ARGB_8888 black-on-white QR bitmap, or null if encoding fails
     *         (e.g. content too long for the format — never expected for an ID).
     */
    fun encode(content: String, sizePx: Int = 600): Bitmap? = runCatching {
        val hints = mapOf(
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 1,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val w = matrix.width
        val h = matrix.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val offset = y * w
            for (x in 0 until w) {
                pixels[offset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, w, 0, 0, w, h)
        }
    }.getOrNull()

    /**
     * The QR payload scheme for a Pitaka library ID. Prefixing lets the scanner
     * distinguish a library-pairing QR from an arbitrary QR the camera might see.
     */
    const val LIBRARY_ID_PREFIX = "pitaka-lib:"

    fun libraryIdPayload(id: String): String = "$LIBRARY_ID_PREFIX$id"

    /**
     * Extracts a VALID library ID from a scanned payload, or null if the QR isn't
     * a genuine Pitak library QR. Two-layer validation so the scanner only accepts
     * our own codes:
     *   1. it must carry the `pitaka-lib:` prefix, and
     *   2. the ID itself must look like one we mint — a 16+ char lowercase
     *      hex/UUID string (our IDs are 32-char hex via SecureRandom, or a
     *      hyphen-stripped UUID). This rejects a prefix typed onto junk, a
     *      truncated scan, or someone hand-crafting `pitaka-lib:hello`.
     */
    fun parseLibraryIdPayload(scanned: String): String? {
        val trimmed = scanned.trim()
        if (!trimmed.startsWith(LIBRARY_ID_PREFIX)) return null
        val id = trimmed.removePrefix(LIBRARY_ID_PREFIX)
        return id.takeIf { isValidLibraryId(it) }
    }

    /** True when [id] has the shape of a Pitak-minted library ID. */
    fun isValidLibraryId(id: String): Boolean =
        dev.khoj.pitaka.domain.model.LibraryId.isValid(id)
}
