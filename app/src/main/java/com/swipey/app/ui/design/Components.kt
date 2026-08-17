package com.swipey.app.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

/**
 * Swipey's primitives. Built on `androidx.compose.foundation` only — no Material widget
 * appears anywhere in this file, and none should ever be added to it.
 *
 * The shared conventions, so a caller only has to learn them once:
 *  - **Colour** comes from [SwipeyTheme.colors]; a component takes a `Color` parameter
 *    only where the choice is genuinely the caller's.
 *  - **Height** is a floor (`defaultMinSize`), never a fixed value, so a control grows
 *    with the user's font-scale setting instead of clipping its own label.
 *  - **Press feedback** is an explicit scale-and-dim, not a ripple. `indication = null`
 *    everywhere; nothing here depends on Material's `LocalIndication`.
 *  - **Disabled** means the click is gone, the content is dimmed to
 *    [SwipeyDisabledAlpha], *and* the control drops its ring. Never one without the
 *    others — see [SwipeyDisabledAlpha] for why opacity alone is not enough here.
 */

// ---------------------------------------------------------------------------
// Tone and variant
// ---------------------------------------------------------------------------

/**
 * Which of the app's three voices a control speaks in.
 *
 * Swipey has exactly two decisions — keep and bin — and everything else is navigation.
 * Tone maps a control onto that: colour is never chosen at a call site, only a meaning.
 *
 * ### Four properties, not one
 * A tone used to resolve to a single colour. It resolves to four now — a [container], an
 * [onContainer], a [ring] and a [ground] — because [SwipeyColors.bin] is a neutral with
 * the same value as [SwipeyColors.textSecondary], and a control whose only distinguishing
 * feature is a grey glyph is indistinguishable from a caption, and from a disabled
 * control. The bin control earns its "I am a target" reading from *treatment* rather than
 * hue: a filled ground and a ring in its own colour, both of which static text never has
 * and a disabled control loses.
 */
enum class SwipeyTone {
    /** Navigation, confirmation, dismissal. Renders as inverted ink — no hue at all. */
    Neutral,

    /** Keeping a photo. The palette's one accent, and the only place it appears. */
    Keep,

    /** Binning a photo, and committing a batch to the system trash. A neutral. */
    Bin,
}

/** How much weight a control carries. */
enum class SwipeyButtonVariant {
    /** A solid fill. One per screen, at most — this is the thing to do next. */
    Filled,

    /** A hairline outline over the canvas. Everything else. */
    Ghost,
}

/** The fill behind a [SwipeyButtonVariant.Filled] control of this tone. */
@Composable
private fun SwipeyTone.container(): Color = when (this) {
    // Inverting the text colour gives a high-contrast neutral fill in both palettes
    // without introducing a colour that means nothing.
    SwipeyTone.Neutral -> SwipeyTheme.colors.textPrimary
    SwipeyTone.Keep -> SwipeyTheme.colors.keep
    SwipeyTone.Bin -> SwipeyTheme.colors.bin
}

/** The ink on a [SwipeyButtonVariant.Filled] control of this tone. */
@Composable
private fun SwipeyTone.onContainer(): Color = when (this) {
    SwipeyTone.Neutral -> SwipeyTheme.colors.canvas
    SwipeyTone.Keep -> SwipeyTheme.colors.onAccent
    // A mid neutral is the one fill whose legible ink flips between the palettes: white
    // clears 5.0:1 on the light theme's #6B7076, but only 3.2:1 on the dark theme's
    // #8B9097, where near-black clears 6.0:1 instead. Precisely the case
    // [SwipeyColors.isDark] exists for.
    SwipeyTone.Bin -> with(SwipeyTheme.colors) { if (isDark) canvas else onAccent }
}

/** The ink on a [SwipeyButtonVariant.Ghost] control of this tone. */
@Composable
private fun SwipeyTone.ink(): Color = when (this) {
    SwipeyTone.Neutral -> SwipeyTheme.colors.textPrimary
    SwipeyTone.Keep -> SwipeyTheme.colors.keep
    SwipeyTone.Bin -> SwipeyTheme.colors.bin
}

/**
 * The ring around an **enabled** [SwipeyButtonVariant.Ghost] control of this tone.
 *
 * Neutral keeps the ordinary hairline: its ink is [SwipeyColors.textPrimary], which is
 * already the loudest thing in the palette and needs no help being noticed. The two
 * decision tones ring themselves in their own colour instead — for Keep that is a
 * flourish, for Bin it is the whole affordance. A disabled control has no ring at all;
 * see [SwipeyDisabledAlpha].
 */
@Composable
private fun SwipeyTone.ring(): Color = when (this) {
    SwipeyTone.Neutral -> SwipeyTheme.colors.hairline
    SwipeyTone.Keep -> SwipeyTheme.colors.keep
    SwipeyTone.Bin -> SwipeyTheme.colors.bin
}

/**
 * The ground behind a [SwipeyButtonVariant.Ghost] control of this tone.
 *
 * Transparent for Neutral, so the app's ordinary secondary buttons stay outlines over the
 * canvas. The decision tones get [SwipeyColors.surfaceStrong], which is what turns a bin
 * glyph from a grey mark into an object with edges — and, on the deck, gives both
 * decisions an opaque disc to sit on over an arbitrarily bright photograph.
 */
@Composable
private fun SwipeyTone.ground(): Color = when (this) {
    SwipeyTone.Neutral -> Color.Transparent
    SwipeyTone.Keep, SwipeyTone.Bin -> SwipeyTheme.colors.surfaceStrong
}

/**
 * Paints a [SwipeyButtonVariant.Ghost] control's ground and ring.
 *
 * The ring is what disappears when [enabled] is false; the ground stays. Both halves of
 * that are deliberate — see [SwipeyDisabledAlpha].
 */
private fun Modifier.ghostSkin(ground: Color, ring: Color, enabled: Boolean, shape: Shape): Modifier =
    this
        .then(if (ground == Color.Transparent) Modifier else Modifier.background(ground))
        .then(if (enabled) Modifier.border(SwipeySize.hairline, ring, shape) else Modifier)

// ---------------------------------------------------------------------------
// Text
// ---------------------------------------------------------------------------

/**
 * Text, in one of the type scale's styles.
 *
 * Defaults to [SwipeyTypography.body] at the container's content colour, so the common
 * case is `SwipeyText("...")`. For a counter, pass one of the `*Numeric` styles — see
 * [SwipeyTypography] for why that matters.
 *
 * @param color defaults to [SwipeyTheme.contentColor], which containers such as
 *   [SwipeyButton] and [SwipeyChip] override for their own contents.
 */
@Composable
fun SwipeyText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SwipeyTheme.typography.body,
    color: Color = SwipeyTheme.contentColor,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.merge(TextStyle(color = color, textAlign = textAlign ?: TextAlign.Unspecified)),
        maxLines = maxLines,
        overflow = overflow,
    )
}

// ---------------------------------------------------------------------------
// Icons
// ---------------------------------------------------------------------------

/**
 * Draws one of [SwipeyIcons] at [tint].
 *
 * The glyphs are stored black and tinted here, so a single vector serves both palettes.
 *
 * @param contentDescription pass `null` **only** when the icon is decorative and an
 *   adjacent label already says what it means; anything an icon alone conveys must be
 *   described. For an icon that is itself the control, use [SwipeyIconButton], which
 *   makes the description mandatory.
 */
@Composable
fun SwipeyIcon(
    icon: ImageVector,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = SwipeyTheme.contentColor,
    size: Dp = SwipeySize.icon,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = contentDescription,
        modifier = modifier.size(size),
        colorFilter = ColorFilter.tint(tint),
    )
}

// ---------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------

/**
 * A pill button.
 *
 * Presses register as a slight scale-down rather than a ripple — quieter, and it reads
 * correctly on top of a photograph, which a spreading light circle does not.
 *
 * The height is a *minimum*, not a fixed value: at a 200% font scale the label grows and
 * takes the pill with it, and because the corner radius is [SwipeyRadius.pill] the shape
 * stays a true pill at any height it ends up.
 *
 * @param tone what the button means; see [SwipeyTone]. Colour follows from it.
 * @param fillWidth for a screen's single primary action, which spans the gutter.
 * @param icon optional leading glyph. Decorative — [text] is the accessible name.
 */
@Composable
fun SwipeyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SwipeyButtonVariant = SwipeyButtonVariant.Filled,
    tone: SwipeyTone = SwipeyTone.Neutral,
    enabled: Boolean = true,
    fillWidth: Boolean = false,
    icon: ImageVector? = null,
) {
    val filled = variant == SwipeyButtonVariant.Filled
    val content = if (filled) tone.onContainer() else tone.ink()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.96f else 1f,
        animationSpec = SwipeyMotion.press(),
        label = "buttonPress",
    )
    val shape = RoundedCornerShape(SwipeyRadius.pill)
    val container = tone.container()
    val ground = tone.ground()
    val ring = tone.ring()

    Row(
        modifier
            .scale(scale)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            // The floor, not the height — see the KDoc above.
            .defaultMinSize(minHeight = SwipeySize.touchMin)
            .clip(shape)
            .then(if (filled) Modifier.background(container) else Modifier.ghostSkin(ground, ring, enabled, shape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else SwipeyDisabledAlpha)
            .padding(horizontal = SwipeySpacing.xl, vertical = SwipeySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SwipeyIcon(icon, contentDescription = null, tint = content, size = 18.dp)
        }
        SwipeyText(text, style = SwipeyTheme.typography.label, color = content, maxLines = 2)
    }
}

/**
 * A circular, icon-only button.
 *
 * Defaults to [SwipeySize.touchPrimary] (56dp) rather than the 48dp floor, because the
 * three controls under the deck are what this is mostly for: hit one-handed, at speed,
 * where a mis-tap costs a photograph.
 *
 * In the [SwipeyButtonVariant.Ghost] variant a Keep or Bin control is a **disc**: a
 * [SwipeyColors.surfaceStrong] ground with a ring in its own tone. That is what stops the
 * Bin control — whose glyph is a neutral one value away from body copy — from reading as
 * a label, or as one of its own disabled states. See [SwipeyTone].
 *
 * @param contentDescription required, and deliberately not nullable. This control has no
 *   visible label, so without it a screen reader announces nothing at all.
 * @param size how big the disc is *drawn*. Below [SwipeySize.touchMin] the drawn disc
 *   shrinks but the tappable box does not: the 48dp floor is held by an invisible box
 *   around it. That split is what lets a control sit quietly beside a section heading —
 *   see Home's albums toggle — without becoming a 48dp circle that outweighs the words it
 *   belongs to, and without a small target the design system elsewhere calls
 *   non-negotiable.
 */
@Composable
fun SwipeyIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: SwipeyButtonVariant = SwipeyButtonVariant.Ghost,
    tone: SwipeyTone = SwipeyTone.Neutral,
    enabled: Boolean = true,
    size: Dp = SwipeySize.touchPrimary,
    iconSize: Dp = SwipeySize.icon,
) {
    val filled = variant == SwipeyButtonVariant.Filled
    val content = if (filled) tone.onContainer() else tone.ink()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.92f else 1f,
        animationSpec = SwipeyMotion.press(),
        label = "iconButtonPress",
    )

    val container = tone.container()
    val ground = tone.ground()
    val ring = tone.ring()

    // Two boxes, not one: the outer carries the touch target and the inner is what the eye
    // sees. They are the same rectangle at every size at or above the floor, so this costs
    // the deck's controls nothing.
    Box(
        modifier
            .size(size.coerceAtLeast(SwipeySize.touchMin))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(size)
                .clip(CircleShape)
                .then(
                    if (filled) Modifier.background(container)
                    else Modifier.ghostSkin(ground, ring, enabled, CircleShape),
                )
                .alpha(if (enabled) 1f else SwipeyDisabledAlpha),
            contentAlignment = Alignment.Center,
        ) {
            SwipeyIcon(icon, contentDescription = contentDescription, tint = content, size = iconSize)
        }
    }
}

// ---------------------------------------------------------------------------
// Captions over photographs
// ---------------------------------------------------------------------------

/**
 * The dark a caption is read against when it is printed on a photograph.
 *
 * Black rather than the palette's surface, and fixed in both themes, because what is
 * underneath is not the canvas — it is a picture the app did not choose, and the only
 * colour that reliably darkens an arbitrary one is black.
 *
 * The value is measured rather than picked. [SwipeyColors.scrim]'s 60% is tuned to dim a
 * whole screen behind a sheet; over an arbitrary photograph — which may be a snow field —
 * it leaves white text at roughly 2.5:1. At 90% a title measures ~17:1 and a caption line
 * ~5.5:1 whatever is underneath. The photograph is still faintly there as texture at that
 * value, which is the point: a caption should look printed on the picture rather than
 * pasted over a hole cut in it.
 */
val SwipeyCaptionScrim = Color.Black.copy(alpha = 0.9f)

/**
 * The ramp that carries a caption printed over a photograph.
 *
 * Flat at [SwipeyCaptionScrim] over the bottom third of its own height, so the words never
 * land on the part of the gradient that is still translucent — the failure it exists to
 * prevent is a caption whose first line is legible and whose second is not.
 *
 * Shared rather than written twice. Home names albums over their covers and the deck names
 * the photograph it is asking you to judge; those are the same act, and two screens that
 * caption a picture two different ways read as two apps. [heightFraction] is the one thing
 * that varies, because it is set by the shape of what it sits on — a 1:1 album tile needs a
 * ramp over most of its height to reach a readable value, while a full-height card would go
 * murky at the same fraction.
 *
 * [scrim] is the escape hatch, and it comes with a bill. Every step lighter than
 * [SwipeyCaptionScrim] is contrast taken away from the words: at 0.9 white text clears 7:1
 * against the worst case this ramp exists for — a white sky — and by 0.62 that is down to
 * about 2.4:1, which is unreadable. A caller that lightens it therefore owes the type
 * something back, and a shadow is the cheapest thing that works, because it is legible
 * against a light ground exactly where a scrim has stopped being. See the deck's caption.
 */
@Composable
fun BoxScope.SwipeyCaptionGradient(
    heightFraction: Float,
    fromTop: Boolean = false,
    scrim: Color = SwipeyCaptionScrim,
) {
    // The same ramp read in the other direction: flat against whichever edge the caption is
    // on, and transparent by the time it reaches the middle of the picture. The flat third
    // stays a third either way — that is the part the caption's legibility is reasoned
    // from, so it is the part that has to survive the flip.
    val stops = if (fromTop) {
        arrayOf(
            0f to scrim,
            0.3f to scrim,
            1f to Color.Transparent,
        )
    } else {
        arrayOf(
            0f to Color.Transparent,
            0.7f to scrim,
            1f to scrim,
        )
    }

    Box(
        Modifier
            .align(if (fromTop) Alignment.TopStart else Alignment.BottomStart)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .background(Brush.verticalGradient(colorStops = stops)),
    )
}

// ---------------------------------------------------------------------------
// Containers
// ---------------------------------------------------------------------------

/**
 * A surface with a 12dp radius and a hairline edge. No shadow, no elevation.
 *
 * Depth here is a one-step tonal shift plus a border, which is the whole reason the
 * palette carries both `surface` and `hairline`. Shadows would put a soft grey halo
 * around every photograph on the screen.
 *
 * @param onClick makes the whole card tappable; `null` leaves it inert.
 */
@Composable
fun SwipeyCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    bordered: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(SwipeySpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(SwipeyRadius.card)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && onClick != null) 0.99f else 1f,
        animationSpec = SwipeyMotion.press(),
        label = "cardPress",
    )

    Column(
        modifier
            .scale(scale)
            .clip(shape)
            .background(SwipeyTheme.colors.surface)
            .then(if (bordered) Modifier.border(SwipeySize.hairline, SwipeyTheme.colors.hairline, shape) else Modifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else SwipeyDisabledAlpha)
            .padding(contentPadding),
        content = content,
    )
}

/** The app's only divider: one hairline, full width, in [SwipeyColors.hairline]. */
@Composable
fun SwipeyDivider(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(SwipeySize.hairline)
            .background(SwipeyTheme.colors.hairline),
    )
}

/**
 * A list row: title, optional subtitle, optional trailing text, hairline divider.
 *
 * Rows are how Home, Albums, the sort chooser and the Bin are all built, so this carries
 * the app's list rhythm — a 56dp floor and a 16dp gutter.
 *
 * The whole row is one tap target, and the divider sits *below* the row's padding rather
 * than inside it, so a run of rows produces evenly spaced rules rather than boxes.
 *
 * @param trailing right-aligned supporting text — a count, a size. Rendered in the
 *   tabular label style, since that is almost always what it is.
 * @param showChevron defaults to whether the row does anything when tapped.
 */
@Composable
fun SwipeyRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    showChevron: Boolean = onClick != null,
    divider: Boolean = true,
    leading: @Composable (() -> Unit)? = null,
) {
    val colors = SwipeyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onClick,
                        )
                    } else {
                        Modifier
                    },
                )
                // A pressed row dims its ground rather than scaling — a row is wide
                // enough that scaling it would visibly shear the rows either side.
                .background(if (pressed && enabled) colors.surfaceStrong else Color.Transparent)
                .alpha(if (enabled) 1f else SwipeyDisabledAlpha)
                .defaultMinSize(minHeight = SwipeySize.touchPrimary)
                .padding(vertical = SwipeySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(SwipeySpacing.md))
            }
            Column(Modifier.weight(1f)) {
                SwipeyText(title, style = SwipeyTheme.typography.body, color = colors.textPrimary)
                if (subtitle != null) {
                    Spacer(Modifier.height(SwipeySpacing.xs))
                    SwipeyText(subtitle, style = SwipeyTheme.typography.label, color = colors.textSecondary)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(SwipeySpacing.md))
                SwipeyText(
                    trailing,
                    style = SwipeyTheme.typography.labelNumeric,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
            if (showChevron) {
                Spacer(Modifier.width(SwipeySpacing.sm))
                SwipeyIcon(
                    SwipeyIcons.ChevronRight,
                    // Decorative: the row's own title is its accessible name, and
                    // "navigates onward" is already implied by the row being clickable.
                    contentDescription = null,
                    tint = colors.textSecondary,
                    size = 18.dp,
                )
            }
        }
        if (divider) SwipeyDivider()
    }
}

/**
 * A small pill for a count or a status — "12 marked · 1.2 GB".
 *
 * Uses tabular figures by default, because that string is the one that changes on every
 * single swipe; see [SwipeyTypography].
 *
 * When [onClick] is given the *visual* pill stays small but the tap target is padded out
 * to [SwipeySize.touchMin], so a chip never becomes a 30dp target. Pass [onClickLabel] with
 * the verb — a screen reader then offers "double tap to review" rather than leaving the
 * count to imply what happens.
 *
 * [trailingIcon] is how a chip that *goes somewhere* says so. A count and a hairline look
 * exactly like a caption; a chevron after the label is the cheapest mark that reads as a
 * door, and it costs 14dp. Reach for it whenever tapping the chip navigates rather than
 * toggling — and leave it off when it does not, or it becomes decoration and stops meaning
 * anything anywhere.
 *
 * A toned chip carries its hue in the **glyph** and keeps its label at
 * [SwipeyColors.textPrimary]. Neither decision colour survives being body text in this
 * palette: `bin` *is* `textSecondary`, so a bin-coloured label would be a caption rather
 * than a control, and `keep` measures 3.9:1 on `surface` in the dark palette, under the
 * 4.5:1 a 13sp label needs. The glyph is a non-text element and clears its own 3:1 floor
 * comfortably, so that is where the colour goes.
 */
@Composable
fun SwipeyChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: SwipeyTone = SwipeyTone.Neutral,
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    tabular: Boolean = true,
) {
    val colors = SwipeyTheme.colors
    val glyph = when (tone) {
        SwipeyTone.Neutral -> colors.textSecondary
        SwipeyTone.Keep -> colors.keep
        SwipeyTone.Bin -> colors.bin
    }
    // See the KDoc: a neutral chip is a caption and reads as one; a toned chip is a
    // control, and its label goes to full reading contrast so it looks like one.
    val ink = if (tone == SwipeyTone.Neutral) colors.textSecondary else colors.textPrimary
    val shape = RoundedCornerShape(SwipeyRadius.pill)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val style = if (tabular) SwipeyTheme.typography.labelNumeric else SwipeyTheme.typography.label

    Box(
        modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .defaultMinSize(minHeight = SwipeySize.touchMin)
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            enabled = enabled,
                            onClickLabel = onClickLabel,
                            role = Role.Button,
                            onClick = onClick,
                        )
                } else {
                    Modifier
                },
            )
            .alpha(if (enabled) 1f else SwipeyDisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .clip(shape)
                .background(if (pressed && enabled) colors.surfaceStrong else colors.surface)
                .border(SwipeySize.hairline, colors.hairline, shape)
                // Tighter on the trailing side when a chevron is there: a chevron is mostly
                // its own whitespace, and padding it like a word leaves the pill looking
                // lopsided with a gap where the arrow should be pointing out of.
                .padding(
                    start = SwipeySpacing.md,
                    end = if (trailingIcon != null) SwipeySpacing.sm else SwipeySpacing.md,
                    top = SwipeySpacing.sm,
                    bottom = SwipeySpacing.sm,
                ),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SwipeyIcon(icon, contentDescription = null, tint = glyph, size = 14.dp)
            }
            SwipeyText(text, style = style, color = ink, maxLines = 1)
            if (trailingIcon != null) {
                // Secondary, always — this one is not saying anything, it is only reporting
                // that the pill goes somewhere. At the label's own weight it would read as a
                // second glyph competing with the leading one for the same sentence.
                SwipeyIcon(trailingIcon, contentDescription = null, tint = colors.textSecondary, size = 14.dp)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Progress
// ---------------------------------------------------------------------------

/**
 * A 2dp hairline progress indicator.
 *
 * @param progress `0f..1f` for determinate; **`null` for indeterminate**, which sweeps a
 *   short segment across the track. The indeterminate form replaces the spinner the app
 *   would otherwise need — a 2dp rule at the top of the screen says "working" without
 *   putting a rotating object in the middle of a photograph.
 * @param contentDescription supply for a determinate bar that is the only report of a
 *   long operation's state; the correct range info is published alongside it. Leave
 *   `null` for a purely decorative loading hint.
 */
@Composable
fun SwipeyProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    trackColor: Color = SwipeyTheme.colors.hairline,
    indicatorColor: Color = SwipeyTheme.colors.textPrimary,
    contentDescription: String? = null,
) {
    val target = progress?.coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = SwipeyMotion.progress(),
        label = "progress",
    )
    // Only started when indeterminate, so a determinate bar costs no frames at rest.
    val sweep = if (target == null) {
        val transition = rememberInfiniteTransition(label = "indeterminate")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1100)),
            label = "sweep",
        ).value
    } else {
        0f
    }

    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics {
            this.contentDescription = contentDescription
            progressBarRangeInfo = if (target == null) {
                ProgressBarRangeInfo.Indeterminate
            } else {
                ProgressBarRangeInfo(animated, 0f..1f)
            }
        }
    } else {
        Modifier
    }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(SwipeySize.progress)
            .then(semanticsModifier),
    ) {
        val y = size.height / 2f
        drawLine(trackColor, Offset(0f, y), Offset(size.width, y), strokeWidth = size.height, cap = StrokeCap.Round)
        if (target == null) {
            // A segment a third of the track wide, travelling left to right and wrapping
            // off the end rather than bouncing — a bounce reads as an error.
            val segment = size.width / 3f
            val start = sweep * (size.width + segment) - segment
            drawLine(
                indicatorColor,
                Offset(start.coerceAtLeast(0f), y),
                Offset((start + segment).coerceAtMost(size.width), y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
        } else if (animated > 0f) {
            drawLine(
                indicatorColor,
                Offset(0f, y),
                Offset(size.width * animated, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Scaffold
// ---------------------------------------------------------------------------

/**
 * The root of every screen: canvas colour, system-bar insets, and the screen gutter.
 *
 * `MainActivity` draws edge to edge, so the canvas runs under the status and navigation
 * bars while [content] is inset out from under them. That is what lets a photograph fill
 * the physical screen while the counter above it stays clear of the clock.
 *
 * ### The two slots
 * [content] is inset and gutter-padded — ordinary screen content goes here. [overlay] is
 * full-bleed, drawn on top, and receives no padding at all; it is where [SwipeySheet]
 * goes, since a bottom sheet has to reach the physical bottom edge and manage its own
 * insets. [SwipeyDialog] needs neither slot — it opens its own window.
 *
 * @param applyInsets set `false` for a screen that handles insets itself, such as the
 *   deck, where the photograph is meant to run under the status bar.
 */
@Composable
fun SwipeyScreen(
    modifier: Modifier = Modifier,
    applyInsets: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = SwipeySpacing.lg),
    overlay: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(SwipeyTheme.colors.canvas),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (applyInsets) Modifier.windowInsetsPadding(WindowInsets.safeDrawing) else Modifier)
                .padding(contentPadding),
            content = content,
        )
        overlay()
    }
}

// ---------------------------------------------------------------------------
// Sheet and dialog
// ---------------------------------------------------------------------------

/**
 * How far down the sheet must be pushed before releasing dismisses it rather than
 * putting it back.
 *
 * A third of its own height, measured rather than guessed at, so a tall sheet asks for a
 * long push and the info sheet's short one asks for less. Under half because
 * [SheetFlingVelocity] carries the impatient case, exactly as the deck's pull does — a user
 * who means it flicks, and a user who is exploring gets a long, reversible travel.
 */
private const val SheetDismissFraction = 0.35f

/**
 * The downward speed at which a release dismisses however far the push got. px/s.
 *
 * Lower than the deck's `PullFlingVelocity`, and deliberately: that gesture *opens* a grid
 * over a session in progress and should take some doing, while this one closes a panel the
 * user has finished reading. Getting rid of something should be easier than summoning it.
 */
private const val SheetFlingVelocity = 900f

/**
 * A bottom sheet, built from scratch.
 *
 * Rendered inline rather than in its own window, which is what lets it animate against
 * the screen behind it and pad itself out of the navigation bar. Place it in
 * [SwipeyScreen]'s `overlay` slot — from `content` it would inherit the screen gutter and
 * stop short of the bottom edge.
 *
 * Dismissal is wired four ways, all of which must work: a push down on the sheet itself,
 * the scrim, the system Back gesture, and whatever [content] itself offers.
 *
 * ### The push takes two mechanisms, because the sheet has two kinds of surface
 * Every sheet in the app scrolls its body — the details sheet holds eight rows and is read at
 * 200% font scale — and a scrollable child claims a vertical drag before its parent can see
 * it. So a plain drag detector on the sheet would work on the grabber and the title and do
 * nothing at all across the body, which is most of what a thumb lands on.
 *
 *  - **A drag detector**, for the parts that do not scroll: the grabber, the title, the
 *    padding around them.
 *  - **A nested-scroll connection**, for the body: it takes the downward travel the content
 *    could not use — which, at the top of a list, is all of it — and spends it on the sheet
 *    instead. Dragging up gives it back before the content scrolls again, so the sheet is
 *    always the thing that moves last.
 *
 * The same handoff the deck uses between its grid and its shade, and for the same reason:
 * a list that refuses to move is the clearest way an app has of saying "you are stuck".
 *
 * @param visible drives the enter and exit animations; the sheet stays in the
 *   composition either way, so its state survives being closed and reopened.
 */
@Composable
fun SwipeySheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwipeyTheme.colors

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    val scope = rememberCoroutineScope()

    /** How far a finger has pushed the sheet down, in px. Zero at rest, never negative. */
    val pushed = remember { Animatable(0f) }

    /** The sheet's own height, which is what [SheetDismissFraction] is a fraction of. */
    var sheetHeight by remember { mutableIntStateOf(0) }

    // Reset on the way *in*, not on the way out. A dismissed sheet keeps the offset the
    // finger left it at, so the exit animation carries on from there rather than snapping
    // the sheet back up to slide it down again.
    LaunchedEffect(visible) { if (visible) pushed.snapTo(0f) }

    /** Moves the sheet by [dy] px of finger, and reports how much of it was usable. */
    fun push(dy: Float): Float {
        val from = pushed.value
        val to = (from + dy).coerceAtLeast(0f)
        if (to == from) return 0f
        scope.launch { pushed.snapTo(to) }
        return to - from
    }

    /** Decides where a released push belongs: gone, or back where it started. */
    fun settle(velocity: Float) {
        val far = sheetHeight > 0 && pushed.value > sheetHeight * SheetDismissFraction
        // An upward flick puts it back even from past the threshold: the last thing the
        // finger did is a better account of intent than where it happened to stop.
        val flungShut = velocity < -SheetFlingVelocity
        if (!flungShut && (far || velocity > SheetFlingVelocity)) {
            onDismiss()
        } else {
            scope.launch { pushed.animateTo(0f, SwipeyMotion.sheet()) }
        }
    }

    // Remembered rather than rebuilt each pass: the sheet recomposes on every frame it
    // moves, and swapping the connection object mid-drag would tear down the node currently
    // handling that drag.
    val fromContent = remember(onDismiss) {
        object : NestedScrollConnection {
            // Upward, with the sheet off its seat: it goes back before the body may scroll
            // again. Whatever lifted the sheet has to be what lowers it.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                if (pushed.value <= 0f) return Offset.Zero
                return Offset(0f, push(available.y))
            }

            // Downward, and the body has already taken what it could — which at the top of a
            // list is nothing. Whatever is left pushes the sheet toward the bottom edge.
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                return Offset(0f, push(available.y))
            }

            // A sheet that has been moved at all owns the release, including the fling that
            // would otherwise be spent scrolling a body nobody is looking at any more.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pushed.value <= 0f) return Velocity.Zero
                settle(available.y)
                return available
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(SwipeyMotion.chrome()),
            exit = fadeOut(SwipeyMotion.chrome()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    // The scrim thins as the sheet is pushed away, so the screen behind is
                    // visibly coming back and the gesture reads as reversible while it is
                    // still reversible. Read inside the layer lambda, so a frame of pushing
                    // redraws without recomposing.
                    .graphicsLayer {
                        alpha = if (sheetHeight > 0) {
                            1f - (pushed.value / sheetHeight).coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                    .background(colors.scrim)
                    // A tap gesture rather than `clickable`: this must swallow taps
                    // without announcing itself to a screen reader as a button.
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(SwipeyMotion.sheet()) { it },
            exit = slideOutVertically(SwipeyMotion.sheet()) { it },
        ) {
            Column(
                modifier
                    .fillMaxWidth()
                    .onSizeChanged { sheetHeight = it.height }
                    .graphicsLayer { translationY = pushed.value }
                    .nestedScroll(fromContent)
                    .clip(RoundedCornerShape(topStart = SwipeyRadius.sheet, topEnd = SwipeyRadius.sheet))
                    .background(colors.surface)
                    // Stops a tap on the sheet body falling through to the scrim below.
                    .pointerInput(Unit) { detectTapGestures { } }
                    // The push, for everything on the sheet that does not scroll. A drag
                    // that starts on the body never reaches here — the scrollable claims it
                    // first — which is what `fromContent` above is for.
                    .pointerInput(onDismiss) {
                        // The real thing rather than the last frame's delta, for the same
                        // reason SwipeCard tracks velocity properly: on a 120Hz display each
                        // frame's movement is half as large, and a flick that eased off at
                        // the end reads as nearly stationary.
                        val tracker = VelocityTracker()
                        detectVerticalDragGestures(
                            onDragStart = { tracker.resetTracking() },
                            onDragEnd = {
                                settle(tracker.calculateVelocity().y)
                                tracker.resetTracking()
                            },
                            // Without this a gesture the system takes away leaves the sheet
                            // stranded halfway down with no way back but another drag.
                            onDragCancel = {
                                settle(0f)
                                tracker.resetTracking()
                            },
                            onVerticalDrag = { change, dy ->
                                tracker.addPosition(change.uptimeMillis, change.position)
                                change.consume()
                                push(dy)
                            },
                        )
                    }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SwipeySpacing.lg)
                    .padding(top = SwipeySpacing.md, bottom = SwipeySpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grabber. It says what the gesture is: the sheet can be pushed down from
                // here — or from anywhere else on it — and let go of.
                Box(
                    Modifier
                        .padding(bottom = SwipeySpacing.lg)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(SwipeyRadius.pill))
                        .background(colors.hairline),
                )
                if (title != null) {
                    SwipeyText(
                        title,
                        style = SwipeyTheme.typography.title,
                        color = colors.textPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = SwipeySpacing.md),
                    )
                }
                Column(Modifier.fillMaxWidth(), content = content)
            }
        }
    }
}

/**
 * A panel that comes in from the right-hand edge.
 *
 * The sibling of [SwipeySheet], and built the same way for the same reasons: inline rather
 * than in its own window, so it animates against the screen behind it and manages its own
 * insets. It goes in [SwipeyScreen]'s `overlay` slot for the same reason a sheet does — from
 * `content` it would inherit the screen gutter and stop short of the edge it is supposed to
 * be attached to.
 *
 * ### Why the right edge
 * It is opened from a control in the top-right corner, and a panel that appears anywhere
 * other than under the finger that summoned it has to be found rather than noticed. Coming
 * in from the left — where a navigation drawer conventionally lives — would also fight the
 * system Back gesture on the left edge of the screen.
 *
 * ### Its width
 * Capped at [DrawerMaxWidth] but never more than [DrawerWidthFraction] of the screen, so a
 * strip of the page behind it always shows. That strip is what makes the panel read as
 * something laid *over* Home rather than as having navigated away from it, and it doubles as
 * the tap target for getting back.
 *
 * Dismissal is wired three ways, all of which must work: the scrim, the system Back gesture,
 * and whatever [content] itself offers.
 *
 * @param visible drives the enter and exit animations; the drawer stays in the composition
 *   either way, so its state survives being closed and reopened.
 */
@Composable
fun SwipeyDrawer(
    visible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = SwipeyTheme.colors

    if (visible) {
        BackHandler(onBack = onDismiss)
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(SwipeyMotion.chrome()),
            exit = fadeOut(SwipeyMotion.chrome()),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(colors.scrim)
                    // A tap gesture rather than `clickable`: this must swallow taps without
                    // announcing itself to a screen reader as a button.
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(SwipeyMotion.sheet()) { it },
            exit = slideOutHorizontally(SwipeyMotion.sheet()) { it },
        ) {
            BoxWithConstraints {
                Column(
                    modifier
                        .width(minOf(DrawerMaxWidth, maxWidth * DrawerWidthFraction))
                        .fillMaxHeight()
                        .clip(
                            RoundedCornerShape(
                                topStart = SwipeyRadius.sheet,
                                bottomStart = SwipeyRadius.sheet,
                            ),
                        )
                        .background(colors.surface)
                        // Stops a tap on the panel falling through to the scrim behind it.
                        .pointerInput(Unit) { detectTapGestures { } }
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(horizontal = SwipeySpacing.lg)
                        .padding(top = SwipeySpacing.lg, bottom = SwipeySpacing.xl),
                ) {
                    if (title != null) {
                        SwipeyText(
                            title,
                            style = SwipeyTheme.typography.title,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = SwipeySpacing.sm),
                        )
                    }
                    content()
                }
            }
        }
    }
}

/** The widest the drawer gets, however much screen there is. */
private val DrawerMaxWidth = 320.dp

/** How much of a narrow screen the drawer may take, so the page behind always shows. */
private const val DrawerWidthFraction = 0.82f

/**
 * A centred confirmation dialog.
 *
 * Opens its own window (`androidx.compose.ui.window.Dialog` — platform plumbing, not a
 * Material widget), so it needs no slot in [SwipeyScreen] and gets Back-press and
 * tap-outside dismissal from the platform.
 *
 * ### Why the secondary action isn't called "cancel"
 * [dismissText] pairs with [onDismissAction], which defaults to [onDismiss] but does not
 * have to be it. Swipey's one real confirm — discarding marked photos — offers "Review"
 * as its second choice, which navigates somewhere rather than closing the dialog. Baking
 * in a cancel-only secondary would have made that impossible to express.
 *
 * @param confirmTone use [SwipeyTone.Bin] where confirming destroys work.
 */
@Composable
fun SwipeyDialog(
    visible: Boolean,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    body: String? = null,
    dismissText: String? = null,
    onDismissAction: () -> Unit = onDismiss,
    confirmTone: SwipeyTone = SwipeyTone.Neutral,
) {
    if (!visible) return
    val colors = SwipeyTheme.colors
    val shape = RoundedCornerShape(SwipeyRadius.card)

    Dialog(
        onDismissRequest = onDismiss,
        // The platform's default width is a Material metric; Swipey sizes its own.
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier
                .padding(SwipeySpacing.xl)
                .widthIn(max = 380.dp)
                .clip(shape)
                .background(colors.surface)
                .border(SwipeySize.hairline, colors.hairline, shape)
                .padding(SwipeySpacing.xl),
        ) {
            SwipeyText(title, style = SwipeyTheme.typography.title, color = colors.textPrimary)
            if (body != null) {
                Spacer(Modifier.height(SwipeySpacing.md))
                SwipeyText(body, style = SwipeyTheme.typography.body, color = colors.textSecondary)
            }
            Spacer(Modifier.height(SwipeySpacing.xl))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dismissText != null) {
                    SwipeyButton(
                        text = dismissText,
                        onClick = onDismissAction,
                        variant = SwipeyButtonVariant.Ghost,
                    )
                }
                SwipeyButton(
                    text = confirmText,
                    onClick = onConfirm,
                    variant = SwipeyButtonVariant.Filled,
                    tone = confirmTone,
                )
            }
        }
    }
}

/**
 * Runs [content] with [LocalSwipeyContentColor] set to [color], so nested [SwipeyText]
 * and [SwipeyIcon] inherit it without every call site passing a colour by hand.
 */
@Composable
fun SwipeyContentColor(color: Color, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalSwipeyContentColor provides color, content = content)
}
