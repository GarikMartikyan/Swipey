package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileTest {
    /** Every [record] below is written at this instant. */
    private val trashedAt = 1_000L

    /**
     * The earliest `now` at which a PENDING_TRASH row is old enough to be resolved — i.e.
     * the first instant outside the I2 grace window. The default for every test that isn't
     * about the window itself, so those tests read as "long after the dialog was answered"
     * and keep exercising the §8.1 table exactly as before.
     */
    private val aged = trashedAt + PENDING_TRASH_GRACE_MILLIS

    private fun record(id: Long, state: TrashState, name: String = "f$id.jpg", size: Long = 100) =
        LocalTrashRecord(id, false, name, size, trashedAt, state)

    private fun live(id: Long, trashed: Boolean, name: String = "f$id.jpg", size: Long = 100, expires: Long? = 9_999L) =
        LiveTrashRow(id, trashed, name, size, expires)

    private fun resolve(local: List<LocalTrashRecord>, live: List<LiveTrashRow>, now: Long = aged) =
        resolveRecords(local, live.associateBy { it.mediaId }, now)

    @Test fun pendingTrashThatIsTrashedIsPromoted() {
        val r = resolve(listOf(record(1, TrashState.PENDING_TRASH)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingTrashThatIsNotTrashedIsDropped() {
        val r = resolve(listOf(record(1, TrashState.PENDING_TRASH)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecord(1)), r)
    }

    @Test fun pendingRestoreThatIsStillTrashedRevertsToTrashed() {
        val r = resolve(listOf(record(1, TrashState.PENDING_RESTORE)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingRestoreThatIsUntrashedClearsBothRows() {
        val r = resolve(listOf(record(1, TrashState.PENDING_RESTORE)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    @Test fun trashedThatIsStillTrashedIsKept() {
        val r = resolve(listOf(record(1, TrashState.TRASHED)), listOf(live(1, true)))
        assertEquals(listOf(Resolution.Keep(1)), r)
    }

    /** Restored by Google Photos behind our back: it must become swipeable again. */
    @Test fun trashedThatWasRestoredElsewhereClearsBothRows() {
        val r = resolve(listOf(record(1, TrashState.TRASHED)), listOf(live(1, false)))
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    @Test fun absentRowIsVanishedRegardlessOfState() {
        TrashState.entries.forEach { state ->
            assertEquals(
                "state $state",
                listOf(Resolution.Vanished(1)),
                resolve(listOf(record(1, state)), emptyList()),
            )
        }
    }

    /**
     * MediaStore reuses ids after a rescan; a reused id points at a newly-scanned,
     * untrashed file. The guard requires BOTH name and size to disagree before treating
     * an untrashed row as a different file — the next two tests pin each single-column
     * case independently, since without them a `&&` -> `||` regression is caught by
     * nothing in this suite.
     */
    @Test fun idReuseWithBothDisplayNameAndSizeMismatchedIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "different.jpg", size = 999)),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun untrashedRowAgreeingOnSizeAloneIsNotTreatedAsIdReuse() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "different.jpg", size = 100)),
        )
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    @Test fun untrashedRowAgreeingOnNameAloneIsNotTreatedAsIdReuse() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "old.jpg", size = 999)),
        )
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    // --- Safety net: MediaProvider legitimately rewrites DISPLAY_NAME and/or SIZE on a
    // trashed row (trimFilename truncation past ~235 UTF-8 bytes, ensureUniqueFileColumns
    // de-dup suffixing, MIME-driven extension coercion, or a post-rename rescan correcting
    // a stale/zero SIZE). None of these may ever cause a still-trashed row to be discarded. ---

    @Test fun trashedRowWithRenamedDisplayNameIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg")),
            listOf(live(1, true, name = "old (1).jpg")),
        )
        assertEquals(listOf(Resolution.Keep(1)), r)
    }

    @Test fun trashedRowWithCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, size = 0)),
            listOf(live(1, true, size = 4_096)),
        )
        assertEquals(listOf(Resolution.Keep(1)), r)
    }

    @Test fun trashedRowWithRenamedDisplayNameAndCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 0)),
            listOf(live(1, true, name = "old (1).jpg", size = 4_096)),
        )
        assertEquals(listOf(Resolution.Keep(1)), r)
    }

    @Test fun pendingTrashRowWithRenamedDisplayNameIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH, name = "old.jpg")),
            listOf(live(1, true, name = "old (1).jpg")),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingTrashRowWithCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH, size = 0)),
            listOf(live(1, true, size = 4_096)),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingTrashRowWithRenamedDisplayNameAndCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH, name = "old.jpg", size = 0)),
            listOf(live(1, true, name = "old (1).jpg", size = 4_096)),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingRestoreRowWithRenamedDisplayNameIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_RESTORE, name = "old.jpg")),
            listOf(live(1, true, name = "old (1).jpg")),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingRestoreRowWithCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_RESTORE, size = 0)),
            listOf(live(1, true, size = 4_096)),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun pendingRestoreRowWithRenamedDisplayNameAndCorrectedSizeIsNotDiscarded() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_RESTORE, name = "old.jpg", size = 0)),
            listOf(live(1, true, name = "old (1).jpg", size = 4_096)),
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    @Test fun emptyInputProducesNoResolutions() {
        assertEquals(emptyList<Resolution>(), resolve(emptyList(), listOf(live(1, true))))
    }

    @Test fun binShowsOnlyStillTrashedEntriesWithExpiry() {
        val local = listOf(
            record(1, TrashState.TRASHED),
            record(2, TrashState.TRASHED),
            record(3, TrashState.TRASHED),
        )
        val liveRows = listOf(live(1, true, expires = 5_000L), live(2, false))
        val view = reconcileBin(local, liveRows.associateBy { it.mediaId }, aged)
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
        assertEquals(5_000L, view.entries[0].expiresAtSec)
        assertEquals(setOf(2L, 3L), view.vanished.toSet())
    }

    @Test fun binIsEmptyWhenNothingIsTrashed() {
        val view = reconcileBin(emptyList(), emptyMap(), aged)
        assertEquals(emptyList<BinEntry>(), view.entries)
        assertEquals(emptyList<Long>(), view.vanished)
    }

    @Test fun binIncludesPendingTrashThatIsConfirmedTrashed() {
        val view = reconcileBin(
            listOf(record(1, TrashState.PENDING_TRASH)),
            mapOf(1L to live(1, true)),
            aged,
        )
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
    }

    // --- I2: the PENDING_TRASH grace window. The §8.1 table reads PENDING_TRASH +
    // IS_TRASHED = 0 as "the user declined", but that is indistinguishable from "the
    // consent dialog is still on screen and hasn't been answered". Deleting the row in the
    // second case loses the only pointer to a file the user is about to trash: it lands in
    // the system trash with no Bin entry and no deck entry. Holding the row costs nothing —
    // reconcileBin shows it nowhere and trashedCount() counts only TRASHED. ---

    @Test fun freshPendingTrashWithAnUntrashedRowIsHeldNotDeleted() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH)),
            listOf(live(1, false)),
            now = trashedAt,
        )
        assertEquals(listOf(Resolution.AwaitingConsent(1)), r)
    }

    @Test fun pendingTrashIsStillHeldOneMillisecondBeforeTheGraceWindowCloses() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH)),
            listOf(live(1, false)),
            now = aged - 1,
        )
        assertEquals(listOf(Resolution.AwaitingConsent(1)), r)
    }

    @Test fun pendingTrashIsDroppedTheMomentTheGraceWindowCloses() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH)),
            listOf(live(1, false)),
            now = aged,
        )
        assertEquals(listOf(Resolution.DeleteRecord(1)), r)
    }

    /**
     * The window is a property of each row's own trashedAt, not of the pass — one stale
     * record must not keep a fresh one from being held, or vice versa.
     */
    @Test fun eachPendingTrashRowIsAgedAgainstItsOwnTrashedAt() {
        val fresh = LocalTrashRecord(1, false, "f1.jpg", 100, aged, TrashState.PENDING_TRASH)
        val old = record(2, TrashState.PENDING_TRASH)
        val r = resolve(listOf(fresh, old), listOf(live(1, false), live(2, false)), now = aged)
        assertEquals(listOf(Resolution.AwaitingConsent(1), Resolution.DeleteRecord(2)), r)
    }

    /**
     * The window must never delay the *good* outcome. A user who answers Allow promptly
     * gets their items into the Bin immediately, not after five minutes.
     */
    @Test fun graceWindowNeverDelaysPromotingAPendingRowThatIsAlreadyTrashed() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH)),
            listOf(live(1, true)),
            now = trashedAt,
        )
        assertEquals(listOf(Resolution.MarkTrashed(1)), r)
    }

    /**
     * PENDING_RESTORE needs no window: an unanswered restore dialog leaves the item still
     * trashed, so it takes the IS_TRASHED = 1 branch and is promoted, never deleted. It is
     * specifically "we asked to trash this and it still isn't trashed" that is ambiguous.
     */
    @Test fun graceWindowDoesNotApplyToPendingRestore() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_RESTORE)),
            listOf(live(1, false)),
            now = trashedAt,
        )
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    /** Nor to a TRASHED row restored elsewhere — that is a fact, not a pending question. */
    @Test fun graceWindowDoesNotApplyToTrashedState() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED)),
            listOf(live(1, false)),
            now = trashedAt,
        )
        assertEquals(listOf(Resolution.DeleteRecordAndReview(1)), r)
    }

    /**
     * The window is not blanket protection: it only softens the one ambiguous cell. An
     * absent row is still Vanished, and id reuse (untrashed, both name and size disagreeing)
     * is still caught, even for a row written moments ago.
     */
    @Test fun graceWindowDoesNotProtectAnAbsentRow() {
        val r = resolve(listOf(record(1, TrashState.PENDING_TRASH)), emptyList(), now = trashedAt)
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun graceWindowDoesNotProtectAFreshRowAgainstIdReuse() {
        val r = resolve(
            listOf(record(1, TrashState.PENDING_TRASH, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "different.jpg", size = 999)),
            now = trashedAt,
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    /**
     * A held row is invisible, not "gone". Counting it in `vanished` would put
     * "N items are no longer in the trash" on the Bin about an item the user may be one
     * tap away from putting there.
     */
    @Test fun binShowsAHeldPendingTrashRowInNeitherEntriesNorVanished() {
        val view = reconcileBin(
            listOf(record(1, TrashState.PENDING_TRASH)),
            mapOf(1L to live(1, false)),
            trashedAt,
        )
        assertEquals(emptyList<BinEntry>(), view.entries)
        assertEquals(emptyList<Long>(), view.vanished)
    }

    @Test fun binReportsAPendingTrashRowOnceItsGraceWindowHasClosed() {
        val view = reconcileBin(
            listOf(record(1, TrashState.PENDING_TRASH)),
            mapOf(1L to live(1, false)),
            aged,
        )
        assertEquals(emptyList<BinEntry>(), view.entries)
        assertEquals(listOf(1L), view.vanished)
    }
}
