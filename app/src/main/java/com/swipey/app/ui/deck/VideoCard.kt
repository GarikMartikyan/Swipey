package com.swipey.app.ui.deck

import android.view.LayoutInflater
import android.view.TextureView
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.swipey.app.R
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.settings.LocalSettings
import kotlinx.coroutines.delay

/**
 * How far the video's controls sit in from the card's bottom edge.
 *
 * It used to be 112dp, to clear the deck's own Bin/Undo/Keep row — from when a video filled
 * the screen and those three circles were drawn on top of it. The card is inset now and
 * those controls sit below it on the page, so the clearance was holding the timeline a
 * third of the way up a picture for no one's benefit. The caption moving to the top edge is
 * what freed this one to go where a scrubber belongs: at the bottom, where a thumb is.
 */
private val ControlsInset = 16.dp

/** How often the scrubber asks the player where it is. */
private const val PositionPollMillis = 120L

/** The scrubber's track, and the thumb that rides it. */
private val TimelineTrack = 3.dp
private val TimelineThumb = 13.dp

/**
 * The shortest gap between two seeks while a finger is dragging the scrubber.
 *
 * 40ms — twenty-five repositions a second, which is already more than the eye resolves from a
 * moving thumb. A drag on a 120Hz display delivers events three times that fast, and every
 * one of them turned into a seek would be work the user cannot see: scrubbing mode makes a
 * seek cheap, not free.
 *
 * It throttles the *picture* only. The bar itself still follows every event, so the thumb
 * never lags the finger — which is the half of this that would be noticed.
 */
private const val ScrubSeekIntervalMillis = 40L

/**
 * The row the scrubber lives in. Taller than the track it draws, on purpose: the track is
 * 3dp and a 3dp drag target is not a target. The extra height is transparent, so what the
 * user sees is a hairline and what the user's thumb gets is [SwipeySize.touchMin].
 */
private val TimelineRow = 48.dp

/**
 * One video's playback, hoisted out of the composable that draws it.
 *
 * This exists because of the preview's pinch-zoom. The video surface belongs inside the
 * zoom layer — that is the thing being magnified — and the controls very much do not: a
 * scrubber that doubled in size and slid off the screen with the picture would be unusable
 * at exactly the moment someone is inspecting a frame. Two composables reading one holder
 * is what lets the same player be drawn in one layer and driven from another.
 *
 * Everything here is Compose state, so a change to any of it redraws the controls without
 * the caller wiring up listeners.
 */
@Stable
class VideoPlayback internal constructor(private val itemId: Long, muted: Boolean) {

    internal var player: ExoPlayer? by mutableStateOf(null)

    /** Per item: a new clip always starts playing, even if the last one was paused. */
    var playing: Boolean by mutableStateOf(true)
        private set

    /**
     * Deliberately *not* per item: sound is a decision about the session, not about one
     * clip. Someone who turns it on to hear what a video is should not have to turn it on
     * again for the next one.
     *
     * Which is why it arrives as a constructor argument rather than starting at a fixed
     * value and being corrected by an effect afterwards. A player built muted and unmuted a
     * frame later is a player that says the wrong thing for a frame — and, worse, gives the
     * effect that reports changes back to [VideoSound] a spurious first value to report.
     */
    var muted: Boolean by mutableStateOf(muted)
        internal set

    /** Where the clip is now, polled while it plays. */
    var positionMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * The clip's length as the *player* reports it, which is the only number that can be
     * trusted to line up with [positionMs]. MediaStore's duration column disagrees with it
     * by a frame or two often enough to leave a scrubber that never quite reaches its end.
     */
    var durationMs: Long by mutableLongStateOf(0L)
        internal set

    /**
     * Whether a finger is on the scrubber right now.
     *
     * Hoisted out of the timeline that tracks it because something else needs to know: the
     * ambient light behind the picture samples the surface only while the clip is moving,
     * and a scrub moves the picture without playing it. Without this, dragging the bar on a
     * paused video would slide the frame through a glow left over from wherever it was
     * paused. See [rememberVideoAmbient].
     */
    var scrubbing: Boolean by mutableStateOf(false)
        private set

    fun togglePlaying() {
        playing = !playing
    }

    /**
     * The sound button, on the card and in the preview alike.
     *
     * Reports the new value to [VideoSound] here rather than through an effect watching
     * [muted], because a tap is the *only* thing that changes it: an effect would also fire
     * for the seed, and for the second player the preview stands up over the first, and
     * would have to distinguish those from a decision. This cannot.
     */
    fun toggleMuted() {
        muted = !muted
        VideoSound.set(muted)
    }

    fun seekTo(ms: Long) {
        val clamped = clamp(ms)
        positionMs = clamped
        player?.seekTo(clamped)
        VideoResume.remember(itemId, clamped)
    }

    /**
     * A finger has landed on the scrubber.
     *
     * Puts the player into media3's scrubbing mode, which is what makes seeking on every
     * frame of a drag affordable at all: it drops audio, holds the codec open and seeks to
     * the nearest keyframe rather than decoding forward to an exact position. Without it
     * each seek is a full decoder flush, and a drag is a slideshow.
     *
     * The price is accuracy, and it is the right price: while the finger is down the user is
     * looking for a moment, not a frame, and [endScrub] pays the exact seek once on release.
     */
    fun beginScrub() {
        scrubbing = true
        player?.setScrubbingModeEnabled(true)
    }

    /**
     * Moves the picture to [ms] with the finger still down.
     *
     * Deliberately not [seekTo]: nothing here is remembered as a resume point, because an
     * intermediate position of a drag is not somewhere the user has chosen to be.
     */
    fun scrubTo(ms: Long) {
        val clamped = clamp(ms)
        positionMs = clamped
        player?.seekTo(clamped)
    }

    /** The finger has lifted: one exact seek, and back out of scrubbing mode. */
    fun endScrub(ms: Long) {
        seekTo(ms)
        player?.setScrubbingModeEnabled(false)
        scrubbing = false
    }

    private fun clamp(ms: Long) = ms.coerceIn(0L, if (durationMs > 0) durationMs else ms)
}

/**
 * Where a clip was when its player was last torn down.
 *
 * The deck card and the preview are two different composables holding two different
 * ExoPlayers — the card leaves composition when the preview opens, precisely so there is
 * never a second decoder and a second soundtrack. Without this, opening the preview to look
 * at something twenty seconds in would drop the user back at the start of the clip, and
 * closing it again would do the same in reverse.
 *
 * One entry, not a cache: exactly one video is alive at a time, so remembering more than
 * the last one would be holding state nothing can ask for.
 */
private object VideoResume {
    private var id: Long? = null
    private var positionMs: Long = 0L

    fun remember(itemId: Long, ms: Long) {
        id = itemId
        positionMs = ms
    }

    fun positionFor(itemId: Long): Long = if (id == itemId) positionMs else 0L
}

/**
 * Whether the sound is on, for this run of the app.
 *
 * ### Three claims, and this object is the second and third
 * Settings says which way a run *starts*. Turning the sound on or off during a clip carries
 * to the clips after it — sound is a decision about the session, not about one photograph,
 * and it always has been. And the next launch starts from Settings again, whatever was done
 * last time.
 *
 * That last claim is why this is an object and not a `rememberSaveable`. A saveable survives
 * an Activity recreation, which is right — a rotation is not a new run — but it *also*
 * survives process death, because Android hands the saved bundle back when a task is
 * resumed. The user would then find last week's mute still in force on a cold start, which
 * is exactly what "when I launch the app, apply the behaviour from settings" rules out. A
 * plain object dies with the process, which is the lifetime the promise is about.
 *
 * Nothing is written back to Settings. Muting one loud clip in a quiet room is a decision
 * about that moment; treating it as a change of mind about the app would leave the setting
 * drifting somewhere the user never put it.
 */
private object VideoSound {

    /** Null until the first clip of the run, when [mutedWith] seeds it from Settings. */
    private var muted: Boolean? = null

    /** The session's answer, seeding it from [soundOn] if this is the run's first clip. */
    fun mutedWith(soundOn: Boolean): Boolean = muted ?: (!soundOn).also { muted = it }

    fun set(value: Boolean) {
        muted = value
    }

    /**
     * Throws the session's answer away, so the next clip takes the setting again.
     *
     * Called when the user changes the setting itself. Anything else would mean opening
     * Settings, turning sound on, going back to the deck and finding it still silent —
     * a switch that visibly does nothing is worse than no switch.
     */
    fun forget() {
        muted = null
    }
}

/** Re-seeds video sound from Settings. Called when the setting is changed. */
fun forgetVideoSoundChoice() = VideoSound.forget()

/**
 * Builds the player for [item] and keeps it alive for as long as this composable is.
 *
 * Released on ON_STOP so a backgrounded app doesn't hold a hardware decoder for the whole
 * time it's backgrounded, and rebuilt on ON_START; also released if the caller leaves
 * composition entirely (fix round 1, Important 3).
 */
@Composable
fun rememberVideoPlayback(item: MediaItem): VideoPlayback {
    val context = LocalContext.current
    // The sound this run is running with. It survives the trip into the preview and back,
    // an Activity recreation, and every clip change for the rest of the run — but not the
    // run itself, which is the whole point. See [VideoSound], where all three halves of that
    // promise are argued.
    val soundOn = LocalSettings.current.videoSound
    val playback = remember(item.id) { VideoPlayback(item.id, VideoSound.mutedWith(soundOn)) }

    // The setting itself moved while the app was running. `forgetVideoSoundChoice` has
    // already thrown this run's answer away by the time this lands, so `mutedWith` re-seeds
    // — and it reaches the clip currently on screen rather than only the one after it,
    // because a switch that visibly does nothing is worse than no switch.
    LaunchedEffect(soundOn) { playback.muted = VideoSound.mutedWith(soundOn) }

    LifecycleStartEffect(item.id) {
        playback.player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(ExoMediaItem.fromUri(contentUriFor(item.id, isVideo = true)))
            repeatMode = Player.REPEAT_MODE_ALL
            volume = 0f
            playWhenReady = true
            // Where the last player of this clip left off — zero for a clip being opened
            // for the first time, which is what seekTo does with it anyway.
            seekTo(VideoResume.positionFor(item.id))
            prepare()
        }
        onStopOrDispose {
            val released = playback.player
            // Remembered before release: afterwards the position reads as zero, and the
            // preview would open at the start of every clip.
            released?.let { VideoResume.remember(item.id, it.currentPosition) }
            playback.player = null
            released?.release()
        }
    }

    // The player above is rebuilt on every item change and on every ON_START, always muted
    // and playing; this is what re-applies the user's own choices to whichever instance is
    // currently alive. Keyed on the player as well as the flags, so a rebuild after a
    // backgrounding does not silently discard them.
    LaunchedEffect(playback.player, playback.muted, playback.playing) {
        playback.player?.volume = if (playback.muted) 0f else 1f
        playback.player?.playWhenReady = playback.playing
    }

    // Polling rather than a Player.Listener, because there is no position callback to
    // listen to — ExoPlayer reports events, not a clock. It runs only while there is a
    // player and it is playing, so a paused card costs nothing; the loop ends with the
    // effect on every item change.
    LaunchedEffect(playback.player, playback.playing) {
        val player = playback.player ?: return@LaunchedEffect
        while (true) {
            val duration = player.duration
            // C.TIME_UNSET is Long.MIN_VALUE, and a live/unprepared clip reports it. Left
            // at zero until the real figure arrives, which the scrubber reads as "no
            // timeline yet" rather than drawing a bar of nonsense width.
            playback.durationMs = if (duration > 0) duration else 0L
            playback.positionMs = player.currentPosition
            if (!playback.playing) break
            delay(PositionPollMillis)
        }
    }

    return playback
}

/**
 * The picture, and nothing else.
 *
 * Everything that used to sit on top of this — the play glyph, the badge, the sound
 * control — now comes from [VideoControls], so a caller can put this inside a transformed
 * layer and the controls outside it. See [VideoPlayback].
 *
 * [ambient] is the other half of [VideoAmbientLayer]: the `TextureView` this puts on screen
 * is the only place a frame of the clip exists in a form anything else can read, so the
 * layer that lights the card is fed from here. Optional, and null costs nothing — the
 * sampling loop simply never finds a surface.
 */
@Composable
fun VideoSurface(
    playback: VideoPlayback,
    modifier: Modifier = Modifier,
    ambient: VideoAmbient? = null,
) {
    // The single PlayerView instance survives across item changes (same composition slot,
    // since `factory` only runs once) — tracked here so a new player can be bound to it in
    // `update`, and so an outgoing player can be detached from it before release.
    val boundView = remember { arrayOfNulls<PlayerView>(1) }

    AndroidView(
        modifier = modifier,
        // Inflated rather than constructed, purely to reach `surface_type`. See
        // res/layout/deck_player_view.xml: the default SurfaceView composites outside the
        // Compose draw pass, so the card's rounded clip would not apply to video.
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.deck_player_view, null) as PlayerView)
                .apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
                .also { boundView[0] = it }
        },
        update = { view ->
            // Rebind on every item/lifecycle change — `factory` only runs once, so without
            // this the view keeps showing (or freezes on) whatever player it was first
            // bound to (fix round 1, Critical 1).
            if (view.player !== playback.player) view.player = playback.player
            // Re-attached rather than attached once, for the same reason: `factory` runs
            // once per composition slot, and the ambient is a new object on every item.
            // The cast is a question, not an assumption — `surface_type` is XML, and a
            // layout edited back to a SurfaceView would leave nothing to sample rather
            // than crash the deck.
            ambient?.attach(view.videoSurfaceView as? TextureView)
        },
        onRelease = { view ->
            view.player = null
            ambient?.attach(null)
        },
    )
}

/**
 * Play/pause, where you are in the clip, and sound.
 *
 * ### It is a sibling of the picture, never a child of it
 * In the preview the picture sits in a `graphicsLayer` that pinch-zoom scales and pans.
 * This row is deliberately outside that layer, so magnifying a frame magnifies the frame:
 * the scrubber keeps its size, its position and — the part that actually matters — its
 * touch targets, which a 3× scale would otherwise have thrown off the bottom of the screen.
 *
 * ### The same control in both places, which it did not used to be
 * The card's timeline was read-only, and the reasoning was sound as far as it went: the deck
 * is a horizontal-swipe surface, a scrubber is a horizontal-drag control, and a live one
 * across the bottom of the card means a swipe that starts an inch too low seeks the video
 * instead of judging it. What it cost was worse — the only way to look inside a clip was to
 * open the preview, on the one screen whose whole job is deciding whether a clip is worth
 * keeping.
 *
 * So the row takes the drag on both surfaces, and the collision is settled where collisions
 * between gestures are settled: the timeline consumes the pointer, so the card's detector
 * never sees a drag that started on the bar, and a swipe has the other nine tenths of the
 * card to begin in. See [VideoTimeline].
 */
@Composable
fun VideoControls(
    playback: VideoPlayback,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoIconButton(
            icon = if (playback.playing) SwipeyIcons.Pause else SwipeyIcons.Play,
            label = if (playback.playing) Copy.VIDEO_PAUSE else Copy.VIDEO_PLAY,
            onClick = { playback.togglePlaying() },
        )

        SwipeyText(
            formatDuration(playback.positionMs),
            style = SwipeyTheme.typography.labelNumeric,
            color = SwipeyDarkColors.textPrimary,
            maxLines = 1,
        )

        VideoTimeline(
            playback = playback,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = SwipeySpacing.xs),
        )

        SwipeyText(
            formatDuration(playback.durationMs),
            style = SwipeyTheme.typography.labelNumeric,
            color = SwipeyDarkColors.textSecondary,
            maxLines = 1,
        )

        // An icon now, not a chip that spelled out "Muted" in words. The chip was the
        // widest object on the card and it stated a fact the glyph states by being a
        // speaker with a line through it — and next to a play/pause glyph, a word read as
        // a different kind of thing entirely.
        VideoIconButton(
            icon = if (playback.muted) SwipeyIcons.SoundOff else SwipeyIcons.SoundOn,
            label = if (playback.muted) Copy.VIDEO_UNMUTE else Copy.VIDEO_MUTE,
            onClick = { playback.toggleMuted() },
        )
    }
}

/**
 * The scrubber: a track, however much of it has been watched, and a thumb.
 *
 * ### The bar follows the finger; the picture follows the bar
 * While a drag is in progress the bar draws from the finger rather than from the player, and
 * that half has not changed: the position that comes back from a seek lands a keyframe away
 * from where it was asked for, and a bar drawn from the player would be pulled out from
 * under the thumb holding it.
 *
 * What has changed is the picture, which now seeks along with the drag instead of waiting
 * for release. That was not affordable before: an ordinary seek is a decoder flush, and one
 * per frame of a drag is a slideshow. media3's scrubbing mode is built for exactly this —
 * see [VideoPlayback.beginScrub] — and the seeks are throttled to
 * [ScrubSeekIntervalMillis] on top of it, because a 120Hz drag can ask for twice as many
 * repositions a second as any of this is worth.
 *
 * There is no thumbnail bubble above the bar, deliberately. The picture *is* the preview,
 * which is the whole point of scrubbing it live — and a bubble would need a second decoder
 * to fill, then cover part of the frame it was describing.
 *
 * A tap anywhere on the track jumps there. That is the one gesture people try first on a bar
 * like this, and without it the control would be a drag-only target on a surface where
 * everything else responds to a tap.
 */
@Composable
private fun VideoTimeline(
    playback: VideoPlayback,
    modifier: Modifier = Modifier,
) {
    val duration = playback.durationMs
    // Non-null only while a finger is down. Also the flag for "draw from the drag": the
    // player's own position is stale for as long as this is set.
    var scrub by remember(playback) { mutableStateOf<Float?>(null) }

    val played = when {
        scrub != null -> scrub!!
        duration > 0 -> (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f)
        else -> 0f
    }

    val seekable = duration > 0
    fun msAt(fraction: Float): Long = (fraction.coerceIn(0f, 1f) * duration).toLong()

    Box(
        modifier
            .height(TimelineRow)
            .semantics {
                contentDescription = Copy.videoElapsed(
                    formatDuration(playback.positionMs),
                    formatDuration(duration),
                )
            }
            .pointerInput(seekable) {
                if (!seekable) return@pointerInput
                detectTapGestures { offset -> playback.seekTo(msAt(offset.x / size.width)) }
            }
            .pointerInput(seekable) {
                if (!seekable) return@pointerInput
                // The last drag frame that was allowed to move the picture. A plain local:
                // it lives exactly as long as this gesture handler does, and nothing outside
                // the drag can read it. Timestamps come off the pointer events themselves,
                // so there is no clock to read and no assumption about frame rate.
                var lastSeekAt = 0L
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        val at = (offset.x / size.width).coerceIn(0f, 1f)
                        scrub = at
                        playback.beginScrub()
                        // The bar jumps to the finger, so the picture does too — otherwise
                        // the first thing a drag does is show a frame from wherever the
                        // playhead happened to be.
                        playback.scrubTo(msAt(at))
                        lastSeekAt = 0L
                    },
                    onDragEnd = {
                        playback.endScrub(msAt(scrub ?: played))
                        scrub = null
                    },
                    // A gesture the system takes away must still leave the player out of
                    // scrubbing mode and sitting where the finger left it.
                    onDragCancel = {
                        playback.endScrub(msAt(scrub ?: played))
                        scrub = null
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        // Consumed, so the card's own swipe detector never sees a drag that
                        // was aimed at this bar. That is what keeps a live scrubber on the
                        // deck from turning a low swipe into a seek — the row claims the
                        // gesture before the card can, and a swipe has the rest of the card
                        // to start in.
                        change.consume()
                        val next = ((scrub ?: played) + dragAmount / size.width).coerceIn(0f, 1f)
                        scrub = next
                        if (change.uptimeMillis - lastSeekAt >= ScrubSeekIntervalMillis) {
                            lastSeekAt = change.uptimeMillis
                            playback.scrubTo(msAt(next))
                        }
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(TimelineThumb)) {
            val track = TimelineTrack.toPx()
            val radius = track / 2f
            val centreY = size.height / 2f
            // Inset by the thumb's radius at both ends so a bar at 0 or 1 draws the thumb
            // inside the row rather than half outside it.
            val thumbR = TimelineThumb.toPx() / 2f
            val left = thumbR
            val right = size.width - thumbR
            val x = left + (right - left) * played

            drawRoundRect(
                color = SwipeyDarkColors.textPrimary.copy(alpha = 0.28f),
                topLeft = Offset(left, centreY - radius),
                size = Size((right - left).coerceAtLeast(0f), track),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            drawRoundRect(
                color = SwipeyDarkColors.textPrimary,
                topLeft = Offset(left, centreY - radius),
                size = Size((x - left).coerceAtLeast(0f), track),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
            )
            // A dark collar under the thumb, so it stays visible where the played and
            // unplayed halves of the track are both light — over a bright frame the
            // white-on-white edge is otherwise the one thing you cannot see.
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = thumbR + 1.5f, center = Offset(x, centreY))
            drawCircle(SwipeyDarkColors.textPrimary, radius = thumbR, center = Offset(x, centreY))
        }
    }
}

/** A glyph on a scrim disc, sized to the app's touch floor. The video chrome's one button. */
@Composable
private fun VideoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(SwipeySize.touchMin)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(VideoButtonDisc)
                .clip(CircleShape)
                .background(SwipeyDarkColors.surface.copy(alpha = 0.66f)),
            contentAlignment = Alignment.Center,
        ) {
            // The clickable above carries the name; announcing it twice would read the
            // control out as "Pause, Pause".
            SwipeyIcon(icon, contentDescription = null, tint = SwipeyDarkColors.textPrimary, size = VideoButtonIcon)
        }
    }
}

/** The visible disc inside the 48dp target, and the glyph inside that. */
private val VideoButtonDisc = 36.dp
private val VideoButtonIcon = 18.dp

/**
 * A video on the top card.
 *
 * Muted and looping by default — a session is dozens of clips long, and a wall of sound
 * every time one arrives is the fastest way to make someone put the phone down.
 *
 * The controls here are the same [VideoControls] the preview draws, and the timeline is live
 * on both: drag it and the picture seeks under your thumb. The bar consumes that drag, so the
 * card's swipe detector never sees it — which is what lets a swipe surface carry a
 * horizontal-drag control at all. See [VideoTimeline].
 *
 * ### The card is bigger than the picture
 * A photograph is cropped to the card and fills it. A video is fitted, so on a 16:9 clip the
 * picture is a band across the middle and the rest of the card used to be flat ground. It is
 * lit now — see [VideoAmbientLayer], which is drawn under the picture and sampled from it.
 * [dragProgress] is passed through to it so the light can lag behind a card being swiped.
 */
@Composable
fun VideoCard(
    item: MediaItem,
    tapTogglesPlayback: Boolean = true,
    dragProgress: () -> Float = { 0f },
) {
    val playback = rememberVideoPlayback(item)
    val ambient = rememberVideoAmbient(playback)

    Box(Modifier.fillMaxSize()) {
        // Under the picture, and under everything else: this is the ground the card stands
        // on, not a thing on top of it.
        //
        // `still` is the same URI the card underneath was already lit from, so a promoted
        // clip arrives lit and stays lit — the sampler needs a rendered surface before it
        // can read anything, and that is a beat later than the card appearing.
        VideoAmbientLayer(
            ambient,
            still = contentUriFor(item.id, isVideo = true),
            dragProgress = dragProgress,
        )

        VideoSurface(playback, Modifier.fillMaxSize(), ambient = ambient)

        // The whole frame is the play/pause control — but only where playback is the thing
        // a tap should mean. On the deck it is not: a tap there opens the preview, because
        // the card is cropped and seeing the whole frame is the more urgent question while
        // deciding. The two cannot both own the tap.
        //
        // `clickable` rather than a raw tap gesture on purpose: it carries the semantics
        // with it, so the gesture has a button equivalent by construction. It consumes taps
        // only — a horizontal drag passes straight through to the card's own gesture
        // detector above it.
        if (tapTogglesPlayback) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClickLabel = if (playback.playing) Copy.VIDEO_PAUSE else Copy.VIDEO_PLAY,
                        role = Role.Button,
                    ) {
                        playback.togglePlaying()
                    },
            )
        }

        // Chrome over a photograph is dark-palette in both themes: the ground under it is
        // the video frame, not the canvas.
        SwipeyTheme(colors = SwipeyDarkColors) {
            Box(Modifier.fillMaxSize()) {
                if (!playback.playing) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(SwipeyTheme.colors.scrim),
                        contentAlignment = Alignment.Center,
                    ) {
                        // Decorative: the surface behind it is the control, and it already
                        // announces "Play".
                        SwipeyIcon(
                            SwipeyIcons.Play,
                            contentDescription = null,
                            tint = SwipeyTheme.colors.textPrimary,
                            size = 32.dp,
                        )
                    }
                }

                VideoControls(
                    playback = playback,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = SwipeySpacing.sm)
                        .padding(bottom = ControlsInset),
                )
            }
        }
    }
}

// The duration badge that used to sit here is gone: the controls row states the clip's
// length as the right-hand end of its own timeline, and a pill repeating the same figure
// two inches away was the card saying one thing twice.

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
