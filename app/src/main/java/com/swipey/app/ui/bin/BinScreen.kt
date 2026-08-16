package com.swipey.app.ui.bin

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.BinEntry
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.DisclosureNote
import com.swipey.app.ui.common.DisclosureSheet
import com.swipey.app.ui.common.binDisclosures
import com.swipey.app.ui.common.rememberTrashLauncher
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * What Swipey put in the system trash, and the one thing it can do about it: put it back.
 *
 * There is no delete here, and there is no "empty bin" — not disabled, not hidden behind
 * a confirm, absent. Swipey has no permanent-delete function at all (spec §9 rule 7), and
 * a screen that looks like it might is a screen that has already lied.
 *
 * The grid speaks the same language as Review — same tile, same caption — so the two
 * halves of a session read as one place. The difference is what a tap means: here it
 * selects, and selection is shown with a tick.
 */
@Composable
fun BinScreen(viewModel: BinViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var notesOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Task 10 review finding: the restore flow must never render ResultScreen (see
    // BinViewModel.onRestoreFinished for why). It is entirely self-contained here —
    // launch, verify, and land back on this same Bin, refreshed. There is no
    // `onRestore` callback for a caller to misroute; Task 20 has nothing to wire wrong.
    var attemptedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val trashLauncher = rememberTrashLauncher(repository = viewModel.repository) { report ->
        viewModel.onRestoreFinished(attemptedIds, report)
    }

    val colors = SwipeyTheme.colors
    // The soonest any item here stops being recoverable. "At least" is what makes one
    // line honest for the whole grid (rule 3): every item lasts at least this long.
    val earliestExpirySec = state.entries.mapNotNull { it.expiresAtSec }.minOrNull()

    SwipeyScreen(
        overlay = {
            DisclosureSheet(
                visible = notesOpen,
                onDismiss = { notesOpen = false },
                notes = binDisclosures(),
            )
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            SwipeyText(
                Copy.BIN_TITLE,
                modifier = Modifier.padding(top = SwipeySpacing.xxl),
                style = SwipeyTheme.typography.display,
                color = colors.textPrimary,
            )
            earliestExpirySec?.let {
                val date = Instant.ofEpochSecond(it)
                    .atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("d MMM"))
                SwipeyText(
                    Copy.expiresAtLeast(date),
                    modifier = Modifier.padding(top = SwipeySpacing.xs),
                    style = SwipeyTheme.typography.bodyNumeric,
                    color = colors.textSecondary,
                )
            }

            Spacer(Modifier.height(SwipeySpacing.md))
            if (state.loading || state.restoring || trashLauncher.inFlight) {
                SwipeyProgressBar(progress = null)
            } else {
                Spacer(Modifier.height(SwipeySize.progress))
            }

            state.restoreMessage?.let {
                SwipeyText(
                    it,
                    modifier = Modifier.padding(top = SwipeySpacing.md),
                    style = SwipeyTheme.typography.body,
                    color = colors.textPrimary,
                )
            }

            if (state.vanishedCount > 0) {
                // Ruling R14: vanished covers every non-entry outcome, including items
                // restored elsewhere (e.g. in Google Photos), not just items genuinely
                // gone from the device. Copy.vanishedNotice is worded to be accurate for
                // both causes — it must not be reworded to imply destruction.
                SwipeyText(
                    Copy.vanishedNotice(state.vanishedCount),
                    modifier = Modifier.padding(top = SwipeySpacing.xs),
                    style = SwipeyTheme.typography.label,
                    color = colors.textSecondary,
                )
            }

            // I4 / spec §12. Checked before the empty branch: "Nothing here" after a query
            // threw would tell the user their trashed photos are gone, which is both false and
            // the single most alarming thing this screen could say.
            if (state.failed) {
                Column(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = SwipeySpacing.xxxl),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    SwipeyText(
                        Copy.BIN_LOAD_FAILED,
                        style = SwipeyTheme.typography.body,
                        color = colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(SwipeySpacing.lg))
                    SwipeyButton(
                        text = Copy.RETRY,
                        onClick = { viewModel.refresh() },
                        variant = SwipeyButtonVariant.Ghost,
                    )
                }
            } else if (state.entries.isEmpty() && !state.loading) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = SwipeySpacing.xxxl),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeyText(
                        Copy.BIN_EMPTY,
                        style = SwipeyTheme.typography.body,
                        color = colors.textSecondary,
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
                    items(state.entries, key = { it.record.mediaId }) { entry ->
                        BinTile(
                            entry = entry,
                            selected = entry.record.mediaId in state.selected,
                            onToggle = { viewModel.toggle(entry.record.mediaId) },
                        )
                    }
                }
            }

            // Ruling R7: footer noting how many other items sit in the system trash
            // that Swipey did not put there — otherwise queryTrashed()/binOtherItems
            // are both dead code and the design spec goes unmet.
            if (state.otherItemsCount > 0) {
                SwipeyText(
                    Copy.binOtherItems(state.otherItemsCount),
                    modifier = Modifier.padding(top = SwipeySpacing.sm),
                    style = SwipeyTheme.typography.label,
                    color = colors.textSecondary,
                )
            }

            // Rules 4, 7, 5 and 2 in full behind the ⓘ — including
            // Copy.RESTORE_CONFIRM_NOTE, which is what the button below needs explaining.
            DisclosureNote(
                text = Copy.BIN_SHORT_NOTE,
                onOpen = { notesOpen = true },
            )

            if (!state.failed && state.entries.isNotEmpty()) {
                SwipeyButton(
                    // "Restore 0" is not a label; before a selection exists the button
                    // says what it is, and only counts once there is something to count.
                    text = if (state.selected.isEmpty()) {
                        Copy.BIN_RESTORE
                    } else {
                        // F8(b): was composed at this call site; now lives in Copy.kt.
                        Copy.binRestoreAction(state.selected.size)
                    },
                    onClick = {
                        val ids = state.selected.toList()
                        attemptedIds = ids
                        scope.launch {
                            // Fix round 2, Critical 2 (restore side): without this, a throw
                            // from beginRestore() (e.g. its Room write) would leave
                            // `restoring` latched true forever — trashLauncher.start() is
                            // never reached, so nothing else resets it.
                            try {
                                trashLauncher.start(viewModel.beginRestore())
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                viewModel.onRestoreFailed()
                            }
                        }
                    },
                    modifier = Modifier.padding(top = SwipeySpacing.sm, bottom = SwipeySpacing.lg),
                    tone = SwipeyTone.Keep,
                    // F2: disabled for the whole in-flight window, not just until the click
                    // handler returns — beginRestore() is a suspend Room write + binder round
                    // trip before the dialog even appears, during which state.selected is
                    // otherwise unchanged and a second tap would start a second sequence.
                    //
                    // Fix round 2 re-review, restore-side latch: `state.restoring` alone is not
                    // enough. The catch block above resets `restoring` on any throw out of
                    // `trashLauncher.start(...)`, but if that throw happened *after* `start()`
                    // already flipped `inFlight = true` (e.g. `launchNext()`'s
                    // `activityLauncher.launch(request)` itself throwing), nothing outside
                    // TrashLauncher can ever clear `inFlight` again — a retry's `start()` would
                    // then be a silent no-op at `if (inFlight) return`, latching `restoring` true
                    // forever with no dialog and no callback to reset it. Deriving `enabled` from
                    // both flags, mirroring the trash side's `committing = preparing ||
                    // trashLauncher.inFlight` (SwipeyApp.kt), closes that gap: the button cannot
                    // re-enable while a stuck `inFlight` would make a retry a no-op.
                    enabled = state.selected.isNotEmpty() && !state.restoring && !trashLauncher.inFlight,
                    fillWidth = true,
                    icon = SwipeyIcons.Restore,
                )
            }
        }
    }
}

/**
 * One trashed item. Selection is a tick, not a colour change alone — colour on its own
 * is the one signal a user who can't see it gets nothing from.
 */
@Composable
private fun BinTile(
    entry: BinEntry,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val colors = SwipeyTheme.colors
    val interaction = remember { MutableInteractionSource() }

    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SwipeyRadius.card))
            .background(colors.surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = if (selected) Copy.BIN_DESELECT else Copy.BIN_SELECT,
                role = Role.Button,
                onClick = onToggle,
            ),
    ) {
        AsyncImage(
            model = contentUriFor(entry.record.mediaId, entry.record.isVideo),
            contentDescription = entry.record.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        if (selected) {
            Box(Modifier.fillMaxSize().background(colors.keep.copy(alpha = 0.28f)))
        }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(SwipeySpacing.xs)
                .size(24.dp)
                .clip(CircleShape)
                .background(if (selected) colors.keep else colors.scrim),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                SwipeyIcon(
                    SwipeyIcons.Check,
                    // Decorative: the tile is the control, and its click label already
                    // says which way the next tap goes.
                    contentDescription = null,
                    tint = colors.onAccent,
                    size = 14.dp,
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(SwipeySpacing.xs)
                .clip(RoundedCornerShape(SwipeyRadius.pill))
                .background(colors.scrim)
                .padding(horizontal = SwipeySpacing.sm, vertical = SwipeySpacing.xs),
        ) {
            SwipeyText(
                formatBytes(entry.record.sizeBytes),
                style = SwipeyTheme.typography.labelNumeric,
                // Ink on a scrim over a photograph: dark-palette ink is correct in both
                // themes, since the ground under it is a dimmed image, not the canvas.
                color = SwipeyDarkColors.textPrimary,
                maxLines = 1,
            )
        }
    }
}
