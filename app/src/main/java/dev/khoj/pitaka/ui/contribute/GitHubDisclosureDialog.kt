package dev.khoj.pitaka.ui.contribute

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.khoj.pitaka.R

/**
 * F-04 (audit): pre-submit disclosure shown before any contributor or crash
 * submission opens a PUBLIC GitHub issue in the browser.
 *
 * Why this exists: both the translation-suggestion flow and the crash-report
 * flow open a GitHub Issue Form in the user's browser. If the user is signed
 * into GitHub there, the issue is authored under their real, public,
 * search-indexable username — with no prior in-app warning. This dialog makes
 * that exposure explicit and calm (it's a public comment, not a gamble),
 * before the browser opens.
 *
 * Design (decided with the user):
 *  - Path B + 1-tap account creation. No developer infrastructure (§1.1):
 *    anonymity is user-controlled — paste from your own / a throwaway account.
 *    No app-controlled submission channel (would need an embedded secret or a
 *    developer relay; both break §1.1 and the relay would *see* IP+payload).
 *  - Tone: matter-of-fact, no scare words.
 *  - Once-then-remembered: the caller persists an ack flag after [onContinue]
 *    so the full dialog only shows on first submit per flow; a quiet standing
 *    caption (see [githubDisclosureCaptionRes]) keeps the fact visible after.
 *  - Per-flow body text + separate ack flags ([DisclosureFlow]).
 *
 * Layout note: this flow has FOUR actions, which do not fit Material3's
 * standard horizontal confirm/dismiss button row (an earlier version stacked
 * three buttons inside the dismiss slot, overflowing the card). Instead we
 * leave the button-row slots empty and render the actions as full-width
 * stacked buttons inside the dialog content with one consistent gap.
 *
 * This composable is STATELESS w.r.t. persistence — it only owns the local
 * two-step page state (disclosure → account tips). The caller supplies the
 * callbacks and persists the ack.
 */
enum class DisclosureFlow { Translation, Crash }

/** github.com signup, opened via the shared browser-pinned [openInBrowser]. */
const val GITHUB_SIGNUP_URL = "https://github.com/signup"

/** Standing-caption string shown near a submit button after the user acks. */
val githubDisclosureCaptionRes: Int
    get() = R.string.gh_disclosure_caption

@Composable
fun GitHubDisclosureDialog(
    flow: DisclosureFlow,
    onContinue: () -> Unit,
    onCopyInstead: () -> Unit,
    onCreateAccount: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Two pages: the disclosure itself, then optional account-creation tips
    // (privacy info + advice about changing an existing GitHub username).
    var showAccountTips by remember { mutableStateOf(false) }

    if (!showAccountTips) {
        val bodyRes = when (flow) {
            DisclosureFlow.Translation -> R.string.gh_disclosure_body_translate
            DisclosureFlow.Crash -> R.string.gh_disclosure_body_crash
        }
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.gh_disclosure_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(bodyRes))
                    Text(
                        stringResource(R.string.gh_disclosure_no_account),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // All four actions stacked full-width below the body, with
                    // one consistent gap. The primary is emphasised (filled).
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onContinue,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_disclosure_continue)) }
                    TextButton(
                        onClick = onCopyInstead,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_disclosure_copy_instead)) }
                    TextButton(
                        onClick = { showAccountTips = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_disclosure_create_account)) }
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_disclosure_cancel)) }
                }
            },
            // Actions live in the content (see layout note); the standard
            // horizontal button row is intentionally empty.
            confirmButton = {},
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.gh_account_tips_title)) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(stringResource(R.string.gh_account_tips_body))
                    Spacer(Modifier.height(2.dp))
                    Text(
                        stringResource(R.string.gh_account_tips_username_heading),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.gh_account_tips_username_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = onCreateAccount,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_account_tips_open_signup)) }
                    TextButton(
                        onClick = { showAccountTips = false },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.gh_account_tips_back)) }
                }
            },
            confirmButton = {},
        )
    }
}
