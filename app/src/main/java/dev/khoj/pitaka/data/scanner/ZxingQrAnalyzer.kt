package dev.khoj.pitaka.data.scanner

import android.annotation.SuppressLint
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import dev.khoj.pitaka.ui.common.QrEncoder

/**
 * CameraX `ImageAnalysis` bridge for the library-pairing QR scan (PLAN-merge.md
 * D40), backed by ZXing instead of ML Kit (F-Droid variant). Only QR_CODE is
 * decoded, and only a payload that [QrEncoder.parseLibraryIdPayload] validates
 * as a genuine Pitak library ID is accepted — a random web/Wi-Fi/contact QR the
 * camera happens to see is ignored.
 *
 * Hands the bare, validated ID upstream via [onLibraryIdScanned].
 *
 * The YUV-luminance decode path is identical to [ZxingBarcodeAnalyzer]; QR is a
 * 2D symbol so no rotation is required for correctness (ZXing's detector finds
 * the finder patterns at any orientation), but we still strip row padding.
 */
class ZxingQrAnalyzer(
    private val onLibraryIdScanned: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    @SuppressLint("UnsafeOptInUsageError") // imageProxy.image is officially supported per docs
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val raw = decode(imageProxy)
            if (raw != null) {
                val id = QrEncoder.parseLibraryIdPayload(raw)
                if (id != null) onLibraryIdScanned(id)
            }
        } catch (t: Throwable) {
            // §1.1: a frame with no QR throws NotFoundException — normal. Retry
            // on the next frame; never crash.
        } finally {
            imageProxy.close()
        }
    }

    private fun decode(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height
        val buffer = plane.buffer
        val rowStride = plane.rowStride

        buffer.rewind()
        val yData = ByteArray(width * height)
        if (rowStride == width) {
            buffer.get(yData, 0, minOf(buffer.remaining(), yData.size))
        } else {
            val row = ByteArray(rowStride)
            var pos = 0
            for (y in 0 until height) {
                if (buffer.remaining() < rowStride) break
                buffer.get(row, 0, rowStride)
                System.arraycopy(row, 0, yData, pos, width)
                pos += width
            }
        }

        val source = PlanarYUVLuminanceSource(
            yData, width, height, 0, 0, width, height, false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        return try {
            reader.decodeWithState(bitmap).text
        } catch (t: Throwable) {
            null
        } finally {
            reader.reset()
        }
    }
}
