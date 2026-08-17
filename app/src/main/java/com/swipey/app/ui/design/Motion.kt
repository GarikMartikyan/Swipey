package com.swipey.app.ui.design

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Shared motion, so the whole app moves at one tempo.
 *
 * Two rules underneath these numbers:
 *
 *  1. **Anything the user is dragging uses a spring**, because a spring is the only spec
 *     that can be interrupted mid-flight and re-target without a visible seam — which is
 *     exactly what happens when a card is half-swiped and the finger changes direction.
 *  2. **Anything that merely appears or disappears uses a short tween**, because chrome
 *     that springs draws attention to itself, and Swipey's chrome exists to be ignored.
 */
object SwipeyMotion {

    /** [chrome]'s duration, exposed for callers that need the raw number. */
    const val ChromeMillis = 180

    /**
     * How a card comes to rest — after a release that didn't cross the threshold, or when
     * the next card rises into place.
     *
     * Slightly underdamped (0.8) so there is a single, barely-perceptible settle rather
     * than a dead stop; stiff enough (400) that the deck never feels like it is waiting
     * for the animation to catch up with the user's thumb.
     */
    fun <T> cardSettle(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 400f)

    /** Fades and cross-dissolves for interface chrome: counters, chips, toolbars. */
    fun <T> chrome(): FiniteAnimationSpec<T> = tween(durationMillis = ChromeMillis, easing = FastOutSlowInEasing)

    /**
     * A sheet arriving or leaving. Longer than [chrome] because it travels further, and
     * eased rather than sprung so it never overshoots past the screen edge.
     */
    fun <T> sheet(): FiniteAnimationSpec<T> = tween(durationMillis = 240, easing = FastOutSlowInEasing)

    /**
     * A control's press-down and release. Very stiff and nearly critically damped: this
     * should register as tactile feedback, not as an animation.
     */
    fun <T> press(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.9f, stiffness = 1200f)

    /** A determinate progress bar advancing. Linear, so a steady rate looks steady. */
    fun <T> progress(): FiniteAnimationSpec<T> = tween(durationMillis = 220, easing = LinearEasing)
}

/**
 * The app's haptic vocabulary, kept to three verbs.
 *
 * Haptics carry real information in Swipey: the user is looking at a photograph, not at
 * the button they just pressed, so the confirmation that a decision landed often arrives
 * through their thumb rather than their eyes. Which makes consistency load-bearing — the
 * same event must always feel the same, hence one helper rather than scattered
 * `performHapticFeedback` calls.
 *
 * Obtain one with [rememberSwipeyHaptics].
 *
 * @param enabled false silences all three verbs. Held here, at the one place every haptic
 *   in the app passes through, rather than checked at the call sites — there are only two
 *   of them today, and a setting each of them had to remember to honour would be a setting
 *   the third one silently ignores.
 */
@Stable
class SwipeyHaptics internal constructor(
    private val feedback: HapticFeedback,
    private val enabled: Boolean,
) {

    /**
     * A decision has been recorded — a card committed to Keep or Bin, or a batch moved to
     * the system trash. The heavier of the two, because it is the one the user must not
     * miss.
     */
    fun commit() {
        if (!enabled) return
        feedback.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    /**
     * A decision has been taken back. Deliberately lighter than [commit]: undo returns to
     * a previous state rather than creating a new one, and should feel like less
     * happened, not more.
     */
    fun undo() {
        if (!enabled) return
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    /**
     * A swipe has crossed the point where releasing would commit it. Fires once per
     * crossing, so the user can feel the threshold without looking for it.
     */
    fun threshold() {
        if (!enabled) return
        feedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }
}

/**
 * Whether haptics fire at all, for the whole tree.
 *
 * Declared here rather than read from the settings package, so the design system does not
 * have to know that a Settings screen exists. The root provides it; see
 * `ui/settings/LocalSettings.kt`. Defaulted to true, so a `@Preview` or a component lifted
 * out of the tree behaves the way the app does.
 */
val LocalSwipeyHapticsEnabled = staticCompositionLocalOf { true }

/** Remembers a [SwipeyHaptics] bound to the current haptic feedback handler. */
@Composable
fun rememberSwipeyHaptics(): SwipeyHaptics {
    val feedback = LocalHapticFeedback.current
    val enabled = LocalSwipeyHapticsEnabled.current
    return remember(feedback, enabled) { SwipeyHaptics(feedback, enabled) }
}
