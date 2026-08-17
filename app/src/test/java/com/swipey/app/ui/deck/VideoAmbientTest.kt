package com.swipey.app.ui.deck

import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one piece of the ambient layer that is arithmetic rather than pixels.
 *
 * Everything else about the bloom — the blur, the alpha, the parallax — can only be judged by
 * looking at it. This can be got wrong silently: a sample bitmap whose aspect disagrees with
 * the frame's stretches the light sideways, and a rounding that reaches zero throws from
 * `Bitmap.createBitmap` on the first clip that trips it.
 */
class VideoAmbientTest {

    @Test fun landscapeKeepsItsShape() {
        assertEquals(IntSize(48, 27), ambientSampleSize(1920, 1080))
    }

    @Test fun uprightKeepsItsShape() {
        assertEquals(IntSize(27, 48), ambientSampleSize(1080, 1920))
    }

    @Test fun squareIsSquare() {
        assertEquals(IntSize(48, 48), ambientSampleSize(1080, 1080))
    }

    @Test fun aFrameSmallerThanTheSampleIsLeftAlone() {
        // Nothing is gained by asking a 32px-wide surface for 48 columns of it.
        assertEquals(IntSize(32, 18), ambientSampleSize(32, 18))
    }

    @Test fun anExtremeAspectStillHasAShortEdge() {
        // 2000×10 rounds its short edge to zero, and a zero-height bitmap throws. The floor
        // is what keeps a letterbox-shaped surface from taking the card down with it.
        assertEquals(IntSize(48, 1), ambientSampleSize(2000, 10))
    }

    @Test fun anUnmeasuredSurfaceHasNoSample() {
        // The TextureView reports 0×0 until it has been laid out, which is the state the
        // first sample after an item change arrives in.
        assertNull(ambientSampleSize(0, 0))
        assertNull(ambientSampleSize(1920, 0))
        assertNull(ambientSampleSize(-1, 10))
    }
}
