package com.swipey.app.ui.deck

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.theme.KeepGreen
import com.swipey.app.ui.theme.MarkRed
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * The top card. Commits at 30% of screen width or on a fling.
 * onSwiped(itemId, keep) — keep = true is kept (right), false is marked (left). The
 * id is reported alongside the decision so a caller can detect and ignore a decision
 * that no longer applies to whatever card is now current (fix round 1, Critical 2).
 * onCommittingChanged reports whether a decision is currently mid-animation, so the
 * caller can disable any other way of recording a decision (e.g. buttons) until it
 * resolves.
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
    val threshold = screenWidthPx * 0.3f
    val offsetX = remember(itemId) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var velocity by remember(itemId) { mutableFloatStateOf(0f) }

    // Non-null once a drag (or fling) has committed this card to a decision. Kept in
    // Compose state — rather than a plain local in the gesture callback — so it can
    // both key the commit LaunchedEffect below (so a re-key cancels it cleanly) and
    // report "committing" upward to gate external ways of recording a decision.
    var pendingKeep by remember(itemId) { mutableStateOf<Boolean?>(null) }
    val fired = remember(itemId) { booleanArrayOf(false) }

    LaunchedEffect(itemId) { offsetX.snapTo(0f) }

    SideEffect { onCommittingChanged(pendingKeep != null) }

    // Runs the fly-off animation and then reports the decision. Keyed on
    // (itemId, pendingKeep): if the card is re-keyed for the next item before this
    // finishes, this coroutine is cancelled rather than surviving to call onSwiped
    // against the new current item (fix round 1, Critical 2).
    LaunchedEffect(itemId, pendingKeep) {
        val keep = pendingKeep ?: return@LaunchedEffect
        offsetX.animateTo(
            if (keep) screenWidthPx * 1.5f else -screenWidthPx * 1.5f,
            tween(220),
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

    val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = progress * 12f
            }
            .clip(RoundedCornerShape(16.dp))
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
                        val committed = overThreshold || abs(velocity) > 1500f
                        if (committed) {
                            // Past the threshold, position is the trustworthy signal.
                            // Otherwise this only committed via the fling check, so
                            // take the direction from the fling itself — position
                            // alone (e.g. +20px after a sharp leftward flick) can
                            // disagree with it (fix round 1, Important 2).
                            pendingKeep = if (overThreshold) offsetX.value > 0 else velocity > 0
                        } else {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                        velocity = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        if (pendingKeep == null) {
                            velocity = dragAmount * 60f
                            scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                        }
                    },
                )
            },
    ) {
        content()
        if (progress != 0f) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background((if (progress > 0) KeepGreen else MarkRed).copy(alpha = abs(progress) * 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (progress > 0) "KEEP" else "BIN",
                    // Match the backdrop's fade-in instead of popping in at full
                    // opacity for any non-zero drag (fix round 1, Minor 2).
                    modifier = Modifier.graphicsLayer { alpha = abs(progress) },
                )
            }
        }
    }
}
