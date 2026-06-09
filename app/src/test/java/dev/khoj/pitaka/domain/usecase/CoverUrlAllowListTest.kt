package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F-09 (audit): the cover-URL allow-list is the boundary between the
 * device's local book records and the world-facing `books.json`. Any
 * gap here turns the public page into a tracker for visitors.
 */
class CoverUrlAllowListTest {

    private fun sanitize(s: String?) = CoverUrlAllowList.sanitizeCoverUrl(s)

    // --- Allow-listed remote covers ---------------------------------------

    @Test
    fun openlibrary_https_url_passes() {
        val url = "https://covers.openlibrary.org/b/id/12345-L.jpg"
        assertThat(sanitize(url)).isEqualTo(url)
    }

    @Test
    fun google_books_https_url_passes() {
        val url = "https://books.google.com/books/content?id=abc&img=1"
        assertThat(sanitize(url)).isEqualTo(url)
    }

    @Test
    fun google_books_user_content_host_passes() {
        val url = "https://books.googleusercontent.com/books/content?id=xyz"
        assertThat(sanitize(url)).isEqualTo(url)
    }

    @Test
    fun host_match_is_case_insensitive() {
        val url = "https://Covers.OpenLibrary.org/b/id/1-L.jpg"
        assertThat(sanitize(url)).isEqualTo(url)
    }

    // --- Bundled local covers ---------------------------------------------

    @Test
    fun bundled_covers_path_passes() {
        val path = "covers/3f2c7a1b9e4d8051.jpg"
        assertThat(sanitize(path)).isEqualTo(path)
    }

    @Test
    fun bundled_path_rejects_traversal() {
        assertThat(sanitize("covers/../etc/passwd")).isNull()
        assertThat(sanitize("covers/..%2Fpasswd")).isNull() // raw .. anywhere
    }

    @Test
    fun bundled_path_rejects_nested_directories() {
        assertThat(sanitize("covers/sub/x.jpg")).isNull()
    }

    @Test
    fun bundled_path_rejects_protocol_smuggling() {
        // "covers/javascript:alert(1)" would be rendered as src=, browsers
        // ignore js: in img-src, but it's a confused log line — reject.
        assertThat(sanitize("covers/javascript:alert(1)")).isNull()
    }

    @Test
    fun bundled_path_rejects_empty_filename() {
        assertThat(sanitize("covers/")).isNull()
    }

    // --- Rejections --------------------------------------------------------

    @Test
    fun http_scheme_rejected_even_on_allowed_host() {
        // Insecure scheme drops visitor cookies / Referer over the wire.
        assertThat(sanitize("http://covers.openlibrary.org/b/id/1-L.jpg")).isNull()
    }

    @Test
    fun non_allowlisted_https_host_rejected() {
        assertThat(sanitize("https://attacker.example/track?u=visitor")).isNull()
    }

    @Test
    fun subdomain_of_allowed_host_rejected() {
        // We allow `books.google.com`, not arbitrary `*.google.com`.
        assertThat(sanitize("https://evil.google.com/x.jpg")).isNull()
    }

    @Test
    fun userinfo_in_url_rejected() {
        // https://attacker@host/ is an auth-confusion display vector.
        assertThat(sanitize("https://attacker@covers.openlibrary.org/x.jpg"))
            .isNull()
    }

    @Test
    fun javascript_scheme_rejected() {
        assertThat(sanitize("javascript:alert(1)")).isNull()
    }

    @Test
    fun data_url_rejected() {
        // The viewer renders covers in <img src>; data: URLs would render
        // and bloat books.json. The bundled-cover path is for local images.
        assertThat(sanitize("data:image/png;base64,AAAA")).isNull()
    }

    @Test
    fun file_scheme_rejected() {
        assertThat(sanitize("file:///etc/hosts")).isNull()
    }

    @Test
    fun about_blank_rejected() {
        assertThat(sanitize("about:blank")).isNull()
    }

    @Test
    fun protocol_relative_rejected() {
        // `//evil.com/x.jpg` would inherit the page scheme on GitHub Pages.
        assertThat(sanitize("//attacker.example/x.jpg")).isNull()
    }

    @Test
    fun scheme_relative_path_rejected() {
        // Bare `/foo.jpg` is same-origin but escapes the covers/ namespace.
        assertThat(sanitize("/covers/x.jpg")).isNull()
    }

    @Test
    fun blank_input_returns_null() {
        assertThat(sanitize(null)).isNull()
        assertThat(sanitize("")).isNull()
        assertThat(sanitize("   ")).isNull()
    }

    @Test
    fun malformed_url_returns_null() {
        assertThat(sanitize("ht!tp://broken")).isNull()
        // URI parse succeeds but host is null \u2014 still rejected.
        assertThat(sanitize("https:///no-host/x.jpg")).isNull()
    }

    @Test
    fun whitespace_around_otherwise_valid_url_trimmed_and_accepted() {
        val raw = "  https://covers.openlibrary.org/b/id/1-L.jpg  "
        assertThat(sanitize(raw)).isEqualTo(raw.trim())
    }
}
