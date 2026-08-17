package com.swipey.app.domain

/**
 * Which side of the deck sends a photograph to the bin.
 *
 * [LEFT] is how Swipey has always dealt, and it is still the default: left is the discard
 * gesture in nearly every card deck a phone has ever shown, so it is the reading a new user
 * arrives with. The setting exists because it is also the *wrong* reading for anyone whose
 * grip puts a comfortable leftward flick under their thumb and an awkward one on the right —
 * and for anyone who has learned the opposite convention elsewhere and cannot unlearn it.
 *
 * There are only two of them and they are exact opposites, which is why everything below is
 * a boolean identity rather than a `when` at each call site. The deck asks the same question
 * in three places — which way did the thumb go, which way should the card fly, which badge
 * should be showing — and all three are the same question with the arguments moved around.
 */
enum class BinSide { LEFT, RIGHT }

/**
 * True when a card travelling to the right is being kept.
 *
 * The whole setting in one line. Everything else here is stated in terms of it, so there is
 * exactly one place where "which side means what" is decided.
 */
val BinSide.rightKeeps: Boolean get() = this == BinSide.LEFT

/**
 * The decision a card released towards one side or the other records.
 *
 * [towardsRight] is the *gesture*, already resolved from position or fling by the caller —
 * this is only the half that turns a direction into a meaning, which is the half the setting
 * changes.
 */
fun BinSide.keepFor(towardsRight: Boolean): Boolean = towardsRight == rightKeeps

/**
 * The way a card carrying [keep] should fly off.
 *
 * The inverse of [keepFor], and needed because a decision can arrive without a gesture: the
 * Bin and Keep buttons under the deck commit through the same card animation, and the card
 * has to leave towards the side that decision lives on or the button and the swipe would
 * disagree about where the bin is.
 */
fun BinSide.flightIsRight(keep: Boolean): Boolean = keep == rightKeeps

/**
 * Geometric drag progress re-signed as a *decision*: negative bins, positive keeps.
 *
 * The card's own progress is signed by direction — negative is leftward — and the badge that
 * rides above it is signed by outcome. Those were the same number for as long as left meant
 * bin, and this is the one line that keeps them the same number when it does not.
 */
fun BinSide.decisionProgress(dragProgress: Float): Float =
    if (rightKeeps) dragProgress else -dragProgress
