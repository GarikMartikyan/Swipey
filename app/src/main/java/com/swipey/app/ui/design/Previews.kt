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
    Row(horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm)) {
        val colors = SwipeyTheme.colors
        listOf(colors.canvas, colors.surface, colors.surfaceStrong, colors.hairline, colors.keep, colors.bin).forEach {
            Box(
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(SwipeyRadius.card))
                    .background(it)
                    .border(SwipeySize.hairline, colors.hairline, RoundedCornerShape(SwipeyRadius.card)),
            )
        }
    }
    SwipeyDivider()
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
