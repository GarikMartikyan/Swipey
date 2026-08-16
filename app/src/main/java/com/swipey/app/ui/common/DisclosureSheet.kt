package com.swipey.app.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyDivider
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeySheet
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/**
 * Progressive disclosure for the spec §9 rules, shared by Review, Result and the Bin.
 *
 * The rules are binding and none of their wording may be softened — but rendering
 * all of them at once, as four stacked caveat paragraphs under every screen, is how
 * they were read least. So the pattern is: **one plain line on screen** ([DisclosureNote]),
 * and the **full text, verbatim**, one tap away ([DisclosureSheet]).
 *
 * Nothing is dropped. The three `*Disclosures()` functions below are the only place
 * that decides which notes a screen carries, so "does every rule still reach the user
 * on the screen it applies to?" is answerable by reading this file alone.
 */

/**
 * The on-screen half: a short, honest line with an ⓘ, the whole of which is the tap
 * target that opens [DisclosureSheet].
 *
 * The row — not the glyph — is the control, so it has a visible name rather than an
 * icon needing a description, and it is comfortably over the 48dp floor at any font
 * scale. The ⓘ itself is decorative, and marked so.
 *
 * @param text one of `Copy.TRASH_SHORT_NOTE` / `Copy.BIN_SHORT_NOTE`.
 */
@Composable
fun DisclosureNote(
    text: String,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwipeyTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = interaction,
                // No ripple anywhere in Swipey; see the design system's conventions.
                indication = null,
                onClickLabel = Copy.DISCLOSURE_ACTION,
                role = Role.Button,
                onClick = onOpen,
            )
            .defaultMinSize(minHeight = SwipeySize.touchMin)
            .padding(vertical = SwipeySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeyText(
            text,
            modifier = Modifier.weight(1f),
            style = SwipeyTheme.typography.label,
            color = colors.textSecondary,
        )
        SwipeyIcon(
            SwipeyIcons.Info,
            // Decorative: the line beside it is this control's accessible name, and
            // "opens more detail" is already carried by the onClickLabel above.
            contentDescription = null,
            tint = colors.textSecondary,
            size = 18.dp,
        )
    }
}

/**
 * The sheet half: every applicable note in full, exactly as `Copy` words it.
 *
 * Lives in [com.swipey.app.ui.design.SwipeyScreen]'s `overlay` slot — from `content`
 * it would inherit the screen gutter and stop short of the bottom edge.
 *
 * @param notes in the order the screen should present them; build with one of the
 *   `*Disclosures()` functions below rather than assembling a list at a call site.
 */
@Composable
fun DisclosureSheet(
    visible: Boolean,
    onDismiss: () -> Unit,
    notes: List<String>,
    modifier: Modifier = Modifier,
) {
    SwipeySheet(
        visible = visible,
        onDismiss = onDismiss,
        modifier = modifier,
        title = Copy.DISCLOSURE_TITLE,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                // A 200% font scale turns four notes into more than a screenful.
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            notes.forEachIndexed { index, note ->
                if (index > 0) {
                    Spacer(Modifier.height(SwipeySpacing.md))
                    SwipeyDivider()
                    Spacer(Modifier.height(SwipeySpacing.md))
                }
                SwipeyText(
                    note,
                    style = SwipeyTheme.typography.body,
                    color = SwipeyTheme.colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(SwipeySpacing.xl))
        SwipeyButton(
            text = Copy.DISCLOSURE_DISMISS,
            onClick = onDismiss,
            variant = SwipeyButtonVariant.Ghost,
            fillWidth = true,
        )
    }
}

/**
 * Review's notes: everything the user should know *before* committing.
 *
 * Rule 2 ([Copy.TRASH_SIZE_NOTE]) — trashing frees no bytes.
 * Rule 4 ([Copy.SYSTEM_TRASH_NOTE]) — it is the phone's trash, shared with other apps.
 * Rule 7 ([Copy.NO_PERMANENT_DELETE_NOTE]) — Swipey cannot delete anything for good.
 *
 * @param batches how many consent dialogs the commit will raise; more than one adds
 *   [Copy.multipleConfirmations] so a second dialog is never a surprise.
 */
fun reviewDisclosures(batches: Int): List<String> = buildList {
    add(Copy.TRASH_SIZE_NOTE)
    add(Copy.SYSTEM_TRASH_NOTE)
    add(Copy.NO_PERMANENT_DELETE_NOTE)
    if (batches > 1) add(Copy.multipleConfirmations(batches))
}

/** Result's notes: rules 2, 4 and 7, restated after the fact. */
fun resultDisclosures(): List<String> = listOf(
    Copy.TRASH_SIZE_NOTE,
    Copy.SYSTEM_TRASH_NOTE,
    Copy.NO_PERMANENT_DELETE_NOTE,
)

/**
 * The Bin's notes: rules 4, 7, 5 and 2 — in that order, because on this screen
 * "whose trash is this" is the first question and "Android will ask again"
 * ([Copy.RESTORE_CONFIRM_NOTE]) is what the Restore button needs explaining.
 */
fun binDisclosures(): List<String> = listOf(
    Copy.SYSTEM_TRASH_NOTE,
    Copy.NO_PERMANENT_DELETE_NOTE,
    Copy.RESTORE_CONFIRM_NOTE,
    Copy.TRASH_SIZE_NOTE,
)
