package dev.khoj.pitaka

import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.khoj.pitaka.data.prefs.AppPreferences
import dev.khoj.pitaka.data.prefs.ThemeMode
import dev.khoj.pitaka.data.vault.VaultSession
import dev.khoj.pitaka.data.vault.VaultWindowSecurity
import dev.khoj.pitaka.ui.PitakaApp
import dev.khoj.pitaka.ui.theme.PitakaTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : FragmentActivity() {
    @Inject lateinit var prefs: AppPreferences
    @Inject lateinit var vaultSession: VaultSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // F-12: keep FLAG_SECURE in lock-step with the vault state so the
        // Recents thumbnail, screen-casts, and accessibility readers never
        // capture vault-derived PII. Collected lifecycle-aware (only while
        // STARTED) at the Activity level — not inside Compose — because the
        // window flag is window state, not UI state.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vaultSession.state.collect { state ->
                    if (VaultWindowSecurity.shouldSecure(state)) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    }
                }
            }
        }

        setContent {
            PitakaRoot(prefs = prefs)
        }
    }
}

// Fully transparent system bars so the app's own Compose background paints
// behind both the status and navigation bars. That background recomposes in the
// same frame as the rest of the UI when the theme changes, so there is no
// separate OS-applied bar color that lags a frame behind the content (the
// previous nav-bar scrim did exactly that). enableEdgeToEdge then only controls
// icon contrast (light/dark icons), which is far less perceptible than a
// trailing color block.
private val transparentScrim = Color.TRANSPARENT

@Composable
private fun PitakaRoot(prefs: AppPreferences) {
    val mode by prefs.themeMode().collectAsState(initial = ThemeMode.System)
    val useDynamic by prefs.useDynamicColor().collectAsState(initial = false)
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (mode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    // Apply edge-to-edge REACTIVELY to the resolved theme. Calling
    // enableEdgeToEdge once in onCreate (the old approach) froze the system-bar
    // icon contrast at launch, so switching to Light left the bar icons styled
    // for dark (invisible). Both bars use a transparent scrim so the only thing
    // changing here is icon contrast — the bar *backgrounds* are the app's own
    // surface, which repaints instantly with the rest of the theme (no lag).
    val activity = androidx.compose.ui.platform.LocalContext.current as? ComponentActivity
    DisposableEffect(darkTheme) {
        activity?.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(transparentScrim, transparentScrim) { darkTheme },
            navigationBarStyle = SystemBarStyle.auto(transparentScrim, transparentScrim) { darkTheme },
        )
        onDispose {}
    }

    PitakaTheme(darkTheme = darkTheme, useDynamicColor = useDynamic) {
        Surface(modifier = Modifier.fillMaxSize()) {
            PitakaApp()
        }
    }
}
