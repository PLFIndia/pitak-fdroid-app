package dev.khoj.pitaka.domain.usecase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F-09 (audit): the bundled publish viewer ships a Content-Security-
 * Policy meta tag whose `img-src` list mirrors
 * [CoverUrlAllowList.ALLOWED_HOSTS]. If either side is edited without
 * the other, this test fails \u2014 single source of truth for cover
 * origins.
 *
 * The HTML asset is read directly from the source set
 * (`src/main/assets/publish/index.html`). We don't need Robolectric or
 * AGP's merged-assets pipeline for this — the file is ours, no overlay,
 * and a plain JVM unit test can read it as long as we find the project
 * directory regardless of which CWD Gradle picks.
 */
class PublishViewerCspTest {

    private val html: String by lazy {
        val rel = "src/main/assets/publish/index.html"
        // Gradle defaults CWD to the subproject (here `app/`), but a
        // developer invoking the test from the repo root or from inside
        // an IDE may give us either. Walk up from CWD looking for the file.
        val tried = mutableListOf<java.io.File>()
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir"))
        var found: java.io.File? = null
        while (dir != null && found == null) {
            for (sub in listOf(".", "app")) {
                val candidate = java.io.File(java.io.File(dir, sub), rel).normalize()
                tried += candidate
                if (candidate.isFile) { found = candidate; break }
            }
            dir = dir.parentFile
        }
        val file = found ?: error("publish/index.html not found. Tried: $tried")
        file.readText(Charsets.UTF_8)
    }

    private val cspMeta: String by lazy {
        // Pull the content="..." attribute of the CSP meta tag.
        val rx = Regex(
            """<meta\s+http-equiv="Content-Security-Policy"\s+content="([^"]+)"\s*/?>""",
            RegexOption.IGNORE_CASE,
        )
        val match = rx.find(html) ?: error("CSP meta tag not found in index.html")
        match.groupValues[1]
    }

    @Test
    fun csp_meta_is_present() {
        assertThat(cspMeta).isNotEmpty()
    }

    @Test
    fun default_src_locked_to_self() {
        assertThat(cspMeta).contains("default-src 'self'")
    }

    @Test
    fun img_src_lists_self_and_data_and_all_allowed_hosts() {
        // Must include self and data: for the bundled placeholder + logo
        // data URLs.
        assertThat(cspMeta).containsMatch("""img-src[^;]*\s'self'""")
        assertThat(cspMeta).containsMatch("""img-src[^;]*\sdata:""")

        // Each Kotlin-side allow-listed host must appear as an https:// origin.
        for (host in CoverUrlAllowList.ALLOWED_HOSTS) {
            assertThat(cspMeta).contains("https://$host")
        }
    }

    /**
     * F-22: the original drift guard only checked that every allow-listed host
     * APPEARS in the CSP — it could not catch a CSP that lists an EXTRA host
     * absent from [CoverUrlAllowList.ALLOWED_HOSTS]. That is the dangerous
     * direction: a CSP looser than the Kotlin sanitizer would let the published
     * page load images from an origin the sanitizer rejects, silently widening
     * the F-09 exfiltration surface. This test pins the `img-src` host set to
     * EXACTLY {'self', data:} ∪ {https://<each allowed host>}, so drift in
     * either direction fails the build.
     */
    @Test
    fun img_src_has_no_host_beyond_the_kotlin_allow_list() {
        // Extract the img-src directive body (everything up to the next ';').
        val imgSrc = Regex("""img-src([^;]*)""")
            .find(cspMeta)?.groupValues?.get(1)
            ?: error("img-src directive not found in CSP")

        // Tokenise into individual sources.
        val tokens = imgSrc.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.toSet()

        // The exact set we permit: keyword sources + one https:// origin per
        // allow-listed host. Nothing else may appear.
        val expected = buildSet {
            add("'self'")
            add("data:")
            for (host in CoverUrlAllowList.ALLOWED_HOSTS) add("https://$host")
        }

        assertThat(tokens).isEqualTo(expected)
    }

    @Test
    fun script_src_excludes_unsafe_eval() {
        // We allow inline because the viewer is a single self-contained
        // file, but we never allow eval / new Function.
        assertThat(cspMeta).doesNotContain("'unsafe-eval'")
    }

    @Test
    fun object_src_and_frame_ancestors_locked_down() {
        assertThat(cspMeta).contains("object-src 'none'")
        assertThat(cspMeta).contains("frame-ancestors 'none'")
        assertThat(cspMeta).contains("base-uri 'none'")
        assertThat(cspMeta).contains("form-action 'none'")
    }

    @Test
    fun connect_src_is_self_only() {
        // The viewer fetches only the sibling books.json.
        assertThat(cspMeta).contains("connect-src 'self'")
    }
}
