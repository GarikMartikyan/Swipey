package com.swipey.app.domain

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * How dark a caption's ground has to be, given the photograph under it.
 *
 * The deck prints two lines over a picture the app did not choose, and the only honest way
 * to keep them readable is to stop guessing: measure what is behind them and work out the
 * least darkness that satisfies the contrast standard. Everything here is the arithmetic
 * for that — pure Kotlin, no android.* (spec §10), so it can be tested rather than eyeballed
 * on a phone.
 *
 * The numbers come from WCAG 2.2: 4.5:1 for ordinary text, and 3:1 once type is 18.66px and
 * bold, which is why the deck's caption sets its name line large — a bigger bar cleared by
 * the type is a smaller bill paid by the photograph.
 */
object CaptionContrast {

    /** WCAG AA for text below the large-text threshold. The size-and-date line. */
    const val TargetNormal = 4.5

    /** WCAG AA for text at 18.66px bold or above. The album line. */
    const val TargetLarge = 3.0

    /**
     * The ceiling on how dark the ground may go.
     *
     * Past this the ramp stops being protection and becomes a black bar with a photograph
     * behind it. A frame that cannot be satisfied within this budget is a frame where the
     * caption is as readable as it is going to get; the type's own shadow covers the rest.
     */
    const val MaxAlpha = 0.72f

    /**
     * sRGB relative luminance of a grey, per WCAG's own formula.
     *
     * Grey rather than a colour because that is all the caller has: the backdrop is a mean
     * sampled off a photograph, and the ink over it is white or a translucency of white.
     */
    fun relativeLuminance(level: Float): Double {
        val c = (level / 255.0).coerceIn(0.0, 1.0)
        val linear = if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        return linear
    }

    /** WCAG contrast between two relative luminances, order-independent. */
    fun contrastRatio(a: Double, b: Double): Double {
        val hi = max(a, b)
        val lo = min(a, b)
        return (hi + 0.05) / (lo + 0.05)
    }

    /**
     * The luminance of white ink laid over [backdrop] at [inkAlpha].
     *
     * This is what Apple calls vibrancy and what the deck's second line now uses: a
     * translucent level of the *same* ink rather than a fixed grey. The distinction is the
     * whole reason this function exists — a grey has one luminance whatever is behind it,
     * so it fails on a light ground no matter how the ground is tuned, while white at an
     * alpha gets lighter exactly when its surroundings do.
     */
    fun vibrantInkLuminance(backdrop: Float, inkAlpha: Float): Double {
        val white = 250f // SwipeyDarkColors.textPrimary
        return relativeLuminance(white * inkAlpha + backdrop * (1f - inkAlpha))
    }

    /**
     * The least scrim alpha that puts the caption's *smaller* line at [target] over a
     * backdrop measuring [backdropLuma] (0..255).
     *
     * Solved by search rather than algebra: the ink's luminance moves with the ground it is
     * composited over, so the two sides of the inequality both depend on the answer. Twelve
     * steps of a coarse sweep, refined once — this runs per card, not per frame, and the
     * precision that matters is a percent.
     *
     * Returns 0 when the photograph is already dark enough to need nothing at all, which is
     * the case this whole exercise exists to protect: most photographs need far less than a
     * fixed scrim gives them, and a fixed scrim is therefore mostly dirt.
     */
    fun scrimAlphaFor(
        backdropLuma: Float,
        inkAlpha: Float,
        target: Double = TargetNormal,
        maxAlpha: Float = MaxAlpha,
    ): Float {
        fun ratioAt(alpha: Float): Double {
            val ground = backdropLuma * (1f - alpha)
            return contrastRatio(vibrantInkLuminance(ground, inkAlpha), relativeLuminance(ground))
        }

        if (ratioAt(0f) >= target) return 0f

        var lo = 0f
        var hi = maxAlpha
        if (ratioAt(hi) < target) return maxAlpha
        repeat(12) {
            val mid = (lo + hi) / 2f
            if (ratioAt(mid) >= target) hi = mid else lo = mid
        }
        // Rounded up to the nearest percent: rounding down would hand back a value that
        // just misses the target it was asked to hit.
        return (kotlin.math.ceil(hi * 100f) / 100f).coerceIn(0f, maxAlpha)
    }
}
