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
    fun homeBinSubtitle(count: Int) = "$count items"

    const val SORT_TITLE = "Sort by"
    const val SORT_NEWEST = "Newest first"
    const val SORT_OLDEST = "Oldest first"
    const val SORT_LARGEST = "Largest first"
    const val SORT_SMALLEST = "Smallest first"

    const val ALBUMS_EMPTY = "No albums found"

    /**
     * Whole-branch review, I4 (spec §12: "empty state with retry; never crash"). One
     * neutral message for every read that throws — the user can't act on the difference
     * between an IllegalArgumentException and a SecurityException, and the honest thing to
     * say is that nothing was read, not that anything was lost. Nothing is: a failed read
     * writes nothing.
     */
    const val LOAD_FAILED = "Couldn't read your photos just now."
    const val BIN_LOAD_FAILED = "Couldn't read your bin just now."
    const val RETRY = "Try again"

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
    // Fix round 2, Important 4: this is now wired to the permanently-denied state,
    // where Android will no longer show a permission prompt at all — re-launching the
    // request would silently do nothing, so the only working action is Settings.
    // "Try again" would mislabel that; kept alongside PARTIAL_ACTION's identical wording
    // since both open the same screen for the same reason.
    const val DENIED_ACTION = "Open settings"

    const val DECK_EMPTY_TITLE = "Nothing left to review"
    const val DECK_EMPTY_BODY = "You've been through everything here."
    const val DECK_NOTHING_MARKED = "Nothing marked — all caught up"
    const val DECK_BACK_CONFIRM = "Discard the items you've marked?"
    const val DECK_DISCARD = "Discard"
    const val DECK_REVIEW = "Review"

    // -----------------------------------------------------------------------
    // The deck's chrome
    // -----------------------------------------------------------------------

    /**
     * The three controls under the card. Each is the button equivalent of a gesture, and
     * each is icon-only, so these strings are the *only* thing a screen reader has to go
     * on — they name the decision, not the glyph.
     *
     * "item", not "photo": the deck shows videos too, and a label that is wrong a third
     * of the time is worse than one that is slightly less specific.
     */
    const val DECK_BIN_ACTION = "Bin this item"
    const val DECK_KEEP_ACTION = "Keep this item"
    const val DECK_UNDO_ACTION = "Undo the last decision"

    /** The visible Undo label, where there is room for one (the all-caught-up state). */
    const val DECK_UNDO = "Undo"

    /**
     * The deck's position counter. [shown] is the card's ordinal — the 1-based place of
     * the photo on screen, not the count of decisions already made — so the first card
     * reads "1 / 318" rather than "0 / 318".
     */
    fun deckCounter(shown: Int, total: Int) = "$shown / $total"

    /** Rule 1: *marked*, never "deleted" — nothing has moved anywhere yet. */
    fun deckMarked(count: Int, size: String) = "$count marked · $size"

    // -----------------------------------------------------------------------
    // First-run coach marks
    // -----------------------------------------------------------------------
    //
    // Shown once, over the very first card, then never again. The two directions are
    // the whole mechanic; the third line is the reassurance, and it is the reason a
    // first-time user is willing to try the gesture at all. Rules 1, 4 and 7 in one
    // sentence: where things go, whose trash it is, and that nothing is destroyed.

    const val COACH_TITLE = "Two ways to decide"
    const val COACH_SWIPE_LEFT = "Swipe left to bin"
    const val COACH_SWIPE_RIGHT = "Swipe right to keep"
    const val COACH_REASSURE =
        "Binned items go to your phone's trash — nothing is deleted permanently, " +
            "and Undo takes back your last decision."
    const val COACH_DISMISS = "Got it"

    // -----------------------------------------------------------------------
    // Video cards
    // -----------------------------------------------------------------------

    /**
     * The sound toggle's label. It states the *current* state rather than the action,
     * because it sits on screen while a video plays and has to be readable at a glance;
     * the control's role is announced alongside it.
     */
    const val VIDEO_MUTED = "Muted"
    const val VIDEO_SOUND_ON = "Sound on"

    /** The tap action on a video: the whole surface is the play/pause control. */
    const val VIDEO_PLAY = "Play"
    const val VIDEO_PAUSE = "Pause"

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

    /**
     * Fix round 2, Critical 2: shown when the commit path itself throws (e.g. before
     * `TrashLauncher.start()` is ever reached) rather than reporting a dialog outcome.
     * Distinct from `cancelled()`, which describes a partially-declined *dialog*
     * result — this is "nothing happened, nothing was trashed," never "some items were."
     */
    const val COMMIT_FAILED = "Couldn't move those items to trash. Nothing was changed — try again."

    /**
     * Whole-branch review, I1. Spec §8.2: "*Cancellation (RESULT_CANCELED) leaves marks
     * intact and returns to Review with a snackbar.*" Shown on Review itself, so the marks
     * the sentence promises are still there are visible right behind it.
     *
     * Worded to be true of every path that reaches it, not just a "Don't allow" tap: a
     * declined dialog, a dialog backed out of, and a verification query that threw all
     * leave exactly this state — nothing trashed, marks untouched. Rule 1 wording ("moved
     * to trash", never "deleted"); it claims nothing about what MediaStore did, only what
     * it did not do.
     */
    const val COMMIT_CANCELLED = "Nothing was moved to trash — your marked items are still here."

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

    /** Fix round 2, Critical 2: the restore-side equivalent of [COMMIT_FAILED]. */
    const val RESTORE_FAILED = "Couldn't restore those items. Nothing was changed — try again."

    // -----------------------------------------------------------------------
    // Progressive disclosure
    // -----------------------------------------------------------------------
    //
    // The seven rules above are binding, but stacking them as four caveat
    // paragraphs under every screen was the least readable thing in the app.
    // The fix is disclosure, not deletion: one plain line on screen, the full
    // text one tap away in a sheet. Nothing here replaces a note above — the
    // long forms are all still shown verbatim, via `ui/common/DisclosureSheet.kt`,
    // which is the single place that decides which notes a screen carries.

    /**
     * Review's and Result's on-screen line.
     *
     * Rule 1 ("moved to trash", never "deleted") and rule 3: "about a month" is
     * hedged on purpose — the headline must not promise a hard deadline, and the
     * precise, per-item minimum is [expiresAtLeast] plus [TRASH_SIZE_NOTE] behind
     * the ⓘ. It also says nothing about space, so it cannot be read as rule 2's
     * "you got these bytes back".
     */
    const val TRASH_SHORT_NOTE = "Moved to trash · recoverable for about a month"

    /**
     * The Bin's on-screen line. Rules 1 and 7 in one breath; rules 4 and 5 —
     * whose trash it is, and that Android asks again — sit behind the ⓘ.
     */
    const val BIN_SHORT_NOTE = "In your phone's trash · nothing here is deleted"

    /** Label and accessible name for the ⓘ that opens the full disclosure. */
    const val DISCLOSURE_ACTION = "What this means"
    const val DISCLOSURE_TITLE = "What happens to your photos"
    const val DISCLOSURE_DISMISS = "Got it"

    /**
     * Result's outcome line. Pairs with the large numeral above it; the audited
     * sentence [resultTitle] is what a screen reader is given for the pair, so
     * the count is never announced twice. See `ResultScreen`.
     */
    const val RESULT_OUTCOME_LINE = "items moved to trash"

    /** Marks the active choice in the sort chooser. */
    const val SELECTED = "Selected"

    /** The tap action on a Review thumbnail — never "delete", it only unmarks. */
    const val REVIEW_UNMARK = "Remove from this list"

    /** The tap action on a Bin thumbnail, either way round. */
    const val BIN_SELECT = "Select"
    const val BIN_DESELECT = "Deselect"
}
