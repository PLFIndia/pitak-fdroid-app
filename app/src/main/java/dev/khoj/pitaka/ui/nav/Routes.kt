package dev.khoj.pitaka.ui.nav

/**
 * Compose navigation route constants.
 *
 * Phase 1 routes:
 *   library                  — Library list (start)
 *   library/add              — Add Book (manual entry)
 *   library/{bookId}         — Book detail
 *
 * Phase 1 placeholders (so the bottom nav scaffold compiles end-to-end):
 *   wishlist                 — Phase 3
 *   loans                    — Phase 4 (vault-gated)
 *   settings                 — Phase 7
 */
object Routes {
    const val WELCOME = "welcome"
    const val LIBRARY = "library"
    const val LIBRARY_ADD = "library/add"
    const val LIBRARY_ADD_WITH_ISBN_PATTERN = "library/add?isbn={isbn}"
    fun libraryAddWithIsbn(isbn: String): String = "library/add?isbn=$isbn"
    const val LIBRARY_EDIT_PATTERN = "library/{bookId}/edit"
    fun libraryEdit(bookId: Long): String = "library/$bookId/edit"
    const val LIBRARY_DETAIL_PATTERN = "library/{bookId}"
    fun libraryDetail(bookId: Long) = "library/$bookId"

    const val SCANNER = "scanner?target={target}"
    fun scanner(target: ScanTarget = ScanTarget.Library): String = "scanner?target=${target.name}"

    const val WISHLIST = "wishlist"
    const val WISHLIST_ADD = "wishlist/add"
    const val WISHLIST_ADD_WITH_ISBN_PATTERN = "wishlist/add?isbn={isbn}"
    fun wishlistAddWithIsbn(isbn: String): String = "wishlist/add?isbn=$isbn"
    const val WISHLIST_EDIT_PATTERN = "wishlist/{bookId}/edit"
    fun wishlistEdit(bookId: Long): String = "wishlist/$bookId/edit"
    const val WISHLIST_DETAIL_PATTERN = "wishlist/{bookId}"
    fun wishlistDetail(bookId: Long) = "wishlist/$bookId"

    const val EXPORT = "io/export"
    const val IMPORT = "io/import"
    const val MERGE = "io/merge"
    const val MERGE_SCAN_QR = "io/merge/scan-qr"

    const val PENDING = "pending"
    const val BORROWER_PROFILE_PATTERN = "borrower/{bookId}"
    fun borrowerProfile(borrowerId: Long): String = "borrower/$borrowerId"
    const val LEND_PATTERN = "lend/{bookId}"
    fun lend(bookId: Long): String = "lend/$bookId"
    const val BACKUP_PASSPHRASE = "vault/backup-passphrase"

    const val PUBLISH = "publish"
    const val GH_AUTH = "publish/github-auth"
    const val CF_WIZARD = "publish/cloudflare-wizard"

    const val LOANS = "loans"
    // Settings is a top-level bottom-nav destination. An optional `tab` query arg
    // lets other screens deep-link to a specific tab (e.g. Pending's stale-backup
    // banner → Data tab, where Backup/Restore now live inline). The bare
    // `settings` route still works; the arg defaults to empty (Appearance).
    const val SETTINGS = "settings?tab={tab}"
    const val SETTINGS_BASE = "settings"
    fun settings(tab: String? = null): String =
        if (tab.isNullOrEmpty()) SETTINGS_BASE else "settings?tab=$tab"
    const val ARG_TAB = "tab"
    const val TAB_DATA = "data"

    const val BOOKMARKS = "bookmarks"

    const val ARG_BOOK_ID = "bookId"
    const val ARG_ISBN = "isbn"
    const val ARG_TARGET = "target"
}

/** Which catalog the scanner hand-off should populate. */
enum class ScanTarget { Library, Wishlist }
