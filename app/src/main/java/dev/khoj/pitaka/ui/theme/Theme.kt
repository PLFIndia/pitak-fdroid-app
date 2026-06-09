package dev.khoj.pitaka.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Pitaka Material 3 theme.
 *
 * D29: static saffron-seeded palette is the default on every Android version.
 * Dynamic color (Android 12+) is only used when the user explicitly opts in
 * via Settings → Appearance. The opt-in flag is not wired yet (Phase 7);
 * the [useDynamicColor] parameter is the seam where it will plug in.
 *
 * Reference: android/nowinandroid PitakaTheme analogue (NiaTheme.kt) — adapted to
 * a single static seed rather than gradient-bound dynamic theming.
 */
@Composable
fun PitakaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> PitakaDarkColorScheme
        else -> PitakaLightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PitakaTypography,
        content = content,
    )
}

// Light scheme — saffron-on-paper.
private val PitakaLightColorScheme = lightColorScheme(
    primary = Saffron400,
    onPrimary = Ink50,
    primaryContainer = Saffron100,
    onPrimaryContainer = Saffron900,

    secondary = Saffron600,
    onSecondary = Ink50,
    secondaryContainer = Saffron200,
    onSecondaryContainer = Saffron900,

    tertiary = Saffron700,
    onTertiary = Ink50,
    tertiaryContainer = Saffron300,
    onTertiaryContainer = Saffron900,

    background = Ink50,
    onBackground = Ink900,

    surface = Ink50,
    onSurface = Ink900,
    surfaceVariant = Saffron50,
    onSurfaceVariant = NeutralGrey80,

    outline = NeutralGrey70,
    outlineVariant = NeutralGrey30,

    error = Error,
    onError = OnError,
)

// Dark scheme — saffron-on-ink.
private val PitakaDarkColorScheme = darkColorScheme(
    primary = Saffron300,
    onPrimary = Saffron900,
    primaryContainer = Saffron700,
    onPrimaryContainer = Saffron100,

    secondary = Saffron200,
    onSecondary = Saffron900,
    secondaryContainer = Saffron800,
    onSecondaryContainer = Saffron100,

    tertiary = Saffron100,
    onTertiary = Saffron900,
    tertiaryContainer = Saffron600,
    onTertiaryContainer = Saffron100,

    background = NeutralGrey90,
    onBackground = Ink50,

    surface = NeutralGrey90,
    onSurface = Ink50,
    surfaceVariant = NeutralGrey80,
    onSurfaceVariant = NeutralGrey30,

    outline = NeutralGrey30,
    outlineVariant = NeutralGrey70,

    error = Error,
    onError = OnError,
)
