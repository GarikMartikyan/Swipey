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
     * Sun. A disc and eight rays.
     *
     * Settings shows this or [Moon] depending on which palette is in force, rather than one
     * neutral glyph for "appearance" — a half-filled circle is the conventional mark and it
     * says only *that a theme exists*, where a sun says which one you are looking at. The row
     * then answers its own question before it is opened, which is the whole idea of that
     * screen.
     *
     * Eight rays and not four: four reads as a compass. They stop short of the frame so the
     * glyph keeps the same optical size as the rest of the set, which are mostly boxes.
     */
    val Sun: ImageVector = icon("Sun") {
        // Disc, drawn as two half-arcs like every other circle here.
        moveTo(12f, 7.5f)
        arcTo(4.5f, 4.5f, 0f, false, true, 12f, 16.5f)
        arcTo(4.5f, 4.5f, 0f, false, true, 12f, 7.5f)
        // Rays, on the eight principal angles.
        moveTo(12f, 2.5f)
        lineTo(12f, 5.1f)
        moveTo(12f, 18.9f)
        lineTo(12f, 21.5f)
        moveTo(2.5f, 12f)
        lineTo(5.1f, 12f)
        moveTo(18.9f, 12f)
        lineTo(21.5f, 12f)
        moveTo(5.28f, 5.28f)
        lineTo(7.12f, 7.12f)
        moveTo(16.88f, 16.88f)
        lineTo(18.72f, 18.72f)
        moveTo(5.28f, 18.72f)
        lineTo(7.12f, 16.88f)
        moveTo(16.88f, 7.12f)
        lineTo(18.72f, 5.28f)
    }

    /**
     * Moon. A crescent, cut from one disc by another.
     *
     * Two arcs and a close: the outer sweep is the moon's edge, the inner one is the shadow
     * biting into it. Drawing it as a subtraction rather than as a banana is what keeps the
     * horns sharp at 22dp — a hand-drawn crescent goes blunt at the tips and reads as a
     * kidney. See [Sun], which this pairs with.
     */
    val Moon: ImageVector = icon("Moon") {
        moveTo(20.6f, 13.1f)
        arcTo(8.6f, 8.6f, 0f, true, true, 11.3f, 3.7f)
        arcTo(6.7f, 6.7f, 0f, false, false, 20.6f, 13.1f)
        close()
    }

    /**
     * A handset shaking between two zigzags — haptic feedback.
     *
     * A zigzag rather than the concentric arcs the rest of the set uses for things leaving a
     * device, and deliberately: [SoundOn]'s arcs say *radiating*, which is right for sound
     * and wrong for a buzz. A zigzag says *oscillating*, and the handset between two of them
     * reads as shaking rather than broadcasting.
     *
     * Settings shows this or [HapticsOff] depending on which way the setting is set, on the
     * same reasoning as [Sun] and [Moon].
     */
    val Haptics: ImageVector = icon("Haptics") {
        // Handset: eight units wide, one-unit corners so it reads as a phone, not a bar.
        moveTo(9f, 5f)
        lineTo(15f, 5f)
        arcTo(1f, 1f, 0f, false, true, 16f, 6f)
        lineTo(16f, 18f)
        arcTo(1f, 1f, 0f, false, true, 15f, 19f)
        lineTo(9f, 19f)
        arcTo(1f, 1f, 0f, false, true, 8f, 18f)
        lineTo(8f, 6f)
        arcTo(1f, 1f, 0f, false, true, 9f, 5f)
        close()
        // Left zigzag, then right, mirrored about the centre line.
        zigzags()
    }

    /**
     * [Haptics] struck through.
     *
     * Not the cross-where-the-waves-were that [SoundOff] uses, because the zigzags are the
     * half of this glyph a cross would have to replace, and two crosses beside a phone reads
     * as an error state rather than as a setting turned off. A slash corner to corner is
     * unambiguous — and the handset is drawn **broken** where the slash passes, so the two
     * shapes read as one mark rather than as a line laid on top of an intact phone.
     */
    val HapticsOff: ImageVector = icon("HapticsOff") {
        // The handset's lower-left run: down the left edge, round the bottom, and up the
        // right as far as the slash.
        moveTo(8f, 8f)
        lineTo(8f, 18f)
        arcTo(1f, 1f, 0f, false, false, 9f, 19f)
        lineTo(15f, 19f)
        arcTo(1f, 1f, 0f, false, false, 16f, 18f)
        lineTo(16f, 16f)
        // And its upper-right run, picking up on the far side of the slash.
        moveTo(16f, 10.34f)
        lineTo(16f, 6f)
        arcTo(1f, 1f, 0f, false, false, 15f, 5f)
        lineTo(10.66f, 5f)
        zigzags()
        // The slash, corner to corner.
        moveTo(2f, 2f)
        lineTo(22f, 22f)
    }

    /** The shake, either side of the handset. Shared so the pair cannot drift apart. */
    private fun PathBuilder.zigzags() {
        moveTo(2f, 8f)
        lineTo(4f, 10f)
        lineTo(2f, 12f)
        lineTo(4f, 14f)
        lineTo(2f, 16f)
        moveTo(22f, 8f)
        lineTo(20f, 10f)
        lineTo(22f, 12f)
        lineTo(20f, 14f)
        lineTo(22f, 16f)
    }

    /**
     * Share. An arrow leaving an open tray.
     *
     * Not Android's three-node graph, which draws a thing (a network) rather than an action
     * and is unreadable at this weight — three dots and two hairlines collapse into a smudge
     * at 20dp. An arrow going up out of a container says *out of here, to somewhere else*,
     * which is the whole of what the button does.
     */
    val Share: ImageVector = icon("Share") {
        // The tray, open at the top so the arrow can leave through it.
        moveTo(6f, 11.5f)
        lineTo(6f, 19f)
        arcTo(1.5f, 1.5f, 0f, false, false, 7.5f, 20.5f)
        lineTo(16.5f, 20.5f)
        arcTo(1.5f, 1.5f, 0f, false, false, 18f, 19f)
        lineTo(18f, 11.5f)
        // The arrow, and its head.
        moveTo(12f, 15f)
        lineTo(12f, 3.5f)
        moveTo(8f, 7.5f)
        lineTo(12f, 3.5f)
        lineTo(16f, 7.5f)
    }

    /**
     * Gallery. A framed picture: a horizon, a sun, and a hill behind it.
     *
     * The universal mark for "an image lives here", and the right one for a button that hands
     * the photograph to another app — it names the destination rather than the journey. Two
     * overlapping slopes rather than one, because a single triangle in a box reads as a play
     * button turned on its side.
     */
    val Gallery: ImageVector = icon("Gallery") {
        moveTo(5.5f, 3.5f)
        lineTo(18.5f, 3.5f)
        arcTo(2f, 2f, 0f, false, true, 20.5f, 5.5f)
        lineTo(20.5f, 18.5f)
        arcTo(2f, 2f, 0f, false, true, 18.5f, 20.5f)
        lineTo(5.5f, 20.5f)
        arcTo(2f, 2f, 0f, false, true, 3.5f, 18.5f)
        lineTo(3.5f, 5.5f)
        arcTo(2f, 2f, 0f, false, true, 5.5f, 3.5f)
        close()
        // Sun.
        moveTo(8f, 10.4f)
        arcTo(1.6f, 1.6f, 0f, false, true, 8f, 7.2f)
        arcTo(1.6f, 1.6f, 0f, false, true, 8f, 10.4f)
        close()
        // The near slope, then the far one rising behind it.
        moveTo(3.5f, 17f)
        lineTo(9f, 11.5f)
        lineTo(14.5f, 17f)
        moveTo(12.5f, 15f)
        lineTo(15.5f, 12f)
        lineTo(20.5f, 17f)
    }

    /**
     * Two arrows, one going each way — which side of the deck bins.
     *
     * Not [Bin], and not [Shuffle]. The bin glyph would name the outcome and say nothing
     * about the setting, which is a *direction*; Shuffle's two paths cross, and crossing is
     * exactly the wrong idea here — these two never meet. Opposed arrows on their own rows
     * is the oldest way of drawing "these two can be swapped over".
     */
    val SwapSides: ImageVector = icon("SwapSides") {
        // Upper, pointing left.
        moveTo(20.5f, 9f)
        lineTo(4.5f, 9f)
        moveTo(8.5f, 5f)
        lineTo(4.5f, 9f)
        lineTo(8.5f, 13f)
        // Lower, pointing right.
        moveTo(3.5f, 15f)
        lineTo(19.5f, 15f)
        moveTo(15.5f, 11f)
        lineTo(19.5f, 15f)
        lineTo(15.5f, 19f)
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
