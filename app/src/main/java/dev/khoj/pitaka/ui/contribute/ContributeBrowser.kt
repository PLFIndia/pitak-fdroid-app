package dev.khoj.pitaka.ui.contribute

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent

/**
 * Opens [uri] in a real **browser**, never a deep-linked app.
 *
 * Why this exists: GitHub's Android app registers `github.com/...` paths as App
 * Links and, when installed, intercepts a bare `ACTION_VIEW`. For the
 * suggestion flow its in-app composer drops the issue-form prefill; for the
 * account-signup link it would route into the app's own sign-in instead of the
 * web signup. Both contributor entry points are `github.com` URLs, so both need
 * the same browser-pinned path — hence one shared helper (single source of
 * truth) rather than a copy in each call site.
 *
 * Strategy: resolve a concrete browser package via a neutral `http://` probe
 * (the GitHub app never claims arbitrary http authorities, so it is excluded by
 * construction), then prefer Chrome Custom Tabs pinned to that package, falling
 * back to a package-pinned `ACTION_VIEW`. Returns `false` only when no browser
 * can be found at all (offline / browserless device).
 */
internal fun openInBrowser(ctx: Context, uri: Uri): Boolean {
    val browserPkg = resolveBrowserPackage(ctx)

    val customTabs = CustomTabsIntent.Builder().build().apply {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (browserPkg != null) intent.setPackage(browserPkg)
    }
    try {
        customTabs.launchUrl(ctx, uri)
        return true
    } catch (_: ActivityNotFoundException) {
        // fall through to explicit-browser ACTION_VIEW
    }

    val viewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (browserPkg != null) setPackage(browserPkg)
    }
    return try {
        ctx.startActivity(viewIntent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}

/**
 * Finds the default browser's package name by querying a neutral `http://` URL.
 * The GitHub app does not register for arbitrary http authorities (only
 * `github.com`), so it is excluded from this resolution by construction.
 * Returns `null` if no browser is installed, in which case callers leave the
 * intent unpinned and let the system resolve it.
 */
internal fun resolveBrowserPackage(ctx: Context): String? {
    val probe = Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com"))
    val resolved: ResolveInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ctx.packageManager.resolveActivity(
                probe,
                android.content.pm.PackageManager.MATCH_DEFAULT_ONLY,
            )
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.resolveActivity(probe, 0)
        }
    val pkg = resolved?.activityInfo?.packageName
    // Guard against the system "resolver" sentinel (no concrete default chosen).
    return pkg?.takeIf { it != "android" }
}
