package dev.khoj.pitaka.data.images

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Robolectric is needed because [ImagePipeline] uses Android's
 * BitmapFactory / Bitmap, which is the standard Android image pipeline
 * we want to test (no point mocking).
 */
@RunWith(RobolectricTestRunner::class)
class ImagePipelineTest {

    private val pipeline = ImagePipeline()

    @Test
    fun `downscaleForPublish produces bytes for a valid PNG`() {
        // Smallest possible 100×100 valid PNG (encoded inline).
        val png = makeBlankPng(width = 100, height = 100)
        val result = pipeline.downscaleForPublish(png.inputStream())
        assertThat(result).isNotNull()
        assertThat(result!!.size).isGreaterThan(0)
        // 100x100 is already smaller than 400x600, so the JPEG should
        // still be tiny.
        assertThat(result.size).isLessThan(10_000)
    }

    @Test
    fun `downscaleForPublish downscales an oversized image`() {
        val png = makeBlankPng(width = 2400, height = 3600)
        val result = pipeline.downscaleForPublish(png.inputStream())
        assertThat(result).isNotNull()
        // After downscale to <=400×600 with JPEG q80, even a uniform image
        // should land well under 50KB.
        assertThat(result!!.size).isLessThan(50_000)
    }

    @Test
    fun `centerCropSquare produces a PNG`() {
        val png = makeBlankPng(width = 500, height = 200)
        val result = pipeline.centerCropSquare(png.inputStream())
        assertThat(result).isNotNull()
        // PNG signature
        val sig = result!!.take(8).map { it.toInt() and 0xFF }
        assertThat(sig).containsExactly(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A).inOrder()
    }

    /** Builds a uniform-color PNG via Android's Bitmap. */
    private fun makeBlankPng(width: Int, height: Int): ByteArray {
        val bmp = android.graphics.Bitmap.createBitmap(
            width, height, android.graphics.Bitmap.Config.ARGB_8888,
        )
        bmp.eraseColor(android.graphics.Color.MAGENTA)
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return out.toByteArray()
    }
}
