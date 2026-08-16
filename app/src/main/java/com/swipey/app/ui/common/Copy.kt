package com.swipey.app.ui.common

/**
 * All user-facing copy lives here so the spec §9 rules can be audited in one place.
 *
 * Rules, binding:
 *  1. "moved to trash", never "deleted"
 *  2. never claim space was freed — trashing frees zero bytes
 *  3. expiry is a minimum: "Recoverable until at least ..."
 *  4. this is the SYSTEM trash, shared with Google Photos / Files
 *  5. restoring shows a second system confirmation
 *  6. per-item outcomes come from the IS_TRASHED re-check
 *  7. Swipey has no permanent-delete function
 */
object Copy {
    const val APP_NAME = "Swipey"

    const val HOME_ALL_MEDIA = "All media"
    const val HOME_ALL_MEDIA_SUB = "Everything, in the order you choose"
    const val HOME_ALBUMS = "Albums"
    const val HOME_ALBUMS_SUB = "Pick a folder to clean up"
    const val HOME_SHUFFLE = "Shuffle"
    const val HOME_SHUFFLE_SUB = "Random order"
    const val HOME_BIN = "Bin"

    const val PERMISSION_TITLE = "Swipey needs access to your photos and videos"
    const val PERMISSION_BODY =
        "Swipey shows your photos one at a time so you can keep or bin them. " +
            "It never deletes anything permanently."
    const val PERMISSION_GRANT = "Grant access"

    const val PARTIAL_TITLE = "Swipey needs access to all photos"
    const val PARTIAL_BODY =
        "With only selected photos shared, Swipey can't show you the bin, " +
            "so it can't promise that what you remove is recoverable. " +
            "Please allow access to all photos and videos."
    const val PARTIAL_ACTION = "Open settings"

    const val DENIED_TITLE = "Access denied"
    const val DENIED_BODY = "Swipey can't do anything without access to your gallery."
    const val DENIED_ACTION = "Try again"

    const val DECK_EMPTY_TITLE = "Nothing left to review"
    const val DECK_EMPTY_BODY = "You've been through everything here."
    const val DECK_NOTHING_MARKED = "Nothing marked — all caught up"
    const val DECK_BACK_CONFIRM = "Discard the items you've marked?"
    const val DECK_DISCARD = "Discard"
    const val DECK_REVIEW = "Review"

    const val REVIEW_TITLE = "Review"
    const val REVIEW_EMPTY = "Nothing marked yet"
    fun reviewHeader(count: Int, size: String) = "$count items · $size"
    fun reviewAction(count: Int) = "Move $count items to trash"

    /**
     * Rule 2: this says where the bytes went, not that they came back.
     * Rule 3 (F5): deliberately states no timeframe. MediaProvider's expiry is
     * per-item and the trash can be emptied sooner (by the user, Google Photos, or
     * Files — see SYSTEM_TRASH_NOTE) or later than any fixed number promised here;
     * expiresAtLeast() carries the honest, per-item minimum instead.
     */
    const val TRASH_SIZE_NOTE = "Space is freed when the trash is emptied, not now."
    /** Rule 4. */
    const val SYSTEM_TRASH_NOTE =
        "This is your phone's trash, shared with Google Photos and Files. " +
            "If you empty it there, these items are gone from Swipey's bin too."
    /** Rule 7. */
    const val NO_PERMANENT_DELETE_NOTE = "Swipey can't delete anything permanently."
    /** Rule 5. */
    const val RESTORE_CONFIRM_NOTE = "Android will ask you to confirm the restore."

    fun multipleConfirmations(count: Int) =
        "Android will ask you to confirm $count times, once per batch."

    fun resultTitle(count: Int) = "$count items moved to trash"
    fun expiresAtLeast(date: String) = "Recoverable until at least $date"
    /** F6: [total] must be every id the commit attempted — confirmed + declined + vanished. */
    fun cancelled(done: Int, total: Int) = "Stopped after $done of $total items"

    // F8(c): distinct from BIN_TITLE — on ResultScreen, right after items were just binned,
    // a bare "Bin" label reads ambiguously as the verb (throw away) rather than the
    // navigate-to-Bin destination it actually is.
    const val RESULT_VIEW_BIN = "View Bin"
    // F8(b): was hardcoded at the ResultScreen call site, breaking this file's own
    // invariant that all user-facing copy lives here.
    const val RESULT_DONE = "Done"

    const val BIN_TITLE = "Bin"
    const val BIN_EMPTY = "Nothing here"
    const val BIN_RESTORE = "Restore"
    /** F8(b): was composed at the BinScreen call site ("${BIN_RESTORE} ${count}"). */
    fun binRestoreAction(count: Int) = "Restore $count"
    fun binOtherItems(count: Int) = "$count other items are in your phone's trash, put there by other apps."
    fun vanishedNotice(count: Int) = "$count items are no longer in the trash."

    /**
     * Rule 6: honest per-item outcome for a restore attempt, shown on the Bin itself.
     * Restore never routes through Result — a cancelled restore still reports its ids
     * in RecoveryReport.confirmedTrashed (that field means "currently trashed", not
     * "just trashed by this action"), so surfacing it via resultTitle()/expiresAtLeast()
     * would falsely tell the user their restore attempt moved items to trash.
     */
    fun restoreOutcome(restored: Int, attempted: Int) = "Restored $restored of $attempted items"
}
