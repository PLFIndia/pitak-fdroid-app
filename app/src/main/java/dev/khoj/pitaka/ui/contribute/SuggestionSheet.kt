package dev.khoj.pitaka.ui.contribute

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.khoj.pitaka.R
import kotlinx.coroutines.launch

/**
 * Contributor-mode suggestion sheet controller.
 *
 * Lives at the app root (provided by [PitakaApp]) so any [LocalizedText] in
 * any screen can call `LocalSuggestionSheetController.current.open(...)` to
 * pop the sheet with that string's English source pre-filled.
 *
 * Phase 1 backend: GitHub-only. Submit builds a pre-filled GitHub Issue URL
 * for the public pitaka-translations repo and opens it via Intent. Per
 * project §1.1 (no developer infrastructure post-install) — the URL is built
 * locally, GitHub hosts the form, and the user takes it from there.
 */
class SuggestionSheetController {
    data class Target(val stringId: Int, val englishSource: String)

    private val _target = mutableStateOf<Target?>(null)
    val target: State<Target?> = _target

    fun open(stringId: Int, englishSource: String) {
        _target.value = Target(stringId, englishSource)
    }

    fun dismiss() {
        _target.value = null
    }
}

private val NoOpSuggestionController = SuggestionSheetController()

val LocalSuggestionSheetController = staticCompositionLocalOf { NoOpSuggestionController }

/**
 * Wraps [content] with a [SuggestionSheetController] and renders the sheet
 * over it. Mounted once near the app root by [PitakaApp].
 */
@Composable
fun SuggestionSheetHost(
    repoOwner: String,
    repoName: String,
    disclosureAcked: Boolean,
    onDisclosureAck: () -> Unit,
    content: @Composable () -> Unit,
) {
    val controller = remember { SuggestionSheetController() }
    CompositionLocalProvider(LocalSuggestionSheetController provides controller) {
        content()
        val target by controller.target
        target?.let { t ->
            SuggestionSheet(
                target = t,
                repoOwner = repoOwner,
                repoName = repoName,
                disclosureAcked = disclosureAcked,
                onDisclosureAck = onDisclosureAck,
                onDismiss = { controller.dismiss() },
            )
        }
    }
}

/**
 * Mapping from in-app language picker to (display label, Android locale code
 * used in the `values-xx/` directory, GitHub issue body language label).
 */
private enum class SuggestLanguage(
    val labelRes: Int,
    val androidCode: String,
    val englishName: String,
) {
    Hindi(R.string.suggest_sheet_lang_hindi, "hi", "Hindi"),
    Punjabi(R.string.suggest_sheet_lang_punjabi, "pa", "Punjabi"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SuggestionSheet(
    target: SuggestionSheetController.Target,
    repoOwner: String,
    repoName: String,
    disclosureAcked: Boolean,
    onDisclosureAck: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current

    // Look up the string-resource entry name for the chosen ID. The
    // resource name (e.g. "library_title") is the key translators key off in
    // values-xx/strings.xml — far more useful in the issue than the numeric ID.
    val resourceKey = remember(target.stringId) {
        runCatching { ctx.resources.getResourceEntryName(target.stringId) }
            .getOrDefault("(unknown)")
    }

    var language by remember { mutableStateOf(SuggestLanguage.Hindi) }
    var translation by remember { mutableStateOf("") }
    var contextNote by remember { mutableStateOf("") }
    val missingMsg = stringResource(R.string.suggest_sheet_missing_translation)
    val noBrowserMsg = stringResource(R.string.suggest_sheet_no_browser)
    val copiedMsg = stringResource(R.string.crash_reports_copied)

    // F-04: gate the GitHub-opening submit behind the pre-submit disclosure.
    var showDisclosure by remember { mutableStateOf(false) }

    // Opens the prefilled GitHub Issue Form in a browser. Returns whether a
    // browser was found (false → caller surfaces the "no browser" toast).
    fun openGitHub(): Boolean = openGitHubIssueIntent(
        ctx = ctx,
        repoOwner = repoOwner,
        repoName = repoName,
        resourceKey = resourceKey,
        englishSource = target.englishSource,
        language = language,
        translation = translation,
        contextNote = contextNote,
    )

    // After a successful browser open, hide the sheet.
    fun finishAndClose() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    // Submit tap: validate, then either go straight to GitHub (already
    // acknowledged) or show the disclosure first.
    fun onSubmitTapped() {
        if (translation.isBlank()) {
            Toast.makeText(ctx, missingMsg, Toast.LENGTH_SHORT).show()
            return
        }
        if (disclosureAcked) {
            if (openGitHub()) finishAndClose()
            else Toast.makeText(ctx, noBrowserMsg, Toast.LENGTH_LONG).show()
        } else {
            showDisclosure = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                stringResource(R.string.suggest_sheet_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // English source — read-only, shown so the contributor knows
            // exactly what they're translating.
            Column {
                Text(
                    stringResource(R.string.suggest_sheet_english_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    target.englishSource,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    resourceKey,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Language picker.
            Column {
                Text(
                    stringResource(R.string.suggest_sheet_language_label),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SuggestLanguage.values().forEach { lang ->
                        FilterChip(
                            selected = language == lang,
                            onClick = { language = lang },
                            label = { Text(stringResource(lang.labelRes)) },
                        )
                    }
                }
            }

            // Translation field.
            OutlinedTextField(
                value = translation,
                onValueChange = { translation = it },
                label = { Text(stringResource(R.string.suggest_sheet_translation_label)) },
                placeholder = { Text(stringResource(R.string.suggest_sheet_translation_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            // Optional context note.
            OutlinedTextField(
                value = contextNote,
                onValueChange = { contextNote = it },
                label = { Text(stringResource(R.string.suggest_sheet_context_label)) },
                placeholder = { Text(stringResource(R.string.suggest_sheet_context_placeholder),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                minLines = 1,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.suggest_sheet_cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSubmitTapped() }) {
                    Text(stringResource(R.string.suggest_sheet_submit))
                }
            }

            // F-04: quiet standing caption once the user has acknowledged the
            // disclosure — keeps the "this goes public" fact visible without
            // re-showing the full dialog on every submit.
            if (disclosureAcked) {
                Text(
                    stringResource(githubDisclosureCaptionRes),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    // F-04: pre-submit disclosure. On Continue, persist the ack, open GitHub,
    // and close the sheet. Copy-instead copies a plain-text rendering of the
    // suggestion so the user can paste it from their own / a throwaway account.
    if (showDisclosure) {
        GitHubDisclosureDialog(
            flow = DisclosureFlow.Translation,
            onContinue = {
                showDisclosure = false
                onDisclosureAck()
                if (openGitHub()) finishAndClose()
                else Toast.makeText(ctx, noBrowserMsg, Toast.LENGTH_LONG).show()
            },
            onCopyInstead = {
                showDisclosure = false
                copyToClipboard(
                    ctx,
                    "Pitak translation suggestion",
                    buildSuggestionText(resourceKey, target.englishSource, language, translation, contextNote),
                )
                Toast.makeText(ctx, copiedMsg, Toast.LENGTH_SHORT).show()
                finishAndClose()
            },
            onCreateAccount = {
                showDisclosure = false
                openInBrowser(ctx, Uri.parse(GITHUB_SIGNUP_URL))
            },
            onDismiss = { showDisclosure = false },
        )
    }
}

/**
 * Builds the GitHub Issue Form pre-fill URL and opens it in a **browser** via
 * Chrome Custom Tabs.
 *
 * URL shape:
 * `https://github.com/<owner>/<repo>/issues/new?template=suggest_translation.yml&...`
 *
 * Each form field name maps to an `id:` in the Issue Form YAML. We URL-encode
 * everything per RFC 3986.
 *
 * Why Custom Tabs and not a plain `ACTION_VIEW`: the GitHub Android app
 * registers `github.com/<owner>/<repo>/issues/new` as an App Link and, when
 * installed, intercepts a bare `ACTION_VIEW`. Its in-app composer ignores the
 * issue-form `template=`/field query params, producing a blank, label-less
 * issue. Routing through a browser (Custom Tabs, falling back to an explicit
 * browser package) keeps GitHub's web Issue Form — which honours the prefill.
 *
 * Returns `false` if no browser can be found at all (offline / browserless
 * device), so the caller can surface the existing "no browser" message.
 */
private fun openGitHubIssueIntent(
    ctx: Context,
    repoOwner: String,
    repoName: String,
    resourceKey: String,
    englishSource: String,
    language: SuggestLanguage,
    translation: String,
    contextNote: String,
): Boolean {
    val params = buildString {
        append("template=suggest_translation.yml")
        append("&title=").append(Uri.encode("[$resourceKey][${language.androidCode}] ${englishSource.take(60)}"))
        append("&string-id=").append(Uri.encode(resourceKey))
        append("&english-source=").append(Uri.encode(englishSource))
        append("&language=").append(Uri.encode(language.englishName))
        append("&translation=").append(Uri.encode(translation))
        append("&context=").append(Uri.encode(contextNote.ifBlank { "(none)" }))
        append("&app-version=").append(Uri.encode(appVersionString(ctx)))
    }
    val uri = Uri.parse("https://github.com/$repoOwner/$repoName/issues/new?$params")

    // Shared browser-pinned opener (see ContributeBrowser.openInBrowser) — keeps
    // GitHub's web Issue Form instead of the GitHub app's prefill-dropping composer.
    return openInBrowser(ctx, uri)
}

private fun appVersionString(ctx: Context): String = runCatching {
    val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
    @Suppress("DEPRECATION")
    val code = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
        pi.longVersionCode
    } else {
        pi.versionCode.toLong()
    }
    "${pi.versionName ?: "?"} ($code)"
}.getOrDefault("(unknown)")

/**
 * F-04: plain-text rendering of a translation suggestion for the
 * "Copy instead" path, so a privacy-conscious contributor can paste it from
 * their own (or a throwaway) GitHub account instead of opening the prefilled
 * form under whatever account their browser is signed into.
 */
private fun buildSuggestionText(
    resourceKey: String,
    englishSource: String,
    language: SuggestLanguage,
    translation: String,
    contextNote: String,
): String = buildString {
    appendLine("Pitak translation suggestion")
    appendLine("String: $resourceKey")
    appendLine("Language: ${language.englishName}")
    appendLine("English: $englishSource")
    appendLine("Suggested: $translation")
    if (contextNote.isNotBlank()) appendLine("Context: $contextNote")
}.trimEnd()

private fun copyToClipboard(ctx: Context, label: String, text: String) {
    runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
        cm?.setPrimaryClip(android.content.ClipData.newPlainText(label, text))
    }
}
