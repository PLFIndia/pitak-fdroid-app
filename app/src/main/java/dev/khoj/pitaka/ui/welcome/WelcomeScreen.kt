package dev.khoj.pitaka.ui.welcome

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import dev.khoj.pitaka.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Two-stage welcome screen.
 *
 * Stage 1 — Pitak brand: app launcher icon (open-book glyph on saffron
 * square) + the name "pitaka" in three scripts (Brahmi 𑀧𑀺𑀝𑀓, Devanagari
 * पिटक, Latin Pitak). User taps "Enter" to advance.
 *
 * Stage 2 — the user's library: the icon swaps to the user's custom logo (if
 * set), and the three brand scripts are replaced by the user's library page —
 * their library name in English (or the default "My Library"), plus an optional
 * second line with the name in their local language/script if they set one.
 * Holds for [STAGE_TWO_HOLD_MS] then auto-advances to the Library tab.
 */
@Composable
fun WelcomeScreen(
    onContinue: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val brahmiFamily = remember { FontFamily(Font(R.font.noto_sans_brahmi)) }
    var stage by remember { mutableStateOf(WelcomeStage.AppIcon) }

    LaunchedEffect(stage) {
        if (stage == WelcomeStage.UserIcon) {
            // 1.2s is a FLOOR, not a fixed delay: hold at least this long so the
            // stage-2 brand moment is seen, then dismiss as soon as the library
            // data is ready. If loading takes longer than the floor, wait for it
            // so the Library is already populated when the overlay fades away.
            // Keyed only on `stage` (not libraryReady) so the floor isn't
            // restarted when readiness flips — we await readiness explicitly.
            delay(STAGE_TWO_HOLD_MS)
            viewModel.libraryReady.first { it }
            onContinue()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            // The icon is structurally the SAME element in both stages — it never
            // participates in a layout animation, so it never shifts position.
            // Only the image inside it (app icon ↔ user logo) cross-fades, and the
            // background tint animates. This is the key to a non-"dancing" swap.
            WelcomeIcon(
                stage = stage,
                userLogoUri = state.libraryLogoUri,
            )

            Spacer(Modifier.height(40.dp))

            // Text region: full width + a stable MINIMUM height so the column
            // doesn't re-center when the two stages differ in height, but can
            // still grow to fit (heightIn(min=…), not a fixed height — a fixed
            // height clipped the taller stage-1 three-script stack). Crossfade
            // content fills width and centers, so the narrower stage-2 text stays
            // centered instead of snapping to the left edge.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = WELCOME_TEXT_AREA_MIN_HEIGHT),
                contentAlignment = Alignment.TopCenter,
            ) {
                Crossfade(
                    targetState = stage,
                    animationSpec = tween(durationMillis = 400),
                    label = "welcomeStageText",
                    modifier = Modifier.fillMaxWidth(),
                ) { s ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        when (s) {
                            WelcomeStage.AppIcon -> PitakBrandText(brahmiFamily)
                            WelcomeStage.UserIcon -> UserLibraryText(
                                libraryName = state.libraryName,
                                libraryNameLocal = state.libraryNameLocal,
                                defaultName = stringResource(R.string.welcome_default_library_name),
                            )
                        }
                    }
                }
            }
        }

        // Enter button — only visible in stage 1. In stage 2 we auto-advance.
        if (stage == WelcomeStage.AppIcon) {
            Button(
                onClick = { stage = WelcomeStage.UserIcon },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp)
                    .fillMaxWidth(0.5f),
            ) {
                Text(stringResource(R.string.welcome_enter), fontSize = 18.sp)
            }
        }
    }
}

/** Stage 1: "pitaka" in Brahmi, Devanagari, and Latin. */
@Composable
private fun PitakBrandText(brahmiFamily: FontFamily) {
    // 1. Brahmi (Ashokan)
    Text(
        text = "\uD804\uDC27\uD804\uDC3A\uD804\uDC1D\uD804\uDC13",
        fontFamily = brahmiFamily,
        fontSize = 56.sp,
        color = MaterialTheme.colorScheme.primary,
    )

    Spacer(Modifier.height(16.dp))

    // 2. Devanagari (Hindi/Sanskrit/Pali)
    Text(
        text = "पिटक",
        fontSize = 40.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
    )

    Spacer(Modifier.height(12.dp))

    // 3. Latin
    Text(
        text = "Pitak",
        fontSize = 30.sp,
        fontWeight = FontWeight.Normal,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Stage 2: the user's own library page.
 *
 * Line 1 = the user's library name in English (or the default "My Library" when
 * they've left it blank). Line 2 = the optional local-language name, shown only
 * if the user set one.
 */
@Composable
private fun UserLibraryText(
    libraryName: String,
    libraryNameLocal: String,
    defaultName: String,
) {
    val englishName = libraryName.ifBlank { defaultName }
    Text(
        text = englishName,
        fontSize = 36.sp,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onBackground,
    )
    if (libraryNameLocal.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = libraryNameLocal,
            fontSize = 28.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun WelcomeIcon(
    stage: WelcomeStage,
    userLogoUri: String,
) {
    val saffron = colorResource(id = R.color.pitaka_saffron_400)
    val showUserLogo = stage == WelcomeStage.UserIcon && userLogoUri.isNotBlank()
    // Animate the background tint so it eases between the saffron app-icon tile
    // and the neutral logo backdrop rather than snapping.
    val bgColor by animateColorAsState(
        targetValue = if (showUserLogo) MaterialTheme.colorScheme.surfaceVariant else saffron,
        animationSpec = tween(durationMillis = 400),
        label = "welcomeIconBg",
    )

    // The Box itself is fixed (size + position never change). Only the image
    // INSIDE cross-fades, so the icon never moves on screen during the swap.
    Box(
        modifier = Modifier
            .size(128.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = showUserLogo,
            animationSpec = tween(durationMillis = 400),
            label = "welcomeIconImage",
        ) { userLogo ->
            if (userLogo) {
                AsyncImage(
                    model = userLogoUri,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // App's open-book launcher foreground (white pages / dark spine).
                Image(
                    painter = painterResource(id = R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

enum class WelcomeStage {
    AppIcon,    // launcher icon visible, Enter button visible
    UserIcon,   // user logo visible (or hold on launcher), auto-advances
}

const val STAGE_TWO_HOLD_MS: Long = 1200L

/**
 * Minimum height for the welcome name area. Reserves enough room that the layout
 * doesn't re-center when the shorter stage-2 text cross-fades in, but it's a
 * floor (heightIn min) not a hard cap — so the taller stage-1 three-script stack
 * is never clipped. Generous on purpose; extra space sits inside the centered
 * column.
 */
private val WELCOME_TEXT_AREA_MIN_HEIGHT = 200.dp
