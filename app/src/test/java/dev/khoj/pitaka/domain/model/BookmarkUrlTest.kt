package dev.khoj.pitaka.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookmarkUrlTest {

    // --- accepted ---

    @Test
    fun accepts_plain_https_host() {
        assertThat(BookmarkUrl.normalizeOrNull("https://example.com"))
            .isEqualTo("https://example.com")
    }

    @Test
    fun accepts_github_io_pages() {
        assertThat(BookmarkUrl.isValid("https://alice.github.io/library/")).isTrue()
    }

    @Test
    fun accepts_pages_dev() {
        assertThat(BookmarkUrl.isValid("https://my-lib.pages.dev")).isTrue()
    }

    @Test
    fun accepts_custom_domain_with_path_and_query() {
        assertThat(BookmarkUrl.isValid("https://books.school.edu/catalog?page=2#top")).isTrue()
    }

    @Test
    fun trims_surrounding_whitespace() {
        assertThat(BookmarkUrl.normalizeOrNull("  https://example.com  "))
            .isEqualTo("https://example.com")
    }

    @Test
    fun scheme_is_case_insensitive_but_url_preserved() {
        assertThat(BookmarkUrl.normalizeOrNull("HTTPS://Example.com/Path"))
            .isEqualTo("HTTPS://Example.com/Path")
    }

    // --- rejected ---

    @Test
    fun rejects_blank() {
        assertThat(BookmarkUrl.normalizeOrNull("")).isNull()
        assertThat(BookmarkUrl.normalizeOrNull("   ")).isNull()
    }

    @Test
    fun rejects_http() {
        assertThat(BookmarkUrl.isValid("http://example.com")).isFalse()
    }

    @Test
    fun rejects_other_schemes() {
        assertThat(BookmarkUrl.isValid("ftp://example.com")).isFalse()
        assertThat(BookmarkUrl.isValid("javascript:alert(1)")).isFalse()
        assertThat(BookmarkUrl.isValid("mailto:a@b.com")).isFalse()
    }

    @Test
    fun rejects_scheme_relative_and_bare_host() {
        assertThat(BookmarkUrl.isValid("//example.com")).isFalse()
        assertThat(BookmarkUrl.isValid("example.com")).isFalse()
    }

    @Test
    fun rejects_https_without_host() {
        assertThat(BookmarkUrl.isValid("https://")).isFalse()
        assertThat(BookmarkUrl.isValid("https:///path")).isFalse()
    }

    @Test
    fun rejects_host_without_dot() {
        assertThat(BookmarkUrl.isValid("https://localhost")).isFalse()
        assertThat(BookmarkUrl.isValid("https://localhost:8080/x")).isFalse()
    }

    @Test
    fun rejects_embedded_whitespace() {
        assertThat(BookmarkUrl.isValid("https://exa mple.com")).isFalse()
        assertThat(BookmarkUrl.isValid("https://example.com/a b")).isFalse()
    }

    @Test
    fun rejects_leading_or_trailing_dot_host() {
        assertThat(BookmarkUrl.isValid("https://.com")).isFalse()
        assertThat(BookmarkUrl.isValid("https://example.")).isFalse()
    }
}
