package dev.khoj.pitaka.data.crash

import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Pure-JVM-ish tests for [CrashReportStore]. Uses Robolectric to obtain a
 * [android.content.Context] with a real `filesDir` so the store can write
 * `.txt` files to a temp directory.
 */
@RunWith(RobolectricTestRunner::class)
class CrashReportStoreTest {

    private lateinit var store: CrashReportStore

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        store = CrashReportStore(ctx)
        // Defensive: previous test runs in the same Robolectric process may
        // have left files behind in the per-app filesDir.
        store.deleteAll()
    }

    @Test
    fun `write persists a file and list returns it`() {
        val file = store.write("hello world", atEpochMillis = 1_700_000_000_000L)
        assertThat(file).isNotNull()
        assertThat(file!!.exists()).isTrue()
        assertThat(file.name).startsWith("crash-")
        assertThat(file.name).endsWith(".txt")

        val listed = store.list()
        assertThat(listed).hasSize(1)
        assertThat(listed[0].file.absolutePath).isEqualTo(file.absolutePath)
        assertThat(store.hasAny()).isTrue()
        assertThat(store.count()).isEqualTo(1)
    }

    @Test
    fun `read returns the exact body written`() {
        val body = "Pitak crash report\n=================\nLine A\nLine B\n"
        val file = store.write(body)!!
        assertThat(store.read(file)).isEqualTo(body)
    }

    @Test
    fun `prune caps storage at MAX_REPORTS, keeping newest`() {
        // Write MAX + 3 reports with strictly increasing timestamps so the
        // sort order is deterministic.
        val base = 1_700_000_000_000L
        val total = CrashReportStore.MAX_REPORTS + 3
        val written = (0 until total).map { i ->
            store.write("report $i", atEpochMillis = base + i * 1_000L)!!
                .also { it.setLastModified(base + i * 1_000L) }
        }

        val listed = store.list()
        assertThat(listed).hasSize(CrashReportStore.MAX_REPORTS)

        // The 3 oldest reports must have been pruned; their files no longer exist.
        val pruned = written.take(3)
        pruned.forEach { f ->
            assertThat(f.exists()).isFalse()
        }
        // The 5 newest reports must still be present.
        written.drop(3).forEach { f ->
            assertThat(f.exists()).isTrue()
        }
    }

    @Test
    fun `list returns newest first`() {
        val base = 1_700_000_000_000L
        val a = store.write("a", atEpochMillis = base + 1_000)!!.also { it.setLastModified(base + 1_000) }
        val b = store.write("b", atEpochMillis = base + 2_000)!!.also { it.setLastModified(base + 2_000) }
        val c = store.write("c", atEpochMillis = base + 3_000)!!.also { it.setLastModified(base + 3_000) }

        val listed = store.list().map { it.file.name }
        // Robolectric on some JDKs collapses lastModified precision, so just
        // check that newest is first and oldest is last.
        assertThat(listed.first()).isEqualTo(c.name)
        assertThat(listed.last()).isEqualTo(a.name)
        assertThat(listed).contains(b.name)
    }

    @Test
    fun `delete removes a single file but leaves others`() {
        val a = store.write("a")!!
        val b = store.write("b")!!
        assertThat(store.count()).isEqualTo(2)

        val deleted = store.delete(a)
        assertThat(deleted).isTrue()
        assertThat(a.exists()).isFalse()
        assertThat(b.exists()).isTrue()
        assertThat(store.count()).isEqualTo(1)
    }

    @Test
    fun `deleteAll removes every file and hasAny is false`() {
        store.write("a")
        store.write("b")
        store.write("c")
        assertThat(store.count()).isEqualTo(3)

        store.deleteAll()
        assertThat(store.count()).isEqualTo(0)
        assertThat(store.hasAny()).isFalse()
    }

    @Test
    fun `read returns null for a vanished file`() {
        val f = store.write("temp")!!
        f.delete()
        assertThat(store.read(f)).isNull()
    }
}
