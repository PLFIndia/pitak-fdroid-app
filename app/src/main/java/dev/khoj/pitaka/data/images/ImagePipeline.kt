package dev.khoj.pitaka.data.images

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import java.io.InputStream
import javax.inject.Inject

/**
 * Pure-pixel utilities for downscaling cover images before publish or
 * before persisting a user-supplied cover (Phase 8 D23).
 *
 * Two operations:
 *  - [downscaleForPublish]: max 400×600 (book aspect), JPEG quality 80.
 *    Targets ~30–60KB per cover so a 1000-book library publishes
 *    in <50MB.
 *  - [centerCropSquare]: max 256×256 PNG for the library logo.
 *
 * No Android Bitmap APIs leak out — input is `InputStream` /
 * `ByteArray`, output is `ByteArray`. Easy to unit-test.
 */
class ImagePipeline @Inject constructor() {

    fun downscaleForPublish(
        input: InputStream,
        maxWidth: Int = COVER_MAX_W,
        maxHeight: Int = COVER_MAX_H,
        jpegQuality: Int = COVER_JPEG_Q,
    ): ByteArray? {
        // Read the whole stream into memory once so we can decode it twice:
        // pass 1 reads only the bounds (inJustDecodeBounds), pass 2 decodes
        // with an inSampleSize so a 12-megapixel camera photo never inflates to
        // its full ~50MB ARGB_8888 bitmap before we shrink it. A book cover
        // target is tiny (400x600), so sampling first is both safer (no OOM on
        // low-RAM devices) and faster. Streams aren't reliably re-readable, so
        // buffer to bytes.
        val bytes = input.readBytes()

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxWidth, maxHeight)
        }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return null
        return try {
            scaleToBounds(src, maxWidth, maxHeight).toJpeg(jpegQuality)
        } finally {
            src.recycle()
        }
    }

    /**
     * Largest power-of-two sample size that keeps the sampled image at or above
     * the target bounds (so the subsequent exact [scaleToBounds] still has
     * enough pixels). Standard Android "Loading Large Bitmaps Efficiently"
     * recipe.
     */
    private fun computeInSampleSize(srcW: Int, srcH: Int, reqW: Int, reqH: Int): Int {
        var sample = 1
        var halfW = srcW / 2
        var halfH = srcH / 2
        while (halfW / sample >= reqW && halfH / sample >= reqH) {
            sample *= 2
        }
        return sample
    }

    fun centerCropSquare(
        input: InputStream,
        size: Int = LOGO_SIZE,
    ): ByteArray? {
        val src = BitmapFactory.decodeStream(input) ?: return null
        return try {
            val side = minOf(src.width, src.height)
            val xOff = (src.width - side) / 2
            val yOff = (src.height - side) / 2
            val cropped = Bitmap.createBitmap(src, xOff, yOff, side, side)
            val scaled = Bitmap.createScaledBitmap(cropped, size, size, true)
            try {
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
                out.toByteArray()
            } finally {
                if (scaled !== cropped) cropped.recycle()
                scaled.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    private fun scaleToBounds(src: Bitmap, maxW: Int, maxH: Int): Bitmap {
        if (src.width <= maxW && src.height <= maxH) return src
        val ratio = minOf(maxW.toFloat() / src.width, maxH.toFloat() / src.height)
        val newW = (src.width * ratio).toInt().coerceAtLeast(1)
        val newH = (src.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, newW, newH, true)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    companion object {
        const val COVER_MAX_W = 400
        const val COVER_MAX_H = 600
        const val COVER_JPEG_Q = 80
        const val LOGO_SIZE = 256
    }
}
