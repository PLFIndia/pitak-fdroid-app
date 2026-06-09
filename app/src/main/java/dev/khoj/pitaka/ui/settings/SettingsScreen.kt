package dev.khoj.pitaka.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.khoj.pitaka.R
import dev.khoj.pitaka.data.crash.CrashReportFile
import dev.khoj.pitaka.data.prefs.ThemeMode
import dev.khoj.pitaka.ui.contribute.LocalizedText
import dev.khoj.pitaka.ui.contribute.contributorLongPress
import dev.khoj.pitaka.ui.contribute.DisclosureFlow
import dev.khoj.pitaka.ui.contribute.GITHUB_SIGNUP_URL
import dev.khoj.pitaka.ui.contribute.GitHubDisclosureDialog
import dev.khoj.pitaka.ui.contribute.githubDisclosureCaptionRes
import dev.khoj.pitaka.ui.contribute.openInBrowser
import android.net.Uri
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onExport: () -> Unit,
    onImport: () -> Unit,
    onPublish: () -> Unit,
    onSetBackupPassphrase: () -> Unit,
    initialTab: String = "",
    onMerge: () -> Unit = {},
    onScanQr: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: dev.khoj.pitaka.ui.backup.BackupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val versionName = androidx.compose.ui.platform.LocalContext.current.let { ctx ->
        runCatching {
            val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
            pi.versionName ?: ""
        }.getOrDefault("")
    }

    // Backup/restore is inlined into the Data tab (the dedicated screen was
    // removed). This composable hosts the file-picker launchers, the restore
    // passphrase dialog, and the post-restore forced-restart dialog so the
    // BackupViewModel keeps the exact behaviour it had as a standalone screen.
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    var showRestart by remember { mutableStateOf(false) }

    val createBackupLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument(
            "application/octet-stream",
        ),
    ) { uri -> uri?.let { backupViewModel.onBackupDestinationPicked(it) } }

    val restoreSourceLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { backupViewModel.onRestoreSourcePicked(it) } }

    LaunchedEffect(Unit) {
        backupViewModel.events.collect { event ->
            when (event) {
                dev.khoj.pitaka.ui.backup.BackupEvent.BackupSaved ->
                    Toast.makeText(ctx, ctx.getString(R.string.backup_saved), Toast.LENGTH_SHORT).show()
                dev.khoj.pitaka.ui.backup.BackupEvent.WrongPassphrase ->
                    Toast.makeText(ctx, ctx.getString(R.string.restore_wrong_passphrase), Toast.LENGTH_LONG).show()
                dev.khoj.pitaka.ui.backup.BackupEvent.RestoreSucceeded -> showRestart = true
                is dev.khoj.pitaka.ui.backup.BackupEvent.Error ->
                    Toast.makeText(ctx, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    val launchBackup = {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        createBackupLauncher.launch(ctx.getString(R.string.backup_default_filename, stamp))
    }
    val launchRestore = {
        restoreSourceLauncher.launch(arrayOf("application/octet-stream", "application/zip", "*/*"))
    }

    val tabs = listOf(
        R.string.settings_tab_appearance,
        R.string.settings_tab_security,
        R.string.settings_tab_data,
        R.string.settings_tab_translate,
    )
    // Honour a deep-link to a specific tab (e.g. Pending → Data). Default 0.
    val startTab = when (initialTab) {
        dev.khoj.pitaka.ui.nav.Routes.TAB_DATA -> 2
        else -> 0
    }
    var selectedTab by rememberSaveable { mutableStateOf(startTab) }

    // No fixed-height TopAppBar / Scaffold bars — header and footer are plain
    // content so they take only the vertical space their text needs.
    Column(modifier = Modifier.fillMaxSize()) {
        LocalizedText(
            R.string.nav_settings,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
        )

        // 2x2 grid of tappable tab buttons.
        TabGrid(
            tabs = tabs,
            selected = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        // Tab body scrolls; About is pinned below it on every tab.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Spacer(Modifier.height(8.dp))
            when (selectedTab) {
                0 -> AppearanceTab(state, viewModel, versionName)
                1 -> SecurityTab(state, viewModel, onSetBackupPassphrase)
                2 -> DataTab(
                    viewModel = viewModel,
                    onImport = onImport,
                    onExport = onExport,
                    onBackupNow = launchBackup,
                    onRestore = launchRestore,
                    backupBusy = backupState.busy,
                    onPublish = onPublish,
                    onMerge = onMerge,
                    onScanQr = onScanQr,
                )
                3 -> ContributeTab(state, viewModel)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    // Restore: passphrase prompt (shown once a source file is picked).
    if (backupState.pendingRestoreUri != null) {
        RestorePassphraseDialog(
            onCancel = { backupViewModel.onCancelRestore() },
            onSubmit = { p -> backupViewModel.onRestoreWithPassphrase(p) },
        )
    }

    // Restore succeeded → force a clean process restart so Room/Hilt reopen the
    // swapped DBs. Same behaviour as the former standalone Backup screen.
    if (showRestart) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.restore_succeeded_title)) },
            text = { Text(stringResource(R.string.restore_succeeded_body)) },
            confirmButton = {
                TextButton(onClick = {
                    android.os.Process.killProcess(android.os.Process.myPid())
                    kotlin.system.exitProcess(0)
                }) { Text(stringResource(R.string.restore_succeeded_button)) }
            },
        )
    }
}

/**
 * Restore passphrase prompt — moved verbatim from the former BackupScreen so the
 * encrypted-archive restore flow is unchanged.
 */
@Composable
private fun RestorePassphraseDialog(
    onCancel: () -> Unit,
    onSubmit: (CharArray) -> Unit,
) {
    var pass by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.restore_passphrase_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.restore_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text(stringResource(R.string.restore_passphrase_field)) },
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Password,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(pass.toCharArray()); pass = "" },
                enabled = pass.isNotEmpty(),
            ) { Text(stringResource(R.string.restore_passphrase_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.add_book_cancel))
            }
        },
    )
}

/**
 * Shared Settings card. One source of truth for the tonal card look so every
 * tab matches AND so the dark-mode fix lives in one place. Plain
 * `surfaceContainer` cards washed into the background in dark mode (low tonal
 * contrast); we bump to `surfaceContainerHigh` and add a hairline outline so
 * the card boundary is unambiguous in BOTH light and dark themes.
 */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** 2x2 grid of selectable tab buttons. */
@Composable
private fun TabGrid(
    tabs: List<Int>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TabGridButton(tabs[0], selected == 0, { onSelect(0) }, Modifier.weight(1f))
            TabGridButton(tabs[1], selected == 1, { onSelect(1) }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TabGridButton(tabs[2], selected == 2, { onSelect(2) }, Modifier.weight(1f))
            TabGridButton(tabs[3], selected == 3, { onSelect(3) }, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TabGridButton(
    labelRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        androidx.compose.material3.Button(onClick = onClick, modifier = modifier) {
            LocalizedText(labelRes)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            LocalizedText(labelRes)
        }
    }
}

// --- Tabs ---

@Composable
private fun AppearanceTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    versionName: String,
) {
    SettingsCard {
        LibraryNameRow(state.libraryName, viewModel::onLibraryNameChange)
        LibraryNameLocalRow(state.libraryNameLocal, viewModel::onLibraryNameLocalChange)
        LibraryLogoRow(
            logoUri = state.libraryLogoUri,
            onPicked = viewModel::onLibraryLogoPicked,
            onCleared = viewModel::onLibraryLogoCleared,
        )
        ThemeModeRow(state.themeMode, viewModel::onThemeModeChange)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            DynamicColorRow(state.useDynamicColor, viewModel::onDynamicColorToggle)
        }
    }

    // About + disclaimer lives ONLY on the Appearance tab (it is fixed content
    // that would otherwise eat vertical space on every tab). It scrolls with the
    // tab body, so it no longer occupies a pinned footer.
    Spacer(Modifier.height(8.dp))
    AboutFooter(versionName)
}

@Composable
private fun SecurityTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
    onSetBackupPassphrase: () -> Unit,
) {
    // App lock (PIN + biometric) — gates the whole app, independent of the vault.
    SettingsCard {
        dev.khoj.pitaka.ui.applock.AppLockSection()
    }

    Spacer(Modifier.height(12.dp))

    // Vault & backups — protects borrower/loan data and the encrypted backup
    // archive. Separate lock from the whole-app App Lock above.
    SettingsCard {
        // Backup passphrase (protects encrypted backups; reached via its own screen).
        LocalizedText(R.string.settings_security_vault_title,
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        LocalizedText(R.string.settings_security_vault_help,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = onSetBackupPassphrase, modifier = Modifier.fillMaxWidth()) {
            LocalizedText(R.string.settings_set_backup_passphrase)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

        // Vault auto-lock timeout (borrower data).
        AutoLockRow(state.autoLockMs, viewModel::onAutoLockChange)
    }
}

@Composable
private fun DataTab(
    viewModel: SettingsViewModel,
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    backupBusy: Boolean,
    onPublish: () -> Unit,
    onMerge: () -> Unit,
    onScanQr: () -> Unit,
) {
    // What's stored / what's private.
    LocalizedText(R.string.settings_data_explainer_title,
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    LocalizedText(R.string.settings_data_explainer_books,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    LocalizedText(R.string.settings_data_explainer_vault,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Transfer & backup — one tonal card. The plaintext book-list actions
    // (import/export) sit above a single hairline that marks the privacy
    // boundary; the encrypted full-backup action sits below it. The hairline is
    // used once, for a real semantic boundary, not to separate similar buttons.
    TransferBackupCard(
        onImport = onImport,
        onExport = onExport,
        onBackupNow = onBackupNow,
        onRestore = onRestore,
        backupBusy = backupBusy,
    )

    CommunityLibrarySection(viewModel = viewModel, onMerge = onMerge, onScanQr = onScanQr)

    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

    // Publish a public, read-only copy of your library to the web.
    OutlinedButton(onClick = onPublish, modifier = Modifier.fillMaxWidth()) {
        LocalizedText(R.string.publish_title)
    }
}

/**
 * Transfer & backup — a single tonal card grouping the data-movement actions.
 * Import/Export act on the PLAINTEXT book list (Library + Wishlist); the
 * encrypted Full-backup section (Backup now + Restore) produces / consumes an
 * archive that also carries the vault. A single hairline separates those two
 * privacy classes inside the card so a user never mistakes "I exported my list"
 * for "my loan data is backed up".
 */
@Composable
private fun TransferBackupCard(
    onImport: () -> Unit,
    onExport: () -> Unit,
    onBackupNow: () -> Unit,
    onRestore: () -> Unit,
    backupBusy: Boolean,
) {
    // Card 1 — plaintext book list (Import / Export).
    SettingsCard {
        LocalizedText(
            R.string.settings_data_card_booklist_label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        LocalizedText(
            R.string.settings_data_card_booklist_caption,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        CardAction(
            labelRes = R.string.settings_data_card_import_label,
            helpRes = R.string.settings_data_import_help,
            buttonRes = R.string.import_title,
            onClick = onImport,
        )
        CardAction(
            labelRes = R.string.settings_data_card_export_label,
            helpRes = R.string.settings_data_export_help,
            buttonRes = R.string.export_title,
            onClick = onExport,
        )
    }

    Spacer(Modifier.height(12.dp))

    // Card 2 — encrypted full backup (Backup now / Restore). A SEPARATE card
    // from the book list so the plaintext vs encrypted privacy boundary reads
    // as two distinct things, not one list split by a line.
    SettingsCard {
        LocalizedText(
            R.string.settings_data_card_backup_label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            )
            LocalizedText(
                R.string.settings_data_card_backup_caption,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Backup now (primary) — writes an encrypted archive to a SAF dest.
            androidx.compose.material3.Button(
                onClick = onBackupNow,
                enabled = !backupBusy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (backupBusy) {
                    androidx.compose.material3.CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    LocalizedText(R.string.backup_now)
                }
            }
            // Restore (secondary, destructive) — picks a .pitabak file, then
            // prompts for the passphrase. Warning copy sits above the button.
            LocalizedText(
                R.string.restore_warning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        OutlinedButton(
            onClick = onRestore,
            enabled = !backupBusy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LocalizedText(R.string.restore_pick)
        }
    }
}

/**
 * One action inside [TransferBackupCard]: a bold label + optional help line on
 * the left, a full-width-trailing button on the right. The button label is
 * separate from the row label so the row can say "Import a list" while the
 * button just says "Import".
 */
@Composable
private fun CardAction(
    labelRes: Int,
    helpRes: Int?,
    buttonRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LocalizedText(
                labelRes,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            if (helpRes != null) {
                LocalizedText(
                    helpRes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onClick) {
            LocalizedText(buttonRes)
        }
    }
}

@Composable
private fun ContributeTab(
    state: SettingsUiState,
    viewModel: SettingsViewModel,
) {
    // Card 1 — translation contribution.
    SettingsCard {
        LocalizedText(
            R.string.contribute_translation_section_title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
        ContributorModeRow(state.contributorMode, viewModel::onContributorModeToggle)
        if (state.contributorMode) {
            Spacer(Modifier.height(8.dp))
            ShowHintsRow(state.showTranslatableHints, viewModel::onShowTranslatableHintsToggle)
            // Live preview: the same LocalizedText marker used everywhere else, so
            // flipping the toggle above boxes/unboxes this sample exactly as it will
            // across the app — an immediate, in-place clue to what the toggle does.
            // Wrapped in a tinted, labeled "Example" card so it reads as a sample
            // rather than a stray translatable string in the settings list.
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    stringResource(R.string.settings_translate_hints_example_label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                dev.khoj.pitaka.ui.contribute.LocalizedText(
                    stringId = R.string.settings_translate_hints_demo,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            // Mode OFF: this is the "I haven't started" state and the only time the
            // tab would otherwise be mostly empty. Fill it with a short onboarding
            // guide whose first step — create a GitHub account — removes the real
            // blocker for new contributors. Disappears once the mode is on, where
            // the toggle + live preview teach the rest.
            Spacer(Modifier.height(16.dp))
            ContributorGuide()
        }
    }

    Spacer(Modifier.height(12.dp))

    // Card 2 — crash reports. Separate card so it reads as its own concern,
    // distinct from the translation section above.
    SettingsCard {
        CrashReportsSection(viewModel)
    }
}

@Composable
private fun ContributorGuide() {
    val context = LocalContext.current
    val noBrowserMsg = stringResource(R.string.settings_translate_guide_no_browser)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(R.string.settings_translate_guide_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.settings_translate_guide_intro),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        GuideStep(1, stringResource(R.string.settings_translate_guide_step1))
        GuideStep(2, stringResource(R.string.settings_translate_guide_step2))
        GuideStep(3, stringResource(R.string.settings_translate_guide_step3))
        GuideStep(4, stringResource(R.string.settings_translate_guide_step4))
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = {
                val opened = dev.khoj.pitaka.ui.contribute.openInBrowser(
                    context,
                    android.net.Uri.parse("https://github.com/signup"),
                )
                if (!opened) {
                    android.widget.Toast
                        .makeText(context, noBrowserMsg, android.widget.Toast.LENGTH_LONG)
                        .show()
                }
            },
        ) {
            Text(stringResource(R.string.settings_translate_guide_signup))
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            "$number.",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(24.dp),
        )
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun AboutFooter(versionName: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))
        LocalizedText(R.string.settings_about_blurb,
            style = MaterialTheme.typography.bodySmall)
        LocalizedText(R.string.settings_about_version, versionName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        LocalizedText(R.string.settings_about_disclaimer,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryNameRow(persisted: String, onSave: (String) -> Unit) {
    dev.khoj.pitaka.ui.components.EditableField(
        labelRes = R.string.settings_library_name,
        value = persisted,
        onSave = onSave,
        // Appearance tab: drop the help line once a value is set (the field is
        // self-explanatory after first setup). Scoped to the call site so the
        // shared EditableField is unchanged elsewhere.
        helpRes = if (persisted.isBlank()) R.string.settings_library_name_help else null,
        placeholder = stringResource(R.string.settings_library_name_placeholder),
    )
}

@Composable
private fun LibraryNameLocalRow(persisted: String, onSave: (String) -> Unit) {
    dev.khoj.pitaka.ui.components.EditableField(
        labelRes = R.string.settings_library_name_local,
        value = persisted,
        onSave = onSave,
        helpRes = if (persisted.isBlank()) R.string.settings_library_name_local_help else null,
        placeholder = stringResource(R.string.settings_library_name_local_placeholder),
    )
}

@Composable
private fun ThemeModeRow(current: ThemeMode, onChange: (ThemeMode) -> Unit) {
    LocalizedText(R.string.settings_theme_mode,
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            modifier = Modifier.contributorLongPress(R.string.settings_theme_system),
            selected = current == ThemeMode.System,
            onClick = { onChange(ThemeMode.System) },
            label = { LocalizedText(R.string.settings_theme_system, passthroughTap = true) },
        )
        FilterChip(
            modifier = Modifier.contributorLongPress(R.string.settings_theme_light),
            selected = current == ThemeMode.Light,
            onClick = { onChange(ThemeMode.Light) },
            label = { LocalizedText(R.string.settings_theme_light, passthroughTap = true) },
        )
        FilterChip(
            modifier = Modifier.contributorLongPress(R.string.settings_theme_dark),
            selected = current == ThemeMode.Dark,
            onClick = { onChange(ThemeMode.Dark) },
            label = { LocalizedText(R.string.settings_theme_dark, passthroughTap = true) },
        )
    }
}

@Composable
private fun DynamicColorRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LocalizedText(R.string.settings_dynamic_color,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LocalizedText(R.string.settings_dynamic_color_help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ContributorModeRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LocalizedText(R.string.settings_translate_toggle_label,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LocalizedText(R.string.settings_translate_toggle_help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun ShowHintsRow(enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            LocalizedText(R.string.settings_translate_hints_label,
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            LocalizedText(R.string.settings_translate_hints_help,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = onChange)
    }
}

@Composable
private fun AutoLockRow(currentMs: Long, onChange: (Long) -> Unit) {
    LocalizedText(R.string.settings_auto_lock,
        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    val options = listOf(
        15_000L to R.string.settings_auto_lock_15s,
        60_000L to R.string.settings_auto_lock_60s,
        300_000L to R.string.settings_auto_lock_5m,
        900_000L to R.string.settings_auto_lock_15m,
        3_600_000L to R.string.settings_auto_lock_1h,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        options.forEach { (ms, label) ->
            FilterChip(
                modifier = Modifier.contributorLongPress(label),
                selected = currentMs == ms,
                onClick = { onChange(ms) },
                label = { LocalizedText(label, passthroughTap = true) },
            )
        }
    }
}

@Composable
private fun LibraryLogoRow(
    logoUri: String,
    onPicked: (android.net.Uri) -> Unit,
    onCleared: () -> Unit,
) {
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
    ) { uri -> if (uri != null) onPicked(uri) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .size(48.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUri.isNotBlank()) {
                coil.compose.AsyncImage(
                    model = logoUri,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text("पि", style = MaterialTheme.typography.titleMedium)
            }
        }
        Spacer(Modifier.padding(start = 12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(R.string.settings_logo_label), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            // Drop the help line once a logo is set (self-explanatory after setup).
            if (logoUri.isBlank()) {
                Text(
                    stringResource(R.string.settings_logo_help),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = { picker.launch("image/*") }) { Text(stringResource(R.string.settings_logo_pick)) }
        if (logoUri.isNotBlank()) {
            TextButton(onClick = onCleared) { Text(stringResource(R.string.common_clear)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Crash reports section (Post-1.0). Lives in the Contribute tab.
//
// Always rendered (no zero-pixel collapse) so users discover the feature
// before they ever crash — Contribute is a list of ways to help, not a
// place that springs new sections on you out of nowhere. When zero reports
// are on disk we show a muted "Nothing to send right now" line. When at
// least one report exists, each row shows a recorded timestamp + filename
// and offers three actions: Send (opens prefilled GitHub Issue Form,
// full trace copied to clipboard), Copy (just clipboard), Delete.
// ---------------------------------------------------------------------------
@Composable
private fun CrashReportsSection(viewModel: SettingsViewModel) {
    val reports by viewModel.crashReports.collectAsStateWithLifecycle()
    val disclosureAcked by viewModel.crashDisclosureAck.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val noBrowser = stringResource(R.string.crash_reports_no_browser)
    val copied = stringResource(R.string.crash_reports_copied)

    // F-04: which report the user tapped Send on while unacknowledged — drives
    // the disclosure dialog. Null when no dialog is showing.
    var pendingReport by remember { mutableStateOf<CrashReportFile?>(null) }

    fun openInBrowserFor(report: CrashReportFile) {
        val url = viewModel.prepareCrashSubmission(ctx, report)
        if (url == null) {
            Toast.makeText(ctx, noBrowser, Toast.LENGTH_LONG).show()
            return
        }
        val opened = openInBrowser(ctx, Uri.parse(url))
        if (!opened) Toast.makeText(ctx, noBrowser, Toast.LENGTH_LONG).show()
    }

    LocalizedText(
        R.string.crash_reports_section_title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    )
    LocalizedText(
        R.string.crash_reports_section_help,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (reports.isEmpty()) {
        Spacer(Modifier.height(8.dp))
        LocalizedText(
            R.string.crash_reports_empty,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    Spacer(Modifier.height(8.dp))
    reports.forEach { report ->
        CrashReportRow(
            report = report,
            onSend = {
                if (disclosureAcked) openInBrowserFor(report)
                else pendingReport = report
            },
            onCopy = {
                if (viewModel.copyCrashReport(ctx, report)) {
                    Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                }
            },
            onDelete = { viewModel.deleteCrashReport(report) },
        )
        Spacer(Modifier.height(8.dp))
    }

    if (disclosureAcked) {
        Text(
            text = stringResource(githubDisclosureCaptionRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (reports.size > 1) {
        TextButton(
            onClick = { viewModel.deleteAllCrashReports() },
            modifier = Modifier.fillMaxWidth(),
        ) {
            LocalizedText(R.string.crash_reports_delete_all)
        }
    }

    // F-04: pre-submit disclosure for the crash flow.
    pendingReport?.let { report ->
        GitHubDisclosureDialog(
            flow = DisclosureFlow.Crash,
            onContinue = {
                pendingReport = null
                viewModel.setCrashDisclosureAck()
                openInBrowserFor(report)
            },
            onCopyInstead = {
                pendingReport = null
                if (viewModel.copyCrashReport(ctx, report)) {
                    Toast.makeText(ctx, copied, Toast.LENGTH_SHORT).show()
                }
            },
            onCreateAccount = {
                pendingReport = null
                openInBrowser(ctx, Uri.parse(GITHUB_SIGNUP_URL))
            },
            onDismiss = { pendingReport = null },
        )
    }
}

@Composable
private fun CrashReportRow(
    report: CrashReportFile,
    onSend: () -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val timestamp = remember(report.timestampMillis) {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            .format(Date(report.timestampMillis))
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .padding(12.dp),
    ) {
        Text(
            text = timestamp,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = report.file.name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSend, modifier = Modifier.weight(1f)) {
                LocalizedText(R.string.crash_reports_send)
            }
            TextButton(onClick = onCopy) {
                LocalizedText(R.string.crash_reports_copy)
            }
            TextButton(onClick = onDelete) {
                LocalizedText(R.string.crash_reports_delete)
            }
        }
    }
}

/**
 * Community-library section (PLAN-merge.md D40/D41): the maintainer name, this
 * app's library ID (shown + QR + regenerate), and the "Merge from a file" entry.
 */
@Composable
private fun CommunityLibrarySection(
    viewModel: SettingsViewModel,
    onMerge: () -> Unit,
    onScanQr: () -> Unit,
) {
    val ctx = LocalContext.current
    val libraryId by viewModel.libraryId.collectAsStateWithLifecycle()
    val maintainer by viewModel.maintainerName.collectAsStateWithLifecycle()
    var showQr by remember { mutableStateOf(false) }
    var showRegenConfirm by remember { mutableStateOf(false) }
    var regenConfirmText by remember { mutableStateOf("") }

    // Ensure an ID exists the moment this section is shown.
    LaunchedEffect(Unit) { viewModel.ensureLibraryId() }

    SettingsCard {
        Text(
            stringResource(R.string.settings_community_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.settings_community_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        // Maintainer name — uses the shared EditableField (local draft + explicit
        // Save), the same proven pattern as the library-name field. Binding a
        // TextField's value straight to the DataStore flow garbles input, which is
        // why every persisted field in this app goes through EditableField.
        dev.khoj.pitaka.ui.components.EditableField(
            labelRes = R.string.settings_maintainer_name_label,
            value = maintainer,
            onSave = { viewModel.onMaintainerNameChange(it) },
            helpRes = R.string.settings_maintainer_name_help,
        )
        Spacer(Modifier.height(8.dp))

        // Library ID (truncated display) + actions.
        Text(
            stringResource(R.string.settings_library_id_label),
            style = MaterialTheme.typography.labelLarge,
        )
        Text(
            libraryId.ifBlank { "…" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { showQr = true },
                enabled = libraryId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_library_id_show_qr)) }
            OutlinedButton(
                onClick = {
                    val clip = ctx.getSystemService(android.content.ClipboardManager::class.java)
                    clip?.setPrimaryClip(android.content.ClipData.newPlainText("library-id", libraryId))
                    Toast.makeText(ctx, R.string.settings_library_id_copied, Toast.LENGTH_SHORT).show()
                },
                enabled = libraryId.isNotBlank(),
                modifier = Modifier.weight(1f),
            ) { Text(stringResource(R.string.settings_library_id_copy)) }
        }
        TextButton(onClick = { showRegenConfirm = true }) {
            Text(stringResource(R.string.settings_library_id_regenerate))
        }

        // Pair with another library by scanning its QR (D40). Adopts that ID.
        OutlinedButton(onClick = onScanQr, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_scan_library_qr))
        }
        Text(
            stringResource(R.string.settings_scan_library_qr_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        // Merge entry point.
        OutlinedButton(onClick = onMerge, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.settings_merge_from_file))
        }
        Text(
            stringResource(R.string.settings_merge_help),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (showQr) {
        val bmp = remember(libraryId) {
            dev.khoj.pitaka.ui.common.QrEncoder.encode(
                dev.khoj.pitaka.ui.common.QrEncoder.libraryIdPayload(libraryId),
            )
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showQr = false },
            confirmButton = {
                TextButton(onClick = { showQr = false }) { Text(stringResource(R.string.add_book_cancel)) }
            },
            title = { Text(stringResource(R.string.settings_library_id_qr_title)) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        stringResource(R.string.settings_library_id_qr_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    if (bmp != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = stringResource(R.string.settings_library_id_qr_title),
                            modifier = Modifier.size(240.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(libraryId, style = MaterialTheme.typography.labelSmall)
                }
            },
        )
    }

    if (showRegenConfirm) {
        val requiredPhrase = stringResource(R.string.settings_library_id_regen_confirm_phrase)
        val phraseMatches = regenConfirmText.trim().equals(requiredPhrase, ignoreCase = true)
        val dismiss = {
            showRegenConfirm = false
            regenConfirmText = ""
        }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = dismiss,
            title = { Text(stringResource(R.string.settings_library_id_regen_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.settings_library_id_regen_body))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = regenConfirmText,
                        onValueChange = { regenConfirmText = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.settings_library_id_regen_confirm_label)) },
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = phraseMatches,
                    onClick = {
                        dismiss()
                        viewModel.onRegenerateLibraryId()
                    },
                ) { Text(stringResource(R.string.settings_library_id_regenerate)) }
            },
            dismissButton = {
                TextButton(onClick = dismiss) {
                    Text(stringResource(R.string.add_book_cancel))
                }
            },
        )
    }
}
