package com.swipey.app.ui.deck

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.positionChangeIgnoreConsumed
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Dp
import kotlin.math.abs

/**
 * A vertical drag that yields to a horizontal one.
 *
 * The deck already has a horizontal drag detector on the card, and it is the app's whole
 * interaction — nothing added here may make a swipe less reliable. That rules out
 * `detectVerticalDragGestures`: it decides on its own axis in isolation, so a diagonal
 * flick can satisfy both detectors and the user gets a card leaving sideways while the grid
 * comes down over it.
 *
 * ### Two thresholds, not one radius
 *
 * The first version armed on total distance travelled in any direction, which forced one
 * number to answer two different questions and got both wrong. Small, and it claimed the
 * beginning of ordinary swipes. Large, and there was a long stretch where the finger moved
 * and nothing happened — which does not read as a high threshold, it reads as a broken
 * screen.
 *
 * So the two questions are asked separately, and whichever answers first ends it:
 *
 *  - **[HorizontalBail] of sideways travel** and this is a swipe. Bail immediately, consume
 *    nothing, stay out of the way for the rest of the gesture. Deliberately the smaller of
 *    the two, so the common gesture wins ties by arriving first.
 *  - **[VerticalArm] of downward travel**, with vertical clearly dominant
 *    ([DominanceRatio]), and this is a pull. Small enough that the grid starts following the
 *    finger almost at once, because responsiveness is what tells the user the gesture was
 *    understood.
 *
 * Being *recognised* early and being *committed* early are different things, and only the
 * second needs to be hard. The caller decides how far the pull must go to open; this only
 * decides whose gesture it is. That separation is the whole point of the rewrite.
 *
 * Downward only. An upward drag is not a pull, and claiming one would swallow a gesture this
 * has no use for — the card would stop responding for the rest of it, which reads as the app
 * freezing rather than as a gesture being declined.
 *
 * @param enabled when false the gesture is not installed at all, rather than installed and
 *   ignored — a detector that swallows events it will not act on is worse than none.
 * @param onStart called once, when the pull is claimed.
 * @param onDrag the vertical delta in pixels, positive downward. The travel spent reaching
 *   [VerticalArm] is included in the first call, so the shade is already where the finger
 *   put it rather than lagging a threshold behind it.
 * @param onEnd the total vertical travel and the release velocity in px/s, positive
 *   downward. Called exactly once per claimed pull, on release or cancellation, so a caller
 *   can settle an animation without tracking which of the two happened.
 */
fun Modifier.verticalPull(
    enabled: Boolean,
    key: Any?,
    onStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onEnd: (travel: Float, velocity: Float) -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(key) {
    val arm = VerticalArm.toPx()
    val bail = HorizontalBail.toPx()

    awaitEachGesture {
        // requireUnconsumed = false: the card's clickable sees the down first and marks it,
        // and a pull that refused to start after a press would only work on the gutter.
        val down = awaitFirstDown(requireUnconsumed = false)
        val velocity = VelocityTracker()

        var travelled = Offset.Zero
        var claimed = false

        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break

            if (!change.pressed) {
                if (claimed) onEnd(travelled.y, velocity.calculateVelocity().y)
                break
            }

            if (!claimed) {
                // Ignoring consumption while measuring: the card consumes horizontal
                // movement as soon as it starts a swipe, and a detector that could not see
                // that movement would conclude the finger had stopped and claim the gesture
                // out from under it.
                travelled += change.positionChangeIgnoreConsumed()

                if (abs(travelled.x) >= bail) break

                if (travelled.y >= arm && travelled.y > abs(travelled.x) * DominanceRatio) {
                    claimed = true
                    velocity.addPosition(change.uptimeMillis, change.position)
                    onStart()
                    onDrag(travelled.y)
                    change.consume()
                }
            } else {
                val delta = change.positionChange()
                travelled += delta
                velocity.addPosition(change.uptimeMillis, change.position)
                onDrag(delta.y)
                change.consume()
            }
        }
    }
}

/**
 * How far down the finger must go before the pull is recognised.
 *
 * Small, and deliberately so. This is the *recognition* threshold, not the commit
 * threshold — once armed the grid follows the finger and can still be put back, so being
 * generous here costs nothing except a little movement the user can undo by not continuing.
 * The previous 44dp bought its resistance in the worst possible currency: a long stretch
 * where the screen ignored the finger entirely.
 */
private val VerticalArm = Dp(20f)

/**
 * How far sideways the finger may go before this concedes the gesture is a swipe.
 *
 * Smaller than [VerticalArm], which is what gives the swipe the benefit of the doubt: on a
 * genuinely diagonal drag the horizontal bail is reached first, and the card keeps the
 * gesture. The important interaction should win ties.
 */
private val HorizontalBail = Dp(14f)

/**
 * How much steeper than horizontal a drag must be before it counts as a pull.
 *
 * People do not swipe along a ruler — a thumb pivots from a knuckle, so a "horizontal" flick
 * arrives as a shallow arc. 1.8 puts the boundary near 61°, which leaves that arc with the
 * swipe without demanding the near-vertical drag 2.2 did.
 */
private const val DominanceRatio = 1.8f
