package com.swipey.app.ui.deck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaDay
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.groupedByDay
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import java.util.Calendar
import java.util.TimeZone

/** How far a marked thumbnail fades. Enough to read as decided, not so far it can't be recognised. */
private const val MarkedAlpha = 0.38f

/** The tick's disc. Small, because the dimming is doing the work and this only names it. */
private val BadgeSize = 20.dp

/**
 * The tap area around that disc.
 *
 * Larger than the disc it contains, and larger than it looks: a 20dp target on the corner of
 * a 104dp cell would be missed as often as hit, and every miss now opens the card instead of
 * ticking it — a mistake that costs the user their place. 40dp is what fits inside the cell
 * while leaving the majority of the picture as the other control.
 */
private val CheckTarget = 40.dp

/** The unticked box's outline. One pixel, like every other border in the app. */
private val CheckRing = 1.5.dp

/**
 * The ring around the card the deck is on.
 *
 * Heavier than the filmstrip's 1.5dp ring around the same photograph, because it is doing a
 * harder job: the strip shows nine thumbnails and this shows the whole session, so "which one
 * is current" has to survive being one cell in a screenful rather than one in nine.
 *
 * A ring and nothing else — no scale, no glow, no badge. The cell already carries a tick box
 * and a dimmed state, and a fourth thing competing for the same 104dp square would make the
 * grid harder to read in exchange for saying something the position on screen already says.
 */
private val CurrentRing = 2.dp

/**
 * How far above the current card the grid opens.
 *
 * Three items — one row on a phone, less than a row on anything wider, which is the right way
 * for it to be wrong: the cost of overshooting is a card half off the top edge, and the cost
 * of undershooting is only a little less context above it.
 */
private const val ScrollLeadIn = 3

/**
 * Every item in the session, with the marked ones shown as marked.
 *
 * This is the deck seen from above: the same queue, in the same order, with the same set of
 * decisions — not a second list with its own state. Everything it draws comes from
 * [DeckUiState], and every tap goes back to the same `SwipeSession`, so the grid and the
 * counter cannot drift apart.
 *
 * ### Marked reads as dimmed
 * A marked thumbnail drops to [MarkedAlpha] and gains a tick. Dimming rather than tinting,
 * because there is no bin colour in this palette to tint with — the same refusal that
 * shaped the deck. It also matches what marking *means*: the photograph is on its way out,
 * so it recedes. The tick is not decoration on top of that; it is what carries the state
 * for anyone who cannot see the difference in brightness, and it is why the box announces
 * itself as a checkbox while the picture behind it announces itself as a button.
 *
 * ### Order, not date — and read from the bottom up
 * Grouped by day but never re-sorted. The grid shows the session's own order, because a
 * shuffled session genuinely is in that order and a grid that quietly sorted it would be
 * describing a different queue from the one the deck is about to serve.
 *
 * That order is laid out **reversed**, so what is coming sits above the current card and what
 * has been decided sits below it. The shade arrives by a downward pull, so the finger's next
 * natural move is downward too, and downward on an unreversed list walks *away* from the
 * queue. Reversing costs nothing, because a reversed queue is still exactly the queue.
 *
 * ### It opens on the current card
 * Which is not the same as opening at the foot, and that was the bug this replaced. The foot
 * of a reversed queue is where the session *started*; it coincides with the current card
 * exactly once, on the first card, and fifty swipes later the grid was opening fifty rows
 * from the photograph the user had just been looking at. It now scrolls to the card the deck
 * is on, [ScrollLeadIn] items early so it lands a row below the header rather than flush
 * against it, and rings that card so it can be found in a screenful of thumbnails.
 *
 * **What that costs.** Anchoring at the foot also gave the shade a way back: with no list
 * left to scroll, the first upward drag went to `DeckScreen`'s nested-scroll connection,
 * which spent it closing the shade. Opening in the middle of the list means an upward drag
 * now scrolls through everything already decided before it reaches the end and starts closing
 * the shade — so on a long session, push-up-to-close is several drags away rather than one.
 * Done and Back are unaffected, and Done is in the header where it cannot scroll off.
 *
 * ### Two taps, and the difference between them is where your thumb lands
 * The tick box marks; the photograph itself opens. That split exists because a grid of the
 * session is two things at once — a list of decisions to edit, and a map of where you are in
 * the queue — and one tap cannot mean both. It used to mean only the first, which left the
 * second unreachable: seeing a photograph twenty cards ahead and wanting to deal with it now
 * meant swiping twenty cards to get there.
 *
 * The tick box is a real target rather than a decoration on the corner: [CheckTarget] square,
 * over the app's touch floor once the cell's own bounds are counted, and it swallows the tap
 * so an aimed tick never opens the card underneath it.
 *
 * @param onToggle marks or unmarks — there is no third state *here*, exactly as there is
 *   none in the deck. Keep is the absence of a mark.
 * @param onOpen deals this photograph next. Everything between here and there is left
 *   undecided rather than judged in passing — see [com.swipey.app.domain.SwipeSession.jumpTo].
 */
@Composable
fun SessionGrid(
    items: List<MediaItem>,
    currentId: Long?,
    markedIds: Set<Long>,
    markedBytes: Long,
    onToggle: (itemId: Long, marked: Boolean) -> Unit,
    onOpen: (itemId: Long) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SwipeyTheme.colors
    // The zone offset is read once and passed down, so the pure grouping stays pure.
    val zoneOffsetSec = remember(items) { TimeZone.getDefault().rawOffset / 1000L }
    // Reversed *before* grouping, not after: one reversal then gives both the order of the
    // days and the order within each day, and because grouping only ever collects runs of
    // neighbours, every day comes out whole. Reversing the grouped list instead would have
    // left each day's own items still running forwards inside a list running backwards.
    val days = remember(items, zoneOffsetSec) { items.asReversed().groupedByDay(zoneOffsetSec) }

    // Where the current card sits among the lazy items — one per day header plus one per
    // photograph. The arithmetic mirrors the emission below, and the two must be changed
    // together. -1 for a session with no current card, which is an exhausted one.
    val currentIndex = remember(days, currentId) { days.indexOfLazyItem(currentId) }
    val gridState = rememberLazyGridState()

    // Anchored before the first frame the user can see. The shade composes this as soon as
    // the pull passes 0, when it is still translated almost entirely off-screen, so the
    // scroll has landed long before there is anything to watch it land.
    //
    // On the current card, and it used to be on the foot of the list. Those were the same
    // thing exactly once — on the first card of a session — and the difference was the bug:
    // the queue is laid out reversed, so the foot is where the session *started*, and fifty
    // swipes in the grid opened fifty rows away from the photograph the user had just been
    // looking at. The reversal is still right, and this is what it was always for.
    //
    // [ScrollLeadIn] items early, so the card lands a row down from the header with what is
    // coming visible above it, rather than flush against the top edge looking like the start
    // of the list.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            gridState.scrollToItem((currentIndex - ScrollLeadIn).coerceAtLeast(0))
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        SessionGridHeader(
            total = items.size,
            markedCount = markedIds.size,
            markedBytes = markedBytes,
            onDone = onDone,
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 104.dp),
            modifier = Modifier.fillMaxSize(),
            state = gridState,
            contentPadding = PaddingValues(
                start = SwipeySpacing.sm,
                end = SwipeySpacing.sm,
                bottom = SwipeySpacing.xxl,
            ),
            horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
            verticalArrangement = Arrangement.spacedBy(SwipeySpacing.xs),
        ) {
            days.forEach { day ->
                item(
                    key = "day-${day.dayStartSec}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    DayHeader(day)
                }
                items(day.items, key = { it.id }) { media ->
                    SessionCell(
                        item = media,
                        marked = media.id in markedIds,
                        current = media.id == currentId,
                        onToggle = { onToggle(media.id, it) },
                        onOpen = { onOpen(media.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionGridHeader(total: Int, markedCount: Int, markedBytes: Long, onDone: () -> Unit) {
    val colors = SwipeyTheme.colors
    Column(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = SwipeySpacing.lg, vertical = SwipeySpacing.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SwipeyText(
                    Copy.GRID_TITLE,
                    style = SwipeyTheme.typography.title,
                    color = colors.textPrimary,
                    maxLines = 1,
                )
                SwipeyText(
                    // The marked figure is the one that matters, so it is stated even at
                    // zero — "nothing marked" on the screen where marking happens is
                    // information, not an empty control.
                    Copy.gridSubtitle(total, markedCount, com.swipey.app.domain.formatBytes(markedBytes)),
                    style = SwipeyTheme.typography.labelNumeric,
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
            SwipeyButton(Copy.GRID_DONE, onClick = onDone, variant = SwipeyButtonVariant.Ghost)
        }
    }
}

@Composable
private fun DayHeader(day: MediaDay) {
    SwipeyText(
        formatDay(day.dayStartSec),
        modifier = Modifier.padding(
            start = SwipeySpacing.xs,
            top = SwipeySpacing.md,
            bottom = SwipeySpacing.xs,
        ),
        style = SwipeyTheme.typography.label,
        color = SwipeyTheme.colors.textSecondary,
        maxLines = 1,
    )
}

/**
 * One photograph in the grid: a tick box that decides it, and a picture that opens it.
 *
 * The two controls are siblings rather than one nested inside the other, so each announces
 * itself for what it is — a checkbox carrying the marked state, and a button that deals this
 * card next. A single node cannot be both, and the previous version being only the first is
 * what made the second impossible.
 */
@Composable
private fun SessionCell(
    item: MediaItem,
    marked: Boolean,
    current: Boolean,
    onToggle: (Boolean) -> Unit,
    onOpen: () -> Unit,
) {
    val shape = RoundedCornerShape(SwipeyRadius.card / 2)
    Box(
        Modifier
            .aspectRatio(1f)
            // Before the clip, not after: a border drawn inside a node clipped to its own
            // shape loses the outer half of its stroke to that clip. Here it straddles the
            // cell's edge into the 4dp gutter, which is what makes a 2dp ring read as 2dp.
            .then(
                if (current) Modifier.border(CurrentRing, SwipeyTheme.colors.textPrimary, shape) else Modifier,
            )
            .clip(shape)
            .clickable(
                role = Role.Button,
                onClickLabel = Copy.GRID_OPEN,
                onClick = onOpen,
            )
            // The ring is the whole of what the outline says visually, so it is said in
            // words too — otherwise the one cell that is already on screen is the one cell a
            // screen reader cannot tell apart.
            .then(
                if (current) Modifier.semantics { stateDescription = Copy.GRID_CURRENT } else Modifier,
            ),
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            // Null: the cell above carries the label. Announcing a filename here as well
            // would read every thumbnail out twice.
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = if (marked) MarkedAlpha else 1f },
            contentScale = ContentScale.Crop,
        )

        // Always drawn, marked or not. A tick box that only appeared once something was
        // ticked would be a control you had to already know about to find — and now that
        // the rest of the cell does something else, finding it is the whole game.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .size(CheckTarget)
                // Checkbox, not Button: this has a state a screen reader must be able to
                // read back, and "marked" is that state. TalkBack announces it as ticked or
                // not, which is the whole of what the dimming says visually.
                .toggleable(
                    value = marked,
                    role = Role.Checkbox,
                    onValueChange = onToggle,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(BadgeSize)
                    .clip(CircleShape)
                    // Filled when marked, a ring over the photograph when not. The ring
                    // needs a ground of its own: an outline alone disappears into a busy
                    // thumbnail, which is most of them.
                    .background(
                        if (marked) SwipeyTheme.colors.keep else SwipeyDarkColors.scrim,
                    )
                    .border(
                        CheckRing,
                        if (marked) Color.Transparent else SwipeyDarkColors.textPrimary,
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (marked) {
                    SwipeyIcon(
                        SwipeyIcons.Check,
                        contentDescription = null,
                        tint = SwipeyDarkColors.textPrimary,
                        size = 13.dp,
                    )
                }
            }
        }
    }
}

/**
 * Where [id] sits in the flat list of lazy items the grid emits, or -1 if it is not there.
 *
 * The grid emits one item per day header and one per photograph, so a media item's index is
 * not its index in the day it belongs to — every header before it counts as well. This walks
 * the same structure the emission walks, in the same order, and **must be changed with it**:
 * a header that stopped being emitted, or a second one per day, would silently scroll the
 * grid to the wrong row rather than fail.
 *
 * Null [id] is the exhausted session, which has no current card to find.
 */
private fun List<MediaDay>.indexOfLazyItem(id: Long?): Int {
    if (id == null) return -1
    var index = 0
    for (day in this) {
        index++ // the day's header
        for (item in day.items) {
            if (item.id == id) return index
            index++
        }
    }
    return -1
}

/**
 * A day label: "Today", "Yesterday", or a date.
 *
 * Formatted here rather than in the domain because it needs the device's zone, its locale
 * and its idea of what today is — none of which belong in a pure function that a test has
 * to be able to pin down.
 */
private fun formatDay(dayStartSec: Long): String {
    val cal = Calendar.getInstance()
    val today = cal.startOfDay()
    val day = Calendar.getInstance().apply { timeInMillis = dayStartSec * 1000L }.startOfDay()
    val daysApart = ((today - day) / 86_400_000L).toInt()
    return when (daysApart) {
        0 -> Copy.GRID_TODAY
        1 -> Copy.GRID_YESTERDAY
        else -> android.text.format.DateFormat.format("d MMM yyyy", day).toString()
    }
}

private fun Calendar.startOfDay(): Long {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
    return timeInMillis
}
