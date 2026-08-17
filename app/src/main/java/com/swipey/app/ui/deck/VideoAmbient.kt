package com.swipey.app.ui.deck

import android.graphics.Bitmap
import android.view.TextureView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The light a video throws into the space it doesn't fill.
 *
 * ### The problem
 * The deck's card is a fixed 7:10 rectangle and a video is *fitted* into it rather than
 * cropped — see `MediaCardContent`, and `VideoSurface`'s `AspectRatioFrameLayout` underneath
 * it. A photograph is cropped and fills the card; a video cannot be, because the edges of a
 * clip are not recoverable by looking harder, and a 16:9 clip in an upright card would lose
 * roughly 60% of its width. So it is fitted, and the arithmetic of that is stark: a 16:9 clip
 * covers about 39% of the card's height, and the other 61% was flat `surface` — a black band
 * above the picture and another below it, with the caption sitting on one and the scrubber on
 * the other.
 *
 * ### What this does
 * Fills that space with the clip's own light: the current frame, blown up to cover the whole
 * card and blurred past the point of being a picture, at [AmbientAlpha] over the card's
 * ground, with the sharp fitted frame drawn on top of it. The bands stop being absence and
 * become the room the video is playing in — and because it is sampled live, the light moves
 * with the clip: a wave breaks and the whole card goes pale, a cut to night and it goes out.
 *
 * ### Why it costs almost nothing
 * The frame is read at [AmbientSampleEdge] pixels on its long edge — about 1,300 pixels, once
 * every [AmbientSampleMillis] — straight off the `TextureView` the player is already drawing
 * into. There is no second decoder, no second player, and nothing is read from MediaStore. An
 * ExoPlayer has exactly one output surface, so a blurred *copy* of the video was never
 * available; this is the same trick from the other direction, and it is why the deck can
 * afford it. It is also the same idea as [rememberTopStripLuma], which already reads a
 * thumbnail to decide how dark the caption's ground has to be — that one asks a still frame a
 * question once, this one asks the live surface twelve times a second.
 *
 * The upscale from 48 pixels does most of the blurring by itself, and [AmbientBlur] is there
 * to take the bilinear tent artefacts off it rather than to do the work. Sampling is the
 * throttle on the whole thing: the layer is only re-rendered — and therefore only re-blurred —
 * when a new frame lands, so the expensive part runs at twelve frames a second and not sixty.
 *
 * ### There is no idle animation, deliberately
 * An earlier pass drifted the bloom on a slow sine so the card was never quite still. It is
 * gone: it put a 60fps redraw *and* a re-blur on the one screen a user holds their thumb on
 * for an entire session, and it made a paused video look like it was still playing. Light in
 * a room does not drift when the picture is frozen. The bloom moves when the clip moves, and
 * when the user drags — and otherwise it is still.
 */

/** How many pixels of the frame's long edge the light is built from. */
internal const val AmbientSampleEdge = 48

/**
 * How often the surface is asked for a frame.
 *
 * ~12 per second. Fast enough that a cut is not something you can catch happening, slow
 * enough that the readback and the blur behind it are a rounding error. The light is a mood,
 * not a mirror; there is nothing in it that needs 60fps to be legible.
 */
private const val AmbientSampleMillis = 80L

/** Takes the last of the bilinear stair-stepping off a 48px frame blown up 20×. */
private val AmbientBlur = 24.dp

/**
 * How much of the card's ground the light is allowed to take.
 *
 * The ceiling is the chrome, not taste. The caption sits on the top band and the scrubber on
 * the bottom one, both in the dark palette, and both were contrast-checked against
 * `SwipeyDarkColors.surface`. At full strength a bright frame lifts those bands to something
 * white type cannot be read on. 0.62 is the point where a sunlit frame still leaves the
 * scrubber's unplayed track visible against it.
 */
private const val AmbientAlpha = 0.62f

/**
 * Colour, pushed a little past what the frame reports.
 *
 * Averaging 1,300 pixels of anything walks it toward grey, and grey light reads as a dirty
 * screen rather than as a glow. The boost puts back roughly what the downscale took.
 */
private const val AmbientSaturation = 1.5f

/**
 * How far past the card the light is drawn.
 *
 * The blur samples outward, so a layer drawn exactly to the card's bounds fades to nothing at
 * its own edges and leaves a dark rim inside the corners. The overscan puts the fade off the
 * card, and gives [AmbientDrift] somewhere to travel without dragging that rim into view.
 */
private const val AmbientOverscan = 1.25f

/**
 * How far the light lags behind a card being dragged.
 *
 * The card travels and the bloom does not travel with it — quite: it slides back by up to
 * this much as the drag approaches the commit point, so the light reads as belonging to the
 * screen the card is being dragged across rather than as a texture painted on the card. It is
 * the one thing here the user drives directly, and it is deliberately small; the drag already
 * has a tilt, a lift and a badge answering it, and a fourth voice shouting would be noise.
 *
 * Driven by the clamped drag progress rather than the raw offset, so it is bounded by
 * construction and cannot slide the overscan off the card during the fly-off.
 */
private val AmbientDrift = 20.dp

/**
 * The size of the bitmap a frame is sampled into.
 *
 * Sized from the *surface*, not from the player's `videoSize`: the `TextureView` is laid out
 * by an `AspectRatioFrameLayout`, so its bounds are already the fitted picture's bounds —
 * rotation applied, pixel aspect applied, letterbox excluded. Asking it for a bitmap of the
 * same shape is what keeps the light from being stretched sideways relative to the frame it
 * came from.
 *
 * Returns null for a surface that has not been measured yet, which is the state the first
 * sample after an item change arrives in.
 */
internal fun ambientSampleSize(width: Int, height: Int, longEdge: Int = AmbientSampleEdge): IntSize? {
    if (width <= 0 || height <= 0) return null
    val edge = longEdge.coerceAtLeast(1)
    // Nothing is gained by asking a small surface for more pixels than it has.
    if (width <= edge && height <= edge) return IntSize(width, height)
    return if (width >= height) {
        IntSize(edge, (edge.toFloat() * height / width).roundToInt().coerceAtLeast(1))
    } else {
        IntSize((edge.toFloat() * width / height).roundToInt().coerceAtLeast(1), edge)
    }
}

/**
 * One video's light, hoisted out of the composable that draws it — for the same reason
 * [VideoPlayback] is: the surface being sampled lives inside the zoom layer in the preview,
 * and the layer being lit sits outside it.
 *
 * [frame] is Compose state, so a new sample redraws the light without the caller listening
 * for anything.
 */
@Stable
class VideoAmbient internal constructor() {

    /** The last frame read off the surface, already small. Null until the first one lands. */
    var frame: ImageBitmap? by mutableStateOf(null)
        private set

    /** Set by [VideoSurface]; null whenever no surface is attached. */
    private var surface: TextureView? = null

    /**
     * Two bitmaps, used in turn.
     *
     * The alternative is one bitmap re-filled in place, which is a frame being overwritten by
     * the sampler while the draw phase is reading it — a tear, on a surface where the whole
     * point is that nothing flickers. Two is enough: a sample is 80ms apart and a draw is
     * over in one.
     */
    private val buffers = arrayOfNulls<Bitmap>(2)
    private var next = 0

    internal fun attach(view: TextureView?) {
        surface = view
        if (view == null) release()
    }

    /** Drops both buffers and the published frame. Called when the surface goes away. */
    private fun release() {
        frame = null
        buffers.fill(null)
    }

    /**
     * Reads the surface once, if there is one and it is ready.
     *
     * Silent on failure, like [rememberTopStripLuma]'s decode: a frame that will not copy is
     * not a reason to fail the card, and the light simply holds its last value. `getBitmap`
     * throws if the rendering context has been lost — during a backgrounding, say, which is
     * exactly when nobody is looking at the card anyway.
     */
    internal fun capture() {
        val view = surface ?: return
        if (!view.isAvailable) return
        val size = ambientSampleSize(view.width, view.height) ?: return

        val slot = next
        var buffer = buffers[slot]
        if (buffer == null || buffer.width != size.width || buffer.height != size.height) {
            buffer = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
            buffers[slot] = buffer
        }

        val filled = runCatching { view.getBitmap(buffer) }.getOrNull() ?: return
        next = (slot + 1) % buffers.size
        frame = filled.asImageBitmap()
    }
}

/**
 * Builds the light for [playback] and keeps it fed for as long as this composable is composed.
 *
 * Remembered against the playback rather than the item, which amounts to the same thing —
 * [rememberVideoPlayback] is itself keyed on the item — but says the real dependency: this
 * samples *that* player's surface, and a new player means a frame from a different clip.
 * Getting it wrong would leave the previous clip's light behind the new one's picture for a
 * fifth of a second on every swipe.
 *
 * The loop stops when the picture stops moving, after one last sample: a still frame's light
 * is correct and will stay correct, and polling a frozen surface twelve times a second is
 * work with no output. It resumes on the next change to either flag, which re-keys the
 * effect.
 *
 * Two flags rather than one, because there are two ways for a clip to be moving.
 * [VideoPlayback.scrubbing] is the second: a drag on the timeline seeks the picture without
 * playing it, and a light that only followed playback would leave the frame sliding under a
 * glow left over from wherever the clip was paused.
 */
@Composable
fun rememberVideoAmbient(playback: VideoPlayback): VideoAmbient {
    val ambient = remember(playback) { VideoAmbient() }

    LaunchedEffect(ambient, playback.player, playback.playing, playback.scrubbing) {
        if (playback.player == null) return@LaunchedEffect
        while (true) {
            ambient.capture()
            if (!playback.playing && !playback.scrubbing) break
            delay(AmbientSampleMillis)
        }
    }

    return ambient
}

/**
 * Draws the light. Belongs *under* the picture, filling whatever the picture is drawn into.
 *
 * [still] is what the light is built from until the first frame has been sampled — a content
 * URI Coil can decode a frame out of, normally the very one the card underneath was already
 * showing. Without it there is a beat at the top of every clip where the card is lit, then
 * isn't, then is again: the sampler needs a surface that has rendered at least once, and the
 * player needs to have decoded a frame to give it one. With it the light is continuous from
 * the moment the card arrives, because it is *the same picture* — Coil serves it from the
 * memory cache it was already put in by [com.swipey.app.ui.deck.NextCardContent].
 *
 * Null [still] is fine and draws nothing until the sampler catches up, which is the right
 * answer in the preview: it opens over a card that is already lit.
 *
 * @param dragProgress the deck's signed, clamped drag — see `SwipeCard`. Read inside a
 *   `graphicsLayer` lambda, which is what keeps a frame of dragging to a redraw rather than a
 *   recomposition; and because the translation lands on the layer *outside* the blur, moving
 *   it re-composites the blurred result rather than re-running the blur.
 */
@Composable
fun VideoAmbientLayer(
    ambient: VideoAmbient,
    modifier: Modifier = Modifier,
    still: Any? = null,
    dragProgress: () -> Float = { 0f },
) {
    // Read here rather than by the caller, deliberately: this subscribes *this* composable to
    // the sampler and nothing above it, so twelve new frames a second redraw the light
    // without recomposing the card, the player view or the chrome around them.
    val frame = ambient.frame

    AmbientLight(modifier, dragProgress) {
        when {
            frame != null -> AmbientSampledFrame(frame)
            still != null -> AmbientStillFrame(still)
        }
    }
}

/**
 * The same light, from a still.
 *
 * This is what lets the card *under* the one being swiped be lit without standing up a second
 * ExoPlayer for a clip nobody is watching yet — see [com.swipey.app.ui.deck.NextCardContent]
 * for why that matters. A blurred first frame and a blurred live frame are, at this radius,
 * the same object; the only difference the user can see is that one of them moves, and it
 * starts moving at exactly the moment the card is promoted and the sampler takes over.
 */
@Composable
fun VideoAmbientStill(model: Any?, modifier: Modifier = Modifier) {
    AmbientLight(modifier) { AmbientStillFrame(model) }
}

/**
 * The layer everything above is drawn into: overscanned content, blurred, and lagging behind
 * the drag. One place, so the live light and the still light cannot drift apart.
 */
@Composable
private fun AmbientLight(
    modifier: Modifier,
    dragProgress: () -> Float = { 0f },
    content: @Composable () -> Unit,
) {
    val drift = with(LocalDensity.current) { AmbientDrift.toPx() }
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer { translationX = -dragProgress() * drift }
            .blur(AmbientBlur),
    ) {
        content()
    }
}

/** The sampled frame, drawn to cover. */
@Composable
private fun AmbientSampledFrame(frame: ImageBitmap) {
    val tint = rememberAmbientTint()
    Canvas(Modifier.fillMaxSize()) {
        // Cover, not fit: the light has to reach every corner of the card, and the frame it
        // comes from is a different shape from the card by definition — that mismatch is the
        // entire reason this layer exists.
        val scale = max(
            size.width / frame.width,
            size.height / frame.height,
        ) * AmbientOverscan
        val width = frame.width * scale
        val height = frame.height * scale

        drawImage(
            image = frame,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(frame.width, frame.height),
            dstOffset = IntOffset(
                ((size.width - width) / 2f).roundToInt(),
                ((size.height - height) / 2f).roundToInt(),
            ),
            dstSize = IntSize(width.roundToInt(), height.roundToInt()),
            alpha = AmbientAlpha,
            colorFilter = tint,
            // Bilinear. The whole treatment is a 48-pixel image blown up twenty times;
            // nearest-neighbour would draw it as the grid of squares it actually is.
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * A decoded still, drawn to cover — `ContentScale.Crop` and [AmbientOverscan] together being
 * what [AmbientSampledFrame] does by hand.
 *
 * The scale sits *inside* the blur (it comes later in the chain), so the blur radius is the
 * same number of pixels in both paths rather than 1.25× larger in this one.
 */
@Composable
private fun AmbientStillFrame(model: Any?) {
    AsyncImage(
        model = model,
        contentDescription = null,
        modifier = Modifier.fillMaxSize().scale(AmbientOverscan),
        contentScale = ContentScale.Crop,
        alpha = AmbientAlpha,
        colorFilter = rememberAmbientTint(),
    )
}

/**
 * Colour, pushed back up by [AmbientSaturation].
 *
 * Remembered rather than held in a top-level `val`, and that is not a style choice: a
 * `ColorFilter` is an `android.graphics.ColorMatrixColorFilter` underneath, and a top-level
 * one would run in this file's static initialiser — which `VideoAmbientTest` triggers when it
 * calls [ambientSampleSize], on a JVM where android.graphics is a stub that throws.
 */
@Composable
private fun rememberAmbientTint(): ColorFilter = remember {
    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(AmbientSaturation) })
}
