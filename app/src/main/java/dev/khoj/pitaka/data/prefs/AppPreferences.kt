package dev.khoj.pitaka.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.khoj.pitaka.domain.repository.BookSort
import dev.khoj.pitaka.domain.repository.WishlistSort
import dev.khoj.pitaka.ui.wishlist.WishlistFilter
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-level user preferences (Phase 7). DataStore-backed so values
 * survive process restarts.
 *
 * Vault-domain prefs (auto-lock timeout, backup staleness threshold,
 * backup-banner ack) live in [VaultPreferences].
 *
 * D22: per-screen sort persistence. D29: dynamic-color toggle.
 * D27: theme mode persistence.
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val Context.dataStore by preferencesDataStore(name = "pitaka_app_prefs")

    private val librarySortKey = stringPreferencesKey("library_sort")
    private val pdfColumnsKey = stringPreferencesKey("pdf_export_columns")
    private val wishlistFilterKey = stringPreferencesKey("wishlist_filter")
    private val wishlistSortKey = stringPreferencesKey("wishlist_sort")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val useDynamicColorKey = stringPreferencesKey("use_dynamic_color")
    private val libraryNameKey = stringPreferencesKey("library_name")
    private val libraryNameLocalKey = stringPreferencesKey("library_name_local")
    private val libraryLogoUriKey = stringPreferencesKey("library_logo_uri")
    private val contributorModeKey = stringPreferencesKey("localization_contributor_mode")
    private val showTranslatableHintsKey = stringPreferencesKey("localization_show_hints")
    private val translateDisclosureAckKey = stringPreferencesKey("github_disclosure_ack_translate")
    private val crashDisclosureAckKey = stringPreferencesKey("github_disclosure_ack_crash")
    private val publishContactLocationKey = stringPreferencesKey("publish_contact_location")
    private val publishContactEmailKey = stringPreferencesKey("publish_contact_email")
    private val publishContactPhoneKey = stringPreferencesKey("publish_contact_phone")
    private val coversHealedKey = stringPreferencesKey("covers_healed_v1")
    private val libraryIdKey = stringPreferencesKey("library_id")
    private val maintainerNameKey = stringPreferencesKey("maintainer_name")

    fun libraryName(): Flow<String> =
        context.dataStore.data.map { p -> (p[libraryNameKey] ?: "").trim() }

    suspend fun setLibraryName(name: String) {
        context.dataStore.edit { it[libraryNameKey] = name.trim() }
    }

    /**
     * Optional library name in the user's local language/script, shown as a
     * second line on the welcome/library page beneath the English name. Blank
     * when the user hasn't set one (then only the English name is shown).
     */
    fun libraryNameLocal(): Flow<String> =
        context.dataStore.data.map { p -> (p[libraryNameLocalKey] ?: "").trim() }

    suspend fun setLibraryNameLocal(name: String) {
        context.dataStore.edit { it[libraryNameLocalKey] = name.trim() }
    }

    /** file:// URL to the cropped library logo PNG, or blank when unset. */
    fun libraryLogoUri(): Flow<String> =
        context.dataStore.data.map { p -> (p[libraryLogoUriKey] ?: "").trim() }

    suspend fun setLibraryLogoUri(uri: String) {
        context.dataStore.edit { it[libraryLogoUriKey] = uri }
    }

    suspend fun clearLibraryLogoUri() {
        context.dataStore.edit { it.remove(libraryLogoUriKey) }
    }

    /**
     * Optional library contact fields shown on the PUBLISHED page only
     * (Publish screen). They have no in-app effect. Each is rendered as a
     * tappable link on the public site when non-blank; clearing one and
     * re-publishing removes it from the live page (single source of truth).
     *
     * These are PII the user deliberately opts into publishing — the Publish
     * UI surfaces a "shown publicly" caution. Contrast D10 (notes stripped).
     */
    fun publishContactLocation(): Flow<String> =
        context.dataStore.data.map { p -> (p[publishContactLocationKey] ?: "").trim() }

    suspend fun setPublishContactLocation(value: String) {
        context.dataStore.edit { it[publishContactLocationKey] = value.trim() }
    }

    fun publishContactEmail(): Flow<String> =
        context.dataStore.data.map { p -> (p[publishContactEmailKey] ?: "").trim() }

    suspend fun setPublishContactEmail(value: String) {
        context.dataStore.edit { it[publishContactEmailKey] = value.trim() }
    }

    fun publishContactPhone(): Flow<String> =
        context.dataStore.data.map { p -> (p[publishContactPhoneKey] ?: "").trim() }

    suspend fun setPublishContactPhone(value: String) {
        context.dataStore.edit { it[publishContactPhoneKey] = value.trim() }
    }

    fun librarySort(): Flow<BookSort> =
        context.dataStore.data.map { p ->
            runCatching { BookSort.valueOf(p[librarySortKey] ?: BookSort.RecentlyAdded.name) }
                .getOrDefault(BookSort.RecentlyAdded)
        }

    suspend fun setLibrarySort(sort: BookSort) {
        context.dataStore.edit { it[librarySortKey] = sort.name }
    }

    /**
     * PDF-export column selection (feature: user-selectable PDF columns).
     * Stored as a CSV of [dev.khoj.pitaka.data.export.PdfColumn] names. Unset →
     * the default catalogue columns. Title is always forced in by parseCsv.
     */
    fun pdfColumns(): Flow<List<dev.khoj.pitaka.data.export.PdfColumn>> =
        context.dataStore.data.map { p ->
            val csv = p[pdfColumnsKey]
            if (csv.isNullOrBlank()) dev.khoj.pitaka.data.export.PdfColumn.DEFAULT
            else dev.khoj.pitaka.data.export.PdfColumn.parseCsv(csv)
        }

    suspend fun setPdfColumns(columns: List<dev.khoj.pitaka.data.export.PdfColumn>) {
        context.dataStore.edit {
            it[pdfColumnsKey] = dev.khoj.pitaka.data.export.PdfColumn.toCsv(columns)
        }
    }

    fun wishlistFilter(): Flow<WishlistFilter> =
        context.dataStore.data.map { p ->
            runCatching { WishlistFilter.valueOf(p[wishlistFilterKey] ?: WishlistFilter.Active.name) }
                .getOrDefault(WishlistFilter.Active)
        }

    suspend fun setWishlistFilter(filter: WishlistFilter) {
        context.dataStore.edit { it[wishlistFilterKey] = filter.name }
    }

    fun wishlistSort(): Flow<WishlistSort> =
        context.dataStore.data.map { p ->
            runCatching { WishlistSort.valueOf(p[wishlistSortKey] ?: WishlistSort.Priority.name) }
                .getOrDefault(WishlistSort.Priority)
        }

    suspend fun setWishlistSort(sort: WishlistSort) {
        context.dataStore.edit { it[wishlistSortKey] = sort.name }
    }

    fun themeMode(): Flow<ThemeMode> =
        context.dataStore.data.map { p ->
            runCatching { ThemeMode.valueOf(p[themeModeKey] ?: ThemeMode.System.name) }
                .getOrDefault(ThemeMode.System)
        }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[themeModeKey] = mode.name }
    }

    fun useDynamicColor(): Flow<Boolean> =
        context.dataStore.data.map { p ->
            (p[useDynamicColorKey] ?: "0") == "1"
        }

    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[useDynamicColorKey] = if (enabled) "1" else "0" }
    }

    /**
     * Localization Contributor Mode (Phase 1 localization).
     *
     * When ON, translatable UI text (LocalizedText wrapper) responds to
     * long-press by opening the suggestion sheet. When OFF, long-press is
     * a no-op and the app behaves identically to a non-contributor build.
     *
     * Default: false. Opt-in only via Settings → "Help translate Pitaka".
     */
    fun contributorMode(): Flow<Boolean> =
        context.dataStore.data.map { p ->
            (p[contributorModeKey] ?: "0") == "1"
        }

    suspend fun setContributorMode(enabled: Boolean) {
        context.dataStore.edit { it[contributorModeKey] = if (enabled) "1" else "0" }
    }

    /**
     * Whether translatable text shows a permanent dotted underline so a
     * contributor can see at a glance which strings are long-pressable.
     *
     * Default: false. Only meaningful when [contributorMode] is on; the Settings
     * toggle that controls it is hidden unless contributor mode is enabled.
     */
    fun showTranslatableHints(): Flow<Boolean> =
        context.dataStore.data.map { p ->
            (p[showTranslatableHintsKey] ?: "0") == "1"
        }

    suspend fun setShowTranslatableHints(enabled: Boolean) {
        context.dataStore.edit { it[showTranslatableHintsKey] = if (enabled) "1" else "0" }
    }

    /**
     * F-04 (audit): one-time acknowledgement that submitting a translation
     * suggestion opens a PUBLIC GitHub issue under the user's GitHub name.
     * The disclosure dialog shows on the first submit; once acknowledged,
     * subsequent submits skip the full dialog (a quiet standing caption keeps
     * the fact visible). DataStore-backed: survives restart, resets on
     * uninstall — a reinstalled app re-discloses, which is the correct
     * privacy default.
     *
     * Separate from [crashDisclosureAck] on purpose: the crash flow publishes
     * a different (larger) payload, so consenting to one must not silently
     * consent to the other.
     */
    fun translateDisclosureAck(): Flow<Boolean> =
        context.dataStore.data.map { p -> (p[translateDisclosureAckKey] ?: "0") == "1" }

    suspend fun setTranslateDisclosureAck(acked: Boolean) {
        context.dataStore.edit { it[translateDisclosureAckKey] = if (acked) "1" else "0" }
    }

    /**
     * F-04 (audit): one-time acknowledgement that submitting a crash report
     * opens a PUBLIC GitHub issue under the user's GitHub name, including
     * device model, Android version, locale, and the top stack frames (but
     * nothing the user typed into their library). See [translateDisclosureAck]
     * for the DataStore semantics; kept separate because the crash payload
     * reveals more than a translation string.
     */
    fun crashDisclosureAck(): Flow<Boolean> =
        context.dataStore.data.map { p -> (p[crashDisclosureAckKey] ?: "0") == "1" }

    suspend fun setCrashDisclosureAck(acked: Boolean) {
        context.dataStore.edit { it[crashDisclosureAckKey] = if (acked) "1" else "0" }
    }

    /**
     * One-shot guard for the cover-heal migration (PLAN-covers.md D4). True
     * once the legacy id-named / cross-wired cover references have been
     * rationalised into UUID-relative form. DataStore-backed: survives
     * restart, resets on uninstall (a reinstall starts from a clean store, so
     * re-running the idempotent heal is harmless).
     */
    fun coversHealed(): Flow<Boolean> =
        context.dataStore.data.map { p -> (p[coversHealedKey] ?: "0") == "1" }

    suspend fun setCoversHealed(done: Boolean) {
        context.dataStore.edit { it[coversHealedKey] = if (done) "1" else "0" }
    }

    /**
     * Library ID (PLAN-merge.md D40): an opaque random namespace string for THIS
     * app's library. Two exports merge automatically only when their library IDs
     * match (a different ID forces an explicit Join / Overwrite decision). Shared
     * to another app via QR; an app can adopt another's by scanning.
     *
     * Lazily auto-generated on first read so every install always has one (and
     * every export always carries one). 128 bits of randomness rendered as a
     * 32-char lowercase hex string — opaque, collision-free for this purpose, and
     * trivially QR-encodable.
     */
    fun libraryId(): Flow<String> =
        context.dataStore.data.map { p -> p[libraryIdKey] ?: "" }

    /** Returns the current library ID, generating + persisting one if absent. */
    suspend fun getOrCreateLibraryId(): String {
        val existing = context.dataStore.data.map { it[libraryIdKey] }.first()
        if (!existing.isNullOrBlank()) return existing
        val generated = newLibraryId()
        context.dataStore.edit { prefs ->
            // Double-check inside the edit to avoid two racers minting different IDs.
            val now = prefs[libraryIdKey]
            if (now.isNullOrBlank()) prefs[libraryIdKey] = generated
        }
        return context.dataStore.data.map { it[libraryIdKey] }.first() ?: generated
    }

    /**
     * Set or adopt a library ID (QR scan / merge-Join / Overwrite / regenerate /
     * detach). Validated at this persistence boundary against
     * [dev.khoj.pitaka.domain.model.LibraryId] so no caller — including a corrupt
     * or hand-crafted import file — can persist a malformed ID that would then
     * propagate into every future export and break QR pairing. Contract:
     *  - blank → CLEAR the ID (detach; the next [getOrCreateLibraryId] re-mints);
     *  - valid → set it (trimmed);
     *  - invalid non-blank junk → NO-OP (the current ID stands).
     */
    suspend fun setLibraryId(id: String) {
        val trimmed = id.trim()
        when {
            trimmed.isEmpty() ->
                context.dataStore.edit { it.remove(libraryIdKey) }
            dev.khoj.pitaka.domain.model.LibraryId.isValid(trimmed) ->
                context.dataStore.edit { it[libraryIdKey] = trimmed }
            else -> Unit // reject junk
        }
    }

    /**
     * Maintainer attribution handle (PLAN-merge.md D41): a short name set once,
     * stamped onto books this app catalogues (`Book.addedBy`). Self-asserted, not
     * signed — coordination among trusted maintainers, not security. Blank until set.
     */
    fun maintainerName(): Flow<String> =
        context.dataStore.data.map { p -> (p[maintainerNameKey] ?: "").trim() }

    suspend fun setMaintainerName(name: String) {
        context.dataStore.edit { it[maintainerNameKey] = name.trim() }
    }

    private fun newLibraryId(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

enum class ThemeMode { System, Light, Dark }
