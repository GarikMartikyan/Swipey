package com.swipey.app.ui.design

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

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
 *  - **Disabled** means the click is gone *and* the content is dimmed to
 *    [SwipeyDisabledAlpha]. Never one without the other.
 */

// ---------------------------------------------------------------------------
// Tone and variant
// ---------------------------------------------------------------------------

/**
 * Which of the app's three voices a control speaks in.
 *
 * Swipey has exactly two decisions — keep and bin — and everything else is navigation.
 * Tone maps a control onto that: colour is never chosen at a call site, only a meaning.
 */
enum class SwipeyTone {
    /** Navigation, confirmation, dismissal. Renders as inverted ink — no hue at all. */
    Neutral,

    /** Keeping a photo. */
    Keep,

    /** Binning a photo, and committing a batch to the system trash. */
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
    SwipeyTone.Keep, SwipeyTone.Bin -> SwipeyTheme.colors.onAccent
}

/** The ink on a [SwipeyButtonVariant.Ghost] control of this tone. */
@Composable
private fun SwipeyTone.ink(): Color = when (this) {
    SwipeyTone.Neutral -> SwipeyTheme.colors.textPrimary
    SwipeyTone.Keep -> SwipeyTheme.colors.keep
    SwipeyTone.Bin -> SwipeyTheme.colors.bin
}

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

    Row(
        modifier
            .scale(scale)
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            // The floor, not the height — see the KDoc above.
            .defaultMinSize(minHeight = SwipeySize.touchMin)
            .clip(shape)
            .then(if (filled) Modifier.background(tone.container()) else Modifier.border(SwipeySize.hairline, SwipeyTheme.colors.hairline, shape))
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
 * @param contentDescription required, and deliberately not nullable. This control has no
 *   visible label, so without it a screen reader announces nothing at all.
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

    Box(
        modifier
            .scale(scale)
            .size(size.coerceAtLeast(SwipeySize.touchMin))
            .clip(CircleShape)
            .then(if (filled) Modifier.background(tone.container()) else Modifier.border(SwipeySize.hairline, SwipeyTheme.colors.hairline, CircleShape))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .alpha(if (enabled) 1f else SwipeyDisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        SwipeyIcon(icon, contentDescription = contentDescription, tint = content, size = iconSize)
    }
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
 * to [SwipeySize.touchMin], so a chip never becomes a 30dp target.
 */
@Composable
fun SwipeyChip(
    text: String,
    modifier: Modifier = Modifier,
    tone: SwipeyTone = SwipeyTone.Neutral,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tabular: Boolean = true,
) {
    val colors = SwipeyTheme.colors
    val ink = when (tone) {
        SwipeyTone.Neutral -> colors.textSecondary
        SwipeyTone.Keep -> colors.keep
        SwipeyTone.Bin -> colors.bin
    }
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
                .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                SwipeyIcon(icon, contentDescription = null, tint = ink, size = 14.dp)
            }
            SwipeyText(text, style = style, color = ink, maxLines = 1)
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
 * A bottom sheet, built from scratch.
 *
 * Rendered inline rather than in its own window, which is what lets it animate against
 * the screen behind it and pad itself out of the navigation bar. Place it in
 * [SwipeyScreen]'s `overlay` slot — from `content` it would inherit the screen gutter and
 * stop short of the bottom edge.
 *
 * Dismissal is wired three ways, all of which must work: the scrim, the system Back
 * gesture, and whatever [content] itself offers.
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
                    .clip(RoundedCornerShape(topStart = SwipeyRadius.sheet, topEnd = SwipeyRadius.sheet))
                    .background(colors.surface)
                    // Stops a tap on the sheet body falling through to the scrim below.
                    .pointerInput(Unit) { detectTapGestures { } }
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = SwipeySpacing.lg)
                    .padding(top = SwipeySpacing.md, bottom = SwipeySpacing.xl),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Grabber. Decorative — the sheet is dismissed by the scrim or Back, not
                // by dragging this, so it is a visual affordance only.
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
