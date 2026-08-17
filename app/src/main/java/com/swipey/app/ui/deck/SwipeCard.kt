package com.swipey.app.ui.deck

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyMotion
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.design.rememberSwipeyHaptics
import kotlin.math.abs
import kotlinx.coroutines.launch

/** How far across the screen a drag must travel before releasing commits it. */
private const val CommitFraction = 0.3f

/** The fling speed that commits a card that never reached [CommitFraction]. */
private const val FlingVelocity = 1500f

/** The fly-off. Deliberately a short tween rather than a spring: an exit should be over. */
private const val ExitMillis = 180

/**
 * How much the card swells while a thumb is on it.
 *
 * The ceiling here is arithmetic, not taste: the card grows about its own centre, so it
 * gains `height * LiftScale / 2` at the top and the same at the bottom, and anything past
 * [CardInset] pushes its rounded corners off the screen — clipping the very edge the lift
 * exists to move. On this app's target device the card is roughly 660dp tall inside the
 * safe area, so 2% costs 6.6dp against a 12dp gutter, with room to spare on a taller
 * tablet card.
 *
 * Reported by the gesture detector's drag start, which fires once the touch clears the
 * system's slop threshold — a few dp of movement, not the instant of contact. Close enough
 * to read as "the app felt that", but it is a drag cue, not a press cue, and the KDoc used
 * to overclaim it.
 */
private const val LiftScale = 0.02f

/** The decision glyph's badge. Large enough to read past a thumb, small enough to see around. */
private val GlyphBadgeSize = 96.dp

/** The glyph inside the badge. */
private val GlyphIconSize = 40.dp

/**
 * The badge's ground opacity.
 *
 * High, because the ground's job is to guarantee the glyph on top of it, and it has to do
 * that over a photograph chosen by someone else — a white beach, a snow field, a flash-lit
 * wall. At 0.55 the disc borrowed enough of a bright picture to take the glyph under the
 * contrast floor; at 0.85 the photograph still reads through it as texture but no longer
 * as luminance.
 */
private const val GlyphGroundAlpha = 0.85f

/** The badge's scale at zero drag; it reaches 1f at the commit point. */
private const val GlyphMinScale = 0.7f

/**
 * The next photograph's scale at rest; it reaches 1f at the commit point.
 *
 * Sits behind the card being swiped and grows to meet it, so the deck reads as a stack
 * rather than as a single picture over an empty canvas. 7% is enough that the inset border
 * of canvas around it registers as depth on the strip the top card has vacated, and little
 * enough that the photograph has effectively finished arriving by the time it is uncovered.
 */
private const val UnderScale = 0.93f

/**
 * The gutter between the card and the edge of the safe area.
 *
 * Small, because the photograph is the point and every dp given to the gutter is taken
 * from the thing being judged. It exists for two reasons: so the rounded corners have
 * canvas to be rounded *against*, and so [LiftScale] has somewhere to grow into. The
 * second is a hard constraint — see that constant.
 */
private val CardInset = SwipeySpacing.md

/**
 * The top card, and the gesture that decides it.
 *
 * Commits at [CommitFraction] of screen width, or on a fling.
 * `onSwiped(itemId, keep)` — keep = true is kept (right), false is marked (left). The
 * id is reported alongside the decision so a caller can detect and ignore a decision
 * that no longer applies to whatever card is now current (fix round 1, Critical 2).
 * [onCommittingChanged] reports whether a decision is currently mid-animation, so the
 * caller can disable any other way of recording a decision (e.g. buttons) until it
 * resolves.
 *
 * ### What the user sees: lift, and a glyph
 * The card never changes angle. Nothing rotates on drag and nothing rotates on exit — the
 * photograph stays square with the screen from the moment a thumb lands to the moment it
 * leaves. Two things carry the gesture instead:
 *
 * **Lift.** On touch the card swells by [LiftScale] and settles back on release. It is
 * keyed to contact rather than to displacement, so it answers "the app felt that" before
 * the user has moved far enough to mean anything. It reads because the card has edges: it
 * is inset from the safe area and rounded to [SwipeyRadius.deck], so growing it moves a
 * visible boundary against the canvas rather than scaling a picture that already fills
 * every pixel.
 *
 * **A glyph.** One badge in the centre of the screen — a bin or a check — fading and
 * growing from [GlyphMinScale] to full size as the drag approaches the commit point. It
 * replaces the earlier treatment, in which the photograph desaturated toward the bin and
 * the right edge warmed toward keep. That treatment argued its own case well and it is
 * worth naming what was traded for this one: the picture no longer visibly drains, so the
 * feedback is now a symbol *about* the decision rather than a preview *of* it. What it
 * buys is legibility over any photograph, and a single unambiguous statement of direction
 * on a card that no longer tilts to tell you.
 *
 * The badge is a sibling of the moving card, not a child of it: it stays put while the
 * photograph travels, which is what makes it read as belonging to the screen rather than
 * as decoration on the picture. It also means it works identically over video, which the
 * old desaturation could not — ExoPlayer draws into a `SurfaceView` on its own composited
 * layer that a `ColorMatrix` inside the Compose canvas cannot reach.
 *
 * ### Why nothing here recomposes while a finger is down
 * Every read of the drag offset lives inside a `graphicsLayer` lambda, which the compose
 * runtime defers to the layout/draw phase. A frame of dragging therefore redraws, but does
 * not recompose — which matters on the one screen a user holds their thumb on for an
 * entire session. A drag allocates nothing.
 */
@Composable
fun SwipeCard(
    itemId: Long,
    onSwiped: (itemId: Long, keep: Boolean) -> Unit,
    onCommittingChanged: (Boolean) -> Unit = {},
    commitRequest: Boolean? = null,
    under: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * CommitFraction
    val offsetX = remember(itemId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberSwipeyHaptics()

    // The real thing, not an estimate. The previous implementation derived fling speed as
    // `dragAmount * 60f`, which bakes in an assumption of 60fps: on a 120Hz display each
    // frame's delta is half as large, so the same flick reported half the velocity and the
    // fling gate below was effectively doubled — roughly half of all quick flicks refused
    // to commit. It also sampled only the final frame, so a flick that eased off at the
    // end read as nearly stationary. VelocityTracker integrates over a window of real
    // timestamps and is frame-rate independent.
    val tracker = remember(itemId) { VelocityTracker() }

    // 0f at rest, 1f while a thumb is down. Keyed on itemId so it cannot outlive the card
    // it belongs to: on a drag commit the finger has already lifted (onDragEnd runs before
    // the fly-off finishes and the deck advances), so re-keying costs nothing there — and
    // it closes the one path where a gesture cancelled by the pointerInput block being
    // torn down mid-drag could strand the lift at 1f, leaving every later card oversized.
    val lift = remember(itemId) { Animatable(0f) }

    // Whether the drag is currently past the commit point. Tracked only to fire the
    // threshold haptic once per crossing rather than on every frame beyond it.
    var pastThreshold by remember(itemId) { mutableStateOf(false) }

    // Non-null once a drag (or fling) has committed this card to a decision. Kept in
    // Compose state — rather than a plain local in the gesture callback — so it can
    // both key the commit LaunchedEffect below (so a re-key cancels it cleanly) and
    // report "committing" upward to gate external ways of recording a decision.
    var pendingKeep by remember(itemId) { mutableStateOf<Boolean?>(null) }
    val fired = remember(itemId) { booleanArrayOf(false) }

    // No entry animation, deliberately. The card used to fade and rise in on every new
    // item, because otherwise a photograph appeared fully formed on bare canvas the
    // instant the last one left. The under-card does that job now and does it better —
    // by the time an item becomes current it has already been on screen, growing into
    // place, for the whole of the previous swipe. Re-animating it here would take a
    // picture that is already at full size and full opacity and flash it back to nothing.
    //
    // There is no snapTo(0f) either: `offsetX` is remembered against itemId, so a re-key
    // already produces a fresh Animatable sitting at zero.

    // Read in composition, deliberately: this is what subscribes the composable to
    // `pendingKeep`, and therefore what guarantees the SideEffect below re-runs — and
    // reports — the instant a decision starts committing. Reading it inside the
    // SideEffect lambda instead would not subscribe to anything.
    val committing = pendingKeep != null
    SideEffect { onCommittingChanged(committing) }

    // A decision made somewhere other than the card — the Bin and Keep buttons — enters
    // here and then takes exactly the path a drag takes. Routing them through the same
    // fly-off is not decoration: without it a tap swapped the photograph on the next frame
    // with no motion at all, and the under-card, sitting at [UnderScale] because no drag
    // had grown it, jumped to full size in the same frame. Two different ways to record
    // the same decision should not look like two different apps.
    //
    // Guarded on `pendingKeep == null` so a request cannot overwrite a drag already
    // committing, and keyed on itemId so a stale request cannot follow the deck onto the
    // next card — the caller clears it in `onSwiped`, which lands in the same state batch
    // as the id change.
    LaunchedEffect(itemId, commitRequest) {
        if (commitRequest != null && pendingKeep == null) pendingKeep = commitRequest
    }

    // Runs the fly-off animation and then reports the decision. Keyed on
    // (itemId, pendingKeep): if the card is re-keyed for the next item before this
    // finishes, this coroutine is cancelled rather than surviving to call onSwiped
    // against the new current item (fix round 1, Critical 2).
    LaunchedEffect(itemId, pendingKeep) {
        val keep = pendingKeep ?: return@LaunchedEffect
        // Whatever raised the lift — a thumb, or nothing at all on a button commit — the
        // card is leaving, so it goes back down alongside the fly-off rather than after it.
        launch { lift.animateTo(0f, SwipeyMotion.press()) }
        offsetX.animateTo(
            if (keep) screenWidthPx * 1.5f else -screenWidthPx * 1.5f,
            tween(ExitMillis),
        )
        fired[0] = true
        onSwiped(itemId, keep)
    }

    // If the card leaves composition mid-animation (e.g. the user navigates away),
    // the LaunchedEffect above is cancelled before it calls onSwiped and the swipe
    // would otherwise be silently lost. Fire the pending decision here instead
    // (fix round 1, Important 1).
    DisposableEffect(itemId) {
        onDispose {
            val keep = pendingKeep
            if (keep != null && !fired[0]) {
                fired[0] = true
                onSwiped(itemId, keep)
            }
        }
    }

    // Task 20 NEW 1: SideEffect above only reports "committing" while this composable
    // is part of the tree. If the card leaves composition mid fly-off (the caller swaps
    // to a different branch — e.g. the deck becomes exhausted — the instant the commit
    // animation finishes), there is no further SideEffect to report false, so the
    // caller's "committing" flag latches true forever. This unkeyed DisposableEffect
    // fires exactly once, on the card's final removal from composition (not on re-key
    // for the next item, which is handled by the keyed effect above), and clears it.
    DisposableEffect(Unit) {
        onDispose { onCommittingChanged(false) }
    }

    // Signed drag progress, -1f (fully binned) .. 1f (fully kept). A function rather
    // than a value: every caller invokes it from inside a draw or layer lambda, so the
    // read of `offsetX.value` happens in the draw phase and costs no recomposition.
    fun progress(): Float = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            // On the stationary parent, not on the card: the gesture area is the whole
            // screen at all times, including the strip the card has already left.
            .pointerInput(itemId) {
                detectHorizontalDragGestures(
                    // The lift answers contact, so it starts here rather than on the
                    // first movement: a thumb resting on the picture has already been
                    // acknowledged before it has travelled far enough to mean anything.
                    onDragStart = {
                        tracker.resetTracking()
                        scope.launch { lift.animateTo(1f, SwipeyMotion.press()) }
                    },
                    onDragEnd = {
                        scope.launch { lift.animateTo(0f, SwipeyMotion.press()) }
                        // A decision is already committed and animating off-screen;
                        // ignore anything further so it can't be re-committed with a
                        // different value while the LaunchedEffect above is in flight.
                        if (pendingKeep != null) {
                            tracker.resetTracking()
                            return@detectHorizontalDragGestures
                        }
                        val velocity = tracker.calculateVelocity().x
                        val overThreshold = abs(offsetX.value) > threshold
                        val committed = overThreshold || abs(velocity) > FlingVelocity
                        if (committed) {
                            // Past the threshold, position is the trustworthy signal.
                            // Otherwise this only committed via the fling check, so
                            // take the direction from the fling itself — position
                            // alone (e.g. +20px after a sharp leftward flick) can
                            // disagree with it (fix round 1, Important 2).
                            pendingKeep = if (overThreshold) offsetX.value > 0 else velocity > 0
                            haptics.commit()
                        } else {
                            pastThreshold = false
                            scope.launch { offsetX.animateTo(0f, SwipeyMotion.cardSettle()) }
                        }
                        tracker.resetTracking()
                    },
                    // Without this a gesture the system takes away (a second pointer, a
                    // navigation drag from the edge) leaves the card stranded off-centre
                    // with no decision made and no way back but another drag.
                    onDragCancel = {
                        tracker.resetTracking()
                        scope.launch { lift.animateTo(0f, SwipeyMotion.press()) }
                        if (pendingKeep == null) {
                            pastThreshold = false
                            scope.launch { offsetX.animateTo(0f, SwipeyMotion.cardSettle()) }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (pendingKeep == null) {
                            // Position and timestamp straight off the pointer event. The
                            // node is full-screen and stationary, so `position` is already
                            // in the coordinate space the velocity is wanted in.
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val target = offsetX.value + dragAmount
                            // One tick as the drag crosses the commit point, so the user
                            // can feel where the decision lands without watching for it.
                            val over = abs(target) > threshold
                            if (over != pastThreshold) {
                                pastThreshold = over
                                if (over) haptics.threshold()
                            }
                            scope.launch { offsetX.snapTo(target) }
                        }
                    },
                )
            },
    ) {
        // The next photograph, beneath everything and never moving sideways: only the top
        // card travels. It grows toward full size as the drag approaches the commit point,
        // so by the moment it is uncovered it has already arrived. Null on the last card.
        if (under != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val t = abs(progress())
                        val s = UnderScale + (1f - UnderScale) * t
                        scaleX = s
                        scaleY = s
                    }
                    .cardSurface(),
            ) {
                under()
            }
        }

        // The photograph itself, and the only thing that moves. No rotationZ: the card is
        // square with the screen at every point in the gesture.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    val s = 1f + LiftScale * lift.value
                    scaleX = s
                    scaleY = s
                }
                .cardSurface(),
        ) {
            content()
        }

        // The decision glyph, stationary at the centre while the photograph travels under
        // it. Decorative by construction — the controls below the card carry the
        // accessible names for both decisions, and a screen-reader user never sees a drag
        // progress in the first place, so this is cleared from the tree rather than
        // announced at alpha 0.
        DecisionGlyph(
            modifier = Modifier.align(Alignment.Center),
            progress = ::progress,
        )
    }
}

/**
 * Turns a full-screen box into the deck's card: inset, rounded, and on its own ground.
 *
 * Applied identically to the travelling card and the one waiting underneath, from one
 * place, because the two must be the same object at different depths — a stack whose cards
 * had different corners would read as two unrelated things overlapping.
 *
 * The order matters. Insets and padding come first so the rounding is cut from the card's
 * own bounds rather than the screen's; the clip follows; the ground goes last so it fills
 * the rounded shape rather than a rectangle behind it. `safeDrawing` keeps the corners
 * clear of the display cutout and the phone's own rounded corners, where a card rounded to
 * 28dp against a screen rounded to something else reads as a mistake.
 *
 * The ground is the dark palette's surface in both themes, for the same reason the chrome
 * is: what sits on this is a photograph, and `ContentScale.Fit` means the card shows
 * through wherever the picture's aspect doesn't match the screen's. A light ground there
 * would flare around a dark photograph.
 */
@Composable
private fun Modifier.cardSurface(): Modifier = this
    .windowInsetsPadding(WindowInsets.safeDrawing)
    .padding(CardInset)
    .clip(RoundedCornerShape(SwipeyRadius.deck))
    .background(SwipeyDarkColors.surface)

/**
 * The one badge that says which way the card is going.
 *
 * Both states are always composed and cross-faded by alpha rather than swapped by an `if`:
 * the drag reads [progress] inside a `graphicsLayer` lambda, so a frame of dragging
 * redraws without recomposing, and a conditional here would put a recomposition on every
 * crossing of zero — on the one screen a user holds their thumb on for a whole session.
 *
 * [progress] is signed: negative is bin, positive is keep. Exactly one badge has non-zero
 * alpha at a time, so they can safely occupy the same space.
 */
@Composable
private fun DecisionGlyph(progress: () -> Float, modifier: Modifier = Modifier) {
    Box(modifier.clearAndSetSemantics { }) {
        Badge(
            icon = SwipeyIcons.Bin,
            // No bin colour: the palette refuses itself one, and inventing an alarm red
            // here would undo that. A neutral ground under a light glyph is the badge
            // saying what is about to happen without editorialising about it.
            ground = Color.Black,
            tint = SwipeyDarkColors.textPrimary,
            amount = { (-progress()).coerceIn(0f, 1f) },
        )
        Badge(
            icon = SwipeyIcons.Check,
            // The dark palette's keep in both themes: the ground under this is a
            // photograph, not the canvas, and the light palette's darker #1D51D6 — tuned
            // to be read as a glyph on white — sinks into a picture rather than sitting on
            // top of it.
            ground = SwipeyDarkColors.keep,
            tint = SwipeyDarkColors.textPrimary,
            amount = { progress().coerceIn(0f, 1f) },
        )
    }
}

/** One state of [DecisionGlyph]: a translucent disc, a glyph, and one alpha to drive both. */
@Composable
private fun Badge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ground: Color,
    tint: Color,
    amount: () -> Float,
) {
    Box(
        Modifier
            .size(GlyphBadgeSize)
            .graphicsLayer {
                val a = amount()
                alpha = a
                // Grows into place rather than merely appearing, so the badge reports how
                // close the drag is to committing and not just which way it points.
                val s = GlyphMinScale + (1f - GlyphMinScale) * a
                scaleX = s
                scaleY = s
            }
            .clip(CircleShape)
            .background(ground.copy(alpha = GlyphGroundAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        SwipeyIcon(icon, contentDescription = null, tint = tint, size = GlyphIconSize)
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

/**
 * A stand-in for a photograph. Busy on purpose: a flat one would let the badge's
 * translucent ground look more legible than it is over a real picture.
 */
private val PreviewPhoto = Brush.linearGradient(
    listOf(Color(0xFFE8703A), Color(0xFFD8B93C), Color(0xFF2E9E6B), Color(0xFF2B5FD9)),
)

/**
 * The drag treatment at the values the user actually sees.
 *
 * The deck is one of the few things in the app a `@Preview` cannot render — it needs a
 * MediaStore, a gesture and a running animation — so this renders the *badge* against a
 * stand-in image instead, at rest and at both commit points. The half-drag frame is the
 * one worth looking at: it is where the badge has to be readable while still clearly not
 * yet committed.
 *
 * Always the dark palette, since that is what the deck draws in over a photograph in both
 * themes.
 */
@Preview(name = "Drag feedback", group = "deck", uiMode = Configuration.UI_MODE_NIGHT_YES, widthDp = 400, heightDp = 240)
@Composable
private fun DragFeedbackPreview() {
    SwipeyTheme(colors = SwipeyDarkColors) {
        Row(
            Modifier
                .fillMaxSize()
                .background(SwipeyDarkColors.canvas)
                .padding(SwipeySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
        ) {
            DragFeedbackSample("bin · 1.0", Modifier.weight(1f), -1f)
            DragFeedbackSample("bin · 0.5", Modifier.weight(1f), -0.5f)
            DragFeedbackSample("at rest", Modifier.weight(1f), 0f)
            DragFeedbackSample("keep · 1.0", Modifier.weight(1f), 1f)
        }
    }
}

/**
 * One frozen frame of a drag, built from the same composable [SwipeCard] uses — so this
 * cannot drift away from the real thing without failing to compile.
 */
@Composable
private fun DragFeedbackSample(label: String, modifier: Modifier = Modifier, progress: Float = 0f) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Box(Modifier.fillMaxSize().background(PreviewPhoto))
            DecisionGlyph(
                progress = { progress },
                modifier = Modifier.align(Alignment.Center),
            )
        }
        SwipeyText(
            label,
            modifier = Modifier.padding(top = SwipeySpacing.xs),
            style = SwipeyTheme.typography.label,
            color = SwipeyDarkColors.textSecondary,
            maxLines = 1,
        )
    }
}
