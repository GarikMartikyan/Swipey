package com.swipey.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.DisclosureNote
import com.swipey.app.ui.common.DisclosureSheet
import com.swipey.app.ui.common.reviewDisclosures
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyProgressBar
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.design.SwipeyTone

/**
 * Everything the user marked, before any of it moves.
 *
 * A 3-column grid, because the question this screen answers is "is there anything in
 * here I actually want?" and that is answered by looking, not by reading a list. Each
 * tile carries its own size — the number that justifies the whole exercise — and a ×
 * badge, so the tap is unmistakably *remove from this list*, never *delete*.
 *
 * The spec §9 disclosures are one line here plus a sheet ([reviewDisclosures]); see
 * `ui/common/DisclosureSheet.kt` for why, and for the full text they still carry.
 *
 * F4 (Task 10 review): [committing] is not in the brief's 3-param signature (Ruling R6
 * pins that block verbatim), but disabling in-flight mutation is a correctness fix, not
 * a signature dispute — without it, Task 20's onCommit can call prepareTrash (which bakes
 * the marked URIs into the PendingIntents right there) while the grid and button stay
 * live, so unmarking a thumbnail or tapping Commit again during the dialog's beat has no
 * effect on what actually gets trashed. Task 20 MUST thread its own in-flight state in
 * here; there is no default because silently omitting it would defeat the fix.
 *
 * [commitError] (fix round 2, Critical 2): set by the caller when the commit path throws
 * before a launcher sequence ever starts (e.g. `prepareTrash`'s Room write). Defaulted to
 * `null` since it postdates the brief's pinned signature and every existing call site
 * predates it having anything to report.
 */
@Composable
fun ReviewScreen(
    items: List<MediaItem>,
    onUnmark: (Long) -> Unit,
    onCommit: () -> Unit,
    committing: Boolean,
    commitError: String? = null,
) {
    var notesOpen by rememberSaveable { mutableStateOf(false) }
    val batches = items.chunkedForRequest().size

    SwipeyScreen(
        overlay = {
            DisclosureSheet(
                visible = notesOpen,
                onDismiss = { notesOpen = false },
                notes = reviewDisclosures(batches),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            SwipeyText(
                Copy.REVIEW_TITLE,
                modifier = Modifier.padding(top = SwipeySpacing.xxl),
                style = SwipeyTheme.typography.display,
                color = SwipeyTheme.colors.textPrimary,
            )
            SwipeyText(
                Copy.reviewHeader(items.size, formatBytes(items.sumOf { it.sizeBytes })),
                modifier = Modifier.padding(top = SwipeySpacing.xs, bottom = SwipeySpacing.md),
                style = SwipeyTheme.typography.bodyNumeric,
                color = SwipeyTheme.colors.textSecondary,
            )

            // A 2dp rule rather than a spinner: the deck's whole point is that nothing
            // rotates on top of a photograph. The slot is held open either way so the
            // grid doesn't jump when a commit starts.
            if (committing) {
                SwipeyProgressBar(progress = null)
            } else {
                Spacer(Modifier.height(SwipeySize.progress))
            }

            if (commitError != null) {
                SwipeyText(
                    commitError,
                    modifier = Modifier.padding(top = SwipeySpacing.md),
                    style = SwipeyTheme.typography.body,
                    color = SwipeyTheme.colors.bin,
                )
            }

            if (items.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = SwipeySpacing.xxxl),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeyText(
                        Copy.REVIEW_EMPTY,
                        style = SwipeyTheme.typography.body,
                        color = SwipeyTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(vertical = SwipeySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
                    verticalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
                ) {
                    items(items, key = { it.id }) { item ->
                        MarkedTile(
                            item = item,
                            enabled = !committing,
                            onUnmark = { onUnmark(item.id) },
                        )
                    }
                }
            }

            DisclosureNote(
                text = Copy.TRASH_SHORT_NOTE,
                onOpen = { notesOpen = true },
                modifier = Modifier.padding(top = SwipeySpacing.sm),
            )

            // One primary action, and only while there is something for it to act on —
            // "Move 0 items to trash" is not a sentence worth rendering.
            if (items.isNotEmpty()) {
                SwipeyButton(
                    text = Copy.reviewAction(items.size),
                    onClick = onCommit,
                    modifier = Modifier.padding(top = SwipeySpacing.sm, bottom = SwipeySpacing.lg),
                    tone = SwipeyTone.Bin,
                    enabled = !committing,
                    fillWidth = true,
                )
            }
        }
    }
}

/**
 * One marked photo.
 *
 * The × badge is the affordance: it says *take this back out of the list*, which is all
 * a tap does. The tile itself is the control — the badge is too small to be a 48dp
 * target and pretending otherwise would give the user a 24dp one — so the badge is
 * decorative and the tile carries the action label.
 */
@Composable
private fun MarkedTile(
    item: MediaItem,
    enabled: Boolean,
    onUnmark: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SwipeyRadius.card))
            .background(SwipeyTheme.colors.surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClickLabel = Copy.REVIEW_UNMARK,
                role = Role.Button,
                onClick = onUnmark,
            ),
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(SwipeySpacing.xs)
                .size(24.dp)
                .clip(CircleShape)
                .background(SwipeyTheme.colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            SwipeyIcon(
                SwipeyIcons.Close,
                contentDescription = null,
                // Ink on a scrim over a photograph: dark-palette ink is correct in both
                // themes, since the ground under it is a dimmed image, not the canvas.
                tint = SwipeyDarkColors.textPrimary,
                size = 14.dp,
            )
        }
        TileCaption(
            text = formatBytes(item.sizeBytes),
            modifier = Modifier.align(Alignment.BottomStart).padding(SwipeySpacing.xs),
        )
    }
}

/** A scrim-backed pill for the one number that belongs on a thumbnail. */
@Composable
private fun TileCaption(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(SwipeyRadius.pill))
            .background(SwipeyTheme.colors.scrim)
            .padding(horizontal = SwipeySpacing.sm, vertical = SwipeySpacing.xs),
    ) {
        SwipeyText(
            text,
            style = SwipeyTheme.typography.labelNumeric,
            color = SwipeyDarkColors.textPrimary,
            maxLines = 1,
        )
    }
}
