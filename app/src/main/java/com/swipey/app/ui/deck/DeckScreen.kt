package com.swipey.app.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyChip
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyDialog
import com.swipey.app.ui.design.SwipeyIconButton
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyProgressBar
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.design.SwipeyTone
import com.swipey.app.ui.design.rememberSwipeyHaptics

/**
 * The deck: one photograph at a time, and two ways to decide about it.
 *
 * ### The photo is the interface
 * The image runs edge to edge and under the system bars; everything else floats on top
 * of it. There is no card frame, no toolbar and no background panel, because every one
 * of those would be a rectangle competing with the photograph for the same screen.
 * What chrome there is sits on a soft gradient — a scrim, not a bar — which is what
 * keeps a white counter legible over a snow scene without painting a grey band across
 * the top of every image.
 *
 * ### Why the chrome is drawn in the dark palette in both themes
 * The ground under the counter, the chip and the three controls is a photograph, not the
 * canvas. Ink chosen to be read against the light theme's near-white page has nothing to
 * do with what sits under it here, so the over-photo chrome is wrapped in the dark
 * palette explicitly. One decision, in one place, rather than a colour argument at
 * every call site.
 *
 * The terminal, failed and loading states are ordinary screens and use the real theme.
 */
@Composable
fun DeckScreen(
    viewModel: DeckViewModel,
    onReview: () -> Unit,
    onDone: () -> Unit,
    // Fix round 2, Important 5: invoked once the user confirms discarding a Back press
    // with marks pending — the caller performs the actual navigation (a plain
    // `navController.popBackStack()`), mirroring what an un-intercepted Back would have
    // done.
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val haptics = rememberSwipeyHaptics()
    val coachMarks = rememberDeckCoachMarks()
    // Whether a swipe decision is currently mid fly-off animation; while true the
    // Bin/Undo/Keep buttons below are disabled so a tap can't record a decision
    // against a card that's already committing to a different one (fix round 1,
    // Critical 2).
    var committing by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Fix round 2, Important 5: DECK_BACK_CONFIRM/DECK_DISCARD/DECK_REVIEW were dead
    // copy — nothing intercepted Back, so marked-but-uncommitted items were silently
    // abandoned. Only intercepts when there's something to lose; with nothing marked,
    // Back falls through to the default pop untouched.
    BackHandler(enabled = state.markedCount > 0) {
        showDiscardConfirm = true
    }

    // Safe to call unconditionally: SwipeyDialog renders nothing at all when invisible.
    // Its second action is not a cancel — it takes the user to Review instead, so the
    // marks they were about to lose have somewhere useful to go.
    SwipeyDialog(
        visible = showDiscardConfirm,
        title = Copy.DECK_BACK_CONFIRM,
        confirmText = Copy.DECK_DISCARD,
        onConfirm = { showDiscardConfirm = false; onBack() },
        onDismiss = { showDiscardConfirm = false },
        dismissText = Copy.DECK_REVIEW,
        onDismissAction = { showDiscardConfirm = false; onReview() },
        confirmTone = SwipeyTone.Bin,
    )

    // Terminal states, spec §4. Only the "marked" path auto-navigates: exhausted with
    // nothing marked stops here instead (fix round 1, Important 5b) so the session's
    // last decision — otherwise unreachable the instant it lands — stays undoable.
    LaunchedEffect(state.exhausted, state.markedCount) {
        if (state.exhausted && !state.loading && state.markedCount > 0) {
            onReview()
        }
    }

    if (state.loading) {
        // A 2dp rule at the top edge, not a spinner: the deck is about to be a
        // photograph, and a rotating object in the middle of it is exactly the chrome
        // this app is trying to remove.
        SwipeyScreen(contentPadding = PaddingValues(0.dp)) {
            SwipeyProgressBar(progress = null, modifier = Modifier.align(Alignment.TopCenter))
        }
        return
    }

    // I4 / spec §12: a read that threw lands here instead of crashing. Checked before the
    // exhausted branch below so a failed load never renders as "nothing left to review" —
    // telling the user their album is empty when it was never read would be a lie, and one
    // that invites them to move on rather than retry.
    if (state.failed) {
        DeckMessage(
            title = Copy.LOAD_FAILED,
            primary = { SwipeyButton(Copy.RETRY, onClick = { viewModel.retry() }) },
            secondary = { SwipeyButton(Copy.RESULT_DONE, onClick = onDone, variant = SwipeyButtonVariant.Ghost) },
        )
        return
    }

    if (state.exhausted && state.markedCount == 0) {
        DeckMessage(
            title = Copy.DECK_EMPTY_TITLE,
            body = Copy.DECK_EMPTY_BODY,
            note = Copy.DECK_NOTHING_MARKED,
            primary = { SwipeyButton(Copy.RESULT_DONE, onClick = onDone) },
            // NEW 2: state.position doubles as "history size" — SwipeSession.position is
            // incremented by every advance() and decremented by every undo(), so it's
            // exactly zero iff there is nothing left to undo (empty album, or an album
            // where every item already has a KEEP row). A dead, always-disabled-in-effect
            // Undo button here would be confusing; hide it instead.
            //
            // This is also the *only* way back to the session's final decision — there is
            // deliberately no auto-navigate off this state — so it stays a real, enabled
            // control rather than a footnote.
            secondary = if (state.position > 0) {
                {
                    SwipeyButton(
                        Copy.DECK_UNDO,
                        onClick = { haptics.undo(); viewModel.undo() },
                        variant = SwipeyButtonVariant.Ghost,
                        icon = SwipeyIcons.Undo,
                    )
                }
            } else {
                null
            },
        )
        return
    }

    val item = state.current
    val currentId = item?.id

    Box(
        Modifier
            .fillMaxSize()
            .background(SwipeyTheme.colors.canvas),
    ) {
        if (item == null) {
            SwipeyText(
                Copy.DECK_EMPTY_TITLE,
                modifier = Modifier.align(Alignment.Center),
                style = SwipeyTheme.typography.title,
                color = SwipeyTheme.colors.textSecondary,
            )
        } else {
            SwipeCard(
                itemId = item.id,
                // The id travels with the decision: DeckViewModel.swipe no-ops unless it
                // still matches the current card, so a mistimed commit can never be
                // recorded against a photo the user never judged.
                onSwiped = { id, keep ->
                    viewModel.swipe(id, keep)
                    coachMarks.dismiss()
                },
                onCommittingChanged = { committing = it },
            ) {
                MediaCardContent(item)
            }
        }

        SwipeyTheme(colors = SwipeyDarkColors) {
            Box(Modifier.fillMaxSize()) {
                DeckTopChrome(
                    modifier = Modifier.align(Alignment.TopCenter),
                    position = state.position,
                    total = state.total,
                    markedCount = state.markedCount,
                    markedBytes = state.markedBytes,
                    onReview = onReview,
                )

                DeckControls(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    // Disabled for the length of the fly-off, so the buttons can't record
                    // a second decision over the top of the one already committing.
                    decisionsEnabled = !committing && currentId != null,
                    undoEnabled = !committing,
                    onBin = {
                        currentId?.let { id ->
                            haptics.commit()
                            viewModel.swipe(id, keep = false)
                            coachMarks.dismiss()
                        }
                    },
                    onUndo = {
                        haptics.undo()
                        viewModel.undo()
                    },
                    onKeep = {
                        currentId?.let { id ->
                            haptics.commit()
                            viewModel.swipe(id, keep = true)
                            coachMarks.dismiss()
                        }
                    },
                )

                // First card only, first run only. `position == 0` is the "very first
                // card" test; `visible` is false forever after the first dismissal, so
                // undoing back to position 0 does not bring it back.
                if (coachMarks.visible && item != null && state.position == 0) {
                    DeckCoachMarkOverlay(onDismiss = { coachMarks.dismiss() })
                }
            }
        }
    }
}

/**
 * The top chrome: how far through the album, and what is marked so far.
 *
 * The gradient is applied before the inset padding, so it covers the status bar too and
 * the counter has a ground everywhere it can be drawn. It ends 24dp below the row rather
 * than at a fixed height, so it grows with the text at large font scales instead of
 * leaving the counter hanging off the bottom of its own scrim.
 *
 * Called only from inside [DeckScreen]'s dark-palette wrapper.
 */
@Composable
private fun DeckTopChrome(
    modifier: Modifier,
    position: Int,
    total: Int,
    markedCount: Int,
    markedBytes: Long,
    onReview: () -> Unit,
) {
    val colors = SwipeyTheme.colors

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            .padding(bottom = SwipeySpacing.xl),
    ) {
        // Full bleed, no gutter: a rule that stops short of the edges reads as a
        // component, and this one is meant to read as the edge of the session itself.
        // No contentDescription — the counter below states the same thing in words, and
        // announcing it twice is noise.
        SwipeyProgressBar(
            progress = if (total > 0) position.toFloat() / total else 0f,
            trackColor = colors.textPrimary.copy(alpha = 0.20f),
            indicatorColor = colors.textPrimary,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = SwipeySpacing.lg, vertical = SwipeySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeyText(
                // `position` is the number of decisions already made, so the card on
                // screen is the one after it: +1 for a human ordinal, coerced for the
                // moment the last card is judged.
                Copy.deckCounter((position + 1).coerceAtMost(total.coerceAtLeast(1)), total),
                modifier = Modifier.weight(1f, fill = false),
                style = SwipeyTheme.typography.labelNumeric,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            // Hidden entirely when nothing is marked: a "0 marked" chip is a control that
            // leads somewhere empty.
            if (markedCount > 0) {
                SwipeyChip(
                    text = Copy.deckMarked(markedCount, formatBytes(markedBytes)),
                    tone = SwipeyTone.Bin,
                    onClick = onReview,
                )
            }
        }
    }
}

/**
 * The three controls, in thumb reach.
 *
 * Bin and Keep are 56dp and sit at the two edges, where a thumb naturally lands; Undo is
 * smaller and central, because it is the rarest of the three and the most expensive to
 * hit by accident. Each is the button equivalent of a gesture, so the deck is fully
 * usable — and fully labelled — without swiping at all.
 *
 * Called only from inside [DeckScreen]'s dark-palette wrapper.
 */
@Composable
private fun DeckControls(
    modifier: Modifier,
    decisionsEnabled: Boolean,
    undoEnabled: Boolean,
    onBin: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))))
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            )
            .padding(horizontal = SwipeySpacing.xl)
            .padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.lg),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xxl, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DeckControl(
            icon = SwipeyIcons.Bin,
            contentDescription = Copy.DECK_BIN_ACTION,
            tone = SwipeyTone.Bin,
            size = SwipeySize.touchPrimary,
            enabled = decisionsEnabled,
            onClick = onBin,
        )
        DeckControl(
            icon = SwipeyIcons.Undo,
            contentDescription = Copy.DECK_UNDO_ACTION,
            tone = SwipeyTone.Neutral,
            size = SwipeySize.touchMin,
            iconSize = 20.dp,
            enabled = undoEnabled,
            onClick = onUndo,
        )
        DeckControl(
            icon = SwipeyIcons.Check,
            contentDescription = Copy.DECK_KEEP_ACTION,
            tone = SwipeyTone.Keep,
            size = SwipeySize.touchPrimary,
            enabled = decisionsEnabled,
            onClick = onKeep,
        )
    }
}

/**
 * One circular control on its own disc.
 *
 * The disc is the reason these read over a bright photograph as well as a dark one: the
 * gradient behind the row handles the general case, and a translucent surface directly
 * under each glyph handles the sunlit-beach case the gradient can't.
 */
@Composable
private fun DeckControl(
    icon: ImageVector,
    contentDescription: String,
    tone: SwipeyTone,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
    iconSize: Dp = SwipeySize.icon,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(SwipeyTheme.colors.surface.copy(alpha = 0.66f)),
        contentAlignment = Alignment.Center,
    ) {
        SwipeyIconButton(
            icon = icon,
            contentDescription = contentDescription,
            onClick = onClick,
            tone = tone,
            enabled = enabled,
            size = size,
            iconSize = iconSize,
        )
    }
}

/**
 * The deck's non-card states — load failed, and all caught up.
 *
 * Centred and capped at a readable measure, in the real theme rather than the deck's
 * over-photo palette: there is no photograph here, so this is an ordinary screen and
 * should look like one.
 */
@Composable
private fun DeckMessage(
    title: String,
    primary: @Composable () -> Unit,
    body: String? = null,
    note: String? = null,
    secondary: (@Composable () -> Unit)? = null,
) {
    val colors = SwipeyTheme.colors

    SwipeyScreen {
        Column(
            Modifier
                .align(Alignment.Center)
                .widthIn(max = 420.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            SwipeyText(
                title,
                style = SwipeyTheme.typography.title,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
            )
            if (body != null) {
                Spacer(Modifier.height(SwipeySpacing.sm))
                SwipeyText(
                    body,
                    style = SwipeyTheme.typography.body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            if (note != null) {
                Spacer(Modifier.height(SwipeySpacing.xs))
                SwipeyText(
                    note,
                    style = SwipeyTheme.typography.label,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(SwipeySpacing.xl))
            primary()
            if (secondary != null) {
                Spacer(Modifier.height(SwipeySpacing.sm))
                secondary()
            }
        }
    }
}

/** The media itself: the whole screen, and the only thing on it that isn't chrome. */
@Composable
fun MediaCardContent(item: MediaItem) {
    if (item.isVideo) {
        VideoCard(item)
    } else {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            // Fit, not Crop, and this is not a stylistic choice: the user is deciding
            // whether to bin this photograph, and cropping it to fill the frame would
            // hide the part of it they might have decided on.
            contentScale = ContentScale.Fit,
        )
    }
}
