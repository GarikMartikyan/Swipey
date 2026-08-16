package com.swipey.app.ui.deck

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyChip
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/**
 * How far the video's own controls sit above the bottom edge, clearing the deck's
 * Bin/Undo/Keep row (32dp of top padding + a 56dp control + 16dp of bottom padding).
 * A fixed number is honest here: those three controls are circles of a fixed size, so
 * the row's height does not grow with the user's font scale.
 */
private val DeckControlsClearance = 112.dp

/**
 * A video on the top card.
 *
 * Muted and looping by default — a session is dozens of clips long, and a wall of sound
 * every time one arrives is the fastest way to make someone put the phone down. The
 * whole surface is the play/pause control, and the sound toggle sits with the duration
 * badge above the deck's own controls.
 */
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

    // Deliberately *not* keyed on item.id: sound is a decision about the session, not
    // about one clip. Someone who turns it on to hear what a video is should not have
    // to turn it on again for the next one.
    var muted by rememberSaveable { mutableStateOf(true) }

    // Playback, on the other hand, is per item: a new video always starts playing, even
    // if the user paused the previous one to look at a frame.
    var playing by rememberSaveable(item.id) { mutableStateOf(true) }

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

    // The player above is rebuilt on every item change and on every ON_START, always
    // muted and playing; this is what re-applies the user's own choices to whichever
    // instance is currently alive. Keyed on the player as well as the flags, so a
    // rebuild after a backgrounding does not silently discard them.
    LaunchedEffect(player, muted, playing) {
        player?.volume = if (muted) 0f else 1f
        player?.playWhenReady = playing
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

        // The whole frame is the play/pause control. `clickable` rather than a raw tap
        // gesture on purpose: it carries the semantics with it, so the gesture has a
        // button equivalent by construction rather than needing a second control drawn
        // somewhere. It consumes taps only — a horizontal drag passes straight through
        // to the card's own gesture detector above it.
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = if (playing) Copy.VIDEO_PAUSE else Copy.VIDEO_PLAY,
                    role = Role.Button,
                ) {
                    playing = !playing
                },
        )

        // Chrome over a photograph is dark-palette in both themes: the ground under it
        // is the video frame, not the canvas.
        SwipeyTheme(colors = SwipeyDarkColors) {
            Box(Modifier.fillMaxSize()) {
                if (!playing) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SwipeyTheme.colors.scrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Decorative: the surface behind it is the control, and it
                        // already announces "Play".
                        SwipeyIcon(
                            SwipeyIcons.Play,
                            contentDescription = null,
                            tint = SwipeyTheme.colors.textPrimary,
                            size = 32.dp,
                        )
                    }
                }

                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                        .padding(bottom = DeckControlsClearance),
                    horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item.durationMs?.let { duration ->
                        DurationBadge(formatDuration(duration))
                    }
                    SwipeyChip(
                        text = if (muted) Copy.VIDEO_MUTED else Copy.VIDEO_SOUND_ON,
                        tabular = false,
                        onClick = { muted = !muted },
                    )
                }
            }
        }
    }
}

/** The clip's length, in a scrim-backed pill — the same caption the grids use. */
@Composable
private fun DurationBadge(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(SwipeyRadius.pill))
            .background(SwipeyTheme.colors.scrim)
            .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.sm),
    ) {
        SwipeyText(
            text,
            style = SwipeyTheme.typography.labelNumeric,
            color = SwipeyTheme.colors.textPrimary,
            maxLines = 1,
        )
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
