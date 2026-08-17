package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SwipeDirectionTest {

    @Test fun leftBinsIsTheDeckSwipeyHasAlwaysDealt() {
        assertFalse("a leftward swipe bins", BinSide.LEFT.keepFor(towardsRight = false))
        assertTrue("a rightward swipe keeps", BinSide.LEFT.keepFor(towardsRight = true))
    }

    @Test fun rightBinsIsTheExactOpposite() {
        assertTrue("a leftward swipe keeps", BinSide.RIGHT.keepFor(towardsRight = false))
        assertFalse("a rightward swipe bins", BinSide.RIGHT.keepFor(towardsRight = true))
    }

    @Test fun theCardFliesTowardsTheSideTheDecisionLivesOn() {
        // The Bin and Keep buttons commit through the card, so the flight has to agree with
        // the gesture that would have made the same decision.
        for (side in BinSide.entries) {
            for (keep in listOf(true, false)) {
                val right = side.flightIsRight(keep)
                assertEquals(
                    "$side: a card flown ${if (right) "right" else "left"} must read back as keep=$keep",
                    keep,
                    side.keepFor(right),
                )
            }
        }
    }

    @Test fun theBadgeFollowsTheOutcomeNotTheDirection() {
        // Dragging right, on a deck where right bins: the geometry is positive, the badge
        // that should be showing is the bin's, which DecisionGlyph draws from a negative.
        assertEquals(0.7f, BinSide.LEFT.decisionProgress(0.7f), 0f)
        assertEquals(-0.7f, BinSide.RIGHT.decisionProgress(0.7f), 0f)
        assertEquals(-0.4f, BinSide.LEFT.decisionProgress(-0.4f), 0f)
        assertEquals(0.4f, BinSide.RIGHT.decisionProgress(-0.4f), 0f)
    }

    @Test fun aStationaryCardFavoursNeitherDecision() {
        for (side in BinSide.entries) assertEquals(0f, side.decisionProgress(0f), 0f)
    }

    @Test fun theBadgeAgreesWithTheDecisionAtEveryPoint() {
        // The invariant that ties the two halves together: whichever badge is lit while the
        // thumb is down must be the decision released at that moment records.
        for (side in BinSide.entries) {
            for (drag in listOf(-1f, -0.3f, 0.3f, 1f)) {
                val badgeSaysKeep = side.decisionProgress(drag) > 0
                assertEquals(
                    "$side at drag=$drag",
                    side.keepFor(towardsRight = drag > 0),
                    badgeSaysKeep,
                )
            }
        }
    }
}
