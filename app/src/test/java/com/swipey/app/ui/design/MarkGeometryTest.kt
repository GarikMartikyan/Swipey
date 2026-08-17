package com.swipey.app.ui.design

import java.io.File
import kotlin.math.hypot
import kotlin.math.sqrt
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
        val bounds = inkBoundsOf(icon.flatten())
        val originX = round(bounds.minX)
        val originY = round(bounds.minY)

        val expected = icon.map { segments -> segments.map { it.translated(-originX, -originY) } }
        val actual = kotlinPathsOf(markSource)

        assertEquals("Mark.kt must declare the same number of paths as the icon", expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (want, got) ->
            assertEquals(
                "path $index in Mark.kt has drifted from the launcher artwork " +
                    "(expected the icon's coordinates minus the ink origin $originX, $originY)",
                want,
                got,
            )
        }
    }

    @Test fun theInAppViewportMatchesTheMarksInk() {
        val bounds = inkBoundsOf(pathDataOf(foreground).map(::parsePathData).flatten())

        val declared = Regex("""private const val Viewport(Width|Height) = ([\d.]+)f""")
            .findAll(markSource.readText())
            .associate { it.groupValues[1] to it.groupValues[2].toDouble() }

        assertEquals("Mark.kt must declare both viewport constants", 2, declared.size)
        // Ink, not vertices. A leaning card's widest point is out on a corner arc, past every
        // point its path names; a viewport measured from the vertices would shave it.
        assertEquals("ViewportWidth", bounds.maxX - bounds.minX, declared.getValue("Width"), 0.001)
        assertEquals("ViewportHeight", bounds.maxY - bounds.minY, declared.getValue("Height"), 0.001)
    }

    @Test fun markStaysInsideTheGuaranteedMaskCircle() {
        val segments = pathDataOf(foreground).map(::parsePathData).flatten()
        val corners = cornersOf(segments)

        // Every extreme of a rounded shape lies on one of its corner arcs, so the furthest
        // ink from the canvas centre is the furthest arc centre plus that arc's radius. This
        // replaces a closed form that assumed a single upright rectangle with one radius —
        // true of the mark this test was written for, and false of every shape since.
        val reach = corners.maxOf { hypot(it.x - canvasCentre, it.y - canvasCentre) + it.radius }

        assertTrue(
            "the mark's corner ink reaches ${"%.2f".format(reach)}dp from centre, past the " +
                "${guaranteedRadius}dp circle every launcher mask is guaranteed to show. A circle " +
                "mask will shave that corner and the silhouette will read as a cut-off slab. " +
                "Shrink the artwork or move it toward the centre.",
            reach <= guaranteedRadius,
        )

        // Off-centre artwork looks fine in a square preview and lopsided inside a circle.
        val bounds = inkBoundsOf(segments)
        assertEquals("mark is not horizontally centred on the canvas", canvasCentre, (bounds.minX + bounds.maxX) / 2, 0.001)
        assertEquals("mark is not vertically centred on the canvas", canvasCentre, (bounds.minY + bounds.maxY) / 2, 0.001)
    }

    @Test fun everyArcIsTheMinorSweptArcTheGeometryAssumes() {
        // cornerOf solves for the centre of a minor arc with the sweep flag set. Both flags
        // are operands 3 and 4, and an arc that set either differently would put its centre
        // on the other side — silently moving where this test thinks the ink is.
        pathDataOf(foreground).map(::parsePathData).flatten()
            .filter { it.command == 'A' }
            .forEach {
                assertEquals("large-arc flag must be 0", 0.0, it.operands[3], 0.0)
                assertEquals("sweep flag must be 1", 1.0, it.operands[4], 0.0)
            }
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

    /** One corner arc, resolved to the circle it is part of. */
    private data class Corner(val x: Double, val y: Double, val radius: Double)

    /** The box the mark's ink actually occupies. */
    private data class Bounds(val minX: Double, val minY: Double, val maxX: Double, val maxY: Double)

    /**
     * Every corner arc in [segments], as the centre and radius of its circle.
     *
     * Walks the path tracking the current point, because an arc is defined by where it
     * starts as well as where it ends, and `H`/`V`/`Z` all move that point without naming
     * both coordinates.
     *
     * The centre of a minor arc from p0 to p1 sits on the perpendicular bisector of the
     * chord, `sqrt(r² - (chord/2)²)` from its midpoint, on the side the sweep flag chooses —
     * which for SVG's y-down space and a set sweep flag is the `(-Δy, Δx)` normal.
     * [everyArcIsTheMinorSweptArcTheGeometryAssumes] holds the artwork to that assumption.
     */
    private fun cornersOf(segments: List<Segment>): List<Corner> {
        var x = 0.0
        var y = 0.0
        var startX = 0.0
        var startY = 0.0
        val corners = mutableListOf<Corner>()
        segments.forEach { segment ->
            when (segment.command) {
                'M' -> { x = segment.operands[0]; y = segment.operands[1]; startX = x; startY = y }
                'L' -> { x = segment.operands[0]; y = segment.operands[1] }
                'H' -> x = segment.operands[0]
                'V' -> y = segment.operands[0]
                'A' -> {
                    val radius = segment.operands[0]
                    val endX = segment.operands[5]
                    val endY = segment.operands[6]
                    val dx = endX - x
                    val dy = endY - y
                    val chord = hypot(dx, dy)
                    val out = sqrt((radius * radius - chord * chord / 4).coerceAtLeast(0.0))
                    corners += Corner(
                        x = (x + endX) / 2 - dy / chord * out,
                        y = (y + endY) / 2 + dx / chord * out,
                        radius = radius,
                    )
                    x = endX
                    y = endY
                }
                'Z' -> { x = startX; y = startY }
            }
        }
        return corners
    }

    /**
     * The ink's bounding box: every corner circle's extent.
     *
     * Straight edges are chords between those circles and can never reach past them, so the
     * corners alone are the whole answer.
     */
    private fun inkBoundsOf(segments: List<Segment>): Bounds {
        val corners = cornersOf(segments)
        assertTrue("the mark has no arcs to measure", corners.isNotEmpty())
        return Bounds(
            minX = corners.minOf { it.x - it.radius },
            minY = corners.minOf { it.y - it.radius },
            maxX = corners.maxOf { it.x + it.radius },
            maxY = corners.maxOf { it.y + it.radius },
        )
    }

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
