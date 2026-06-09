package dev.khoj.pitaka.data.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-13 tests for [CrashReportSender]'s clipboard hygiene: the conditional
 * auto-clear and the "don't clobber the user's clipboard" guarantee.
 *
 * Uses Robolectric for a real [ClipboardManager] and a [StandardTestDispatcher]
 * so the 60s auto-clear timer can be advanced deterministically instead of
 * waiting in wall-clock time.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CrashReportSenderClipboardTest {

    private lateinit var ctx: Context
    private lateinit var store: CrashReportStore
    private lateinit var cm: ClipboardManager

    private val delayMs = 60_000L

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        store = CrashReportStore(ctx)
        store.deleteAll()
        cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private fun sender(scope: CoroutineScope) =
        CrashReportSender(store = store, scope = scope, clearDelayMs = delayMs)

    /** Write a report body and return the listed [CrashReportFile] view. */
    private fun writeReport(body: String): CrashReportFile {
        store.write(body)!!
        return store.list().first()
    }

    private fun currentClipText(): String? =
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()

    @Test
    fun `copy puts the trace on the clipboard with our label`() = runTest {
        val sender = sender(this)
        val report = writeReport("BoomException\n\tat A.b(A.kt:1)")

        assertThat(sender.copy(ctx, report)).isTrue()
        assertThat(currentClipText()).contains("BoomException")
        assertThat(cm.primaryClip?.description?.label).isEqualTo(CrashReportSender.CLIP_LABEL)
    }

    @Test
    fun `auto-clear wipes our trace after the delay`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val sender = sender(scope)
        val report = writeReport("SecretException\n\tat A.b(A.kt:1)")

        sender.copy(ctx, report)
        assertThat(currentClipText()).contains("SecretException")

        // Just before the window: still present.
        advanceTimeBy(delayMs - 1)
        testScheduler.runCurrent()
        assertThat(currentClipText()).contains("SecretException")

        // After the window: cleared (empty or null, never the trace).
        advanceTimeBy(2)
        testScheduler.runCurrent()
        assertThat(currentClipText().orEmpty()).doesNotContain("SecretException")
    }

    @Test
    fun `auto-clear does NOT clobber something the user copied after us`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val sender = sender(scope)
        val report = writeReport("TraceException\n\tat A.b(A.kt:1)")

        sender.copy(ctx, report)

        // User copies their own content before the timer fires.
        cm.setPrimaryClip(ClipData.newPlainText("user", "my important note"))

        advanceTimeBy(delayMs + 10)
        testScheduler.runCurrent()

        // The user's clip survives untouched.
        assertThat(currentClipText()).isEqualTo("my important note")
    }

    @Test
    fun `a fresh copy supersedes the previous timer`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = CoroutineScope(dispatcher)
        val sender = sender(scope)
        val report = writeReport("OnlyException\n\tat A.b(A.kt:1)")

        sender.copy(ctx, report)
        advanceTimeBy(delayMs - 10)
        testScheduler.runCurrent()

        // Re-copy: this should cancel the first timer and restart the window.
        sender.copy(ctx, report)
        assertThat(currentClipText()).contains("OnlyException")

        // Past where the FIRST timer would have fired — clip still here.
        advanceTimeBy(20)
        testScheduler.runCurrent()
        assertThat(currentClipText()).contains("OnlyException")

        // Past the second window — now cleared.
        advanceTimeBy(delayMs)
        testScheduler.runCurrent()
        assertThat(currentClipText().orEmpty()).doesNotContain("OnlyException")
    }
}
