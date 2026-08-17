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
 * The Flick — Swipey's mark, for use inside the app.
 *
 * One card already leaving, and the two strokes it left behind. It is the verb rather than
 * the noun: the app is not a pile of photographs, it is the act of getting through one. The
 * card leans 13°, near enough the tilt the deck gives a card under a dragging thumb.
 *
 * The same three shapes are the launcher icon's foreground
 * (`res/drawable/ic_launcher_foreground.xml`), and the same shapes flattened to one colour
 * are its monochrome layer.
 *
 * This is deliberately not in [SwipeyIcons]. Everything in that set is a 24×24 stroked
 * outline at one weight, tinted by [SwipeyIcon] at draw time; the mark is filled, two-tone,
 * and a different aspect. Putting it there would break the one property that makes an icon
 * set look like a set.
 *
 * ## No accent, on purpose
 *
 * The mark used to be grey and blue — a card cut in half, the bin and keep. It carries no
 * accent now. Swipey spends its one blue on a single meaning, *keep*, at the moment a
 * decision is made; a logo that also wore it would be spending it on nothing, and would make
 * the blue mean "Swipey" as well as "kept". The mark is ink and a muted neutral instead, and
 * the accent stays where it earns its keep.
 *
 * What that costs is the palette flipping with the theme, which the launcher icon must not
 * do — see `values/colors.xml`. There the pair is fixed at the dark palette's values, which
 * is where "paper on ink" comes from.
 *
 * ## Three copies of one shape
 *
 * The coordinates below are the launcher artwork's, shifted to the origin: the icon is
 * drawn on a 108×108 canvas with the mark inset to survive adaptive-icon masking, and that
 * padding is exactly what an in-app logo must not carry. Cropping to the shape's own ink
 * gives the [ViewportWidth]×[ViewportHeight] box here, so a caller who asks for a 28dp-tall
 * mark gets 28dp of ink rather than 28dp of mostly-empty canvas.
 *
 * The duplication is real and worth naming: change the geometry here and the two icon
 * drawables must change with it. There is no way to share one path between a Compose
 * [ImageVector] and an aapt-compiled `<vector>` without generating one from the other at
 * build time, which is more machinery than three shapes deserve. `MarkGeometryTest` reads
 * all three off disk instead and refuses to let them drift.
 */

/**
 * The mark's own bounding box — its **ink**, not its vertices.
 *
 * The distinction did not exist while the mark was one upright rounded rectangle, whose
 * corner tangent points sit exactly at its extremes. The card leans now, so its widest ink
 * is out on a corner arc, past every point the path actually names. A viewport measured from
 * the vertices would be a fraction too small and would shave those corners — which is the
 * one thing a rounded silhouette cannot survive.
 */
private const val ViewportWidth = 50.358f
private const val ViewportHeight = 36.96f

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
    // The card is the loudest ink the palette has and the trails are its quietest neutral,
    // which is the whole scheme: one bright card, and a wake that is barely there. In the
    // dark palette that pair is #FAFAFA on #5A5F66 — exactly the launcher artwork's.
    val trail = SwipeyTheme.colors.textDisabled
    val card = SwipeyTheme.colors.textPrimary

    // Keyed on both colours: the palette swaps when the system flips to dark, and the
    // vector bakes its fills in at build time rather than tinting at draw time.
    val mark = remember(trail, card) { buildMark(trail, card) }

    Image(
        imageVector = mark,
        contentDescription = null,
        modifier = modifier.size(width = height * AspectRatio, height = height),
    )
}

/**
 * Builds the two-tone [ImageVector].
 *
 * Three paths, in the order the launcher artwork declares them — the two trails, then the
 * card — because `MarkGeometryTest` compares them positionally against the XML.
 *
 * The trails stop well short of the card rather than touching it. That gap is what carries
 * the shape once the launcher flattens all three to a single colour for a themed icon: three
 * separate shapes still read as a card with something behind it, where three joined ones
 * would read as a blob.
 *
 * The card's rotation is baked into its coordinates rather than applied as a `group`
 * transform. A transform would be a fourth thing that could be expressed differently here
 * than in the XML, and flat coordinates are what let a test compare the two as values.
 */
private fun buildMark(trailColor: Color, cardColor: Color): ImageVector =
    ImageVector.Builder(
        name = "SwipeyMark",
        defaultWidth = ViewportWidth.dp,
        defaultHeight = ViewportHeight.dp,
        viewportWidth = ViewportWidth,
        viewportHeight = ViewportHeight,
    ).apply {
        // The long trail, nearest the card's path.
        path(fill = SolidColor(trailColor)) {
            moveTo(2.55f, 10.83f)
            horizontalLineTo(11.9f)
            arcTo(2.55f, 2.55f, 0f, false, true, 11.9f, 15.93f)
            horizontalLineTo(2.55f)
            arcTo(2.55f, 2.55f, 0f, false, true, 2.55f, 10.83f)
            close()
        }

        // The short one, further back and further out.
        path(fill = SolidColor(trailColor)) {
            moveTo(7.65f, 22.73f)
            horizontalLineTo(11.9f)
            arcTo(2.55f, 2.55f, 0f, false, true, 11.9f, 27.83f)
            horizontalLineTo(7.65f)
            arcTo(2.55f, 2.55f, 0f, false, true, 7.65f, 22.73f)
            close()
        }

        // The card, leaning 13°.
        path(fill = SolidColor(cardColor)) {
            moveTo(33.822f, 0.163f)
            lineTo(45.417f, 2.84f)
            arcTo(6.375f, 6.375f, 0f, false, true, 50.195f, 10.486f)
            lineTo(45.223f, 32.019f)
            arcTo(6.375f, 6.375f, 0f, false, true, 37.578f, 36.797f)
            lineTo(25.983f, 34.12f)
            arcTo(6.375f, 6.375f, 0f, false, true, 21.205f, 26.474f)
            lineTo(26.176f, 4.941f)
            arcTo(6.375f, 6.375f, 0f, false, true, 33.822f, 0.163f)
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
