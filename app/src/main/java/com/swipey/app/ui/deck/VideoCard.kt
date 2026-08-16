package com.swipey.app.ui.deck

import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem

@Composable
fun VideoCard(item: MediaItem) {
    val context = LocalContext.current

    // One ExoPlayer alive at a time, bound only to the top card. Held in state (not
    // a plain `remember`) so `update` below can react to it changing.
    var player by remember(item.id) { mutableStateOf<ExoPlayer?>(null) }

    // The single PlayerView instance survives across item changes (same composition
    // slot, since `factory` only runs once) — tracked here so a new player can be
    // bound to it in `update`, and so an outgoing player can be detached from it
    // before release.
    val boundView = remember { arrayOfNulls<PlayerView>(1) }

    // Released on ON_STOP so a backgrounded app doesn't hold a hardware decoder for
    // the whole time it's backgrounded, and rebuilt on ON_START; also released if
    // the card leaves composition entirely (fix round 1, Important 3).
    LifecycleStartEffect(item.id) {
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(contentUriFor(item.id, isVideo = true)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            prepare()
        }
        onStopOrDispose {
            val released = player
            // Detach before release so the view is never left holding a reference
            // to a released player (fix round 1, Critical 1).
            if (boundView[0]?.player === released) {
                boundView[0]?.player = null
            }
            released?.release()
            player = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }.also { boundView[0] = it }
            },
            update = { view ->
                // Rebind on every item/lifecycle change — `factory` only runs once,
                // so without this the view keeps showing (or freezes on) whatever
                // player it was first bound to (fix round 1, Critical 1).
                if (view.player !== player) view.player = player
            },
        )
        item.durationMs?.let { duration ->
            Text(
                formatDuration(duration),
                Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
        }
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
