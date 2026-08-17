package com.swipey.app.ui.deck

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * How bright the top of this photograph is, on the 0..255 scale the contrast maths uses.
 *
 * The deck's caption sizes its own ground from this: see
 * [com.swipey.app.domain.CaptionContrast]. Null until the answer arrives, which is a state
 * the caller has to have an answer for — a caption cannot wait for a decode before being
 * legible.
 */
@Composable
fun rememberTopStripLuma(item: MediaItem): Float? {
    val context = LocalContext.current
    var luma by remember(item.id) { mutableStateOf<Float?>(null) }

    LaunchedEffect(item.id) {
        luma = withContext(Dispatchers.IO) {
            runCatching {
                // A thumbnail, not the photograph. MediaStore serves these from its own
                // cache and they are already the size this needs — decoding a 12-megapixel
                // frame to average a hundred pixels of it would be absurd, and would make
                // the caption's ground the most expensive thing on the card.
                //
                // It also covers video for free: loadThumbnail returns a frame for those,
                // which is the same frame the card is about to show.
                val thumb = context.contentResolver.loadThumbnail(
                    contentUriFor(item.id, item.isVideo),
                    Size(ThumbEdge, ThumbEdge),
                    null,
                )
                meanTopStripLuma(thumb)
            }.getOrNull()
            // Failure is silent and returns null on purpose: a thumbnail that will not load
            // is not a reason to fail the card. The caller falls back to a fixed ground —
            // the behaviour the app had before any of this existed.
        }
    }

    return luma
}

/**
 * The mean sRGB luma of the top [StripFraction] of [bitmap].
 *
 * A mean, and the caption's contrast is therefore an average-case guarantee rather than a
 * worst-pixel one: a small bright cloud inside an otherwise mid-grey strip pulls the number
 * less than it pulls the eye. That is the compromise the type's shadow is still here to
 * cover. Sampling the extremes instead — say the 90th percentile — would be defensible and
 * darker; it is a one-line change if the average ever proves too generous.
 */
internal fun meanTopStripLuma(bitmap: Bitmap): Float {
    val rows = max(1, (bitmap.height * StripFraction).roundToInt())
    val pixels = IntArray(bitmap.width * rows)
    bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, rows)

    var total = 0.0
    for (pixel in pixels) {
        total += 0.2126 * Color.red(pixel) + 0.7152 * Color.green(pixel) + 0.0722 * Color.blue(pixel)
    }
    return (total / pixels.size).toFloat()
}

/**
 * How much of the frame counts as "under the caption".
 *
 * The caption block stands about a fifth of the way down the card including its inset, and
 * the ramp behind it fades out shortly after. Sampling much more than that would average in
 * the middle of the photograph, which the caption never touches and which would drag a
 * bright sky's answer down toward a dark subject's.
 */
private const val StripFraction = 0.22f

/** Big enough to average honestly, small enough that MediaStore hands it over instantly. */
private const val ThumbEdge = 64
