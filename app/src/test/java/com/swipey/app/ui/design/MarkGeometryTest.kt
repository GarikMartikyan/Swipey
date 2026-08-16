package com.swipey.app.ui.design

import java.io.File
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Guards against 22.499999999999996 failing a comparison against 22.5. */
private fun round(value: Double) = Math.round(value * 1000.0) / 1000.0

/**
 * Keeps the three copies of Swipey's mark honest, and keeps the mark inside the mask.
 *
 * The same shape is expressed three times — `ic_launcher_foreground.xml`,
 * `ic_launcher_monochrome.xml`, and the [ImageVector] in `ui/design/Mark.kt` — because there
 * is no way to share one path between aapt-compiled `<vector>` XML and Compose without
 * build-time codegen, which is more machinery than a six-segment shape deserves. What there
 * *is* room for is a test that reads all three off disk and refuses to let them drift, in
 * the same spirit as `DomainPurityTest`.
 *
 * The containment check is the one that earns its keep. The first cut of this icon sized the
 * mark full-bleed, past the circle every launcher mask is drawn inside; on a circle-masked
 * device that deleted all four corner radii and with them the rounded-rectangle silhouette
 * that is the only reason the mark reads as a photograph. That failure was invisible in a
 * squircle preview and obvious on the phone. [markStaysInsideTheGuaranteedMaskCircle] makes
 * it a build failure instead.
 */
class MarkGeometryTest {

    private val foreground = File("src/main/res/drawable/ic_launcher_foreground.xml")
    private val monochrome = File("src/main/res/drawable/ic_launcher_monochrome.xml")
    private val markSource = File("src/main/java/com/swipey/app/ui/design/Mark.kt")

    /**
     * The adaptive-icon canvas is 108x108dp and a launcher shows only the central 72x72dp of
     * it, through a mask. 33dp is the radius of the 66dp circle that EVERY mask is guaranteed
     * to show — the budget the mark's ink has to fit inside.
     */
    private val canvasCentre = 54.0
    private val guaranteedRadius = 33.0

    @Test fun bothIconLayersDrawExactlyTheSameShape() {
        val fg = pathDataOf(foreground)
        val mono = pathDataOf(monochrome)
        assertEquals(
            "the monochrome layer must be the foreground's geometry with the colours removed",
            fg,
            mono,
        )
    }

    @Test fun theInAppMarkIsTheLauncherCardShiftedToTheOrigin() {
        val icon = pathDataOf(foreground).map(::parsePathData)
        val (originX, originY) = originOf(icon.flatten())

        val expected = icon.map { segments -> segments.map { it.translated(-originX, -originY) } }
        val actual = kotlinPathsOf(markSource)

        assertEquals("Mark.kt must declare the same number of paths as the icon", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(
                "path $index in Mark.kt has drifted from the launcher artwork " +
                    "(expected the icon's coordinates minus the card origin $originX, $originY)",
                want,
                got,
            )
        }
    }

    @Test fun theInAppViewportMatchesTheCard() {
        val segments = pathDataOf(foreground).map(::parsePathData).flatten()
        val xs = segments.flatMap { it.xs() }
        val ys = segments.flatMap { it.ys() }

        val declared = Regex("""private const val Viewport(Width|Height) = ([\d.]+)f""")
            .findAll(markSource.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }

        assertEquals("Mark.kt must declare both viewport constants", 2, declared.size)
        assertEquals("ViewportWidth", xs.max() - xs.min(), declared.getValue("Width"), 0.001)
        assertEquals("ViewportHeight", ys.max() - ys.min(), declared.getValue("Height"), 0.001)
    }

    @Test fun markStaysInsideTheGuaranteedMaskCircle() {
        val segments = pathDataOf(foreground).map(::parsePathData).flatten()
        val xs = segments.flatMap { it.xs() }
        val ys = segments.flatMap { it.ys() }

        // The card is a rounded rectangle, so its ink reaches furthest at the corner arcs:
        // half-width and half-height inset by the radius, plus the radius back out again.
        // Checking the bare vertices would miss the bulge and pass a shape that clips.
        val halfWidth = (xs.max() - xs.min()) / 2
        val halfHeight = (ys.max() - ys.min()) / 2
        val radius = segments.filter { it.command == 'A' }.map { it.operands[0] }.distinct().single()
        val reach = hypot(halfWidth - radius, halfHeight - radius) + radius

        assertTrue(
            "the mark's corner ink reaches ${"%.2f".format(reach)}dp from centre, past the " +
                "${guaranteedRadius}dp circle every launcher mask is guaranteed to show. A circle " +
                "mask will delete the corner radii and the card will read as two flat slabs. " +
                "Shrink the card or reduce the corner radius.",
            reach <= guaranteedRadius,
        )

        // Off-centre artwork looks fine in a square preview and lopsided inside a circle.
        assertEquals("mark is not horizontally centred on the canvas", canvasCentre, (xs.min() + xs.max()) / 2, 0.001)
        assertEquals("mark is not vertically centred on the canvas", canvasCentre, (ys.min() + ys.max()) / 2, 0.001)
    }

    // -----------------------------------------------------------------------
    // Parsing
    // -----------------------------------------------------------------------

    /**
     * One path command and its operands, normalised so an XML `<path>` and a Compose
     * `PathBuilder` call compare as equal values.
     */
    private data class Segment(val command: Char, val operands: List<Double>) {
        /** Operand indices carrying an x coordinate, by command. */
        private fun xIndices() = when (command) {
            'M', 'L' -> listOf(0)
            'H' -> listOf(0)
            'A' -> listOf(5)
            else -> emptyList()
        }

        /** Operand indices carrying a y coordinate, by command. */
        private fun yIndices() = when (command) {
            'M', 'L' -> listOf(1)
            'V' -> listOf(0)
            'A' -> listOf(6)
            else -> emptyList()
        }

        fun xs() = xIndices().map { operands[it] }

        fun ys() = yIndices().map { operands[it] }

        /** Shifts only the coordinates — radii and arc flags are not positions. */
        fun translated(dx: Double, dy: Double): Segment {
            val moved = operands.toMutableList()
            xIndices().forEach { moved[it] = round(moved[it] + dx) }
            yIndices().forEach { moved[it] = round(moved[it] + dy) }
            return copy(operands = moved)
        }
    }

    /** The card's top-left corner: the origin `Mark.kt` measures from. */
    private fun originOf(segments: List<Segment>): Pair<Double, Double> =
        segments.flatMap { it.xs() }.min() to segments.flatMap { it.ys() }.min()

    private fun pathDataOf(file: File): List<String> =
        Regex("""android:pathData="([^"]+)"""")
            .findAll(file.readText())
            .map { it.groupValues[1].trim() }
            .toList()
            .also { assertTrue("no pathData found in ${file.path}", it.isNotEmpty()) }

    private fun operandCount(command: Char) = when (command) {
        'M', 'L' -> 2
        'H', 'V' -> 1
        'A' -> 7
        'Z' -> 0
        else -> error("MarkGeometryTest does not understand path command '$command'")
    }

    private fun parsePathData(data: String): List<Segment> {
        val tokens = Regex("""[MLHVAZ]|-?\d+(?:\.\d+)?""").findAll(data).map { it.value }.toList()
        val out = mutableListOf<Segment>()
        var i = 0
        while (i < tokens.size) {
            val command = tokens[i++].single()
            val count = operandCount(command)
            out += Segment(command, (0 until count).map { round(tokens[i + it].toDouble()) })
            i += count
        }
        return out
    }

    /** Reads the `path(fill = ...) { ... }` blocks out of `Mark.kt` in declaration order. */
    private fun kotlinPathsOf(file: File): List<List<Segment>> {
        val calls = Regex(
            """(moveTo|lineTo|horizontalLineTo|verticalLineTo|arcTo|close)\(([^)]*)\)""",
        )
        return Regex("""path\(fill = SolidColor\([^)]*\)\) \{(.*?)\n {8}\}""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file.readText())
            .map { block ->
                calls.findAll(block.groupValues[1]).map { call ->
                    val args = call.groupValues[2]
                        .split(',')
                        .map { it.trim().removeSuffix("f") }
                        .filter { it.isNotEmpty() }
                    when (call.groupValues[1]) {
                        "moveTo" -> Segment('M', args.map { round(it.toDouble()) })
                        "lineTo" -> Segment('L', args.map { round(it.toDouble()) })
                        "horizontalLineTo" -> Segment('H', args.map { round(it.toDouble()) })
                        "verticalLineTo" -> Segment('V', args.map { round(it.toDouble()) })
                        // arcTo(rx, ry, theta, isMoreThanHalf, isPositiveArc, x1, y1) — the two
                        // booleans are the SVG large-arc and sweep flags.
                        "arcTo" -> Segment(
                            'A',
                            args.mapIndexed { index, arg ->
                                if (index == 3 || index == 4) {
                                    if (arg.toBoolean()) 1.0 else 0.0
                                } else {
                                    round(arg.toDouble())
                                }
                            },
                        )
                        else -> Segment('Z', emptyList())
                    }
                }.toList()
            }
            .toList()
            .also { assertTrue("no path blocks found in ${file.path}", it.isNotEmpty()) }
    }
}
