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

/**
 * Bridge between CameraX `ImageAnalysis` and Pitaka's [BarcodeAnalyzer], backed
 * by ZXing instead of ML Kit (F-Droid variant — no proprietary Google deps).
 *
 * ZXing is pure-Java and already shipped (QrEncoder uses it to GENERATE QR);
 * here we use it to READ. The CameraX YUV_420_888 frame's luminance (Y) plane
 * is fed straight into a [PlanarYUVLuminanceSource]; ZXing needs no colour data
 * to decode a 1D barcode. The pure detection/debounce policy in
 * [BarcodeAnalyzer] is reused unchanged.
 *
 * Reference: the CameraX-Y-plane → PlanarYUVLuminanceSource path mirrors
 * zxing-android-embedded's DecoderThread (Apache-2.0). Adapted to feed Pitaka's
 * existing BarcodeAnalyzer seam rather than ZXing's own callback.
 */
class ZxingBarcodeAnalyzer(
    private val analyzer: BarcodeAnalyzer,
    private val onIsbnDetected: (String) -> Unit,
) : ImageAnalysis.Analyzer {

    // Restrict to EAN-13 (Bookland ISBN). One reader instance, reset per frame.
    private val reader = MultiFormatReader().apply {
        setHints(
            mapOf(
                DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.EAN_13),
                DecodeHintType.TRY_HARDER to true,
            )
        )
    }

    @SuppressLint("UnsafeOptInUsageError") // imageProxy.image is officially supported per docs
    override fun analyze(imageProxy: ImageProxy) {
        try {
            val rawValue = decode(imageProxy)
            if (rawValue != null) {
                val decision = analyzer.analyze(
                    rawValue = rawValue,
                    format = BarcodeAnalyzer.EAN_13_FORMAT,
                    eanFormatCode = BarcodeAnalyzer.EAN_13_FORMAT,
                )
                if (decision is BarcodeDecision.Accept) {
                    onIsbnDetected(decision.isbn)
                }
            }
        } catch (t: Throwable) {
            // §1.1: degrade gracefully. A frame that fails to decode is normal
            // (ZXing throws NotFoundException when no barcode is present); the
            // next frame retries naturally. Never crash the scanner.
        } finally {
            imageProxy.close()
        }
    }

    /** Returns the decoded barcode text, or null if no barcode in this frame. */
    private fun decode(imageProxy: ImageProxy): String? {
        val plane = imageProxy.planes.firstOrNull() ?: return null
        val width = imageProxy.width
        val height = imageProxy.height

        // Compact the Y plane to exactly width*height, stripping row padding
        // (rowStride may exceed width on some devices).
        val yData = extractLuminance(plane.buffer, plane.rowStride, width, height)

        // Rotate the luminance buffer upright so a barcode the user holds
        // horizontally on screen presents as horizontal image rows (1D readers
        // scan rows). rotationDegrees is the clockwise rotation to upright.
        val (data, w, h) = rotateLuminance(
            yData, width, height, imageProxy.imageInfo.rotationDegrees,
        )

        val source = PlanarYUVLuminanceSource(
            data, w, h, 0, 0, w, h, false,
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

    private fun extractLuminance(
        buffer: java.nio.ByteBuffer,
        rowStride: Int,
        width: Int,
        height: Int,
    ): ByteArray {
        buffer.rewind()
        val out = ByteArray(width * height)
        if (rowStride == width) {
            buffer.get(out, 0, minOf(buffer.remaining(), out.size))
        } else {
            val row = ByteArray(rowStride)
            var pos = 0
            for (y in 0 until height) {
                if (buffer.remaining() < rowStride) break
                buffer.get(row, 0, rowStride)
                System.arraycopy(row, 0, out, pos, width)
                pos += width
            }
        }
        return out
    }

    /**
     * Rotates a width×height luminance byte array by [rotationDegrees]
     * (0/90/180/270, clockwise). Returns the rotated data and its new
     * dimensions. Indices derived for clockwise rotation; verified against the
     * standard image-rotation transforms.
     */
    private fun rotateLuminance(
        data: ByteArray,
        width: Int,
        height: Int,
        rotationDegrees: Int,
    ): Triple<ByteArray, Int, Int> = when (rotationDegrees) {
        90 -> {
            val out = ByteArray(data.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[x * height + (height - 1 - y)] = data[y * width + x]
                }
            }
            Triple(out, height, width)
        }
        180 -> {
            val out = ByteArray(data.size)
            val last = data.size - 1
            for (i in data.indices) out[i] = data[last - i]
            Triple(out, width, height)
        }
        270 -> {
            val out = ByteArray(data.size)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    out[(width - 1 - x) * height + y] = data[y * width + x]
                }
            }
            Triple(out, height, width)
        }
        else -> Triple(data, width, height)
    }
}
