package com.swipey.app.ui.design

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A catalogue of every primitive, in both palettes.
 *
 * These exist so the screens built on this system can be checked against it without
 * running the app — and so a change to a token is visible everywhere it lands before it
 * ships. Each entry renders in dark and light side by side via [SwipeyPreviews].
 */

/** Renders a preview twice: once in each palette. */
@Preview(name = "Dark", group = "design", uiMode = Configuration.UI_MODE_NIGHT_YES, showBackground = true)
@Preview(name = "Light", group = "design", uiMode = Configuration.UI_MODE_NIGHT_NO, showBackground = true)
private annotation class SwipeyPreviews

/**
 * Wraps a preview in the theme and canvas, so every entry below is just its own content.
 * [SwipeyTheme] resolves the palette from the preview's `uiMode`.
 */
@Composable
private fun PreviewCanvas(content: @Composable () -> Unit) {
    SwipeyTheme {
        SwipeyScreen(applyInsets = false, contentPadding = PaddingValues(SwipeySpacing.lg)) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = SwipeySpacing.lg),
                verticalArrangement = Arrangement.spacedBy(SwipeySpacing.md),
            ) {
                content()
            }
        }
    }
}

@SwipeyPreviews
@Composable
private fun TypeScalePreview() = PreviewCanvas {
    SwipeyText("Swipey", style = SwipeyTheme.typography.display)
    SwipeyText("Nothing left to review", style = SwipeyTheme.typography.title)
    SwipeyText(
        "Swipey shows your photos one at a time so you can keep or bin them.",
        style = SwipeyTheme.typography.body,
        color = SwipeyTheme.colors.textSecondary,
    )
    SwipeyText("RECOVERABLE UNTIL", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    // The pair that motivates the tabular styles: these two lines must not shift
    // horizontally relative to one another as the digits change.
    SwipeyText("111 / 312", style = SwipeyTheme.typography.titleNumeric)
    SwipeyText("847 / 312", style = SwipeyTheme.typography.titleNumeric)
}

@SwipeyPreviews
@Composable
private fun ButtonsPreview() = PreviewCanvas {
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SwipeyButton("Done", {}, tone = SwipeyTone.Neutral)
        SwipeyButton("Keep", {}, tone = SwipeyTone.Keep, icon = SwipeyIcons.Check)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SwipeyButton("Bin", {}, tone = SwipeyTone.Bin, icon = SwipeyIcons.Bin)
        SwipeyButton("Review", {}, variant = SwipeyButtonVariant.Ghost)
        SwipeyButton("Disabled", {}, enabled = false)
    }
    SwipeyButton("Move 12 items to trash", {}, tone = SwipeyTone.Bin, fillWidth = true)
}

@SwipeyPreviews
@Composable
private fun IconButtonsPreview() = PreviewCanvas {
    // The deck's three controls, at the size and spacing they are actually used.
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xl), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {}, tone = SwipeyTone.Bin)
        SwipeyIconButton(SwipeyIcons.Undo, "Undo the last decision", {}, size = SwipeySize.touchMin)
        SwipeyIconButton(SwipeyIcons.Check, "Keep this photo", {}, tone = SwipeyTone.Keep)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIconButton(SwipeyIcons.Check, "Keep", {}, variant = SwipeyButtonVariant.Filled, tone = SwipeyTone.Keep)
        SwipeyIconButton(SwipeyIcons.Restore, "Restore", {}, variant = SwipeyButtonVariant.Filled)
        SwipeyIconButton(SwipeyIcons.Close, "Close", {}, enabled = false)
    }
}

@SwipeyPreviews
@Composable
private fun IconSetPreview() = PreviewCanvas {
    // Every glyph at once, which is the only way to see whether the set holds together.
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIcon(SwipeyIcons.Bin, "Bin", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Check, "Keep", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Undo, "Undo", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.ChevronLeft, "Back", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.ChevronRight, "Onward", tint = SwipeyTheme.colors.textPrimary)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIcon(SwipeyIcons.Info, "Info", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Restore, "Restore", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Play, "Play", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Close, "Close", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Bin, "Bin", tint = SwipeyTheme.colors.bin)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md), verticalAlignment = Alignment.CenterVertically) {
        // Home's three. ListRows and Sort sit within a thumb's width of each other on that
        // screen, so this row is where to check they still read as different glyphs.
        SwipeyIcon(SwipeyIcons.ListRows, "List", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Grid, "Grid", tint = SwipeyTheme.colors.textPrimary)
        SwipeyIcon(SwipeyIcons.Sort, "Sort", tint = SwipeyTheme.colors.textPrimary)
    }
}

@SwipeyPreviews
@Composable
private fun CardAndChipPreview() = PreviewCanvas {
    SwipeyCard {
        SwipeyText("All media", style = SwipeyTheme.typography.title)
        SwipeyText(
            "Everything, in the order you choose",
            style = SwipeyTheme.typography.body,
            color = SwipeyTheme.colors.textSecondary,
        )
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SwipeyChip("12 marked · 1.2 GB", onClick = {})
        SwipeyChip("Kept", tone = SwipeyTone.Keep, icon = SwipeyIcons.Check)
        SwipeyChip("Binned", tone = SwipeyTone.Bin, icon = SwipeyIcons.Bin)
    }
    // The deck's marked chip, which is the reason the trailing icon exists. Next to the
    // plain one above it is the whole argument in two objects: same size, same hairline,
    // and only one of them looks like it goes anywhere.
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm), verticalAlignment = Alignment.CenterVertically) {
        SwipeyChip(
            "12 marked · 1.2 GB",
            tone = SwipeyTone.Bin,
            onClick = {},
            icon = SwipeyIcons.Bin,
            trailingIcon = SwipeyIcons.ChevronRight,
        )
    }
}

@SwipeyPreviews
@Composable
private fun RowsPreview() = PreviewCanvas {
    SwipeyRow("All media", subtitle = "Everything, in the order you choose", trailing = "2,481", onClick = {})
    SwipeyRow("Albums", subtitle = "Pick a folder to clean up", onClick = {})
    SwipeyRow("Bin", trailing = "12", onClick = {})
    SwipeyRow("Recoverable until at least 4 Sep", divider = false)
}

@SwipeyPreviews
@Composable
private fun ProgressPreview() = PreviewCanvas {
    SwipeyText("Determinate", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    SwipeyProgressBar(progress = 0.38f, contentDescription = "Reviewing photos")
    SwipeyText("Indeterminate", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    SwipeyProgressBar(progress = null)
    SwipeyText("Complete", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    SwipeyProgressBar(progress = 1f, indicatorColor = SwipeyTheme.colors.keep)
}

@SwipeyPreviews
@Composable
private fun DividerAndPalettePreview() = PreviewCanvas {
    SwipeyDivider()
    // The palette itself, so a token change is visible as a colour rather than a hex.
    // Signal is legible at a glance here: one hue in the whole set, and `bin` is the same
    // swatch as `2nd` beside it — the same hex, not a near-miss.
    val colors = SwipeyTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm)) {
        Swatch("canvas", colors.canvas)
        Swatch("surface", colors.surface)
        Swatch("strong", colors.surfaceStrong)
        Swatch("hairline", colors.hairline)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm)) {
        Swatch("keep", colors.keep)
        Swatch("bin", colors.bin)
        Swatch("2nd", colors.textSecondary)
        Swatch("off", colors.textDisabled)
    }
    SwipeyDivider()
}

/** One palette entry, named, so a swatch grid is readable rather than decorative. */
@Composable
private fun Swatch(name: String, color: Color) {
    val shape = RoundedCornerShape(SwipeyRadius.card)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .clip(shape)
                .background(color)
                .border(SwipeySize.hairline, SwipeyTheme.colors.hairline, shape),
        )
        SwipeyText(
            name,
            modifier = Modifier.padding(top = SwipeySpacing.xs),
            style = SwipeyTheme.typography.label,
            color = SwipeyTheme.colors.textSecondary,
            maxLines = 1,
        )
    }
}

/**
 * The problem this palette creates, and the answer to it — the one preview to check when
 * anything about a bin control changes.
 *
 * `bin` is `textSecondary`, exactly, so nothing about a bin control's *colour* separates
 * it from a caption or from a disabled control. What separates it is treatment: an
 * enabled decision control is a ringed disc on a [SwipeyColors.surfaceStrong] ground; a
 * disabled one keeps the ground, loses the ring, and dims to [SwipeyDisabledAlpha]. The
 * last two rows are the same sentence in the same grey, so the difference has to be
 * visible without reading anything.
 */
@SwipeyPreviews
@Composable
private fun BinAffordancePreview() = PreviewCanvas {
    SwipeyText("Enabled", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xl), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {})
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {}, tone = SwipeyTone.Bin)
        SwipeyIconButton(SwipeyIcons.Undo, "Undo the last decision", {}, size = SwipeySize.touchMin)
        SwipeyIconButton(SwipeyIcons.Check, "Keep this photo", {}, tone = SwipeyTone.Keep)
    }
    SwipeyText("Disabled — ground kept, ring gone", style = SwipeyTheme.typography.label, color = SwipeyTheme.colors.textSecondary)
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xl), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {}, enabled = false)
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {}, tone = SwipeyTone.Bin, enabled = false)
        SwipeyIconButton(SwipeyIcons.Undo, "Undo the last decision", {}, size = SwipeySize.touchMin, enabled = false)
        SwipeyIconButton(SwipeyIcons.Check, "Keep this photo", {}, tone = SwipeyTone.Keep, enabled = false)
    }
    // Static copy in the bin colour, for the comparison the rows above have to survive.
    SwipeyText("This caption is the bin colour", style = SwipeyTheme.typography.body, color = SwipeyTheme.colors.bin)
    SwipeyButton("Move 12 items to trash", {}, tone = SwipeyTone.Bin, fillWidth = true)
    SwipeyButton("Move 12 items to trash", {}, tone = SwipeyTone.Bin, fillWidth = true, enabled = false)
}

/**
 * The sheet, shown open. A preview renders a single frame, so [SwipeySheet] is placed in
 * [SwipeyScreen]'s overlay slot exactly as a real screen would place it.
 */
@SwipeyPreviews
@Composable
private fun SheetPreview() {
    SwipeyTheme {
        SwipeyScreen(
            applyInsets = false,
            overlay = {
                SwipeySheet(visible = true, onDismiss = {}, title = "Sort by") {
                    SwipeyRow("Newest first", onClick = {})
                    SwipeyRow("Oldest first", onClick = {})
                    SwipeyRow("Largest first", onClick = {})
                    SwipeyRow("Smallest first", onClick = {}, divider = false)
                }
            },
        ) {
            Box(Modifier.fillMaxWidth().height(320.dp))
        }
    }
}

/**
 * The dialog's *content*, laid out inline rather than through [SwipeyDialog].
 *
 * A real `Dialog` opens a second window, which the preview renderer does not composite
 * into the frame — so calling [SwipeyDialog] here would render an empty preview. This
 * mirrors its body instead, which is what there is to look at.
 */
@SwipeyPreviews
@Composable
private fun DialogPreview() = PreviewCanvas {
    SwipeyCard(contentPadding = PaddingValues(SwipeySpacing.xl)) {
        SwipeyText("Discard the items you've marked?", style = SwipeyTheme.typography.title)
        SwipeyText(
            "Nothing has been moved to trash yet.",
            style = SwipeyTheme.typography.body,
            color = SwipeyTheme.colors.textSecondary,
            modifier = Modifier.padding(top = SwipeySpacing.md),
        )
        Row(
            Modifier.fillMaxWidth().padding(top = SwipeySpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm, Alignment.End),
        ) {
            SwipeyButton("Review", {}, variant = SwipeyButtonVariant.Ghost)
            SwipeyButton("Discard", {}, tone = SwipeyTone.Bin)
        }
    }
}

/**
 * Every text-bearing primitive at a 200% font scale.
 *
 * This is the check that the "height is a floor, never a fixed value" rule actually
 * holds: at this scale a control with a hardcoded height clips its own label, and that
 * failure is invisible at the default scale.
 */
@Preview(name = "Dark · 200% font", group = "a11y", uiMode = Configuration.UI_MODE_NIGHT_YES, fontScale = 2f, showBackground = true, heightDp = 640)
@Preview(name = "Light · 200% font", group = "a11y", uiMode = Configuration.UI_MODE_NIGHT_NO, fontScale = 2f, showBackground = true, heightDp = 640)
@Composable
private fun LargeFontPreview() = PreviewCanvas {
    SwipeyText("Review", style = SwipeyTheme.typography.title)
    SwipeyRow("All media", subtitle = "Everything, in the order you choose", trailing = "2,481", onClick = {})
    SwipeyChip("12 marked · 1.2 GB", onClick = {})
    SwipeyButton("Move 12 items to trash", {}, tone = SwipeyTone.Bin, fillWidth = true)
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.md), verticalAlignment = Alignment.CenterVertically) {
        SwipeyIconButton(SwipeyIcons.Bin, "Bin this photo", {}, tone = SwipeyTone.Bin)
        SwipeyIconButton(SwipeyIcons.Check, "Keep this photo", {}, tone = SwipeyTone.Keep)
    }
}
