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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
 * onSwiped(true) = kept (right), onSwiped(false) = marked (left).
 */
@Composable
fun SwipeCard(
    key: Any,
    onSwiped: (keep: Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    val screenWidthPx = with(LocalDensity.current) {
        LocalConfiguration.current.screenWidthDp.dp.toPx()
    }
    val threshold = screenWidthPx * 0.3f
    val offsetX = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var velocity by remember(key) { mutableFloatStateOf(0f) }

    LaunchedEffect(key) { offsetX.snapTo(0f) }

    val progress = (offsetX.value / threshold).coerceIn(-1f, 1f)

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationX = offsetX.value
                rotationZ = progress * 12f
            }
            .clip(RoundedCornerShape(16.dp))
            .pointerInput(key) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        val committed = abs(offsetX.value) > threshold || abs(velocity) > 1500f
                        if (committed) {
                            val keep = offsetX.value > 0
                            scope.launch {
                                offsetX.animateTo(
                                    if (keep) screenWidthPx * 1.5f else -screenWidthPx * 1.5f,
                                    tween(220),
                                )
                                onSwiped(keep)
                            }
                        } else {
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                        velocity = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        velocity = dragAmount * 60f
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
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
                Text(if (progress > 0) "KEEP" else "BIN")
            }
        }
    }
}
