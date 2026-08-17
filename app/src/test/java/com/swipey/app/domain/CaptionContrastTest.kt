package com.swipey.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The caption's ground is computed rather than chosen, so these are the tests that stop it
 * being wrong quietly — an under-solved scrim does not crash, it just leaves the second line
 * unreadable on somebody's holiday photographs.
 */
class CaptionContrastTest {

    private val ink = 0.78f // the vibrancy level the deck's caption uses

    @Test fun luminanceMatchesTheWcagAnchors() {
        assertEquals(0.0, CaptionContrast.relativeLuminance(0f), 0.0001)
        assertEquals(1.0, CaptionContrast.relativeLuminance(255f), 0.0001)
        // Mid-grey is nowhere near half: the curve is why a "50% scrim" is not 50% of the
        // contrast problem solved.
        assertTrue(CaptionContrast.relativeLuminance(128f) < 0.25)
    }

    @Test fun contrastRatioIsOrderIndependentAndBounded() {
        val black = CaptionContrast.relativeLuminance(0f)
        val white = CaptionContrast.relativeLuminance(255f)
        assertEquals(21.0, CaptionContrast.contrastRatio(white, black), 0.01)
        assertEquals(
            CaptionContrast.contrastRatio(white, black),
            CaptionContrast.contrastRatio(black, white),
            0.0001,
        )
        assertEquals(1.0, CaptionContrast.contrastRatio(white, white), 0.0001)
    }

    /** The point of vibrancy: the ink tracks its ground instead of sitting at one value. */
    @Test fun vibrantInkGetsLighterAsItsGroundDoes() {
        val onDark = CaptionContrast.vibrantInkLuminance(backdrop = 10f, inkAlpha = ink)
        val onLight = CaptionContrast.vibrantInkLuminance(backdrop = 200f, inkAlpha = ink)
        assertTrue(onLight > onDark)
    }

    @Test fun aDarkPhotographNeedsNoScrimAtAll() {
        assertEquals(0f, CaptionContrast.scrimAlphaFor(backdropLuma = 20f, inkAlpha = ink), 0.001f)
    }

    @Test fun aBrightPhotographNeedsARealOne() {
        val alpha = CaptionContrast.scrimAlphaFor(backdropLuma = 234f, inkAlpha = ink)
        assertTrue("expected a substantial scrim, got $alpha", alpha > 0.4f)
        assertTrue(alpha <= CaptionContrast.MaxAlpha)
    }

    /** The property that matters: whatever it returns actually clears the bar it was given. */
    @Test fun theSolvedAlphaMeetsTheTargetAcrossTheWholeRange() {
        for (luma in 0..255 step 5) {
            val alpha = CaptionContrast.scrimAlphaFor(luma.toFloat(), ink)
            if (alpha >= CaptionContrast.MaxAlpha) continue // capped, and documented as such
            val ground = luma * (1f - alpha)
            val ratio = CaptionContrast.contrastRatio(
                CaptionContrast.vibrantInkLuminance(ground, ink),
                CaptionContrast.relativeLuminance(ground),
            )
            assertTrue("luma=$luma alpha=$alpha gave $ratio", ratio >= CaptionContrast.TargetNormal - 0.05)
        }
    }

    /** Brighter frames never ask for less ground than darker ones. */
    @Test fun theAnswerIsMonotonicInBrightness() {
        var previous = 0f
        for (luma in 0..255 step 5) {
            val alpha = CaptionContrast.scrimAlphaFor(luma.toFloat(), ink)
            assertTrue("luma=$luma went backwards: $alpha < $previous", alpha >= previous - 0.001f)
            previous = alpha
        }
    }

    /** Never darker than the budget, however hostile the frame. */
    @Test fun itNeverExceedsTheCeiling() {
        assertTrue(CaptionContrast.scrimAlphaFor(255f, ink) <= CaptionContrast.MaxAlpha)
        assertTrue(CaptionContrast.scrimAlphaFor(255f, 0.4f) <= CaptionContrast.MaxAlpha)
    }

    /** A large-text bar is cheaper to clear, which is the whole argument for the big line. */
    @Test fun theLargeTextTargetCostsLessGround() {
        val normal = CaptionContrast.scrimAlphaFor(234f, ink, CaptionContrast.TargetNormal)
        val large = CaptionContrast.scrimAlphaFor(234f, ink, CaptionContrast.TargetLarge)
        assertTrue("large=$large should be cheaper than normal=$normal", large < normal)
    }
}
