package dev.khoj.pitaka.data.images

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Pure unit tests for [CoverPaths] — the single source of truth for how a
 * book cover is referenced (PLAN-covers.md D1). No Android dependencies.
 */
class CoverPathsTest {

    private val filesDir = File("/data/user/0/dev.khoj.pitaka/files")

    // --- isLocal ----------------------------------------------------------

    @Test
    fun `relative covers reference is local`() {
        assertThat(CoverPaths.isLocal("covers/abc-123.jpg")).isTrue()
    }

    @Test
    fun `legacy file uri is local`() {
        assertThat(CoverPaths.isLocal("file:///data/user/0/x/files/covers/7.jpg")).isTrue()
    }

    @Test
    fun `remote https url is not local`() {
        assertThat(CoverPaths.isLocal("https://covers.openlibrary.org/b/id/1-M.jpg")).isFalse()
    }

    @Test
    fun `blank and null are not local`() {
        assertThat(CoverPaths.isLocal(null)).isFalse()
        assertThat(CoverPaths.isLocal("")).isFalse()
        assertThat(CoverPaths.isLocal("   ")).isFalse()
    }

    // --- relativeFor ------------------------------------------------------

    @Test
    fun `relativeFor builds covers slash uuid dot jpg`() {
        val uuid = "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"
        assertThat(CoverPaths.relativeFor(uuid)).isEqualTo("covers/$uuid.jpg")
    }

    // --- leafOf -----------------------------------------------------------

    @Test
    fun `leafOf extracts the filename from relative reference`() {
        assertThat(CoverPaths.leafOf("covers/abc-123.jpg")).isEqualTo("abc-123.jpg")
    }

    @Test
    fun `leafOf extracts the filename from legacy file uri`() {
        assertThat(CoverPaths.leafOf("file:///data/user/0/x/files/covers/7.jpg"))
            .isEqualTo("7.jpg")
    }

    @Test
    fun `leafOf rejects path traversal`() {
        assertThat(CoverPaths.leafOf("covers/../secrets.txt")).isNull()
    }

    @Test
    fun `leafOf rejects nested directories`() {
        assertThat(CoverPaths.leafOf("covers/sub/x.jpg")).isNull()
    }

    @Test
    fun `leafOf rejects protocol smuggling`() {
        assertThat(CoverPaths.leafOf("covers/javascript:alert(1)")).isNull()
    }

    @Test
    fun `leafOf rejects empty leaf`() {
        assertThat(CoverPaths.leafOf("covers/")).isNull()
    }

    @Test
    fun `leafOf rejects remote and blank`() {
        assertThat(CoverPaths.leafOf("https://covers.openlibrary.org/b/id/1.jpg")).isNull()
        assertThat(CoverPaths.leafOf(null)).isNull()
        assertThat(CoverPaths.leafOf("")).isNull()
    }

    // --- absoluteCoverFile ------------------------------------------------

    @Test
    fun `absoluteCoverFile resolves under filesDir covers`() {
        val f = CoverPaths.absoluteCoverFile(filesDir, "covers/abc.jpg")
        assertThat(f).isNotNull()
        assertThat(f!!.path).isEqualTo("${filesDir.path}/covers/abc.jpg")
    }

    @Test
    fun `absoluteCoverFile resolves legacy file uri leaf under our filesDir`() {
        // Even a legacy absolute path is re-anchored to OUR filesDir by leaf —
        // this is what lets a restored DB resolve covers on a new install whose
        // package-qualified path differs from the exporting device.
        val f = CoverPaths.absoluteCoverFile(
            filesDir,
            "file:///data/user/0/other.pkg/files/covers/7.jpg",
        )
        assertThat(f).isNotNull()
        assertThat(f!!.path).isEqualTo("${filesDir.path}/covers/7.jpg")
    }

    @Test
    fun `absoluteCoverFile returns null for unsafe or remote references`() {
        assertThat(CoverPaths.absoluteCoverFile(filesDir, "covers/../x")).isNull()
        assertThat(CoverPaths.absoluteCoverFile(filesDir, "https://x/y.jpg")).isNull()
        assertThat(CoverPaths.absoluteCoverFile(filesDir, null)).isNull()
    }
}
