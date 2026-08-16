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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyMotion
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
private const val ExitMillis = 220

/** Degrees of tilt at full drag. Enough to feel like paper, not enough to read as a trick. */
private const val MaxTiltDegrees = 12f

/** How far in from the edge the keep glow reaches, as a fraction of screen width. */
private const val GlowReach = 0.42f

/** The keep glow's opacity at full drag. */
private const val GlowAlpha = 0.5f

/**
 * How dark the photograph goes at a full leftward drag, on top of losing its colour.
 *
 * Enough to read as the picture withdrawing; not so much that the user can no longer see
 * what they are about to bin, which would defeat the entire screen.
 */
private const val BinDimAlpha = 0.4f

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
 * ### What the user sees, and why the two directions don't match
 * The decisions get deliberately asymmetric feedback, because they are not mirror images
 * of one another.
 *
 * **Keep** warms the right edge of the *screen* to [SwipeyDarkColors.keep] as the drag
 * progresses — an edge glow rather than a wash over the photograph, since the photo is
 * the thing being judged and tinting it changes the very thing the user is looking at in
 * order to decide.
 *
 * **Bin** has no colour at all, and that is the palette's central claim rather than an
 * omission. Instead the photograph itself desaturates and dims in step with the drag, so
 * that at the commit point it is fully grey and [BinDimAlpha] darker. The picture
 * visibly leaving is a truer account of what the swipe does than any tint could be — the
 * item goes to the system trash and comes back from the Bin — and it is the one form of
 * feedback that cannot be misread as a warning.
 *
 * The glow and the BIN/KEEP marks are siblings of the moving card, not children of it:
 * they stay put while the photograph travels, which is what makes them read as edges of
 * the *screen* rather than as decoration on the card. The desaturation is the single
 * exception — it belongs to the picture, so it travels with it.
 *
 * A video dims but does not desaturate: ExoPlayer draws into a `SurfaceView` on its own
 * composited layer, which a `ColorMatrix` applied inside the Compose canvas cannot reach.
 * The scrim, drawn over the top, still lands, so the direction of travel still reads.
 *
 * ### Why nothing here recomposes while a finger is down
 * Every read of the drag offset lives inside a `graphicsLayer`, `drawBehind` or
 * `drawWithCache` lambda, which the compose runtime defers to the layout/draw phase. A
 * frame of dragging therefore redraws, but does not recompose — which matters on the one
 * screen a user holds their thumb on for an entire session. The `Paint` and `ColorMatrix`
 * the bin treatment needs are built once per size in the cache block for the same reason:
 * a drag allocates nothing.
 */
@Composable
fun SwipeCard(
    itemId: Long,
    onSwiped: (itemId: Long, keep: Boolean) -> Unit,
    onCommittingChanged: (Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * CommitFraction
    val offsetX = remember(itemId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = rememberSwipeyHaptics()
    var velocity by remember(itemId) { mutableFloatStateOf(0f) }

    // 0f..1f as the incoming card settles into place. Reset per item, so each new
    // photograph rises in rather than appearing fully formed where the last one was.
    val entry = remember(itemId) { Animatable(0f) }

    // Whether the drag is currently past the commit point. Tracked only to fire the
    // threshold haptic once per crossing rather than on every frame beyond it.
    var pastThreshold by remember(itemId) { mutableStateOf(false) }

    // Non-null once a drag (or fling) has committed this card to a decision. Kept in
    // Compose state — rather than a plain local in the gesture callback — so it can
    // both key the commit LaunchedEffect below (so a re-key cancels it cleanly) and
    // report "committing" upward to gate external ways of recording a decision.
    var pendingKeep by remember(itemId) { mutableStateOf<Boolean?>(null) }
    val fired = remember(itemId) { booleanArrayOf(false) }

    LaunchedEffect(itemId) {
        offsetX.snapTo(0f)
        entry.snapTo(0f)
        entry.animateTo(1f, SwipeyMotion.cardSettle())
    }

    // Read in composition, deliberately: this is what subscribes the composable to
    // `pendingKeep`, and therefore what guarantees the SideEffect below re-runs — and
    // reports — the instant a decision starts committing. Reading it inside the
    // SideEffect lambda instead would not subscribe to anything.
    val committing = pendingKeep != null
    SideEffect { onCommittingChanged(committing) }

    // Runs the fly-off animation and then reports the decision. Keyed on
    // (itemId, pendingKeep): if the card is re-keyed for the next item before this
    // finishes, this coroutine is cancelled rather than surviving to call onSwiped
    // against the new current item (fix round 1, Critical 2).
    LaunchedEffect(itemId, pendingKeep) {
        val keep = pendingKeep ?: return@LaunchedEffect
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
                    onDragEnd = {
                        // A decision is already committed and animating off-screen;
                        // ignore anything further so it can't be re-committed with a
                        // different value while the LaunchedEffect above is in flight.
                        if (pendingKeep != null) {
                            velocity = 0f
                            return@detectHorizontalDragGestures
                        }
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
                        velocity = 0f
                    },
                    // Without this a gesture the system takes away (a second pointer, a
                    // navigation drag from the edge) leaves the card stranded off-centre
                    // with no decision made and no way back but another drag.
                    onDragCancel = {
                        velocity = 0f
                        if (pendingKeep == null) {
                            pastThreshold = false
                            scope.launch { offsetX.animateTo(0f, SwipeyMotion.cardSettle()) }
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (pendingKeep == null) {
                            velocity = dragAmount * 60f
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
        // The photograph itself, and the only thing that moves.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationX = offsetX.value
                    rotationZ = progress() * MaxTiltDegrees
                    // Coerced because cardSettle() is underdamped and overshoots 1f;
                    // scale is happy to overshoot, alpha is not.
                    alpha = entry.value.coerceIn(0f, 1f)
                    val rise = 0.98f + 0.02f * entry.value
                    scaleX = rise
                    scaleY = rise
                }
                // Applied inside the layer above, so the drain travels with the
                // photograph rather than staying put like the glow and the marks.
                .binDrain { -progress() },
        ) {
            content()
        }

        // The keep glow, stationary, over the edge the card is heading toward. It has no
        // counterpart on the left: binning is the drain above, and adding a second colour
        // here is exactly what this palette refuses to do.
        Box(Modifier.fillMaxSize().keepGlow { progress() })

        // The two marks. Decorative by construction — the controls below the card carry
        // the accessible names for both decisions, and a screen-reader user never sees
        // a drag progress in the first place, so these are cleared from the tree rather
        // than announced at alpha 0.
        DecisionMark(
            text = "BIN",
            // textPrimary, not an accent. There is no bin colour to reach for, and
            // inventing one at this call site would undo the point of the palette; the
            // photograph draining behind the word is the signal, and the word only names
            // it. Restrained on purpose — this is a label, not a verdict.
            color = SwipeyDarkColors.textPrimary,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = SwipeySpacing.xl)
                .graphicsLayer { alpha = (-progress()).coerceIn(0f, 1f) },
        )
        DecisionMark(
            text = "KEEP",
            color = SwipeyDarkColors.keep,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = SwipeySpacing.xl)
                .graphicsLayer { alpha = progress().coerceIn(0f, 1f) },
        )
    }
}

/**
 * Drains the content of colour and then of light, as [leaving] runs `0f..1f`.
 *
 * This is the whole of Swipey's bin feedback. At `1f` — the commit point — the picture is
 * fully grey and [BinDimAlpha] darker, which is what "on its way out" looks like in a
 * palette that has refused itself a colour to say it with.
 *
 * [leaving] is a lambda rather than a value, and is read inside the draw lambda on
 * purpose: a frame of dragging redraws without recomposing. Values outside `0f..1f` are
 * coerced, so a caller can hand this a raw signed drag progress. The `Paint` and
 * `ColorMatrix` are built once per size rather than once per frame, so a drag allocates
 * nothing.
 */
private fun Modifier.binDrain(leaving: () -> Float): Modifier = drawWithCache {
    val paint = Paint()
    val matrix = ColorMatrix()
    val bounds = Rect(Offset.Zero, size)
    onDrawWithContent {
        val p = leaving().coerceIn(0f, 1f)
        if (p == 0f) {
            // The overwhelmingly common frame, and the only one that costs nothing: no
            // offscreen layer, no filter, no scrim.
            drawContent()
            return@onDrawWithContent
        }
        // Colour goes first and light second, so it reads as the picture draining rather
        // than as a lamp being switched off.
        matrix.setToSaturation(1f - p)
        paint.colorFilter = ColorFilter.colorMatrix(matrix)
        drawIntoCanvas { canvas ->
            canvas.saveLayer(bounds, paint)
            drawContent()
            canvas.restore()
        }
        drawRect(Color.Black, alpha = p * BinDimAlpha)
    }
}

/**
 * Warms the right edge to [SwipeyDarkColors.keep] as [arriving] runs `0f..1f`.
 *
 * The dark palette's accent in both themes: the ground under this is a photograph, not
 * the canvas, so the light palette's darker `#1D51D6` — tuned to be read as a glyph on
 * white — would sink into the picture rather than glow over it. Values at or below `0f`
 * draw nothing, so a caller can hand this a raw signed drag progress.
 */
private fun Modifier.keepGlow(arriving: () -> Float): Modifier = drawBehind {
    val p = arriving().coerceAtMost(1f)
    if (p <= 0f) return@drawBehind
    val reach = size.width * GlowReach
    drawRect(
        Brush.horizontalGradient(
            listOf(Color.Transparent, SwipeyDarkColors.keep.copy(alpha = p * GlowAlpha)),
            startX = size.width - reach,
            endX = size.width,
        ),
    )
}

/** One of the two edge marks. Letter-spaced caps, so it reads as a label, not a shout. */
@Composable
private fun DecisionMark(text: String, color: Color, modifier: Modifier = Modifier) {
    SwipeyText(
        text = text,
        modifier = modifier.clearAndSetSemantics { },
        style = SwipeyTheme.typography.title.copy(letterSpacing = 4.sp),
        color = color,
        maxLines = 1,
    )
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

/**
 * A stand-in for a photograph. Saturated on purpose: a muted one would make the bin
 * treatment look like it was doing nothing.
 */
private val PreviewPhoto = Brush.linearGradient(
    listOf(Color(0xFFE8703A), Color(0xFFD8B93C), Color(0xFF2E9E6B), Color(0xFF2B5FD9)),
)

/**
 * Both drag treatments, side by side, at the values the user actually sees.
 *
 * The deck is one of the few things in the app a `@Preview` cannot render — it needs a
 * MediaStore, a gesture and a running animation — so this renders the two *treatments*
 * against a stand-in image instead. It is the only place the asymmetry is visible at a
 * glance: one direction gains a colour, the other loses one.
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
            DragFeedbackSample("bin · 1.0", Modifier.weight(1f), bin = 1f)
            DragFeedbackSample("bin · 0.5", Modifier.weight(1f), bin = 0.5f)
            DragFeedbackSample("at rest", Modifier.weight(1f))
            DragFeedbackSample("keep · 1.0", Modifier.weight(1f), keep = 1f)
        }
    }
}

/**
 * One frozen frame of a drag, built from the same modifiers [SwipeCard] uses — so this
 * cannot drift away from the real thing without failing to compile.
 */
@Composable
private fun DragFeedbackSample(
    label: String,
    modifier: Modifier = Modifier,
    bin: Float = 0f,
    keep: Float = 0f,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.weight(1f).fillMaxWidth()) {
            // The image goes *inside* the drain rather than behind it: `binDrain` filters
            // the content it wraps, and a `background` modifier sits outside that.
            Box(Modifier.fillMaxSize().binDrain { bin }) {
                Box(Modifier.fillMaxSize().background(PreviewPhoto))
            }
            // A sibling above the picture, exactly as in the card.
            Box(Modifier.fillMaxSize().keepGlow { keep })
            if (bin > 0f) {
                DecisionMark(
                    text = "BIN",
                    color = SwipeyDarkColors.textPrimary,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = bin },
                )
            }
            if (keep > 0f) {
                DecisionMark(
                    text = "KEEP",
                    color = SwipeyDarkColors.keep,
                    modifier = Modifier.align(Alignment.Center).graphicsLayer { alpha = keep },
                )
            }
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
