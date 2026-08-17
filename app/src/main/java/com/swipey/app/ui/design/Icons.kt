package com.swipey.app.ui.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path

/**
 * Swipey's icon set, drawn here rather than depended upon.
 *
 * A dozen glyphs is not enough to justify pulling in `material-icons-extended` (a few
 * thousand vectors, most of a megabyte, drawn in someone else's house style). Drawing
 * them inline also means they obey one grid and one stroke weight, which is most of what
 * makes a set look like a set.
 *
 * The rules every glyph here follows:
 *  - a 24×24 viewport, matching [SwipeySize.icon];
 *  - stroked, never filled, at [StrokeWidth] — outlines read as lighter than solids, and
 *    Swipey's chrome should stay quieter than the photograph behind it;
 *  - round caps and joins, so nothing ends in a hard point;
 *  - geometry on whole or half units wherever the shape allows.
 *
 * Every path is drawn in black and tinted at draw time by [SwipeyIcon], so one vector
 * serves both palettes.
 */
object SwipeyIcons {

    /** The one stroke weight in the set. */
    private const val StrokeWidth = 1.8f

    /**
     * Bin. A tapered body under a wide lid, with two vertical scores.
     *
     * The scores earn their place: without them the shape reads as a plain bucket, and at
     * 24dp the lid alone isn't enough to distinguish it from a download tray.
     */
    val Bin: ImageVector = icon("Bin") {
        // Lid.
        moveTo(3.5f, 6f)
        lineTo(20.5f, 6f)
        // Handle.
        moveTo(9f, 6f)
        lineTo(9f, 3.5f)
        lineTo(15f, 3.5f)
        lineTo(15f, 6f)
        // Body, tapering inward toward the base.
        moveTo(5.5f, 6f)
        lineTo(6.5f, 20.5f)
        lineTo(17.5f, 20.5f)
        lineTo(18.5f, 6f)
        // Scores.
        moveTo(10f, 10f)
        lineTo(10f, 17f)
        moveTo(14f, 10f)
        lineTo(14f, 17f)
    }

    /** Check — the Keep decision. A single unbroken stroke, long arm rising to the right. */
    val Check: ImageVector = icon("Check") {
        moveTo(4.5f, 12.5f)
        lineTo(9.5f, 17.5f)
        lineTo(19.5f, 6.5f)
    }

    /**
     * Undo. A stroke running right, turning back on itself, with the head at the left.
     *
     * The arrowhead is at the *start* of the travel rather than the end, which is what
     * separates "undo" from a plain redo or reply glyph.
     */
    val Undo: ImageVector = icon("Undo") {
        moveTo(4f, 9.5f)
        lineTo(13f, 9.5f)
        // Half-turn down and back, radius 4.5.
        arcTo(4.5f, 4.5f, 0f, false, true, 13f, 18.5f)
        lineTo(8.5f, 18.5f)
        // Head.
        moveTo(8f, 5.5f)
        lineTo(4f, 9.5f)
        lineTo(8f, 13.5f)
    }

    /** Back. A bare left chevron — no shaft, so it never reads as a directional arrow. */
    val ChevronLeft: ImageVector = icon("ChevronLeft") {
        moveTo(15f, 4.5f)
        lineTo(7.5f, 12f)
        lineTo(15f, 19.5f)
    }

    /** The mirror of [ChevronLeft], for the trailing edge of a tappable row. */
    val ChevronRight: ImageVector = icon("ChevronRight") {
        moveTo(9f, 4.5f)
        lineTo(16.5f, 12f)
        lineTo(9f, 19.5f)
    }

    /**
     * Info. A ringed lowercase i.
     *
     * The tittle is a zero-length segment — `moveTo(p)` then `lineTo(p)`. With a round
     * cap that renders as a dot of exactly [StrokeWidth] diameter, which keeps it welded
     * to the set's weight instead of being a separately-tuned circle.
     */
    val Info: ImageVector = icon("Info") {
        // Ring, as two half-arcs; a single arc can't close a full circle.
        moveTo(3f, 12f)
        arcTo(9f, 9f, 0f, true, true, 21f, 12f)
        arcTo(9f, 9f, 0f, true, true, 3f, 12f)
        // Tittle.
        moveTo(12f, 7.6f)
        lineTo(12f, 7.6f)
        // Stem.
        moveTo(12f, 11f)
        lineTo(12f, 16.5f)
    }

    /**
     * Restore — lifting an item back out of the bin.
     *
     * Drawn as an arrow rising out of an open tray rather than as the usual
     * counter-clockwise circular arrow. At 24dp a circular arrow is very hard to tell
     * from a refresh or a sync glyph, and in a screen whose whole subject is trash and
     * recovery, that particular confusion is expensive.
     */
    val Restore: ImageVector = icon("Restore") {
        // Open tray.
        moveTo(4f, 14.5f)
        lineTo(4f, 20f)
        lineTo(20f, 20f)
        lineTo(20f, 14.5f)
        // Shaft.
        moveTo(12f, 15.5f)
        lineTo(12f, 4f)
        // Head.
        moveTo(7.5f, 8.5f)
        lineTo(12f, 4f)
        lineTo(16.5f, 8.5f)
    }

    /** Play, for the video cards. A closed triangle; the round join softens its points. */
    val Play: ImageVector = icon("Play") {
        moveTo(8f, 5f)
        lineTo(19f, 12f)
        lineTo(8f, 19f)
        close()
    }

    /**
     * Pause. Two uprights, at the same optical width as [Play]'s triangle.
     *
     * Inset to 8.5 and 15.5 rather than the 8..19 the triangle spans: a pair of verticals
     * reads wider than a triangle of the same bounding box, because the triangle's mass
     * falls away to a point on one side and the bars' does not. Matching the boxes would
     * have made this the heavier of the two, and they swap places under a thumb.
     */
    val Pause: ImageVector = icon("Pause") {
        moveTo(9.5f, 5.5f)
        lineTo(9.5f, 18.5f)
        moveTo(15.5f, 5.5f)
        lineTo(15.5f, 18.5f)
    }

    /**
     * Sound on. A speaker cone and two arcs.
     *
     * The cone is one closed path rather than a box plus a triangle, so the join where the
     * throat meets the body is a corner of the shape instead of two strokes crossing —
     * which at 20dp is the difference between a speaker and a smudge. Two arcs, not three:
     * the third is what the set's stroke weight cannot fit inside 24 units without the
     * waves touching.
     */
    val SoundOn: ImageVector = icon("SoundOn") {
        moveTo(4f, 9.5f)
        lineTo(7.5f, 9.5f)
        lineTo(11.5f, 5.5f)
        lineTo(11.5f, 18.5f)
        lineTo(7.5f, 14.5f)
        lineTo(4f, 14.5f)
        close()
        // Arcs, like every other curve in the set — the radii are what set how far each
        // wave bows out, and both are drawn as half-circles so the pair stays concentric.
        moveTo(14.8f, 9.4f)
        arcTo(3.4f, 3.4f, 0f, false, true, 14.8f, 14.6f)
        moveTo(17.4f, 6.6f)
        arcTo(6.6f, 6.6f, 0f, false, true, 17.4f, 17.4f)
    }

    /** Sound off. [SoundOn]'s cone, with the arcs struck through — the same cross as [Close]. */
    val SoundOff: ImageVector = icon("SoundOff") {
        moveTo(4f, 9.5f)
        lineTo(7.5f, 9.5f)
        lineTo(11.5f, 5.5f)
        lineTo(11.5f, 18.5f)
        lineTo(7.5f, 14.5f)
        lineTo(4f, 14.5f)
        close()
        moveTo(15.5f, 9.5f)
        lineTo(20.5f, 14.5f)
        moveTo(20.5f, 9.5f)
        lineTo(15.5f, 14.5f)
    }

    /** Close. Two crossing strokes, inset to sit optically level with the chevrons. */
    val Close: ImageVector = icon("Close") {
        moveTo(6f, 6f)
        lineTo(18f, 18f)
        moveTo(18f, 6f)
        lineTo(6f, 18f)
    }

    /**
     * List — Home's albums-as-rows layout.
     *
     * Three lines, each led by a dot, rather than three bare lines: bare lines are [Sort],
     * and the two glyphs sit within a thumb's width of each other on Home. The dot is the
     * same zero-length-segment trick as [Info]'s tittle, so it is exactly [StrokeWidth]
     * across and cannot drift from the set's weight.
     */
    val ListRows: ImageVector = icon("ListRows") {
        moveTo(4f, 6.5f)
        lineTo(4f, 6.5f)
        moveTo(8.5f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(4f, 12f)
        lineTo(4f, 12f)
        moveTo(8.5f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17.5f)
        lineTo(4f, 17.5f)
        moveTo(8.5f, 17.5f)
        lineTo(20f, 17.5f)
    }

    /** Grid — Home's albums-as-tiles layout. Four squares, matching the two-column tiles. */
    val Grid: ImageVector = icon("Grid") {
        moveTo(4f, 4f)
        lineTo(10.5f, 4f)
        lineTo(10.5f, 10.5f)
        lineTo(4f, 10.5f)
        close()
        moveTo(13.5f, 4f)
        lineTo(20f, 4f)
        lineTo(20f, 10.5f)
        lineTo(13.5f, 10.5f)
        close()
        moveTo(4f, 13.5f)
        lineTo(10.5f, 13.5f)
        lineTo(10.5f, 20f)
        lineTo(4f, 20f)
        close()
        moveTo(13.5f, 13.5f)
        lineTo(20f, 13.5f)
        lineTo(20f, 20f)
        lineTo(13.5f, 20f)
        close()
    }

    /**
     * Sort — the order the deck deals in.
     *
     * Three lines of decreasing length. It says "ordered" without committing to a
     * direction, which matters because the chooser behind it offers four orders, two of
     * which run the other way.
     */
    /**
     * Shuffle. Two paths that cross, each ending in an arrowhead.
     *
     * The crossing is the whole glyph: two routes that swap places say "reordered" in a way
     * no single arrow can. Both heads point the same way so it reads as a direction of
     * travel rather than as an exchange between two things.
     */
    val Shuffle: ImageVector = icon("Shuffle") {
        // Lower route, rising to the top-right exit.
        moveTo(3f, 17f)
        lineTo(7f, 17f)
        lineTo(10.5f, 13.5f)
        moveTo(13.5f, 10.5f)
        lineTo(17f, 7f)
        lineTo(21f, 7f)
        // Upper route, falling to the bottom-right exit.
        moveTo(3f, 7f)
        lineTo(7f, 7f)
        lineTo(17f, 17f)
        lineTo(21f, 17f)
        // The two heads.
        moveTo(18f, 4f)
        lineTo(21f, 7f)
        lineTo(18f, 10f)
        moveTo(18f, 14f)
        lineTo(21f, 17f)
        lineTo(18f, 20f)
    }

    val Sort: ImageVector = icon("Sort") {
        moveTo(4f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(4f, 12f)
        lineTo(15f, 12f)
        moveTo(4f, 17.5f)
        lineTo(10f, 17.5f)
    }

    /**
     * Menu — Home's drawer.
     *
     * Three lines of *equal* length, on the same rows as [Sort]'s three of decreasing
     * length. That is the only thing separating the two glyphs and it is enough, because
     * "all the same" and "getting shorter" are read as shapes long before either is read as
     * lines: one is a block, the other a wedge. They are also never adjacent — this sits in
     * the header, [Sort] on the hero card below it — and the ragged edge of a sort glyph is
     * exactly what a menu must not have, since a menu promises nothing about order.
     */
    val Menu: ImageVector = icon("Menu") {
        moveTo(4f, 6.5f)
        lineTo(20f, 6.5f)
        moveTo(4f, 12f)
        lineTo(20f, 12f)
        moveTo(4f, 17.5f)
        lineTo(20f, 17.5f)
    }

    /**
     * Settings. Two upright tracks, each with a knob, set at different heights.
     *
     * Sliders rather than a gear, and *upright* sliders rather than the usual lying-down
     * ones. A gear at 20dp is a ring with a texture nobody can resolve; sliders survive the
     * size because they are four strokes. Turning them on end is what keeps this set legible
     * as a set — [Sort], [ListRows] and [Menu] are already three different arrangements of
     * horizontal lines, and a fourth would have been the one glyph too many.
     */
    val Settings: ImageVector = icon("Settings") {
        moveTo(9f, 4f)
        lineTo(9f, 20f)
        moveTo(15f, 4f)
        lineTo(15f, 20f)
        // The knobs, high on the first track and low on the second, so the glyph reads as
        // two things set to two values rather than as a symmetrical ornament.
        moveTo(6.8f, 8.5f)
        arcTo(2.2f, 2.2f, 0f, true, true, 11.2f, 8.5f)
        arcTo(2.2f, 2.2f, 0f, true, true, 6.8f, 8.5f)
        moveTo(12.8f, 15.5f)
        arcTo(2.2f, 2.2f, 0f, true, true, 17.2f, 15.5f)
        arcTo(2.2f, 2.2f, 0f, true, true, 12.8f, 15.5f)
    }

    /**
     * Builds one glyph to the house rules above. Every glyph shares this single [path]
     * call, so the stroke weight, caps and joins can only ever be changed for the whole
     * set.
     */
    private fun icon(name: String, pathBuilder: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = SwipeySize.icon,
            defaultHeight = SwipeySize.icon,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = StrokeWidth,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            pathBuilder = pathBuilder,
        ).build()
}
