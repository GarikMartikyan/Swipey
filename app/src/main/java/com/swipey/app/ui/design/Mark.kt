package com.swipey.app.ui.design

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The Split — Swipey's mark, for use inside the app.
 *
 * One photograph cut on a near-vertical diagonal and pulled apart: the grey half is the
 * bin, the blue half is keep. The same two shapes are the launcher icon's foreground
 * (`res/drawable/ic_launcher_foreground.xml`), and the same shapes flattened to one colour
 * are its monochrome layer.
 *
 * This is deliberately not in [SwipeyIcons]. Everything in that set is a 24×24 stroked
 * outline at one weight, tinted by [SwipeyIcon] at draw time; the mark is filled, two-tone,
 * and a different aspect. Putting it there would break the one property that makes an icon
 * set look like a set.
 *
 * ## Two copies of one shape
 *
 * The coordinates below are the launcher artwork's, shifted to the origin: the icon is
 * drawn on a 108×108 canvas with the mark inset to survive adaptive-icon masking, and that
 * padding is exactly what an in-app logo must not carry. Cropping to the shape's own bounds
 * gives the [ViewportWidth]×[ViewportHeight] box here, so a caller who asks for a 28dp-tall
 * mark gets 28dp of ink rather than 28dp of mostly-empty canvas.
 *
 * The duplication is real and worth naming: change the geometry here and the two icon
 * drawables must change with it. There is no way to share one path between a Compose
 * [ImageVector] and an aapt-compiled `<vector>` without generating one from the other at
 * build time, which is more machinery than a six-segment shape deserves.
 */

/**
 * The card's own bounding box.
 *
 * The launcher artwork draws this same card at x 31..77, y 28..80 on a 108x108 canvas — the
 * inset that keeps it inside every adaptive-icon mask. In the app there is no mask and no
 * safe zone, so the coordinates below are those minus (31, 28): a caller who asks for a
 * 28dp-tall mark gets 28dp of ink rather than 28dp of mostly-empty canvas.
 */
private const val ViewportWidth = 46f
private const val ViewportHeight = 52f

/** Width per unit of height. Callers size by height; the mark is slightly wider than tall. */
private const val AspectRatio = ViewportWidth / ViewportHeight

/**
 * Draws the mark at [height], in the current palette's [SwipeyColors.bin] and
 * [SwipeyColors.keep].
 *
 * Sized by height rather than by a square `size`, because a lockup aligns a mark to the
 * cap height of the wordmark beside it — the width follows from the artwork, and a caller
 * forced to supply both would sooner or later supply a pair that squashes it.
 *
 * No content description. Wherever the mark appears it sits beside the word "Swipey", and
 * a screen reader announcing "Swipey logo, Swipey" is noise; if it is ever used alone, the
 * caller supplies the description on its own container.
 */
@Composable
fun SwipeyMark(height: Dp, modifier: Modifier = Modifier) {
    val bin = SwipeyTheme.colors.bin
    val keep = SwipeyTheme.colors.keep

    // Keyed on both colours: the palette swaps when the system flips to dark, and the
    // vector bakes its fills in at build time rather than tinting at draw time.
    val mark = remember(bin, keep) { buildMark(bin, keep) }

    Image(
        imageVector = mark,
        contentDescription = null,
        modifier = modifier.size(width = height * AspectRatio, height = height),
    )
}

/**
 * Builds the two-tone [ImageVector].
 *
 * The halves are 6dp apart at the top edge and 6dp apart at the bottom — a gap that stays
 * parallel rather than tapering is what stops the cut reading as a wedge. That gap is also
 * the only thing separating the halves once the launcher flattens the mark to one colour,
 * which is why it is generous.
 */
private fun buildMark(binColor: Color, keepColor: Color): ImageVector =
    ImageVector.Builder(
        name = "SwipeyMark",
        defaultWidth = ViewportWidth.dp,
        defaultHeight = ViewportHeight.dp,
        viewportWidth = ViewportWidth,
        viewportHeight = ViewportHeight,
    ).apply {
        // Bin half: the left piece, square down the cut and rounded on the outside.
        path(fill = SolidColor(binColor)) {
            moveTo(12f, 0f)
            horizontalLineTo(22.5f)
            lineTo(17.5f, 52f)
            horizontalLineTo(12f)
            arcTo(12f, 12f, 0f, false, true, 0f, 40f)
            verticalLineTo(12f)
            arcTo(12f, 12f, 0f, false, true, 12f, 0f)
            close()
        }

        // Keep half: the mirror, carrying the accent.
        path(fill = SolidColor(keepColor)) {
            moveTo(28.5f, 0f)
            horizontalLineTo(34f)
            arcTo(12f, 12f, 0f, false, true, 46f, 12f)
            verticalLineTo(40f)
            arcTo(12f, 12f, 0f, false, true, 34f, 52f)
            horizontalLineTo(23.5f)
            close()
        }
    }.build()

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

/**
 * The mark at the four sizes it has to survive, in both palettes.
 *
 * 96dp is the presentation size; 28dp is the Home lockup; 16dp is the smallest place a
 * mark is worth using at all. The bottom row is the lockup itself, which is the only one
 * of these that ships — the rest are here so a change to the geometry shows its damage at
 * small sizes before it reaches a screen.
 */
@Preview(name = "Dark", group = "design", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", group = "design", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
@Composable
private fun MarkPreview() {
    SwipeyTheme {
        Column(
            Modifier
                .background(SwipeyTheme.colors.canvas)
                .padding(SwipeySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(SwipeySpacing.lg),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.lg),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeyMark(height = 96.dp)
                SwipeyMark(height = 40.dp)
                SwipeyMark(height = 28.dp)
                SwipeyMark(height = 16.dp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                SwipeyMark(height = 28.dp)
                Spacer(Modifier.width(SwipeySpacing.md))
                SwipeyText(
                    "Swipey",
                    style = SwipeyTheme.typography.display,
                    color = SwipeyTheme.colors.textPrimary,
                )
            }
        }
    }
}
