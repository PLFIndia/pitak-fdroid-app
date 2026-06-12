package dev.khoj.pitaka.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.khoj.pitaka.R
import dev.khoj.pitaka.ui.borrowers.BorrowerProfileScreen
import dev.khoj.pitaka.ui.contribute.LocalContributorMode
import dev.khoj.pitaka.ui.contribute.LocalShowTranslatableHints
import dev.khoj.pitaka.ui.contribute.LocalizedText
import dev.khoj.pitaka.ui.contribute.SuggestionSheetHost
import dev.khoj.pitaka.ui.contribute.contributorLongPress
import dev.khoj.pitaka.ui.io.ExportScreen
import dev.khoj.pitaka.ui.io.ImportScreen
import dev.khoj.pitaka.ui.bookmarks.BookmarksScreen
import dev.khoj.pitaka.ui.lend.LendScreen
import dev.khoj.pitaka.ui.library.AddBookScreen
import dev.khoj.pitaka.ui.library.BookDetailScreen
import dev.khoj.pitaka.ui.library.LibraryScreen
import dev.khoj.pitaka.ui.loans.LoansScreen
import dev.khoj.pitaka.ui.nav.Routes
import dev.khoj.pitaka.ui.nav.ScanTarget
import dev.khoj.pitaka.ui.pending.PendingScreen
import dev.khoj.pitaka.ui.publish.CloudflareWizardScreen
import dev.khoj.pitaka.ui.publish.GitHubAuthScreen
import dev.khoj.pitaka.ui.publish.PublishScreen
import dev.khoj.pitaka.ui.scanner.ScannerScreen
import dev.khoj.pitaka.ui.settings.SettingsScreen
import dev.khoj.pitaka.ui.vault.BackupPassphraseScreen
import dev.khoj.pitaka.ui.welcome.WelcomeScreen
import dev.khoj.pitaka.ui.wishlist.AddWishlistScreen
import dev.khoj.pitaka.ui.wishlist.WishlistDetailScreen
import dev.khoj.pitaka.ui.wishlist.WishlistScreen

/**
 * App root composable. Hosts the bottom-nav scaffold and the per-tab NavHost.
 *
 * The Pending button lives in [LibraryScreen]'s top app bar (not a
 * bottom-nav slot).
 */
@Composable
fun PitakaApp() {
    val shellViewModel: AppShellViewModel = hiltViewModel()
    val contributorMode by shellViewModel.contributorMode.collectAsStateWithLifecycle()
    val showHints by shellViewModel.showTranslatableHints.collectAsStateWithLifecycle()
    val translateAcked by shellViewModel.translateDisclosureAck.collectAsStateWithLifecycle()
    val repoOwner = stringResource(R.string.contribute_repo_owner)
    val repoName = stringResource(R.string.contribute_repo_name)
    CompositionLocalProvider(
        LocalContributorMode provides contributorMode,
        LocalShowTranslatableHints provides showHints,
    ) {
        SuggestionSheetHost(
            repoOwner = repoOwner,
            repoName = repoName,
            disclosureAcked = translateAcked,
            onDisclosureAck = shellViewModel::setTranslateDisclosureAck,
        ) {
            // The main app (Library + bottom nav) is always composed underneath,
            // so it loads its data and populates the list BEHIND the welcome
            // overlay. The welcome screen is a splash cover on top — not a nav
            // destination — so it isn't squeezed by the Scaffold's bottom bar
            // (no icon shift) and dismissing it just fades away to reveal an
            // already-ready, populated Library (one settling motion, no empty
            // flash, nothing "falling").
            var showWelcome by rememberSaveable { mutableStateOf(true) }
            val appLockViewModel: dev.khoj.pitaka.ui.applock.AppLockViewModel = hiltViewModel()
            val appLocked by appLockViewModel.locked.collectAsStateWithLifecycle()

            Box(modifier = Modifier.fillMaxSize()) {
                PitakaAppContent()

                AnimatedVisibility(
                    visible = showWelcome,
                    enter = EnterTransition.None,
                    exit = fadeOut(animationSpec = tween(500)),
                ) {
                    WelcomeScreen(onContinue = { showWelcome = false })
                }

                // App Lock is the OUTERMOST gate: when enabled + locked it covers
                // everything (including the welcome screen). It's opaque and
                // consumes all input until unlocked.
                if (appLocked) {
                    dev.khoj.pitaka.ui.applock.AppLockScreen(viewModel = appLockViewModel)
                }
            }
        }
    }
}

@Composable
private fun PitakaAppContent() {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route

    Scaffold(
        // Don't let the Scaffold add system-bar insets to the content: each inner
        // screen owns its own TOP inset (Library's TopAppBar consumes the status
        // bar; Settings' title uses statusBarsPadding), and the bottom NavigationBar
        // consumes the navigation-bar inset itself. Without this, the outer
        // Scaffold (which has no topBar) reserved the status-bar height as top
        // content padding AND the inner screen reserved it again — an empty band
        // above every screen. Zeroing it here removes that double-count.
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // Match the nav bar container to the app surface. M3's default
            // NavigationBar container is the surfaceContainer tonal role, which
            // this scheme doesn't customize — it derives a grayish tint that
            // clashes with the warm Ink50 surface in light mode. Pin it to
            // surface so the strip blends with the rest of the UI.
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                BottomNavTab(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    labelRes = R.string.nav_library,
                    route = Routes.LIBRARY,
                    currentRoute = currentRoute,
                    onClick = { navController.navigateTopLevel(Routes.LIBRARY) },
                )
                BottomNavTab(
                    icon = Icons.Filled.Bookmark,
                    labelRes = R.string.nav_wishlist,
                    route = Routes.WISHLIST,
                    currentRoute = currentRoute,
                    onClick = { navController.navigateTopLevel(Routes.WISHLIST) },
                )
                BottomNavTab(
                    icon = Icons.Filled.People,
                    labelRes = R.string.nav_loans,
                    route = Routes.LOANS,
                    currentRoute = currentRoute,
                    onClick = { navController.navigateTopLevel(Routes.LOANS) },
                )
                BottomNavTab(
                    icon = Icons.Filled.Settings,
                    labelRes = R.string.nav_settings,
                    route = Routes.SETTINGS,
                    currentRoute = currentRoute,
                    onClick = { navController.navigateTopLevel(Routes.SETTINGS_BASE) },
                )
            }
        },
    ) { padding ->
        // consumeWindowInsets tells nested inset consumers (each top-level
        // screen's own inner Scaffold defaults to contentWindowInsets=systemBars,
        // and Settings' footer uses navigationBarsPadding) that the bottom
        // navigation-bar inset is already handled by the NavigationBar above —
        // so they resolve their bottom inset to 0 instead of reserving it a
        // second time. Without this, an empty band appeared between where each
        // screen's list ended and the bottom nav bar. Pattern borrowed from
        // Google's Now in Android (NiaApp consumes the Scaffold padding the same way).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
        ) {
            NavHost(
                navController = navController,
                startDestination = Routes.LIBRARY,
            ) {
                composable(Routes.LIBRARY) {
                    LibraryScreen(
                        onAddBook = { navController.navigate(Routes.LIBRARY_ADD) },
                        onScanBook = { navController.navigate(Routes.scanner(ScanTarget.Library)) },
                        onImportBook = { navController.navigate(Routes.IMPORT) },
                        onOpenBook = { id -> navController.navigate(Routes.libraryDetail(id)) },
                        onOpenBookmarks = { navController.navigate(Routes.BOOKMARKS) },
                        onPublish = { navController.navigate(Routes.PUBLISH) },
                    )
                }
                composable(Routes.LIBRARY_ADD) {
                    AddBookScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        onOpenExistingBook = { id ->
                            navController.popBackStack()
                            navController.navigate(Routes.libraryDetail(id))
                        },
                    )
                }
                composable(
                    Routes.LIBRARY_EDIT_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType },
                    ),
                ) {
                    AddBookScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        // Edit-mode never opens a "duplicate existing" — the user is editing
                        // a known row. Plug a no-op for the interface.
                        onOpenExistingBook = { _ -> navController.popBackStack() },
                    )
                }
                composable(
                    Routes.LIBRARY_ADD_WITH_ISBN_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.ARG_ISBN) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    AddBookScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            // After Saved we want to land back on the library, not the scanner.
                            navController.popBackStack(Routes.LIBRARY, inclusive = false)
                        },
                        onOpenExistingBook = { id ->
                            navController.popBackStack(Routes.LIBRARY, inclusive = false)
                            navController.navigate(Routes.libraryDetail(id))
                        },
                    )
                }
                composable(
                    Routes.SCANNER,
                    arguments = listOf(
                        navArgument(Routes.ARG_TARGET) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = ScanTarget.Library.name
                        },
                    ),
                ) { backStackEntry ->
                    val targetName = backStackEntry.arguments?.getString(Routes.ARG_TARGET)
                        ?: ScanTarget.Library.name
                    val target = runCatching { ScanTarget.valueOf(targetName) }
                        .getOrDefault(ScanTarget.Library)
                    ScannerScreen(
                        onBack = { navController.popBackStack() },
                        onIsbnScanned = { isbn ->
                            val backTo = if (target == ScanTarget.Wishlist) Routes.WISHLIST else Routes.LIBRARY
                            navController.popBackStack(backTo, inclusive = false)
                            val next = if (target == ScanTarget.Wishlist)
                                Routes.wishlistAddWithIsbn(isbn)
                            else
                                Routes.libraryAddWithIsbn(isbn)
                            navController.navigate(next)
                        },
                        onManualEntry = {
                            val backTo = if (target == ScanTarget.Wishlist) Routes.WISHLIST else Routes.LIBRARY
                            navController.popBackStack(backTo, inclusive = false)
                            navController.navigate(
                                if (target == ScanTarget.Wishlist) Routes.WISHLIST_ADD else Routes.LIBRARY_ADD
                            )
                        },
                    )
                }
                composable(
                    Routes.LIBRARY_DETAIL_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: 0L
                    BookDetailScreen(
                        onBack = { navController.popBackStack() },
                        onDeleted = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.libraryEdit(bookId)) },
                        onLend = { navController.navigate(Routes.lend(bookId)) },
                    )
                }
                composable(Routes.WISHLIST) {
                    WishlistScreen(
                        onAddBook = { navController.navigate(Routes.WISHLIST_ADD) },
                        onScanBook = { navController.navigate(Routes.scanner(ScanTarget.Wishlist)) },
                        onOpenBook = { id -> navController.navigate(Routes.wishlistDetail(id)) },
                        onOpenLibraryBook = { id -> navController.navigate(Routes.libraryDetail(id)) },
                    )
                }
                composable(Routes.WISHLIST_ADD) {
                    AddWishlistScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.WISHLIST_ADD_WITH_ISBN_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.ARG_ISBN) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) {
                    AddWishlistScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = {
                            navController.popBackStack(Routes.WISHLIST, inclusive = false)
                        },
                    )
                }
                composable(
                    Routes.WISHLIST_EDIT_PATTERN,
                    arguments = listOf(
                        navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType },
                    ),
                ) {
                    AddWishlistScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.WISHLIST_DETAIL_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) { backStackEntry ->
                    val bookId = backStackEntry.arguments?.getLong(Routes.ARG_BOOK_ID) ?: 0L
                    WishlistDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { navController.navigate(Routes.wishlistEdit(bookId)) },
                        onDeleted = { navController.popBackStack() },
                        onOpenLibraryBook = { id -> navController.navigate(Routes.libraryDetail(id)) },
                    )
                }
                composable(Routes.EXPORT) {
                    ExportScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.IMPORT) {
                    ImportScreen(onBack = { navController.popBackStack() })
                }
                composable(Routes.LOANS) {
                    LoansScreen(
                        onOpenBorrower = { id -> navController.navigate(Routes.borrowerProfile(id)) },
                        onOpenPending = { navController.navigate(Routes.PENDING) },
                    )
                }
                composable(Routes.BOOKMARKS) {
                    BookmarksScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    Routes.BORROWER_PROFILE_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) {
                    BorrowerProfileScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.LEND_PATTERN,
                    arguments = listOf(navArgument(Routes.ARG_BOOK_ID) { type = NavType.LongType }),
                ) {
                    LendScreen(
                        onBack = { navController.popBackStack() },
                        onLent = { navController.popBackStack() },
                    )
                }
                composable(Routes.PENDING) {
                    PendingScreen(
                        onBack = { navController.popBackStack() },
                        onOpenBook = { id ->
                            navController.popBackStack()
                            navController.navigate(Routes.libraryDetail(id))
                        },
                        onSetBackupPassphrase = { navController.navigate(Routes.BACKUP_PASSPHRASE) },
                        onOpenBackup = { navController.navigate(Routes.settings(Routes.TAB_DATA)) },
                    )
                }
                composable(Routes.BACKUP_PASSPHRASE) {
                    BackupPassphraseScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                        onWiped = { navController.popBackStack() },
                    )
                }
                composable(Routes.PUBLISH) {
                    PublishScreen(
                        onBack = { navController.popBackStack() },
                        onSignIn = { navController.navigate(Routes.GH_AUTH) },
                        onOpenCfWizard = { navController.navigate(Routes.CF_WIZARD) },
                    )
                }
                composable(Routes.GH_AUTH) {
                    GitHubAuthScreen(
                        onBack = { navController.popBackStack() },
                        onSignedIn = { navController.popBackStack() },
                    )
                }
                composable(Routes.CF_WIZARD) {
                    CloudflareWizardScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    Routes.SETTINGS,
                    arguments = listOf(navArgument(Routes.ARG_TAB) {
                        type = NavType.StringType
                        defaultValue = ""
                    }),
                ) { entry ->
                    SettingsScreen(
                        initialTab = entry.arguments?.getString(Routes.ARG_TAB).orEmpty(),
                        onExport = { navController.navigate(Routes.EXPORT) },
                        onImport = { navController.navigate(Routes.IMPORT) },
                        onPublish = { navController.navigate(Routes.PUBLISH) },
                        onSetBackupPassphrase = { navController.navigate(Routes.BACKUP_PASSPHRASE) },
                        onMerge = { navController.navigate(Routes.MERGE) },
                        onScanQr = { navController.navigate(Routes.MERGE_SCAN_QR) },
                    )
                }
                composable(Routes.MERGE) {
                    dev.khoj.pitaka.ui.merge.MergeScreen(
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(Routes.MERGE_SCAN_QR) {
                    val scanVm: dev.khoj.pitaka.ui.merge.QrScanViewModel = androidx.hilt.navigation.compose.hiltViewModel()
                    val ctx = androidx.compose.ui.platform.LocalContext.current
                    var handled by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                    dev.khoj.pitaka.ui.merge.QrScanScreen(
                        onBack = { navController.popBackStack() },
                        onLibraryIdScanned = { id ->
                            if (!handled) {
                                handled = true
                                scanVm.adoptLibraryId(id)
                                android.widget.Toast.makeText(
                                    ctx,
                                    ctx.getString(dev.khoj.pitaka.R.string.merge_scan_qr_adopted),
                                    android.widget.Toast.LENGTH_LONG,
                                ).show()
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.BottomNavTab(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    labelRes: Int,
    route: String,
    currentRoute: String?,
    onClick: () -> Unit,
) {
    NavigationBarItem(
        modifier = Modifier.contributorLongPress(labelRes),
        selected = currentRoute == route,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { LocalizedText(labelRes, passthroughTap = true) },
    )
}

private fun androidx.navigation.NavController.navigateTopLevel(route: String) {
    navigate(route) {
        // Flat graph (Library/Wishlist/Loans/Settings + their detail screens are
        // all top-level destinations, not nested per-tab graphs). Pop back to the
        // graph's start so tapping a tab always lands on that tab's ROOT, clearing
        // any detail screen on top. We deliberately do NOT use saveState/
        // restoreState here: that pairing is for nested tab graphs, and in this
        // flat graph it would save+restore the open detail screen (e.g. a book
        // page), so tapping "Library" from a book page would return to that book
        // instead of the book list.
        popUpTo(graph.findStartDestination().id) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private fun android.content.Context.shortToast(resId: Int) {
    android.widget.Toast.makeText(this, resId, android.widget.Toast.LENGTH_SHORT).show()
}
