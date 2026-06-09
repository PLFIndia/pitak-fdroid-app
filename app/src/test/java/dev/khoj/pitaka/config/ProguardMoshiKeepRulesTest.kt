package dev.khoj.pitaka.config

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * F-11 (audit) regression guard. The release build runs R8 with reflection-
 * based Moshi (KotlinJsonAdapterFactory, not @JsonClass codegen), so every
 * package that holds a Moshi-(de)serialized type MUST be covered by a `-keep`
 * rule in proguard-rules.pro — otherwise R8 obfuscates the field names,
 * reflective Moshi falls back to a generic LinkedHashTreeMap, and the
 * consuming cast crashes at runtime.
 *
 * This exact bug shipped once: `PitakaExport` (data.export) was serialized by
 * the export / publish / JSON-import paths but NOT kept, so importing a
 * previously-exported JSON crashed with
 * "LinkedHashTreeMap cannot be cast to Book" (caught on-device 2026-06-01).
 *
 * Build-green can't catch this — R8 only runs on release, and the crash is at
 * runtime. This test reads the actual proguard file and fails the build if a
 * known serialization package loses its keep rule. When you add a new Moshi
 * type in a new package, add the package here AND to proguard-rules.pro.
 */
class ProguardMoshiKeepRulesTest {

    private val proguard: String by lazy {
        val rel = "proguard-rules.pro"
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
        val file = found ?: error("proguard-rules.pro not found. Tried: $tried")
        file.readText(Charsets.UTF_8)
    }

    /**
     * Packages (or types) that hold reflection-Moshi-(de)serialized classes.
     * Each must have a keep rule. Derived from grepping `moshi.adapter<...>`
     * call sites across the codebase:
     *   - data.remote.**       : OpenLibrary / Google Books DTOs
     *   - domain.model.**      : Book, WishlistBook, Bookmark, BookMetadata
     *   - data.export.**       : PitakaExport (export / publish / import)
     *   - data.publish.PublishExport / PublishBook : world-facing publish payload
     *   - data.backup.BackupManifest : restore manifest
     */
    private val requiredKeepRoots = listOf(
        "dev.khoj.pitaka.data.remote.",
        "dev.khoj.pitaka.domain.model.",
        "dev.khoj.pitaka.data.export.",
        "dev.khoj.pitaka.data.publish.PublishExport",
        "dev.khoj.pitaka.data.publish.PublishBook",
        "dev.khoj.pitaka.data.publish.PublishManifest",
        "dev.khoj.pitaka.data.backup.BackupManifest",
    )

    @Test
    fun every_serialized_package_has_a_keep_rule() {
        for (root in requiredKeepRoots) {
            // Match a `-keep class <root>...` line (allowing ** / { *; } tails).
            val present = proguard.lineSequence().any { line ->
                val t = line.trim()
                t.startsWith("-keep") && t.contains(root)
            }
            assertThat(present).isTrue()
        }
    }

    @Test
    fun does_not_reintroduce_the_blanket_keep() {
        // The original blanket keep defeated R8 entirely (F-11). If it ever
        // comes back, this test fails — the scoped rules are the whole point.
        val hasBlanket = proguard.lineSequence().any { line ->
            val t = line.trim().replace(" ", "")
            t.startsWith("-keepclassdev.khoj.pitaka.**{*;}")
        }
        assertThat(hasBlanket).isFalse()
    }

    /**
     * F-22: the [requiredKeepRoots] list above is hand-maintained, so it can
     * only catch a KNOWN package losing its keep — not a NEW Moshi-serialized
     * type that the developer forgot to add to both the proguard file AND the
     * list. This test removes that blind spot by DISCOVERING the serialized
     * types from source: it scans every `adapter<…>` call site under
     * `src/main`, resolves each referenced type to its package, and asserts a
     * `-keep` rule covers it. A new reflectively-serialized type with no keep
     * fails the build here, at unit-test time, instead of silently shipping a
     * release that reads garbage JSON (the exact F-11 failure mode).
     */
    @Test
    fun every_discovered_adapter_type_is_covered_by_a_keep_rule() {
        val mainSrc = projectDir.resolve("src/main/java")
        assertThat(mainSrc.isDirectory).isTrue()

        // Collect simple type names referenced in `adapter<…>` calls. Strips
        // generic wrappers (List<Bookmark> → Bookmark) and nullability.
        val adapterCall = Regex("""adapter<([^>]+)>""")
        val innerType = Regex("""([A-Z][A-Za-z0-9_]*)""")
        val referenced = mutableSetOf<String>()
        mainSrc.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { f ->
            val text = f.readText()
            for (m in adapterCall.findAll(text)) {
                val typeArg = m.groupValues[1]
                // Take the LAST capitalised identifier — the element type for
                // List<X>/Map<_,X>, or the type itself otherwise.
                innerType.findAll(typeArg).lastOrNull()?.let { referenced += it.groupValues[1] }
            }
        }
        // Sanity: the scan must find the types we know are serialized.
        assertThat(referenced).containsAtLeast("PitakaExport", "PublishExport", "Bookmark")

        // Resolve each simple name to its declaring package by finding its
        // class/enum declaration anywhere under src/main.
        val declRegex = { name: String ->
            Regex("""(?m)^\s*(?:data\s+|enum\s+|sealed\s+)?(?:class|interface|object)\s+$name\b""")
        }
        val packageRegex = Regex("""(?m)^package\s+([\w.]+)""")

        val unresolved = mutableListOf<String>()
        val uncovered = mutableListOf<String>()
        for (name in referenced) {
            val declFile = mainSrc.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .firstOrNull { declRegex(name).containsMatchIn(it.readText()) }
            if (declFile == null) {
                // Built-in / stdlib type (e.g. a generic alias) — nothing to keep.
                continue
            }
            val pkg = packageRegex.find(declFile.readText())?.groupValues?.get(1)
            if (pkg == null) { unresolved += name; continue }
            val fqn = "$pkg.$name"
            // A keep covers it if some -keep line names the class exactly or a
            // package-wildcard prefix of it.
            val covered = proguard.lineSequence().any { line ->
                val t = line.trim()
                if (!t.startsWith("-keep")) return@any false
                t.contains(fqn) || packageWildcardCovers(t, pkg)
            }
            if (!covered) uncovered += fqn
        }

        assertThat(unresolved).isEmpty()
        assertThat(uncovered).isEmpty()
    }

    /** True when a `-keep … dev.khoj.pitaka.x.y.** …` rule covers [pkg]. */
    private fun packageWildcardCovers(keepLine: String, pkg: String): Boolean {
        // Match the `<package>.**` token in the keep line and test prefix.
        val m = Regex("""([\w.]+)\.\*\*""").find(keepLine) ?: return false
        val root = m.groupValues[1]
        return pkg == root || pkg.startsWith("$root.")
    }

    private val projectDir: java.io.File by lazy {
        var dir: java.io.File? = java.io.File(System.getProperty("user.dir"))
        while (dir != null) {
            for (sub in listOf(".", "app")) {
                val candidate = java.io.File(dir, sub)
                if (java.io.File(candidate, "proguard-rules.pro").isFile) return@lazy candidate
            }
            dir = dir.parentFile
        }
        error("project dir (with proguard-rules.pro) not found")
    }
}
