package com.swipey.app.ui.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Swipey's design language, in one file.
 *
 * Deliberately *not* Material. The app's content is the user's own photographs; every
 * token here exists to keep the interface quiet enough that they stay the loudest thing
 * on screen. That means: a near-black canvas, one accent per action, hairlines instead of
 * elevation, and no shadows anywhere. There is no `MaterialTheme` in the tree.
 *
 * Everything is reached through the [SwipeyTheme] object — `SwipeyTheme.colors.keep`,
 * `SwipeyTheme.typography.title` — mirroring the shape Compose users already expect,
 * without inheriting Material's visual opinions.
 */

// ---------------------------------------------------------------------------
// Colour
// ---------------------------------------------------------------------------

/**
 * The full colour vocabulary. Twelve roles, no more — a small palette is what makes an
 * interface look designed rather than assembled.
 *
 * @property canvas the page itself; the darkest (or lightest) ground.
 * @property surface a raised-by-implication plane — cards, sheets, rows. No shadow marks
 *   it as raised; the one-step tonal shift does all the work.
 * @property surfaceStrong a second, slightly further step for a surface sitting *on* a
 *   surface (a sheet's grabber, a chip inside a card).
 * @property hairline the only border colour. One pixel, never two.
 * @property textPrimary body and headings.
 * @property textSecondary supporting copy, counters, timestamps.
 * @property textDisabled the same voice at rest; used for disabled controls.
 * @property keep the "keep this photo" accent. Green, but a cool restrained one.
 * @property bin the "bin this photo" accent. Rose rather than red — the app never
 *   permanently deletes anything, and an alarm-red would overstate what a swipe does.
 * @property onAccent ink that sits legibly on [keep], [bin] or an inverted neutral fill.
 * @property scrim the dim behind sheets and dialogs.
 * @property isDark which of the two palettes this is; lets a component branch when a
 *   mirrored value genuinely can't be expressed as a token (e.g. status-bar icons).
 */
@Immutable
data class SwipeyColors(
    val canvas: Color,
    val surface: Color,
    val surfaceStrong: Color,
    val hairline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textDisabled: Color,
    val keep: Color,
    val bin: Color,
    val onAccent: Color,
    val scrim: Color,
    val isDark: Boolean,
)

/**
 * The primary palette. Swipey is dark-first: a photo shown against near-black reads at
 * its own contrast rather than the interface's.
 *
 * [canvas] is `#0B0B0D` rather than pure black on purpose — true black lets OLED smear
 * on scroll and makes a photo's own black borders disappear into the page.
 */
val SwipeyDarkColors = SwipeyColors(
    canvas = Color(0xFF0B0B0D),
    surface = Color(0xFF141417),
    surfaceStrong = Color(0xFF1D1D22),
    hairline = Color(0xFF26262B),
    textPrimary = Color(0xFFF5F5F7),
    textSecondary = Color(0xFF9A9AA2),
    textDisabled = Color(0xFF5A5A62),
    keep = Color(0xFF34D399),
    bin = Color(0xFFFB7185),
    // Both accents are light colours, so dark ink sits on them.
    onAccent = Color(0xFF0B0B0D),
    scrim = Color(0xCC000000),
    isDark = true,
)

/**
 * The daylight mirror. Structurally identical — same roles, same relationships — but the
 * accents are *not* the same hex values.
 *
 * `#34D399` and `#FB7185` are tuned to carry a dark ground; on white they measure roughly
 * 1.6:1 and 2.5:1, far under the 4.5:1 that body-size text needs and under the 3:1 a
 * non-text control needs. So the light palette walks both accents several steps darker to
 * the same hues' 600/700 weights, which clear 5:1 against white in both directions —
 * as a fill under white ink, and as ink on white.
 */
val SwipeyLightColors = SwipeyColors(
    canvas = Color(0xFFFBFBFD),
    surface = Color(0xFFFFFFFF),
    surfaceStrong = Color(0xFFF2F2F5),
    hairline = Color(0xFFE4E4E9),
    textPrimary = Color(0xFF0B0B0D),
    textSecondary = Color(0xFF6B6B74),
    textDisabled = Color(0xFFA8A8B0),
    keep = Color(0xFF047857),
    bin = Color(0xFFE11D48),
    // Both accents are dark colours here, so the ink inverts too.
    onAccent = Color(0xFFFFFFFF),
    scrim = Color(0x99000000),
    isDark = false,
)

// ---------------------------------------------------------------------------
// Type
// ---------------------------------------------------------------------------

/**
 * Four sizes, and a tabular twin for each.
 *
 * The `*Numeric` styles differ only in `fontFeatureSettings = "tnum"`, which switches the
 * system font to fixed-advance digits. Swipey shows a live counter on every card
 * ("47 / 312", "12 marked · 1.2 GB"); with proportional figures a `1` is narrower than a
 * `0`, so the whole string twitches sideways on each swipe. Tabular figures pin it still.
 * Use them for anything whose digits change in place.
 *
 * Sizes are `sp`, not `dp`, so the user's font-scale setting reaches them. Line heights
 * are `sp` for the same reason — a `dp` line height would clip descenders the moment the
 * text grew.
 */
@Immutable
data class SwipeyTypography(
    val display: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val label: TextStyle,
    val displayNumeric: TextStyle,
    val titleNumeric: TextStyle,
    val bodyNumeric: TextStyle,
    val labelNumeric: TextStyle,
)

/** Switches [this] to fixed-advance digits. See [SwipeyTypography]. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")

private val Display = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 34.sp,
    lineHeight = 40.sp,
    letterSpacing = (-0.5).sp,
)

private val Title = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.SemiBold,
    fontSize = 22.sp,
    lineHeight = 28.sp,
    letterSpacing = (-0.2).sp,
)

private val Body = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Normal,
    fontSize = 15.sp,
    lineHeight = 22.sp,
)

private val Label = TextStyle(
    fontFamily = FontFamily.Default,
    fontWeight = FontWeight.Medium,
    fontSize = 13.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.2.sp,
)

/** The system font family throughout — Swipey downloads no fonts. */
val SwipeyDefaultTypography = SwipeyTypography(
    display = Display,
    title = Title,
    body = Body,
    label = Label,
    displayNumeric = Display.tabular(),
    titleNumeric = Title.tabular(),
    bodyNumeric = Body.tabular(),
    labelNumeric = Label.tabular(),
)

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

/**
 * The spacing scale: 4 · 8 · 12 · 16 · 24 · 32 · 48.
 *
 * Every gap in the app is one of these seven. Nothing between them, and nothing above 48
 * that isn't built by composing them — the restriction is the point, since consistent
 * rhythm is most of what reads as "designed".
 */
object SwipeySpacing {
    /** 4.dp — between a label and the thing it labels. */
    val xs = 4.dp

    /** 8.dp — inside a chip; between stacked lines of one idea. */
    val sm = 8.dp

    /** 12.dp — between sibling controls. */
    val md = 12.dp

    /** 16.dp — the default screen gutter and card padding. */
    val lg = 16.dp

    /** 24.dp — between distinct groups. */
    val xl = 24.dp

    /** 32.dp — around a screen's single primary action. */
    val xxl = 32.dp

    /** 48.dp — the breathing room above an empty state's headline. */
    val xxxl = 48.dp
}

/** Corner radii. Two shapes, plus one for sheets. */
object SwipeyRadius {
    /** 12.dp — cards, dialogs, thumbnails. */
    val card = 12.dp

    /** 999.dp — buttons and chips; a true pill at any height. */
    val pill = 999.dp

    /** 20.dp — a bottom sheet's top corners, a touch softer than a card. */
    val sheet = 20.dp
}

/** Fixed sizes that recur, mostly accessibility floors. */
object SwipeySize {
    /** 48.dp — the minimum touch target for anything tappable. Non-negotiable. */
    val touchMin = 48.dp

    /**
     * 56.dp — the deck's Bin/Undo/Keep controls. These are hit one-handed, at speed,
     * hundreds of times in a session, and a mis-tap costs the user a photo, so they get
     * more than the floor.
     */
    val touchPrimary = 56.dp

    /** 1.dp — every border and divider in the app. */
    val hairline = 1.dp

    /** 2.dp — the progress indicator's full height. */
    val progress = 2.dp

    /** 24.dp — the standard icon square. */
    val icon = 24.dp
}

/** Opacity applied to a disabled control's content, in place of a separate grey. */
const val SwipeyDisabledAlpha = 0.38f

// ---------------------------------------------------------------------------
// Wiring
// ---------------------------------------------------------------------------

/**
 * Defaults are the dark palette rather than an error, so a stray `@Preview` or a
 * component pulled out of the tree still renders something coherent.
 */
val LocalSwipeyColors = staticCompositionLocalOf { SwipeyDarkColors }
val LocalSwipeyTypography = staticCompositionLocalOf { SwipeyDefaultTypography }

/**
 * The ink colour a [SwipeyText] uses when it isn't told one. Containers that invert their
 * content — a filled button, a chip — provide this rather than passing colours down by
 * hand, so nested text stays legible without every call site knowing where it sits.
 */
val LocalSwipeyContentColor = staticCompositionLocalOf { SwipeyDarkColors.textPrimary }

/**
 * Provides the design system to [content]. This is the app's only theme wrapper —
 * `MainActivity` calls it, and there is no `MaterialTheme` above or below it.
 *
 * @param darkTheme follows the system setting by default; pass explicitly to pin a
 *   palette (previews do this).
 */
@Composable
fun SwipeyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    colors: SwipeyColors = if (darkTheme) SwipeyDarkColors else SwipeyLightColors,
    typography: SwipeyTypography = SwipeyDefaultTypography,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSwipeyColors provides colors,
        LocalSwipeyTypography provides typography,
        LocalSwipeyContentColor provides colors.textPrimary,
        content = content,
    )
}

/**
 * The accessor object. Shares its name with the [SwipeyTheme] composable above — the same
 * arrangement Compose itself uses for `MaterialTheme` — so `SwipeyTheme { }` wraps and
 * `SwipeyTheme.colors` reads.
 */
object SwipeyTheme {
    val colors: SwipeyColors
        @Composable @ReadOnlyComposable get() = LocalSwipeyColors.current

    val typography: SwipeyTypography
        @Composable @ReadOnlyComposable get() = LocalSwipeyTypography.current

    /** The ink colour for the current container. See [LocalSwipeyContentColor]. */
    val contentColor: Color
        @Composable @ReadOnlyComposable get() = LocalSwipeyContentColor.current
}
