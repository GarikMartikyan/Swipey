package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumeAnchorTest {

    private fun item(id: Long, size: Long, date: Long) =
        MediaItem(id, false, size, date, null, 1L, "Camera", "f$id.jpg")

    /** Five photographs, newest first: 5, 4, 3, 2, 1. */
    private val library = (1L..5L).map { item(it, size = it * 10, date = it * 100) }

    private fun place(of: MediaItem) = QueuePlace(of.id, of.dateAddedSec, of.sizeBytes)

    // -- the bookmark survived ------------------------------------------------

    @Test fun aLivingBookmarkIsItsOwnAnchor() {
        val anchor = library.resumeAnchor(place(library[2]), SortMode.NEWEST)
        assertEquals(3L, anchor?.item?.id)
        assertEquals(3L, anchor?.after?.id)
    }

    // -- the bookmark was deleted: forward first ------------------------------

    @Test fun aDeletedBookmarkOffersTheCardThatFollowedIt() {
        // NEWEST: 5, 4, 3, 2, 1. Delete 3 — the card after it was 2, the one before was 4.
        val remaining = library.filterNot { it.id == 3L }
        val anchor = remaining.resumeAnchor(place(library[2]), SortMode.NEWEST)
        assertEquals(2L, anchor?.item?.id)
        assertEquals(4L, anchor?.after?.id)
    }

    @Test fun theForwardCardIsTheNearestSurvivorNotJustTheNextId() {
        // A whole run committed at once: 4, 3 and 2 are gone, and the bookmark was 3.
        val remaining = library.filterNot { it.id in setOf(2L, 3L, 4L) }
        val anchor = remaining.resumeAnchor(place(library[2]), SortMode.NEWEST)
        assertEquals(1L, anchor?.item?.id)
        assertEquals(5L, anchor?.after?.id)
    }

    // -- nothing ahead: fall back to what came before -------------------------

    @Test fun aDeletedLastCardOffersTheOneBeforeIt() {
        // NEWEST: 1 is last. Nothing follows it, so the offer is 2 — and dealing after 2
        // reopens the queue exhausted, which is the truth about a queue finished last time.
        val remaining = library.filterNot { it.id == 1L }
        val anchor = remaining.resumeAnchor(place(library[0]), SortMode.NEWEST)
        assertEquals(2L, anchor?.item?.id)
        assertEquals(2L, anchor?.after?.id)
    }

    @Test fun aDeletedTailFallsBackPastEveryOtherDeletion() {
        val remaining = library.filterNot { it.id in setOf(1L, 2L, 3L) }
        val anchor = remaining.resumeAnchor(place(library[0]), SortMode.NEWEST)
        assertEquals(4L, anchor?.item?.id)
        assertEquals(4L, anchor?.after?.id)
    }

    // -- nothing behind: deal from the top ------------------------------------

    @Test fun aDeletedFirstCardHasNothingToDealAfter() {
        val remaining = library.filterNot { it.id == 5L }
        val anchor = remaining.resumeAnchor(place(library[4]), SortMode.NEWEST)
        assertEquals(4L, anchor?.item?.id)
        assertNull(anchor?.after)
    }

    // -- the order is the queue's, not the gallery's --------------------------

    @Test fun oldestFirstRunsTheOtherWay() {
        // OLDEST: 1, 2, 3, 4, 5. The card after 3 is 4, not 2.
        val remaining = library.filterNot { it.id == 3L }
        val anchor = remaining.resumeAnchor(place(library[2]), SortMode.OLDEST)
        assertEquals(4L, anchor?.item?.id)
        assertEquals(2L, anchor?.after?.id)
    }

    @Test fun largestFirstFollowsSizeNotDate() {
        val sized = listOf(item(1, 50, 900), item(2, 30, 100), item(3, 10, 500))
        val anchor = sized.filterNot { it.id == 2L }.resumeAnchor(place(sized[1]), SortMode.LARGEST)
        assertEquals(3L, anchor?.item?.id)
        assertEquals(1L, anchor?.after?.id)
    }

    @Test fun smallestFirstFollowsSizeUpwards() {
        val sized = listOf(item(1, 50, 900), item(2, 30, 100), item(3, 10, 500))
        val anchor = sized.filterNot { it.id == 2L }.resumeAnchor(place(sized[1]), SortMode.SMALLEST)
        assertEquals(1L, anchor?.item?.id)
        assertEquals(3L, anchor?.after?.id)
    }

    @Test fun aTiedTimestampIsBrokenByIdSoTheAnswerIsStable() {
        val tied = (1L..3L).map { item(it, size = it, date = 100L) }
        val anchor = tied.filterNot { it.id == 2L }.resumeAnchor(place(tied[1]), SortMode.NEWEST)
        assertEquals(1L, anchor?.item?.id)
        assertEquals(3L, anchor?.after?.id)
    }

    // -- nothing at all -------------------------------------------------------

    @Test fun anEmptyQueueIsNoOffer() {
        assertNull(emptyList<MediaItem>().resumeAnchor(place(library[2]), SortMode.NEWEST))
    }
}
