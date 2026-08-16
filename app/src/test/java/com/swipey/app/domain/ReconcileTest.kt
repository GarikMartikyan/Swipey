package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ReconcileTest {
    private fun record(id: Long, state: TrashState, name: String = "f$id.jpg", size: Long = 100) =
        LocalTrashRecord(id, false, name, size, 1_000L, state)

    private fun live(id: Long, trashed: Boolean, name: String = "f$id.jpg", size: Long = 100, expires: Long? = 9_999L) =
        LiveTrashRow(id, trashed, name, size, expires)

    private fun resolve(local: List<LocalTrashRecord>, live: List<LiveTrashRow>) =
        resolveRecords(local, live.associateBy { it.mediaId })

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
     * untrashed file, so both name AND size disagreeing on an untrashed row means
     * it is a different file.
     */
    @Test fun idReuseDetectedByNameMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "different.jpg", size = 999)),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun idReuseDetectedBySizeMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg", size = 100)),
            listOf(live(1, false, name = "different.jpg", size = 999)),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
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
        val view = reconcileBin(local, liveRows.associateBy { it.mediaId })
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
        assertEquals(5_000L, view.entries[0].expiresAtSec)
        assertEquals(setOf(2L, 3L), view.vanished.toSet())
    }

    @Test fun binIsEmptyWhenNothingIsTrashed() {
        val view = reconcileBin(emptyList(), emptyMap())
        assertEquals(emptyList<BinEntry>(), view.entries)
        assertEquals(emptyList<Long>(), view.vanished)
    }

    @Test fun binIncludesPendingTrashThatIsConfirmedTrashed() {
        val view = reconcileBin(
            listOf(record(1, TrashState.PENDING_TRASH)),
            mapOf(1L to live(1, true)),
        )
        assertEquals(listOf(1L), view.entries.map { it.record.mediaId })
    }
}
