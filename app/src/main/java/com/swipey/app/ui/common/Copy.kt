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

    // -----------------------------------------------------------------------
    // Home
    // -----------------------------------------------------------------------

    const val HOME_ALL_MEDIA = "All media"

    /**
     * The hero's caption. "newest first" is a promise about what the next tap deals, so it
     * is only true while the hero *is* the newest item and the tap *does* sort newest —
     * both of which HomeViewModel and the hero's click handler guarantee. Change either
     * and this line has to change with it; the sort control in the hero's corner is what
     * keeps the other three orders reachable without making this sentence hedge.
     */
    fun homeAllMediaCaption(count: Int) = "${grouped(count)} items · newest first"

    /** The hero's corner control. Icon-only, so this is its whole accessible name. */
    const val HOME_SORT_ACTION = "Change the order"

    const val HOME_ALBUMS = "Albums"

    /** An album row or tile: how many, and how much. */
    fun albumSubtitle(count: Int, size: String) = "${grouped(count)} · $size"

    const val HOME_SHUFFLE = "Shuffle"

    /**
     * Beside the shuffle glyph.
     *
     * This used to read "Starts on this one" next to a thumbnail of the photograph the
     * shuffle would genuinely open on. The thumbnail is a glyph now, so there is no "this
     * one" to point at and the line has to describe the scope instead — which is what the
     * other two rows on this screen do anyway.
     */
    const val HOME_SHUFFLE_SUB = "Everything, in a random order"

    /**
     * The tile beside Shuffle: back to the queue you were last swiping, on the card after
     * the last one you decided.
     *
     * "Recent" rather than "Resume" or "Continue". Both of those describe a session being
     * restored, and nothing is — the marks are gone, the queue is dealt again from the
     * library as it stands now. What survives is a place, and "Recent" claims only that.
     */
    const val HOME_RECENT = "Recent"

    /**
     * The subtitle when a shuffle is what the user was last swiping.
     *
     * A shuffled queue has no album to name, and it is worth naming as a shuffle rather than
     * showing nothing: reopening it deals the same order, which is not what most people
     * expect the word to mean.
     */
    const val HOME_RECENT_SHUFFLE = "Shuffle, where you left it"

    /** Recent, before there is anything to be recent. The tile is drawn but not offered. */
    const val HOME_RECENT_NONE = "Nothing swiped yet"

    /** What the Recent tile does, for a screen reader — the tile itself only says "Recent". */
    const val HOME_RECENT_ACTION = "Carry on from where you stopped"


    /** The list/grid toggle. Names what the next tap does, since the glyph shows only where it goes. */
    const val HOME_SHOW_GRID = "Show albums as a grid"
    const val HOME_SHOW_LIST = "Show albums as a list"

    /** Nothing in the gallery at all. Stated plainly — this is not an error. */
    const val HOME_EMPTY = "Nothing to swipe yet"
    const val HOME_EMPTY_BODY = "Photos and videos on this phone will show up here."

    const val HOME_BIN = "Bin"
    fun homeBinSubtitle(count: Int) = "${grouped(count)} items"

    // --- the menu ---------------------------------------------------------------

    /**
     * The burger's accessible name.
     *
     * "Menu" rather than "Open the menu": a screen reader already announces it as a button,
     * and the app's other icon controls name the thing, not the gesture — see
     * [HOME_SORT_ACTION], which is the exception that names an outcome because "Sort" alone
     * would not say what changes.
     */
    const val HOME_MENU = "Menu"

    /** The drawer's heading. Names the app rather than the panel, which needs no name. */
    const val MENU_TITLE = APP_NAME

    /**
     * Settings. No subtitle any more: the row led with "Not built yet" for as long as it
     * led nowhere, and a row that works needs no note under it saying so.
     */
    const val MENU_SETTINGS = "Settings"

    // -----------------------------------------------------------------------
    // Settings
    // -----------------------------------------------------------------------

    const val SETTINGS_TITLE = "Settings"

    /**
     * ### Every row says what is true, not what it is called
     * A settings row conventionally names a thing and puts its value on the right: "Bin is
     * on the — Left". That is only readable to someone who already knows what left *does*,
     * which is everyone who built the app and nobody else. So each row here carries a
     * sentence instead — "Swipe left to bin, right to keep" — and the sentence is rewritten
     * whenever the setting changes. There is nothing to open in order to understand the
     * screen; opening a row is for *changing* it.
     *
     * That is also why the sheet describes both options rather than listing two words. The
     * moment a user is choosing between Left and Right is the moment they most need to be
     * told what each one means.
     */
    const val SETTINGS_APPEARANCE = "Appearance"
    const val SETTINGS_THEME_LIGHT = "Light"
    const val SETTINGS_THEME_DARK = "Dark"

    /** The row's line, and the sheet's. Names the palette, then the exception to it. */
    fun settingsThemeSays(dark: Boolean) =
        if (dark) "Dark. The deck stays dark either way" else "Light. The deck stays dark either way"

    const val SETTINGS_THEME_LIGHT_SUB = "Near-white pages, dark text"
    const val SETTINGS_THEME_DARK_SUB = "Near-black pages, light text"

    /**
     * Which side bins.
     *
     * "Swipe direction" rather than "Bin is on the", which was written for a row that
     * carried its value on the right and dangles without one. The line underneath names the
     * side, and names it as a gesture, which is how the setting is actually experienced.
     */
    const val SETTINGS_BIN_SIDE = "Swipe direction"
    const val SETTINGS_SIDE_LEFT = "Left"
    const val SETTINGS_SIDE_RIGHT = "Right"

    fun settingsBinSays(binOnLeft: Boolean) =
        if (binOnLeft) "Swipe left to bin, right to keep" else "Swipe right to bin, left to keep"

    const val SETTINGS_SIDE_LEFT_SUB = "Flick left to bin, right to keep"
    const val SETTINGS_SIDE_RIGHT_SUB = "Flick right to bin, left to keep"

    /**
     * Haptic feedback.
     *
     * "Haptic feedback" rather than "Vibration", which is what a phone calls the thing that
     * happens when it rings. This is a much smaller signal doing a specific job: the user is
     * looking at a photograph, not at the control they just used, so the confirmation that a
     * decision landed arrives through their thumb. The line underneath says so, because
     * "on" would otherwise be a setting about buzzing rather than about knowing.
     */
    const val SETTINGS_HAPTICS = "Haptic feedback"
    const val SETTINGS_HAPTICS_ON = "A tick at the point of no return, a knock when it lands"
    const val SETTINGS_HAPTICS_OFF = "The deck stays still under your thumb"
    const val SETTINGS_HAPTICS_ON_SUB = "Feel a decision land without looking away from the photo"
    const val SETTINGS_HAPTICS_OFF_SUB = "Nothing vibrates, whatever you swipe"

    /**
     * The video sound default.
     *
     * "Video sound" and not "Start videos with sound", because the row is a heading now and
     * the sentence under it does the describing. What that sentence has to keep saying is
     * that this is a *starting* value: muting a clip still carries to the clips after it,
     * exactly as it always has, and this only decides which way each run of the app begins.
     */
    const val SETTINGS_VIDEO_SOUND = "Video sound"
    const val SETTINGS_VIDEO_SOUND_ON = "Clips play aloud until you mute one"
    const val SETTINGS_VIDEO_SOUND_OFF = "Clips play silently until you unmute one"
    const val SETTINGS_ON = "On"
    const val SETTINGS_OFF = "Off"
    const val SETTINGS_ON_SUB = "Every clip arrives playing aloud"
    const val SETTINGS_OFF_SUB = "Every clip arrives silent"

    /**
     * Digit grouping for the counts Home shows, which run to five figures on a full phone.
     * Locale-aware, so "2,573" and "2 573" both come out right.
     */
    private fun grouped(count: Int): String =
        java.text.NumberFormat.getIntegerInstance().format(count)

    const val SORT_TITLE = "Sort by"
    const val SORT_NEWEST = "Newest first"
    const val SORT_OLDEST = "Oldest first"
    const val SORT_LARGEST = "Largest first"
    const val SORT_SMALLEST = "Smallest first"

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

    /**
     * The two lines that teach the gesture — and they have to be told which way round the
     * deck is dealing, now that Settings can flip it. A coach mark is the one piece of copy
     * in the app that must never be able to disagree with the control it describes: it is
     * read once, believed, and never shown again.
     */
    fun coachBin(binOnLeft: Boolean) = if (binOnLeft) "Swipe left to bin" else "Swipe right to bin"

    fun coachKeep(binOnLeft: Boolean) = if (binOnLeft) "Swipe right to keep" else "Swipe left to keep"
    const val COACH_REASSURE =
        "Binned items go to your phone's trash — nothing is deleted permanently, " +
            "and Undo takes back your last decision."
    const val COACH_DISMISS = "Got it"

    // -----------------------------------------------------------------------
    // Video cards
    // -----------------------------------------------------------------------

    /**
     * The sound toggle, which is a glyph now rather than a chip that spelled out its own
     * state. So these name the **action** — what the tap does — the way every other icon
     * button in the app does; a speaker with a cross through it already states the state,
     * and a control that announces "Muted" while offering to unmute reads backwards to a
     * screen reader.
     */
    const val VIDEO_MUTE = "Mute"
    const val VIDEO_UNMUTE = "Unmute"

    /** The tap action on a video: the whole surface is the play/pause control. */
    const val VIDEO_PLAY = "Play"
    const val VIDEO_PAUSE = "Pause"

    /**
     * The scrubber's accessible name. Its *value* is announced separately via
     * [videoElapsed], so the two are not baked into one string that a screen reader would
     * have to re-read in full on every frame of a drag.
     */
    const val VIDEO_TIMELINE = "Video timeline"

    /** What the scrubber currently reads, for a screen reader: "0:12 of 1:30". */
    fun videoElapsed(position: String, duration: String) = "$position of $duration"

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
    /**
     * Rule 7, as amended.
     *
     * It used to read "Swipey can't delete anything permanently", which was true for as
     * long as the Bin only knew how to put things back. The Bin now has a Delete, so the
     * sentence had to change with it — a note that reassures the user about a capability
     * the app has since gained is worse than no note at all. What is still true, and what
     * this says instead, is that the app cannot do it quietly or by itself: Android asks,
     * and nothing undoes it afterwards.
     */
    const val PERMANENT_DELETE_NOTE =
        "Deleting from the Bin is permanent. Android will ask you to confirm, and nothing can undo it afterwards."
    /** Rule 5. */
    const val RESTORE_CONFIRM_NOTE = "Android will ask you to confirm the restore."

    fun multipleConfirmations(count: Int) =
        "Android will ask you to confirm $count times, once per batch."

    fun expiresAtLeast(date: String) = "Recoverable until at least $date"

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

    /**
     * The deck's own way out, on both terminal states. Was RESULT_DONE, named after the
     * screen that used to follow a commit; that screen is gone and this is the only caller
     * left, so the name says what it is rather than where it used to live.
     */
    const val DONE = "Done"

    const val BIN_TITLE = "Bin"
    const val BIN_SELECT_ALL = "Select all"
    const val BIN_CLEAR = "Clear"
    const val BIN_DELETE = "Delete"
    /** Pairs with [binRestoreAction]: both count only once there is something to count. */
    fun binDeleteAction(count: Int) = "Delete $count"
    const val BIN_EMPTY = "Nothing here"
    const val BIN_RESTORE = "Restore"
    /** F8(b): was composed at the BinScreen call site ("${BIN_RESTORE} ${count}"). */
    fun binRestoreAction(count: Int) = "Restore $count"
    fun binOtherItems(count: Int) = "$count other items are in your phone's trash, put there by other apps."
    fun vanishedNotice(count: Int) = "$count items are no longer in the trash."

    /**
     * Rule 6: honest per-item outcome for a restore attempt, shown on the Bin itself.
     * A cancelled restore still reports its ids in RecoveryReport.confirmedTrashed — that
     * field means "currently trashed", not "just trashed by this action" — so a count read
     * straight off the report would tell the user their restore had moved items *to* the
     * trash. This sentence is derived from the resolutions instead.
     */
    fun restoreOutcome(restored: Int, attempted: Int) = "Restored $restored of $attempted items"

    /** Fix round 2, Critical 2: the restore-side equivalent of [COMMIT_FAILED]. */
    const val RESTORE_FAILED = "Couldn't restore those items. Nothing was changed — try again."

    /**
     * The delete-side equivalent of [restoreOutcome], and read the same way: [deleted] is
     * counted from what the reconciliation pass could no longer find, never from the
     * dialog's result code. A part-approved batch therefore reports the part that went.
     */
    fun deleteOutcome(deleted: Int, attempted: Int) = "Deleted $deleted of $attempted items"

    /** The delete-side equivalent of [RESTORE_FAILED]. */
    const val DELETE_FAILED = "Couldn't delete those items. Nothing was changed — try again."

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
     * Review's on-screen line.
     *
     * Rule 1 ("moved to trash", never "deleted") and rule 3: "about a month" is
     * hedged on purpose — the headline must not promise a hard deadline, and the
     * precise, per-item minimum is [expiresAtLeast] plus [TRASH_SIZE_NOTE] behind
     * the ⓘ. It also says nothing about space, so it cannot be read as rule 2's
     * "you got these bytes back".
     */
    const val TRASH_SHORT_NOTE = "Moved to trash · recoverable for about a month"

    /**
     * The Bin's on-screen line. Rules 1 and 7 in one breath — and it no longer promises
     * that nothing here gets deleted, because this screen now has a button that does
     * exactly that. Rules 4 and 5 — whose trash it is, and that Android asks again — sit
     * behind the ⓘ, alongside [PERMANENT_DELETE_NOTE].
     */
    const val BIN_SHORT_NOTE = "In your phone's trash · recoverable until you delete it"

    /** Label and accessible name for the ⓘ that opens the full disclosure. */
    const val DISCLOSURE_ACTION = "What this means"
    const val DISCLOSURE_TITLE = "What happens to your photos"
    const val DISCLOSURE_DISMISS = "Got it"

    /** Marks the active choice in the sort chooser. */
    const val SELECTED = "Selected"

    /** The tap action on a Review thumbnail — never "delete", it only unmarks. */
    const val REVIEW_UNMARK = "Remove from this list"

    /** The tap action on a Bin thumbnail, either way round. */
    const val BIN_SELECT = "Select"
    const val BIN_DESELECT = "Deselect"

    // --- deck: the crop, and seeing past it ------------------------------------

    /**
     * The preview control on the deck.
     *
     * The card crops to a fixed shape, which means a wide photograph is shown with its
     * edges held back — and the user is deciding whether to bin it. This names the way to
     * see the rest, so the crop is a presentation choice rather than something withheld.
     */
    const val DECK_PREVIEW = "See the whole photo"

    /** Dismisses the preview. Says what the tap does, not where it goes. */
    const val DECK_PREVIEW_CLOSE = "Close the preview"

    // --- what this photograph is ---------------------------------------------

    /**
     * The badge over the card, and the sheet behind it.
     *
     * The badge answers the two questions that actually change a keep-or-bin decision —
     * how much room it is costing and roughly when it is from — and nothing else. Everything
     * that is merely interesting is one tap away rather than on the photograph.
     */
    fun deckBadge(size: String, date: String) = "$size · $date"

    /** The badge's control. Names what opens, not the gesture that opens it. */
    const val DECK_INFO_ACTION = "Details"

    const val INFO_TITLE = "Details"
    const val INFO_NAME = "File"
    const val INFO_ALBUM = "Album"
    const val INFO_ADDED = "Added"
    const val INFO_SIZE = "Size"
    const val INFO_KIND = "Kind"
    const val INFO_DURATION = "Length"
    const val INFO_RESOLUTION = "Resolution"
    const val INFO_PATH = "Folder"

    const val INFO_KIND_PHOTO = "Photo"
    const val INFO_KIND_VIDEO = "Video"

    /**
     * Stated only when both dimensions are known — see `MediaItem.megapixels`.
     *
     * One decimal place, because the second is noise on a number nobody compares that
     * closely, and a bare "12 MP" would round a 12.9 down to the same string as an 11.5.
     */
    fun infoResolution(width: Int, height: Int, megapixels: Double?): String {
        val dimensions = "$width × $height"
        return if (megapixels == null) dimensions else "$dimensions · %.1f MP".format(megapixels)
    }

    /** Half a resolution is still worth stating; the sheet says which half it has. */
    fun infoWidthOnly(width: Int) = "$width px wide"
    fun infoHeightOnly(height: Int) = "$height px tall"

    /** The megapixel figure on its own, for the header's table. */
    fun infoMegapixels(megapixels: Double) = "%.1f MP".format(megapixels)

    /**
     * What a row says when MediaStore had nothing.
     *
     * An em dash rather than "Unknown" or an omitted row: the label is still true — this
     * item does have a resolution — and a blank in a spec sheet reads as "not recorded",
     * which is exactly the situation. Dropping the row instead would make two photographs
     * produce sheets of different heights for no reason the user can see.
     */
    // -----------------------------------------------------------------------
    // Share, and the carousel behind it
    // -----------------------------------------------------------------------

    /**
     * The two glyphs on the Details sheet's title line.
     *
     * They sit there rather than in the list because the list is seven rows of facts and
     * these are not facts. It costs the sheet no height at all — which is the trade: the
     * least discoverable placement, bought back by the hint underneath.
     */
    const val SHARE = "Share"
    const val VIEW_IN_GALLERY = "View in gallery"

    /** Says the long press exists. An icon cannot, and nothing else on the sheet would. */
    const val SHARE_HINT = "Hold to pick several"

    /** When nothing on the phone can open a picture, or handle a share. */
    const val NO_APP_FOR_VIEW = "No app on this phone opens photos."
    const val NO_APP_FOR_SHARE = "No app on this phone can share photos."

    const val PICK_TITLE = "Choose photos"
    const val PICK_NONE = "Nothing selected yet"
    fun pickSelected(count: Int) = "$count selected"
    fun pickShare(count: Int) = if (count == 0) SHARE else "Share $count"
    fun pickPosition(index: Int, total: Int) = "${grouped(index)} of ${grouped(total)}"

    /** The selected-only grid, reached from the count pill. */
    const val PICK_GRID_TITLE = "Selected"
    const val PICK_GRID_EMPTY = "Nothing selected yet"
    const val PICK_GRID_EMPTY_BODY = "Close this and tick a few photos."

    /** Per-card controls in the carousel. Icon-only, so these are their whole names. */
    const val PICK_SELECT = "Select this photo"
    const val PICK_DESELECT = "Deselect this photo"
    const val PICK_PREVIEW = "See it full screen"
    const val PICK_CLOSE = "Close"

    const val INFO_UNKNOWN = "—"

    /**
     * How long ago, in words.
     *
     * Sits beside the thumbnail at the top of the sheet, where the question is "roughly when"
     * and an exact timestamp is more precision than the eye wants. The precise date is still
     * three rows below, so nothing is lost by rounding here — see [com.swipey.app.domain.relativeAge]
     * for why none of these phrases can come out with a "1" in them.
     */
    fun infoAge(age: com.swipey.app.domain.RelativeAge): String = when (age.bucket) {
        com.swipey.app.domain.AgeBucket.TODAY -> "Today"
        com.swipey.app.domain.AgeBucket.YESTERDAY -> "Yesterday"
        com.swipey.app.domain.AgeBucket.DAYS -> "${age.count} days ago"
        com.swipey.app.domain.AgeBucket.LAST_WEEK -> "Last week"
        com.swipey.app.domain.AgeBucket.WEEKS -> "${age.count} weeks ago"
        com.swipey.app.domain.AgeBucket.MONTHS -> "${age.count} months ago"
        com.swipey.app.domain.AgeBucket.LAST_YEAR -> "Last year"
        com.swipey.app.domain.AgeBucket.YEARS -> "${age.count} years ago"
    }


    // The filmstrip's tail count lived here. It went when the strip was centred: the strip
    // now runs the session through the middle of the screen rather than starting at the
    // gutter, and the room the count occupied is what pays for the decided half. Nothing
    // was lost with it — the progress rule and DECK counter above the card already say how
    // far through the session the user is, without asking them to add anything up.


    // --- the session grid ------------------------------------------------------

    /**
     * The grid's title.
     *
     * "In this session" rather than "All photos": the grid lists the queue the deck is
     * serving, which for an album or a shuffle is not the whole gallery. Calling it "all"
     * would be wrong in exactly the cases where a user most needs to know what they are
     * looking at.
     */
    const val GRID_TITLE = "Everything in this session"

    /** Closes the grid. "Done" rather than "Close" — nothing here needs saving. */
    const val GRID_DONE = "Done"

    /**
     * What tapping a photograph in the grid does — the screen reader's half of the split
     * between the tick box and the picture.
     *
     * "Start here" rather than "Open": it does not show the photograph, it makes it the card
     * being judged and carries the session on from there. A user who heard "Open" and
     * expected a viewer would find they had moved their place in the queue instead.
     */
    const val GRID_OPEN = "Start here"

    /**
     * The card the deck is on, announced as the cell's state.
     *
     * The grid draws a ring around it, and a ring is the one cue that cannot be read out.
     * Stated as a state rather than folded into the label so it is announced *and* the cell
     * keeps the same "Start here" action every other cell has.
     */
    const val GRID_CURRENT = "Current card"

    const val GRID_TODAY = "Today"
    const val GRID_YESTERDAY = "Yesterday"

    /**
     * The grid's count line.
     *
     * States the marked figure even at zero. On the screen where marking happens, "nothing
     * marked" is a fact worth having; a subtitle that appeared only once you had marked
     * something would leave a new user wondering what the screen was for.
     *
     * Never says anything about space freed — moving to the trash frees nothing until the
     * trash is emptied (spec §9, rule 2), so the size here is described as marked, not as
     * saved.
     */
    fun gridSubtitle(total: Int, marked: Int, size: String): String =
        if (marked == 0) {
            "${grouped(total)} items · nothing marked"
        } else {
            "${grouped(total)} items · ${grouped(marked)} marked · $size"
        }

}
