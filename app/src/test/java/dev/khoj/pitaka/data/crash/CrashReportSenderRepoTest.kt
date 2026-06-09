package dev.khoj.pitaka.data.crash

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * F-17: the crash-report destination repo now comes from string resources
 * (crash_repo_owner / crash_repo_name) instead of buildConfigField, so both
 * developer-owned repos are configured in one place. This verifies the
 * prepared submission URL points at the configured repo, and documents the
 * blank-repo fallback contract.
 */
@RunWith(RobolectricTestRunner::class)
class CrashReportSenderRepoTest {

    private lateinit var ctx: Context
    private lateinit var store: CrashReportStore
    private lateinit var sender: CrashReportSender

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        store = CrashReportStore(ctx)
        store.deleteAll()
        // Long delay so the F-13 auto-clear never fires during the test.
        sender = CrashReportSender(store = store, scope = CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined), clearDelayMs = 10_000_000L)
    }

    private fun writeReport(body: String): CrashReportFile {
        store.write(body)!!
        return store.list().first()
    }

    @Test
    fun `prepared url targets the configured crash repo`() = runTest {
        val report = writeReport("BoomException\n\tat A.b(A.kt:1)")
        val payload = sender.prepare(ctx, report)
        assertThat(payload).isNotNull()
        // Repo slug comes from crash_repo_owner / crash_repo_name string resources.
        val owner = ctx.getString(dev.khoj.pitaka.R.string.crash_repo_owner)
        val repo = ctx.getString(dev.khoj.pitaka.R.string.crash_repo_name)
        assertThat(payload!!.url).contains("github.com/$owner/$repo/issues/new")
    }

    @Test
    fun `configured repo slugs are non-blank`() {
        // The blank-repo fallback (prepare -> null -> caller copies instead) is
        // a safety net; in the shipped config both slugs must be present so the
        // Send action actually opens an issue form.
        assertThat(ctx.getString(dev.khoj.pitaka.R.string.crash_repo_owner)).isNotEmpty()
        assertThat(ctx.getString(dev.khoj.pitaka.R.string.crash_repo_name)).isNotEmpty()
    }

    @Test
    fun `prepared url carries the crash issue-form template`() = runTest {
        val report = writeReport("BoomException\n\tat A.b(A.kt:1)")
        val payload = sender.prepare(ctx, report)
        assertThat(payload!!.url).contains("template=crash.yml")
    }
}
