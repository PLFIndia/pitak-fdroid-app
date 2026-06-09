package dev.khoj.pitaka.ui.contribute

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Composition-local boolean: are we in Localization Contributor mode?
 *
 * Provided once at the app root from
 * [dev.khoj.pitaka.data.prefs.AppPreferences.contributorMode]. Read by
 * [LocalizedText] / [contributorLongPress] to decide whether to install the
 * long-press handler. Default `false` means no contributor surface anywhere —
 * regular users see the same UI as before.
 */
val LocalContributorMode = compositionLocalOf { false }

/**
 * Composition-local boolean: should translatable text draw a permanent dotted
 * underline so a contributor can see at a glance which strings are
 * long-pressable?
 *
 * Provided at the app root from
 * [dev.khoj.pitaka.data.prefs.AppPreferences.showTranslatableHints]. Only has a
 * visible effect when [LocalContributorMode] is also true. Default `false`.
 *
 * This replaces the earlier transient 5-second "reveal on long-press" mechanism
 * (RevealController / RevealScope), which proved too subtle and too brief —
 * users almost always missed it. A persistent, user-controlled underline is the
 * single, predictable way to surface translatable text.
 */
val LocalShowTranslatableHints = compositionLocalOf { false }

/**
 * Drop-in replacement for `Text(stringResource(stringId, *formatArgs), ...)`.
 *
 * When [LocalContributorMode] is OFF (default for all regular users), this is
 * functionally and visually identical to a plain [Text] — no extra Modifier,
 * no clickable surface, no listeners, zero runtime overhead.
 *
 * When [LocalContributorMode] is ON, the rendered text becomes long-pressable;
 * a long-press opens the suggestion sheet pre-filled with the string ID and
 * English source via [LocalSuggestionSheetController]. Additionally, when
 * [LocalShowTranslatableHints] is ON, the text carries a permanent dotted
 * underline so the contributor can see it is translatable.
 *
 * @param passthroughTap when true the text is a LABEL inside an interactive
 *   container (NavigationBarItem / FilterChip). No gesture is attached to the
 *   text itself — the container carries [contributorLongPress] instead — but the
 *   hint underline is still drawn so the label looks translatable.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalizedText(
    stringId: Int,
    vararg formatArgs: Any,
    modifier: Modifier = Modifier,
    passthroughTap: Boolean = false,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    textAlign: TextAlign? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
) {
    val text: String = stringResource(stringId, *formatArgs)
    val isContributor = LocalContributorMode.current
    val showHints = LocalShowTranslatableHints.current

    val hintColor = MaterialTheme.colorScheme.primary
    val density = LocalDensity.current

    // The translatable-text marker: drawn whenever contributor mode is on AND
    // the user has enabled the "show translatable text" toggle. Applies to both
    // free-standing and passthrough (label-slot) text.
    //
    // A rounded rectangle AROUND the text (solid stroke + faint fill) replaces
    // the earlier 1dp dotted underline at 0.55 alpha, which was barely visible.
    // The box is the most unambiguous "this string is translatable" signal.
    // Drawn within the text's own bounds (no padding) so it never shifts layout
    // — critical for tight rows like NavigationBar labels / FilterChips. This is
    // a contributor-only, user-switchable surface, so the higher prominence is
    // intentional and does not affect regular browsing.
    val hintModifier = if (isContributor && showHints) {
        Modifier.drawBehind {
            val strokeWidthPx = with(density) { 1.5.dp.toPx() }
            val cornerPx = with(density) { 4.dp.toPx() }
            val inset = strokeWidthPx / 2f
            val corner = CornerRadius(cornerPx, cornerPx)
            // Faint fill so the box reads even against busy backgrounds.
            drawRoundRect(
                color = hintColor.copy(alpha = 0.10f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                cornerRadius = corner,
            )
            // Solid border at high alpha for clear prominence.
            drawRoundRect(
                color = hintColor.copy(alpha = 0.85f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - strokeWidthPx, size.height - strokeWidthPx),
                cornerRadius = corner,
                style = Stroke(width = strokeWidthPx),
            )
        }
    } else {
        Modifier
    }

    // Free-standing translatable text uses the SAME initial-pass long-press
    // detector as [contributorLongPress]: a long-press opens the suggestion
    // sheet, but a normal tap is NOT consumed. This is critical when the text
    // sits inside an interactive container (Button, ListItem, TextButton) — the
    // tap passes through to the container's own onClick instead of being eaten
    // by a no-op combinedClickable. Label-slot text (passthroughTap) gets no
    // gesture here; its container carries [contributorLongPress] instead.
    val gestureModifier = if (!passthroughTap) {
        Modifier.contributorLongPress(stringId)
    } else {
        Modifier
    }

    Text(
        text = text,
        modifier = modifier.then(gestureModifier).then(hintModifier),
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        textAlign = textAlign,
        lineHeight = lineHeight,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
    )
}

/**
 * Container-level long-press handler for contributor mode, designed for
 * interactive components whose own click must keep working — e.g.
 * `NavigationBarItem`, `FilterChip`, `Button`. Apply it to the WHOLE component
 * (so the entire icon+label hit area responds), not to the inner text label.
 *
 * Why a custom detector and not `combinedClickable(onLongClick = …)`: the
 * component already owns a `clickable`/`selectable`. Stacking another clickable
 * on top fights it for the gesture — one wins, the other dies, and which one is
 * undefined. Instead we observe pointer events in the **initial** pass (parents
 * see the initial pass before children), measure the press duration ourselves,
 * and only consume the gesture if it crosses the long-press threshold. A normal
 * tap is left untouched and flows to the component's own onClick, so navigation
 * and selection are unaffected.
 *
 * When contributor mode is OFF this returns the receiver unchanged — zero
 * overhead, identical behaviour to before.
 *
 * Pattern adapted from AOSP's `PointerInputScope.detectTapGestures` long-press
 * timing, restructured to run in the initial pass and to consume conditionally.
 */
@Composable
fun Modifier.contributorLongPress(stringId: Int): Modifier {
    val isContributor = LocalContributorMode.current
    if (!isContributor) return this

    val sheet = LocalSuggestionSheetController.current
    val source = stringResource(stringId)
    val longPressTimeoutMs = LocalViewConfiguration.current.longPressTimeoutMillis

    return this.pointerInput(stringId) {
        awaitEachGesture {
            // Initial pass: see the down before the component's own clickable.
            // Do not consume — a plain tap must still reach the component.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Wait up to the long-press timeout for the finger to lift. If it
            // lifts first (a tap), `up` is non-null and we do nothing. If the
            // timeout elapses with the finger still down, it's a long-press.
            val up = withTimeoutOrNull(longPressTimeoutMs) {
                waitForUpOrCancellation(pass = PointerEventPass.Initial)
            }
            if (up == null) {
                // Long-press fired. Consume so the component does NOT also treat
                // the eventual lift as a click, then open the suggestion sheet.
                down.consume()
                sheet.open(stringId = stringId, englishSource = source)
            }
        }
    }
}
