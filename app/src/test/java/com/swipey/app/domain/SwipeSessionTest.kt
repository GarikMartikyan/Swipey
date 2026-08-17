package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeSessionTest {
    private fun item(id: Long, size: Long = 100) =
        MediaItem(id, false, size, id, null, 1, "Camera", "f$id.jpg")

    private fun session(count: Int = 3) = SwipeSession((1L..count).map { item(it) })

    @Test fun startsAtFirstItem() {
        val s = session()
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.position)
        assertEquals(3, s.total)
        assertFalse(s.isExhausted)
    }

    @Test fun emptyQueueIsImmediatelyExhausted() {
        val s = SwipeSession(emptyList())
        assertNull(s.current)
        assertTrue(s.isExhausted)
        assertEquals(0, s.markedCount)
    }

    @Test fun swipeLeftMarksAndAdvances() {
        val s = session()
        s.swipeLeft()
        assertEquals(1, s.markedCount)
        assertEquals(100L, s.markedBytes)
        assertEquals(2L, s.current?.id)
    }

    @Test fun swipeRightKeepsAndAdvances() {
        val s = session()
        s.swipeRight()
        assertEquals(0, s.markedCount)
        assertEquals(2L, s.current?.id)
    }

    @Test fun markedBytesSumsOnlyMarkedItems() {
        val s = SwipeSession(listOf(item(1, 100), item(2, 250), item(3, 400)))
        s.swipeLeft()
        s.swipeRight()
        s.swipeLeft()
        assertEquals(2, s.markedCount)
        assertEquals(500L, s.markedBytes)
    }

    @Test fun exhaustsAfterLastItem() {
        val s = session(2)
        s.swipeRight()
        s.swipeRight()
        assertTrue(s.isExhausted)
        assertNull(s.current)
    }

    @Test fun swipingPastEndIsANoOp() {
        val s = session(1)
        s.swipeLeft()
        s.swipeLeft()
        assertEquals(1, s.markedCount)
        assertTrue(s.isExhausted)
    }

    @Test fun undoRestoresPreviousItemAndDecision() {
        val s = session()
        s.swipeLeft()
        val undone = s.undo()
        assertEquals(1L, undone?.item?.id)
        assertEquals(Decision.MARK, undone?.previousDecision)
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.markedCount)
    }

    @Test fun undoOfKeepDoesNotChangeMarkedCount() {
        val s = session()
        s.swipeRight()
        val undone = s.undo()
        assertEquals(Decision.KEEP, undone?.previousDecision)
        assertEquals(0, s.markedCount)
        assertEquals(1L, s.current?.id)
    }

    @Test fun undoAtStartReturnsNull() {
        assertNull(session().undo())
    }

    @Test fun undoWorksFromExhaustedState() {
        val s = session(1)
        s.swipeLeft()
        assertTrue(s.isExhausted)
        s.undo()
        assertFalse(s.isExhausted)
        assertEquals(1L, s.current?.id)
        assertEquals(0, s.markedCount)
    }

    @Test fun undoStacksAcrossMultipleDecisions() {
        val s = session()
        s.swipeLeft(); s.swipeLeft(); s.swipeRight()
        s.undo(); s.undo()
        assertEquals(1, s.markedCount)
        assertEquals(2L, s.current?.id)
    }

    @Test fun unmarkRemovesFromMarkedSetWithoutMovingPosition() {
        val s = session()
        s.swipeLeft()
        s.swipeLeft()
        val positionBefore = s.position
        s.unmark(1L)
        assertEquals(1, s.markedCount)
        assertEquals(positionBefore, s.position)
        assertEquals(listOf(2L), s.marked().map { it.id })
    }

    @Test fun unmarkOfUnknownIdIsHarmless() {
        val s = session()
        s.swipeLeft()
        s.unmark(999L)
        assertEquals(1, s.markedCount)
    }

    @Test fun markedPreservesSwipeOrder() {
        val s = SwipeSession(listOf(item(3), item(1), item(2)))
        s.swipeLeft(); s.swipeLeft(); s.swipeLeft()
        assertEquals(listOf(3L, 1L, 2L), s.marked().map { it.id })
    }

    @Test fun undoAfterUnmarkDoesNotResurrectTheMark() {
        val s = session()
        s.swipeLeft()
        s.unmark(1L)
        val undone = s.undo()
        assertEquals(1L, undone?.item?.id)
        assertEquals(0, s.markedCount)
    }

    // --- peek: what the deck draws underneath the card being swiped ---

    @Test fun peekLooksAheadOfCurrent() {
        val s = session()
        assertEquals(1L, s.peek(0)?.id)
        assertEquals(2L, s.peek(1)?.id)
        assertEquals(3L, s.peek(2)?.id)
    }

    @Test fun peekTracksPositionAsTheDeckAdvances() {
        val s = session()
        s.swipeRight()
        assertEquals(2L, s.current?.id)
        assertEquals(3L, s.peek(1)?.id)
    }

    @Test fun peekIsNullOnTheLastCard() {
        val s = session()
        s.swipeRight()
        s.swipeRight()
        assertEquals(3L, s.current?.id)
        // Nothing behind the last photograph, so the deck draws no under-card.
        assertNull(s.peek(1))
    }

    @Test fun peekIsNullPastBothEndsRatherThanThrowing() {
        val s = session()
        assertNull(s.peek(3))
        assertNull(s.peek(99))
        // Negative offsets are as out of bounds as any other: peek looks forward only.
        assertNull(s.peek(-1))
    }

    @Test fun peekIsNullOnAnExhaustedDeck() {
        val s = session(1)
        s.swipeLeft()
        assertTrue(s.isExhausted)
        assertNull(s.peek(0))
        assertNull(s.peek(1))
    }

    @Test fun peekFollowsAnUndoBackwards() {
        val s = session()
        s.swipeRight()
        s.swipeRight()
        s.undo()
        assertEquals(2L, s.current?.id)
        assertEquals(3L, s.peek(1)?.id)
    }

    // --- setMarked: the grid's edit, which must not move the deck ---

    @Test fun setMarkedMarksWithoutAdvancing() {
        val s = session()
        s.setMarked(3L, true)
        assertEquals("marking must not move the deck", 0, s.position)
        assertEquals(1L, s.current?.id)
        assertEquals(1, s.markedCount)
        assertTrue(3L in s.markedIds)
    }

    @Test fun setMarkedUnmarksWithoutRewinding() {
        val s = session()
        s.swipeLeft()
        assertEquals(1, s.position)
        s.setMarked(1L, false)
        assertEquals("unmarking must not rewind the deck", 1, s.position)
        assertEquals(0, s.markedCount)
    }

    @Test fun setMarkedIgnoresIdsOutsideTheQueue() {
        val s = session()
        s.setMarked(999L, true)
        assertEquals(0, s.markedCount)
    }

    @Test fun setMarkedIsIdempotent() {
        val s = session()
        s.setMarked(2L, true)
        s.setMarked(2L, true)
        assertEquals(1, s.markedCount)
        s.setMarked(2L, false)
        s.setMarked(2L, false)
        assertEquals(0, s.markedCount)
    }

    /**
     * The bug the grid would otherwise introduce: an item marked ahead of the deck, then
     * kept when the deck reaches it, would stay marked — recording the opposite of the
     * decision the user just made.
     */
    @Test fun keepingClearsAMarkMadeFromTheGrid() {
        val s = session()
        s.setMarked(1L, true)
        assertEquals(1, s.markedCount)
        s.swipeRight()
        assertEquals("a right-swipe means keep, whatever the grid said earlier", 0, s.markedCount)
    }

    @Test fun markedIdsIsASnapshotNotALiveView() {
        val s = session()
        s.setMarked(1L, true)
        val before = s.markedIds
        s.setMarked(2L, true)
        assertEquals("the earlier snapshot must not have grown", 1, before.size)
        assertEquals(2, s.markedIds.size)
    }

    @Test fun itemsIsTheWholeQueueInOrder() {
        val s = session()
        assertEquals(listOf(1L, 2L, 3L), s.items.map { it.id })
        s.swipeLeft()
        assertEquals("advancing does not shorten the list", 3, s.items.size)
    }

    // --- decided: what the filmstrip shows to the left of the current card ---

    @Test fun nothingIsDecidedAtTheStartOfASession() {
        assertEquals(emptyList<DecidedItem>(), session().decided(5))
    }

    @Test fun decidedRunsOldestFirstSoItReadsTowardsTheCurrentCard() {
        val s = session(4)
        s.swipeRight(); s.swipeLeft(); s.swipeRight()
        assertEquals(listOf(1L, 2L, 3L), s.decided(5).map { it.item.id })
    }

    @Test fun decidedCarriesTheDecisionPerItem() {
        val s = session(4)
        s.swipeRight(); s.swipeLeft(); s.swipeRight()
        assertEquals(listOf(true, false, true), s.decided(5).map { it.kept })
    }

    @Test fun decidedKeepsTheMostRecentWhenCapped() {
        val s = session(6)
        repeat(5) { s.swipeRight() }
        assertEquals(
            "the strip shows what is nearest the current card, not the start of the session",
            listOf(3L, 4L, 5L),
            s.decided(3).map { it.item.id },
        )
    }

    @Test fun decidedIsEmptyForAZeroOrNegativeCount() {
        val s = session()
        s.swipeLeft()
        assertEquals(emptyList<DecidedItem>(), s.decided(0))
        assertEquals(emptyList<DecidedItem>(), s.decided(-1))
    }

    @Test fun undoTakesAnItemBackOutOfDecided() {
        val s = session()
        s.swipeLeft()
        s.swipeRight()
        s.undo()
        assertEquals(listOf(1L), s.decided(5).map { it.item.id })
    }

    /**
     * The reason this reads the mark set rather than the swipe history: the grid can edit a
     * decision after the deck has passed it, and the strip must agree with the grid. A
     * left-swipe then unmarked from the grid is a kept photograph, whatever the swipe was.
     */
    @Test fun decidedFollowsTheGridsEditRatherThanTheOriginalSwipe() {
        val s = session()
        s.swipeLeft()
        assertEquals(false, s.decided(5).single().kept)
        s.setMarked(1L, false)
        assertEquals(true, s.decided(5).single().kept)
    }

    @Test fun decidedFollowsAMarkMadeFromTheGridOnAKeptItem() {
        val s = session()
        s.swipeRight()
        s.setMarked(1L, true)
        assertEquals(false, s.decided(5).single().kept)
    }

    @Test fun decidedIsWholeSessionLongOnAnExhaustedDeck() {
        val s = session(2)
        s.swipeLeft(); s.swipeRight()
        assertTrue(s.isExhausted)
        assertEquals(listOf(1L, 2L), s.decided(5).map { it.item.id })
    }

    // -----------------------------------------------------------------------
    // drop: what a committed trash does to a live session
    // -----------------------------------------------------------------------

    /**
     * The one that matters. A commit made mid-session removes items the deck has already
     * passed, and if the position does not come back with them the deck skips exactly as
     * many cards as were trashed — silently, and only for users who commit before the end.
     */
    @Test fun dropKeepsTheCardOnScreenOnScreen() {
        val s = session(6)
        s.swipeLeft(); s.swipeRight(); s.swipeLeft()   // marks 1 and 3, keeps 2
        assertEquals(4L, s.current?.id)

        s.drop(setOf(1L, 3L))

        assertEquals(4L, s.current?.id)
        assertEquals(1, s.position)
        assertEquals(4, s.total)
        assertEquals(listOf(2L, 4L, 5L, 6L), s.items.map { it.id })
    }

    @Test fun dropClearsTheMarksItTakes() {
        val s = session(3)
        s.swipeLeft(); s.swipeLeft()
        assertEquals(2, s.markedCount)
        s.drop(setOf(1L, 2L))
        assertEquals(0, s.markedCount)
        assertEquals(0L, s.markedBytes)
        assertEquals(emptySet<Long>(), s.markedIds)
    }

    /** Dropping ahead of the deck shortens the queue without moving the position. */
    @Test fun dropAheadOfThePositionDoesNotMoveIt() {
        val s = session(5)
        s.swipeRight()
        s.drop(setOf(4L))
        assertEquals(1, s.position)
        assertEquals(2L, s.current?.id)
        assertEquals(4, s.total)
    }

    /**
     * Undo must not step back onto a photograph that is now in the phone's trash — there is
     * nothing to undo it to. Anything decided before the commit is still undoable.
     */
    @Test fun dropTakesItsItemsOutOfTheUndoHistory() {
        val s = session(4)
        s.swipeRight(); s.swipeLeft()                  // keeps 1, marks 2
        s.drop(setOf(2L))
        assertEquals(1L, s.undo()?.item?.id)
        assertNull(s.undo())
    }

    @Test fun dropOfTheWholeQueueLeavesAnExhaustedSession() {
        val s = session(2)
        s.swipeLeft(); s.swipeLeft()
        s.drop(setOf(1L, 2L))
        assertEquals(0, s.total)
        assertTrue(s.isExhausted)
        assertNull(s.current)
    }

    /** setMarked looks ids up in a cached map, so the cache has to die with the queue. */
    @Test fun dropInvalidatesTheIdLookupUsedByTheGrid() {
        val s = session(3)
        s.setMarked(2L, true)
        s.drop(setOf(2L))
        s.setMarked(2L, true)                          // gone: must be ignored, not resurrect it
        assertEquals(0, s.markedCount)
        assertEquals(listOf(1L, 3L), s.items.map { it.id })
    }

    @Test fun dropOfNothingChangesNothing() {
        val s = session(3)
        s.swipeLeft()
        s.drop(emptySet())
        assertEquals(1, s.position)
        assertEquals(3, s.total)
        assertEquals(1, s.markedCount)
    }

    // --- jumpTo: the grid picking the card to deal next ---

    @Test fun jumpToDealsFromTheItemAsked() {
        val s = session(6)
        assertTrue(s.jumpTo(4L))
        assertEquals(4L, s.current?.id)
        assertEquals(3, s.position)
    }

    @Test fun jumpToDecidesNothing() {
        val s = session(6)
        s.jumpTo(5L)
        assertEquals("jumping is not a decision about anything", 0, s.markedCount)
        assertEquals(emptySet<Long>(), s.keptIds)
    }

    /**
     * The whole reason [DecidedItem.kept] is nullable. Everything jumped over is behind the
     * deck and unmarked, which is exactly the shape a kept photograph has — and the strip
     * must not put a tick on four pictures nobody has looked at.
     */
    @Test fun itemsJumpedOverAreBehindTheDeckButUndecided() {
        val s = session(6)
        s.swipeRight()                                  // 1 is genuinely kept
        s.jumpTo(6L)
        val past = s.decided(10)
        assertEquals(listOf(1L, 2L, 3L, 4L, 5L), past.map { it.item.id })
        assertEquals(listOf(true, null, null, null, null), past.map { it.kept })
    }

    @Test fun jumpingBackwardsPutsADecidedCardBackInFront() {
        val s = session(4)
        s.swipeLeft(); s.swipeRight()
        s.jumpTo(1L)
        assertEquals(1L, s.current?.id)
        assertEquals("the decisions themselves survive being jumped back over", 1, s.markedCount)
        assertEquals(Decision.MARK, s.decisionFor(1L))
        assertEquals(Decision.KEEP, s.decisionFor(2L))
    }

    @Test fun jumpToAnUnknownItemIsIgnored() {
        val s = session(3)
        s.swipeRight()
        assertFalse(s.jumpTo(99L))
        assertEquals("the deck has not moved", 1, s.position)
    }

    // --- undo, once the deck can be somewhere other than one card on ---

    @Test fun undoAfterAJumpReturnsToTheDecisionRatherThanOneCardBack() {
        val s = session(8)
        s.swipeLeft()                                   // decided item 1, at position 0
        s.jumpTo(7L)
        val undone = s.undo()
        assertEquals(1L, undone?.item?.id)
        assertEquals("back to where that decision was made", 0, s.position)
        assertEquals(0, s.markedCount)
    }

    @Test fun undoLiftsAKeepAsWellAsAMark() {
        val s = session(3)
        s.swipeRight()
        s.undo()
        assertEquals(emptySet<Long>(), s.keptIds)
        assertNull(s.decisionFor(1L))
    }

    /** Recorded positions are indices into a queue that a commit reshapes underneath them. */
    @Test fun dropKeepsUndoLandingOnTheRightCard() {
        val s = session(6)
        s.swipeLeft()                                   // 1 marked, decided at position 0
        s.swipeLeft()                                   // 2 marked, decided at position 1
        s.swipeRight()                                  // 3 kept,   decided at position 2
        s.drop(setOf(1L, 2L))                           // both marked ones are trashed
        assertEquals(listOf(3L, 4L, 5L, 6L), s.items.map { it.id })
        assertEquals("the card on screen is still the card on screen", 1, s.position)
        val undone = s.undo()
        assertEquals(3L, undone?.item?.id)
        assertEquals("3 sits at index 0 now, and undo has to know that", 0, s.position)
        assertEquals(3L, s.current?.id)
    }

    // --- decisionFor: what the filmstrip asks about a card it has not reached ---

    @Test fun decisionForIsNullUntilSomethingIsDecided() {
        val s = session(3)
        assertNull(s.decisionFor(1L))
        assertNull(s.decisionFor(99L))
    }

    @Test fun decisionForSeesAMarkMadeAheadOfTheDeck() {
        val s = session(6)
        s.setMarked(5L, true)
        assertEquals(Decision.MARK, s.decisionFor(5L))
        assertEquals("marking ahead does not move the deck", 0, s.position)
    }

    @Test fun untickingAheadOfTheDeckLeavesTheItemUndecided() {
        val s = session(6)
        s.setMarked(5L, true)
        s.setMarked(5L, false)
        assertNull("it is back to never having been judged", s.decisionFor(5L))
    }

    @Test fun untickingBehindTheDeckIsAKeep() {
        val s = session(6)
        s.swipeLeft()
        s.setMarked(1L, false)
        assertEquals(Decision.KEEP, s.decisionFor(1L))
    }
}
