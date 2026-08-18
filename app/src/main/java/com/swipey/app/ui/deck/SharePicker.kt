package com.swipey.app.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIconButton
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.design.rememberSwipeyHaptics
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * How much of the screen the centred card takes. The rest is what makes the neighbours
 * visible: at 80%, 10% of the screen is left on each side, and the cards either side sit
 * far enough in to show a real edge rather than a hairline.
 */
private const val CardWidthFraction = 0.80f

/**
 * The gap between one card and the next, as a fraction of the screen.
 *
 * Small on purpose. The page stride is the card's width *plus* this, so it is the only thing
 * deciding how much of a neighbour shows — and the whole point of the layout is that a
 * neighbour shows. See [SharePicker].
 */
private const val CardGapFraction = 0.03f

/** Cards are portrait, matching the deck's own 7:10. */
private const val CardAspect = 0.7f

/** How far a neighbour fades. Never to nothing: an invisible neighbour is not a neighbour. */
private const val NeighbourAlpha = 0.45f

private val TickSize = 30.dp
private val GridSpacing = 3.dp

/**
 * Pick several photographs to share, from the queue you are already swiping.
 *
 * Opened by holding Share on the details sheet; a plain tap there shares the one card and
 * never comes here.
 *
 * ### Flat, not scaled
 * Every card is the same size, and only opacity says which one is centred. The alternative —
 * shrinking the neighbours — makes a handsomer screen and a slower one: it says "this is the
 * subject, the others are context", and the whole job here is picking several, where the
 * others are not context but the next thing you are about to tick. Same size means the tick
 * on a neighbour is the same size as the tick in the middle, and can be hit without centring
 * anything first.
 *
 * That is the one decision everything else follows from. It is why the ticks are on every
 * card rather than on the middle one, why the position counter is small, and why nothing
 * animates as you drag except the fade.
 *
 * ### It shows the session, not the library
 * The pager is handed the deck's current queue, in the deck's current order, starting on the
 * card whose details were open. A picker that offered the whole gallery would be a second
 * gallery app; this one offers what you were already looking through.
 *
 * @param items the queue, in the order the deck deals it.
 * @param startId the card to open on, centred.
 * @param onShare called with everything ticked, in the order it appears in [items].
 */
@Composable
fun SharePicker(
    items: List<MediaItem>,
    startId: Long,
    onShare: (List<MediaItem>) -> Unit,
    onClose: () -> Unit,
) {
    val haptics = rememberSwipeyHaptics()
    val scope = rememberCoroutineScope()
    val start = remember(items, startId) { items.indexOfFirst { it.id == startId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { items.size }

    // Ids, not indices: the queue is fixed for the life of this screen, but ids are what the
    // grid, the counter and the share intent all agree on, and an index would quietly mean a
    // different photograph if the list were ever rebuilt underneath.
    //
    // **Seeded with the card that was held.** Holding Share on a photograph is already a
    // statement about that photograph, and opening onto it unticked would ask the question a
    // second time — worse, it would make the commonest case (share this one, plus a couple of
    // others) cost an extra tap on the card you were already looking at. Share reads "Share 1"
    // the instant the screen appears, so the way out is visible before anything is chosen.
    //
    // Keyed on [startId] so opening the picker from a different card starts from that card
    // instead of inheriting the last selection; a rotation keeps the key and so keeps the
    // ticks. The explicit saver is not decoration — the default one cannot store a Set, and
    // a selection silently emptied by turning the phone is the worst way to find that out.
    var selected by rememberSaveable(
        startId,
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toSet() }),
    ) { mutableStateOf(setOf(startId)) }
    var showingGrid by rememberSaveable { mutableStateOf(false) }
    var previewing by remember { mutableStateOf<MediaItem?>(null) }
    var infoFor by remember { mutableStateOf<MediaItem?>(null) }

    val chosen = remember(selected, items) { items.filter { it.id in selected } }

    BackHandler {
        when {
            previewing != null -> previewing = null
            infoFor != null -> infoFor = null
            showingGrid -> showingGrid = false
            else -> onClose()
        }
    }

    SwipeyTheme(colors = SwipeyDarkColors) {
        Box(
            Modifier
                .fillMaxSize()
                .background(SwipeyDarkColors.canvas),
        ) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                PickerBar(
                    position = Copy.pickPosition(pager.currentPage + 1, items.size),
                    onClose = onClose,
                )

                BoxWithConstraints(Modifier.weight(1f)) {
                    val cardWidth = maxWidth * CardWidthFraction
                    val gap = maxWidth * CardGapFraction
                    // The pager page is the card plus one gap; the leftover is split either
                    // side as content padding, which is what centres the current page and
                    // leaves the neighbours peeking out from under it.
                    val side = (maxWidth - cardWidth) / 2

                    HorizontalPager(
                        state = pager,
                        contentPadding = PaddingValues(horizontal = side),
                        pageSpacing = gap,
                        // Every page is the same size, so there is nothing to key a
                        // per-page layout off; the only thing that varies is alpha.
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        val item = items[page]
                        PickerCard(
                            item = item,
                            selected = item.id in selected,
                            distance = pageDistance(pager, page),
                            onToggle = {
                                haptics.threshold()
                                selected = if (item.id in selected) selected - item.id else selected + item.id
                            },
                            onPreview = { previewing = item },
                            onInfo = { infoFor = item },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(CardAspect)
                                .align(Alignment.Center),
                        )
                    }
                }

                PickerFooter(
                    chosen = chosen,
                    onOpenGrid = { if (chosen.isNotEmpty()) showingGrid = true },
                    onShare = {
                        haptics.commit()
                        onShare(chosen)
                    },
                )
            }

            AnimatedVisibility(
                visible = showingGrid,
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut(),
            ) {
                SelectedGrid(
                    chosen = chosen,
                    onClose = { showingGrid = false },
                    onPick = { item ->
                        showingGrid = false
                        scope.launch { pager.animateScrollToPage(items.indexOfFirst { it.id == item.id }) }
                    },
                    onRemove = { item -> selected = selected - item.id },
                )
            }

            AnimatedVisibility(previewing != null, enter = fadeIn(), exit = fadeOut()) {
                previewing?.let { Preview(it) { previewing = null } }
            }

            // Held on a card. No Share or gallery glyphs on it — see MediaInfoSheet's
            // showActions: offering to share from inside the sharing screen is a smaller
            // way to do what this screen is already for.
            infoFor?.let { item ->
                MediaInfoSheet(
                    item = item,
                    visible = true,
                    onDismiss = { infoFor = null },
                    showActions = false,
                )
            }
        }
    }
}

/**
 * How far this page is from the settled one, 0f at centre and 1f at a full page away.
 *
 * Read from the pager's live offset rather than from `currentPage`, so the fade tracks the
 * drag continuously instead of snapping when the page index flips.
 */
private fun pageDistance(pager: PagerState, page: Int): Float =
    ((pager.currentPage - page) + pager.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)

@Composable
private fun PickerBar(position: String, onClose: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeyIconButton(
            icon = SwipeyIcons.Close,
            contentDescription = Copy.PICK_CLOSE,
            onClick = onClose,
        )
        SwipeyText(
            Copy.PICK_TITLE,
            modifier = Modifier.weight(1f).padding(horizontal = SwipeySpacing.sm),
            style = SwipeyTheme.typography.title,
            color = SwipeyTheme.colors.textPrimary,
        )
        SwipeyText(
            position,
            style = SwipeyTheme.typography.labelNumeric,
            color = SwipeyTheme.colors.textSecondary,
        )
    }
}

/**
 * One photograph, its tick, and a way to see it whole.
 *
 * [distance] only drives alpha. Nothing here scales or translates with the drag — see
 * [SharePicker] for why that is the point rather than an omission.
 */
@Composable
private fun PickerCard(
    item: MediaItem,
    selected: Boolean,
    distance: Float,
    onToggle: () -> Unit,
    onPreview: () -> Unit,
    onInfo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = rememberSwipeyHaptics()
    val colors = SwipeyTheme.colors
    Box(
        modifier
            .graphicsLayer { alpha = 1f - (1f - NeighbourAlpha) * distance }
            .clip(RoundedCornerShape(SwipeyRadius.deck))
            .background(colors.surface)
            // The same pair the deck card carries, for the same reason: a corner button had
            // to be drawn over someone's photograph, and there was no way to make one legible
            // against every picture that would also stay out of the way of the tick.
            // Neither gesture consumes movement, so the pager still gets every drag.
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = Copy.PICK_PREVIEW,
                role = Role.Button,
                onLongClickLabel = Copy.DECK_INFO_ACTION,
                onLongClick = { haptics.commit(); onInfo() },
                onClick = onPreview,
            ),
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )

        // The caption's ground. Same treatment the deck uses over a photograph: a gradient,
        // never a bar, so a dark picture keeps its corner and a bright one stays readable.
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(96.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f)))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(SwipeySpacing.md),
        ) {
            SwipeyText(
                item.displayName,
                style = SwipeyTheme.typography.label,
                color = SwipeyDarkColors.textPrimary,
                maxLines = 1,
            )
            SwipeyText(
                formatBytes(item.sizeBytes),
                style = SwipeyTheme.typography.labelNumeric,
                color = SwipeyDarkColors.textSecondary,
            )
        }

        // On every card, not only the centred one. That is the whole of this variant: a
        // neighbour can be ticked where it stands, without being brought to the middle first.
        SelectionTick(
            selected = selected,
            onClick = onToggle,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(SwipeySpacing.sm),
        )
    }
}

/** The tick. Filled and blue when on, a hairline ring when off — legible over any picture. */
@Composable
private fun SelectionTick(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = SwipeyTheme.colors
    Box(
        modifier
            .size(TickSize)
            .clip(CircleShape)
            .background(if (selected) colors.keep else Color.Black.copy(alpha = 0.42f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Checkbox,
                onClickLabel = if (selected) Copy.PICK_DESELECT else Copy.PICK_SELECT,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            SwipeyIcon(
                SwipeyIcons.Check,
                contentDescription = null,
                tint = SwipeyDarkColors.onAccent,
                size = 18.dp,
            )
        } else {
            Box(
                Modifier
                    .size(TickSize - 8.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
        }
    }
}

/** The count, and the way out. Both disabled until something is ticked. */
@Composable
private fun PickerFooter(chosen: List<MediaItem>, onOpenGrid: () -> Unit, onShare: () -> Unit) {
    val colors = SwipeyTheme.colors
    val count = chosen.size
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .clip(RoundedCornerShape(SwipeyRadius.pill))
                .background(colors.surface)
                .clickable(enabled = count > 0, onClick = onOpenGrid)
                .padding(horizontal = SwipeySpacing.lg, vertical = SwipeySpacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeyText(
                if (count == 0) Copy.PICK_NONE else Copy.pickSelected(count),
                style = SwipeyTheme.typography.label,
                color = if (count == 0) colors.textSecondary else colors.textPrimary,
            )
        }

        Row(
            Modifier
                .clip(RoundedCornerShape(SwipeyRadius.pill))
                .background(if (count == 0) colors.surface else colors.keep)
                .clickable(enabled = count > 0, onClick = onShare)
                .padding(horizontal = SwipeySpacing.lg, vertical = SwipeySpacing.md),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeyIcon(
                SwipeyIcons.Share,
                contentDescription = null,
                tint = if (count == 0) colors.textDisabled else SwipeyDarkColors.onAccent,
                size = 18.dp,
            )
            SwipeyText(
                Copy.pickShare(count),
                style = SwipeyTheme.typography.label,
                color = if (count == 0) colors.textDisabled else SwipeyDarkColors.onAccent,
            )
        }
    }
}

/**
 * Everything ticked, as a grid.
 *
 * Tapping a cell does not open it — it centres it back in the carousel and closes this. The
 * grid is a way of finding your place in a long selection, not a second viewer.
 */
@Composable
private fun SelectedGrid(
    chosen: List<MediaItem>,
    onClose: () -> Unit,
    onPick: (MediaItem) -> Unit,
    onRemove: (MediaItem) -> Unit,
) {
    val colors = SwipeyTheme.colors
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SwipeyIconButton(
                    icon = SwipeyIcons.Close,
                    contentDescription = Copy.PICK_CLOSE,
                    onClick = onClose,
                )
                SwipeyText(
                    Copy.pickSelected(chosen.size),
                    modifier = Modifier.weight(1f).padding(horizontal = SwipeySpacing.sm),
                    style = SwipeyTheme.typography.title,
                    color = colors.textPrimary,
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(horizontal = SwipeySpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(GridSpacing),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
            ) {
                items(chosen, key = { it.id }) { item ->
                    Box(
                        Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(SwipeyRadius.card))
                            .background(colors.surfaceStrong)
                            .clickable(onClick = { onPick(item) }),
                    ) {
                        AsyncImage(
                            model = contentUriFor(item.id, item.isVideo),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        SelectionTick(
                            selected = true,
                            onClick = { onRemove(item) },
                            modifier = Modifier.align(Alignment.TopEnd).padding(SwipeySpacing.xs),
                        )
                    }
                }
            }
        }
    }
}

/** One photograph, whole. Tap anywhere to leave. */
@Composable
private fun Preview(item: MediaItem, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.96f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClose,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize().padding(SwipeySpacing.md),
            contentScale = ContentScale.Fit,
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(SwipeySpacing.md),
        ) {
            SwipeyIconButton(
                icon = SwipeyIcons.Close,
                contentDescription = Copy.PICK_CLOSE,
                onClick = onClose,
            )
        }
    }
}
