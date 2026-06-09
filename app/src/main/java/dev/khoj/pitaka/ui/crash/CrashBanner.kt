package dev.khoj.pitaka.ui.crash

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.ui.contribute.DisclosureFlow
import dev.khoj.pitaka.ui.contribute.GITHUB_SIGNUP_URL
import dev.khoj.pitaka.ui.contribute.GitHubDisclosureDialog
import dev.khoj.pitaka.ui.contribute.githubDisclosureCaptionRes
import dev.khoj.pitaka.ui.contribute.openInBrowser

/**
 * Top-of-Library banner shown when at least one unsent crash report is on
 * disk. Two actions:
 *   - **Send** : opens the prefilled GitHub Issue URL in a browser, full
 *                trace also copied to clipboard. The file remains until the
 *                user explicitly deletes it (in case the browser tab never
 *                made it to GitHub).
 *   - **Dismiss** : deletes the newest report and hides the banner. If
 *                older reports also exist (rare), the banner reappears
 *                with the next-newest one.
 *
 * On a healthy install with no reports, this composable produces zero
 * pixels — no padding, no divider.
 */
@Composable
fun CrashBanner(
    modifier: Modifier = Modifier,
    viewModel: CrashBannerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val disclosureAcked by viewModel.disclosureAck.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    if (!state.hasReports) return

    val ctx = LocalContext.current
    val noBrowser = stringResource(R.string.crash_reports_no_browser)
    val copied = stringResource(R.string.crash_reports_copied)

    var showDisclosure by remember { mutableStateOf(false) }

    fun openNewest() {
        val url = viewModel.prepareNewestSubmission(ctx)
        if (url == null) {
            Toast.makeText(ctx, noBrowser, Toast.LENGTH_LONG).show()
            return
        }
        val opened = openInBrowser(ctx, Uri.parse(url))
        if (!opened) Toast.makeText(ctx, noBrowser, Toast.LENGTH_LONG).show()
    }

    fun onSendTapped() {
        if (disclosureAcked) openNewest() else showDisclosure = true
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.crash_banner_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.crash_banner_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onSendTapped() }) {
                    Text(stringResource(R.string.crash_banner_send))
                }
                TextButton(onClick = { viewModel.dismissNewest() }) {
                    Text(stringResource(R.string.crash_banner_dismiss))
                }
            }
            if (disclosureAcked) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(githubDisclosureCaptionRes),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }

    if (showDisclosure) {
        GitHubDisclosureDialog(
            flow = DisclosureFlow.Crash,
            onContinue = {
                showDisclosure = false
                viewModel.setDisclosureAck()
                openNewest()
            },
            onCopyInstead = {
                showDisclosure = false
                if (viewModel.copyNewest(ctx)) {
                    Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateAccount = {
                showDisclosure = false
                openInBrowser(ctx, Uri.parse(GITHUB_SIGNUP_URL))
            },
            onDismiss = { showDisclosure = false },
        )
    }
}
