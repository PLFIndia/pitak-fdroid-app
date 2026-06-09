package dev.khoj.pitaka.data.crash

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Pure-JVM tests for [CrashHandler.stripMessages]. The strip step is the
 * privacy-critical part of the crash pipeline: anything left in a header
 * line after the FQN gets uploaded to a public GitHub Issue. Frame lines
 * MUST be left intact so the crash is still diagnosable.
 */
class CrashHandlerStripMessagesTest {

    @Test
    fun `top-level header drops the message`() {
        val input = "java.lang.IllegalStateException: Cannot lend book 'Mom'\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("java.lang.IllegalStateException")
        assertThat(out).doesNotContain("Cannot lend book")
        assertThat(out).doesNotContain("'Mom'")
        // Frame line passes through unchanged.
        assertThat(out).contains("\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)")
    }

    @Test
    fun `Caused by header drops the message but keeps the type`() {
        val input = "java.lang.RuntimeException\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n" +
            "Caused by: java.io.IOException: open /data/data/sensitive/path\n" +
            "\tat dev.khoj.pitaka.Foo.io(Foo.kt:5)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("Caused by: java.io.IOException")
        assertThat(out).doesNotContain("/data/data/sensitive/path")
        assertThat(out).contains("\tat dev.khoj.pitaka.Foo.io(Foo.kt:5)")
    }

    @Test
    fun `Suppressed header is treated like Caused by`() {
        val input = "java.lang.RuntimeException\n" +
            "\tSuppressed: java.lang.NullPointerException: borrower.name is null\n" +
            "\t\tat dev.khoj.pitaka.Bar.f(Bar.kt:1)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("Suppressed: java.lang.NullPointerException")
        assertThat(out).doesNotContain("borrower.name is null")
    }

    @Test
    fun `header with no message is unchanged`() {
        val input = "java.lang.NullPointerException\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("java.lang.NullPointerException")
        assertThat(out).contains("\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)")
    }

    @Test
    fun `dots-N-more lines pass through`() {
        val input = "java.lang.RuntimeException: oops\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n" +
            "\t... 23 more\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("\t... 23 more")
    }

    @Test
    fun `blank lines are preserved`() {
        val input = "java.lang.RuntimeException: oops\n" +
            "\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n"
        val out = CrashHandler.stripMessages(input)
        val lines = out.split('\n')
        // The middle empty line must still be present.
        assertThat(lines).contains("")
    }

    @Test
    fun `multi-line message continuation lines are dropped (F-20)`() {
        // A printed exception whose message spans several lines: only the
        // first sits on the "<FQN>: <message>" header; the rest are standalone
        // continuation lines that historically leaked verbatim.
        val input = "java.lang.IllegalStateException: Cannot import these books:\n" +
            "कबीर के दोहे — borrower Ravi Sharma\n" +
            "Har mauke ke liye ek sher\n" +
            "\tat dev.khoj.pitaka.Importer.run(Importer.kt:42)\n"
        val out = CrashHandler.stripMessages(input)
        // Type + frame survive; every byte of the message is gone.
        assertThat(out).contains("java.lang.IllegalStateException")
        assertThat(out).contains("\tat dev.khoj.pitaka.Importer.run(Importer.kt:42)")
        assertThat(out).doesNotContain("Cannot import these books")
        assertThat(out).doesNotContain("कबीर के दोहे")
        assertThat(out).doesNotContain("Ravi Sharma")
        assertThat(out).doesNotContain("Har mauke")
    }

    @Test
    fun `multi-line Caused-by message continuation is dropped (F-20)`() {
        val input = "java.lang.RuntimeException\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n" +
            "Caused by: java.io.IOException: failed to open\n" +
            "/data/data/dev.khoj.pitaka/files/covers/Ravi's cover.jpg\n" +
            "\tat dev.khoj.pitaka.Foo.io(Foo.kt:5)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("Caused by: java.io.IOException")
        assertThat(out).contains("\tat dev.khoj.pitaka.Foo.io(Foo.kt:5)")
        assertThat(out).doesNotContain("failed to open")
        assertThat(out).doesNotContain("Ravi's cover.jpg")
    }

    @Test
    fun `a non-indented message line that mimics a frame is still dropped (F-20)`() {
        // An attacker-or-accidental message line that literally reads
        // "at <user data>" must NOT be mistaken for a real (indented) frame.
        val input = "java.lang.IllegalStateException: boom\n" +
            "at the library owned by Asha Patel\n" +
            "\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)\n"
        val out = CrashHandler.stripMessages(input)
        assertThat(out).contains("java.lang.IllegalStateException")
        // The real, indented frame survives.
        assertThat(out).contains("\tat dev.khoj.pitaka.Foo.bar(Foo.kt:12)")
        // The fake, non-indented "at ..." message line is dropped.
        assertThat(out).doesNotContain("Asha Patel")
    }
}
