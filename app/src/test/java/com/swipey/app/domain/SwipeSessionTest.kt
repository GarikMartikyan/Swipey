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
}
