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

    /** MediaStore reuses ids after a rescan; name+size mismatch means it is a different file. */
    @Test fun idReuseDetectedByNameMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, name = "old.jpg")),
            listOf(live(1, true, name = "different.jpg")),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
    }

    @Test fun idReuseDetectedBySizeMismatchIsVanished() {
        val r = resolve(
            listOf(record(1, TrashState.TRASHED, size = 100)),
            listOf(live(1, true, size = 999)),
        )
        assertEquals(listOf(Resolution.Vanished(1)), r)
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
