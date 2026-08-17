package com.swipey.app.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.Velocity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.DecidedItem
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyChip
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyDialog
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIconButton
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyMotion
import com.swipey.app.ui.design.SwipeyProgressBar
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme
import com.swipey.app.ui.design.SwipeyTone
import com.swipey.app.ui.design.rememberSwipeyHaptics
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * The deck: one photograph at a time, and two ways to decide about it.
 *
 * ### The photo is the interface
 * The image is a rounded card inset a hair inside the safe area, on a dark page — a
 * lightbox, not a document. There is still no toolbar and no background panel, because
 * either would be a rectangle competing with the photograph for the same screen; the
 * gutter is the only frame, and it exists so the card has corners, so a lift has somewhere
 * to grow, and so the card behind it reads as a card behind it. What chrome there is
 * floats on a soft gradient — a scrim, not a bar — which keeps a white counter legible
 * over a snow scene without painting a grey band across the top of every image.
 *
 * The scrims are full-width while the card is inset, so at the very top and bottom they
 * fall across the page rather than the picture. That is deliberate: a scrim that stopped
 * at the card's edge would draw a second rectangle around it, which is the thing this
 * screen is built to avoid.
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
            secondary = { SwipeyButton(Copy.DONE, onClick = onDone, variant = SwipeyButtonVariant.Ghost) },
        )
        return
    }

    if (state.exhausted && state.markedCount == 0) {
        DeckMessage(
            title = Copy.DECK_EMPTY_TITLE,
            body = Copy.DECK_EMPTY_BODY,
            note = Copy.DECK_NOTHING_MARKED,
            primary = { SwipeyButton(Copy.DONE, onClick = onDone) },
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
    val screenHeightPx = with(LocalDensity.current) { LocalConfiguration.current.screenHeightDp.dp.toPx() }

    // Non-null while a button-initiated decision is flying out. Held here rather than in
    // SwipeCard because the buttons live in the chrome, outside the card.
    var commitRequest by remember { mutableStateOf<Boolean?>(null) }

    // The preview: the current photograph, whole, over everything. Cleared when the deck
    // advances, since a preview of a card the user has already decided on is a window onto
    // the wrong thing.
    var previewing by remember(currentId) { mutableStateOf(false) }

    // The details sheet, keyed on the card for the same reason: it describes one item, and
    // a sheet left open across an advance would be describing the previous one.
    var showingInfo by remember(currentId) { mutableStateOf(false) }

    // The shade, 0f closed to 1f open. An Animatable rather than a plain float because the
    // pull hands it raw finger movement and then lets go: the same value has to be both
    // dragged and settled, and only an Animatable can be re-targeted mid-flight without a
    // seam. Not keyed on the item — the grid outlives any one card.
    val shade = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val shadeOpen = shade.value > 0.5f

    // Back closes the shade before it does anything else. Registered ahead of the
    // discard-confirm handler below so it wins while the grid is up: a user whose last
    // action was opening a grid means "close the grid" by Back, not "abandon the session".
    BackHandler(enabled = shade.value > 0f) {
        scope.launch { shade.animateTo(0f, SwipeyMotion.sheet()) }
    }

    /**
     * The grid's scrolling, continued into the shade.
     *
     * The grid opens sitting on its own foot — the card the deck is on is the bottom row —
     * so the first upward drag inside it has nowhere to go. Without this that drag does
     * nothing at all, and a list that refuses to move is the clearest way an app has of
     * saying "you are stuck": the user's next move is to hunt for the Done button. Handing
     * the leftover travel to the shade instead makes the grid and the deck one continuous
     * surface — pull down to bring the grid over the cards, push up to send it back — and
     * the shade tracks the finger the whole way at the same rate the opening pull used.
     *
     * Remembered against [screenHeightPx] rather than rebuilt each pass: the shade
     * recomposes this screen on every frame it moves, and swapping the connection object
     * mid-drag would tear down the node currently handling that drag.
     */
    val gridToShade = remember(screenHeightPx) {
        object : NestedScrollConnection {
            /** One pixel of finger travel as a fraction of the shade's range. */
            val perPx = 1f / (screenHeightPx * PullTravelFraction)

            /** Moves the shade by [dy] pixels of finger and reports back what it could use. */
            fun drive(dy: Float): Float {
                val from = shade.value
                val to = (from + dy * perPx).coerceIn(0f, 1f)
                if (to == from) return 0f
                scope.launch { shade.snapTo(to) }
                return (to - from) / perPx
            }

            // Downward, with the shade off its seat: it goes back before the grid may
            // scroll. Whatever lifted the shade has to be what lowers it, or the user
            // would be scrolling a grid that is visibly half off the top of the screen.
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                if (shade.value >= 1f) return Offset.Zero
                return Offset(0f, drive(available.y))
            }

            // Upward, and the grid has already taken what it could — which at the foot of
            // the list is nothing. Whatever is left pushes the shade back up.
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
                return Offset(0f, drive(available.y))
            }

            // A shade that has been moved at all owns the release, including the fling that
            // would otherwise be spent scrolling a grid that is no longer under the finger.
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (shade.value >= 1f) return Velocity.Zero
                shade.animateTo(settleTarget(shade.value, available.y), SwipeyMotion.sheet())
                return available
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // Installed only while the grid is closed. Once it is open the grid owns
            // vertical movement — it has to scroll — and hands back whatever it cannot use
            // through [gridToShade], which is what closes it again.
            .verticalPull(
                enabled = !shadeOpen && !previewing && item != null,
                key = currentId,
                onStart = { coachMarks.dismiss() },
                onDrag = { dy ->
                    scope.launch {
                        val step = dy / (screenHeightPx * PullTravelFraction)
                        shade.snapTo((shade.value + step).coerceIn(0f, 1f))
                    }
                },
                onEnd = { _, velocity ->
                    scope.launch {
                        shade.animateTo(settleTarget(shade.value, velocity), SwipeyMotion.sheet())
                    }
                },
            )
            // Not the theme canvas. The deck is a lightbox: the card is grounded in the
            // dark palette (it sits under a photograph, and the chrome over it is dark in
            // both themes), so a light page behind it framed a near-black card in white.
            .background(SwipeyDarkColors.canvas)
            // The deck does not leave, it withdraws: a little smaller and a little darker
            // behind the descending grid, so the shade reads as something arriving over a
            // screen that is still there rather than as a screen replacing it.
            .graphicsLayer {
                val p = shade.value
                val s = 1f - DeckRecede * p
                scaleX = s
                scaleY = s
                alpha = 1f - DeckDim * p
            },
    ) {
        SwipeyTheme(colors = SwipeyDarkColors) {
            DeckTopChrome(
                modifier = Modifier,
                position = state.position,
                total = state.total,
                markedCount = state.markedCount,
                markedBytes = state.markedBytes,
                onReview = onReview,
            )
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (item == null) {
                SwipeyText(
                    Copy.DECK_EMPTY_TITLE,
                    modifier = Modifier.align(Alignment.Center),
                    style = SwipeyTheme.typography.title,
                    color = SwipeyDarkColors.textSecondary,
                )
            } else {
                SwipeCard(
                    itemId = item.id,
                    // The id travels with the decision: DeckViewModel.swipe no-ops unless
                    // it still matches the current card, so a mistimed commit can never be
                    // recorded against a photo the user never judged.
                    onSwiped = { id, keep ->
                        // Cleared here, in the same state batch as the id change, so the
                        // request cannot be re-read against the card that follows.
                        commitRequest = null
                        viewModel.swipe(id, keep)
                        coachMarks.dismiss()
                    },
                    onCommittingChanged = { committing = it },
                    commitRequest = commitRequest,
                    // Tapping the card opens the preview. The card is cropped, so "what
                    // does the rest of this look like" is the question a tap most likely
                    // means here — more likely than anything else a tap could do.
                    onTap = { previewing = true },
                    onTapLabel = Copy.DECK_PREVIEW,
                    under = state.next?.let { next -> { NextCardContent(next) } },
                ) { dragProgress ->
                    // Not rendered while the preview is up: the preview shows the same item
                    // and, for a video, a second VideoCard would stand up a second
                    // ExoPlayer and play its audio behind the one on top.
                    // Playback is not tappable here: the tap belongs to the preview.
                    if (!previewing) {
                        MediaCardContent(
                            item,
                            tapTogglesPlayback = false,
                            dragProgress = dragProgress,
                        )
                    }

                    // Inside the card's content, so it is clipped to the card's corners and
                    // travels with the photograph it describes. Hidden under the preview,
                    // which states the same things at leisure and has the whole screen.
                    if (!previewing) {
                        SwipeyTheme(colors = SwipeyDarkColors) {
                            MediaBadge(item = item, onInfo = { showingInfo = true })
                        }
                    }
                }
            }
        }

        SwipeyTheme(colors = SwipeyDarkColors) {
            DeckFilmstrip(
                decided = state.decided,
                upcoming = state.upcoming,
                position = state.position,
                markedIds = state.markedIds,
                keptIds = state.keptIds,
            )
        }

        SwipeyTheme(colors = DeckControlColors) {
            Box(Modifier.fillMaxWidth()) {
                DeckControls(
                    modifier = Modifier,
                    // Disabled for the length of the fly-off, so the buttons can't record
                    // a second decision over the top of the one already committing.
                    decisionsEnabled = !committing && currentId != null,
                    undoEnabled = !committing,
                    // Both buttons hand the decision to the card rather than recording it
                    // directly, so a tap flies the photograph out exactly the way a drag
                    // does. The decision itself is still recorded once, in `onSwiped`,
                    // when that animation finishes.
                    onBin = {
                        if (currentId != null) {
                            haptics.commit()
                            commitRequest = false
                            coachMarks.dismiss()
                        }
                    },
                    onUndo = {
                        haptics.undo()
                        viewModel.undo()
                    },
                    onKeep = {
                        if (currentId != null) {
                            haptics.commit()
                            commitRequest = true
                            coachMarks.dismiss()
                        }
                    },
                )

            }
        }
    }

    // Above the column, not inside it: both of these cover the whole screen.

    // First card only, first run only. `position == 0` is the "very first card" test;
    // `visible` is false forever after the first dismissal, so undoing back to position 0
    // does not bring it back.
    if (coachMarks.visible && item != null && state.position == 0) {
        SwipeyTheme(colors = SwipeyDarkColors) {
            DeckCoachMarkOverlay(onDismiss = { coachMarks.dismiss() })
        }
    }

    if (previewing && item != null) {
        SwipeyTheme(colors = SwipeyDarkColors) {
            DeckPreview(item = item, onClose = { previewing = false })
        }
    }

    // Composed unconditionally so it can animate out: SwipeySheet draws nothing at all when
    // invisible, and holding it in the tree is what gives the exit its slide.
    if (item != null) {
        MediaInfoSheet(
            item = item,
            visible = showingInfo,
            onDismiss = { showingInfo = false },
        )
    }

    // The shade. Drawn last so it is over everything, and skipped entirely when closed so
    // a grid of hundreds of thumbnails is not composed behind a deck nobody has pulled.
    if (shade.value > 0f) {
        SwipeyTheme(colors = SwipeyDarkColors) {
            Box(
                Modifier
                    .fillMaxSize()
                    .nestedScroll(gridToShade)
                    .graphicsLayer {
                        // Translated by its own height rather than a guess at the screen's,
                        // so it is exactly off-screen at 0f whatever it is drawn into.
                        translationY = -size.height * (1f - shade.value)
                    },
            ) {
                SessionGrid(
                    items = state.items,
                    // What the grid opens on and rings. Null once the session is exhausted,
                    // which is a state the grid can still be pulled open in.
                    currentId = state.current?.id,
                    markedIds = state.markedIds,
                    markedBytes = state.markedBytes,
                    onToggle = { id, marked -> viewModel.setMarked(id, marked) },
                    // Deal that card, then get out of the way. Closing the shade is half of
                    // what the tap means: the user asked to judge this photograph, and
                    // leaving the grid over the top of it would answer only the first half.
                    onOpen = { id ->
                        viewModel.jumpTo(id)
                        scope.launch { shade.animateTo(0f, SwipeyMotion.sheet()) }
                    },
                    onDone = { scope.launch { shade.animateTo(0f, SwipeyMotion.sheet()) } },
                )
            }
        }
    }
}

/**
 * What a thumbnail in the strip is, which is the whole of what it has to say.
 *
 * Note what is *not* here: which side of the current card it sits on. It used to be —
 * `Kept`, `Binned` and `Ahead` each meant a position as well as a state, because behind the
 * deck and decided were the same thing. Two changes separated them. The grid can mark a card
 * the deck has not reached, and it can start the deck from any card at all, leaping over
 * everything between. So a decision now belongs to an item rather than to a place in the
 * queue, and [Undecided] covers both the cards ahead and the ones jumped past.
 */
private enum class FilmstripSlot { Kept, Binned, Current, Undecided }

/**
 * The session, running through the middle of the screen.
 *
 * The card on screen sits at the centre under a ring, what is decided runs off to the left,
 * and what is coming runs off to the right. It is one timeline rather than a queue preview,
 * which is the difference between "here is what is next" and "here is where you are".
 *
 * ### Why centred, and what it costs
 * The strip used to start at the left gutter with the current card first, and close with a
 * "+284" count that ate about 180dp — five thumbnails' worth of room. Dropping the count
 * pays for the left-hand side exactly, so this shows the same distance ahead as the old
 * strip did and adds the same distance behind. The count is no loss: the progress rule and
 * the "47 / 312" counter above already answer how far through the session you are, and they
 * answer it in a form that does not need arithmetic.
 *
 * ### Decided reads as a badge
 * A passed thumbnail dims and takes the same badge the card itself showed while it was being
 * dragged — a tick on [SwipeyColors.keepSignal] for kept, a bin glyph on
 * [SwipeyColors.binSignal] for marked. Reusing the drag vocabulary is the point: the strip
 * says what the card just said, in the same words and the same two colours, so nothing has
 * to be learned twice. This and the card are the only places those two colours appear.
 *
 * The glyphs are not decoration on top of the colour. At this size each badge is a few
 * pixels of green or red, which is the hardest case for anyone who cannot separate the two,
 * and the tick and the bin are what still work when the hue does not.
 *
 * ### The slide
 * The window is rebuilt with the new card already at the centre, so the movement is put back
 * by hand: displace the whole strip by one pitch in the direction the deck just travelled
 * and settle it on the deck's own spring. One decision moves it one pitch, whichever way,
 * which is what makes an undo read as an undo rather than as a redraw.
 *
 * ### The ends
 * Faded rather than cut, with the mask punched through a single offscreen layer. A hard edge
 * at the gutter reads as the end of a component; a fade reads as a session continuing past
 * the screen, which is what it is. It also means the strip needs no leading or trailing
 * treatment for the start of a session, where there is simply nothing to the left yet.
 *
 * **Not a control.** Tapping a thumbnail does nothing, deliberately — forward or back.
 * Jumping the deck forward would leave a gap in the sequence with no way to describe which
 * items were passed over; the session's whole guarantee is that it goes through everything
 * once, in order. This shows where you are; it does not steer.
 *
 * Cleared from the semantics tree for the same reason it has no click handler: to a screen
 * reader it is a row of unlabelled images that cannot be acted on, and the counter above
 * already states the position in words.
 *
 * ### A decision shows wherever the item is
 * The badge used to appear only on the left-hand side, because that was the only side an
 * item could have been decided on. It is not any more: the grid marks cards the deck has not
 * reached, so an item three places *ahead* can already be on its way to the bin. It carries
 * the same bin badge there that it will carry after the deck passes it — which is the point,
 * since the alternative is a strip that quietly withholds a decision until the user has
 * swiped past the photograph it applies to.
 *
 * The mirror of that: an item behind the current card is no longer necessarily decided. A
 * tap in the grid can start the deck anywhere, and everything leapt over sits behind it
 * undecided. Those draw with no badge and at the undecided weight, exactly like the cards
 * ahead — because that is what they are.
 *
 * @param decided the passed items nearest the current card, oldest first, each carrying what
 *   is currently true of it — including "nothing", for a card that was jumped over.
 * @param upcoming the current card at index 0, then what follows.
 * @param markedIds every item bound for the bin, and [keptIds] every item explicitly kept.
 *   Consulted for the cards ahead only; the ones behind carry their own answer in [decided].
 */
@Composable
private fun DeckFilmstrip(
    decided: List<DecidedItem>,
    upcoming: List<MediaItem>,
    position: Int,
    markedIds: Set<Long>,
    keptIds: Set<Long>,
) {
    val current = upcoming.firstOrNull() ?: return
    val step = FilmstripThumb + FilmstripGap
    val stepPx = with(LocalDensity.current) { step.toPx() }

    val slide = remember { Animatable(0f) }
    var previous by remember { mutableIntStateOf(position) }
    LaunchedEffect(position) {
        val travelled = position - previous
        previous = position
        if (travelled != 0) {
            // Clamped to one pitch: a decision only ever moves the deck by one, and a jump
            // from anywhere else should still settle rather than fly across the screen.
            slide.snapTo(travelled.coerceIn(-1, 1) * stepPx)
            slide.animateTo(0f, SwipeyMotion.cardSettle())
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = SwipeySpacing.lg, vertical = SwipeySpacing.sm)
            .height(FilmstripThumb)
            // Offscreen so the ends below can be punched out with DstIn. It clips to the
            // node's bounds as well, which is what keeps the strip inside its gutters
            // without a second clip modifier.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fade = FilmstripFade.toPx().coerceAtMost(size.width / 2f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.Black),
                        startX = 0f,
                        endX = fade,
                    ),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn,
                )
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Black, Color.Transparent),
                        startX = size.width - fade,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fade, 0f),
                    size = Size(fade, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        // Only as many slots as the screen can show. A phone fits five either side; a wider
        // window fits more, and asking for them here rather than fixing a number is what
        // lets the same strip fill a foldable without over-drawing on a handset.
        val reach = ceil((maxWidth / 2f) / step).toInt()

        for (place in -reach..reach) {
            val item: MediaItem
            val slot: FilmstripSlot
            when {
                place < 0 -> {
                    // `decided` ends at the item immediately before the current card, so
                    // its last entry is the one place -1 asks for.
                    val past = decided.getOrNull(decided.size + place) ?: continue
                    item = past.item
                    slot = when (past.kept) {
                        true -> FilmstripSlot.Kept
                        false -> FilmstripSlot.Binned
                        // Passed without being judged — jumped over from the grid.
                        null -> FilmstripSlot.Undecided
                    }
                }
                place == 0 -> {
                    item = current
                    slot = FilmstripSlot.Current
                }
                else -> {
                    item = upcoming.getOrNull(place) ?: continue
                    slot = when (item.id) {
                        in markedIds -> FilmstripSlot.Binned
                        in keptIds -> FilmstripSlot.Kept
                        else -> FilmstripSlot.Undecided
                    }
                }
            }
            FilmstripThumbnail(
                item = item,
                slot = slot,
                modifier = Modifier.offset {
                    IntOffset((place * stepPx + slide.value).roundToInt(), 0)
                },
            )
        }
    }
}

/**
 * One thumbnail, and whatever the strip has to say about it.
 *
 * The dimming is applied to the whole cell rather than to the photograph alone, so a badge
 * recedes with the picture it belongs to. A badge held at full strength over a faded
 * thumbnail would read as the louder of the two, and the decision is not the point here —
 * the sequence is.
 */
@Composable
private fun FilmstripThumbnail(item: MediaItem, slot: FilmstripSlot, modifier: Modifier) {
    val colors = SwipeyTheme.colors
    val shape = RoundedCornerShape(SwipeyRadius.card / 2)

    Box(
        modifier
            .size(FilmstripThumb)
            .graphicsLayer {
                alpha = when (slot) {
                    FilmstripSlot.Current -> 1f
                    FilmstripSlot.Undecided -> FilmstripUndecidedAlpha
                    FilmstripSlot.Kept -> FilmstripKeptAlpha
                    FilmstripSlot.Binned -> FilmstripBinnedAlpha
                }
            },
    ) {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                // The ring, not a size change: growing the current thumbnail would shift
                // every other one sideways on each swipe.
                .then(
                    if (slot == FilmstripSlot.Current) {
                        Modifier.border(FilmstripRing, colors.textSecondary, shape)
                    } else {
                        Modifier
                    },
                ),
            contentScale = ContentScale.Crop,
        )

        if (slot == FilmstripSlot.Kept || slot == FilmstripSlot.Binned) {
            val kept = slot == FilmstripSlot.Kept
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(FilmstripBadgeInset)
                    .size(FilmstripBadge)
                    .clip(CircleShape)
                    // The same two grounds the card's own drag badge uses, so the strip
                    // repeats the decision in the words it was made in.
                    .background(if (kept) colors.keepSignal else colors.binSignal),
                contentAlignment = Alignment.Center,
            ) {
                SwipeyIcon(
                    if (kept) SwipeyIcons.Check else SwipeyIcons.Bin,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    size = FilmstripBadgeIcon,
                )
            }
        }
    }
}

/** How far the preview will zoom. Past this a phone photograph is mostly sensor noise. */
private const val MaxZoom = 6f

/** Where a double-tap lands. Enough to read a face or a sign; not so far it needs panning to orient. */
private const val DoubleTapZoom = 2.5f

/**
 * The current photograph, whole, over everything else.
 *
 * This is the other half of the decision to crop. The card shows a fixed 7:10 rectangle, so
 * a wide photograph reaches the screen with its edges held back — and the user is being
 * asked whether to bin it. Cropping is defensible only if seeing the rest is one tap away,
 * and this is where that tap arrives.
 *
 * [ContentScale.Fit], necessarily: fitting is the entire point, and a preview that cropped
 * would be a second view of the same partial photograph.
 *
 * ### The gestures
 * Pinch to zoom, drag to pan, double-tap to go in and out. Zoom is anchored on the centroid
 * of the pinch rather than the middle of the screen, so the detail under the fingers stays
 * under the fingers — the difference between examining a photograph and fighting one.
 *
 * A single tap closes, but only at rest. Zoomed in, a tap is far more likely to be a pan
 * that never quite moved, and closing on it would throw away the position the user just
 * built; there it resets the zoom instead, which is recoverable. Back always closes, and so
 * does the labelled control, which is what a screen reader can actually find — "tap
 * anywhere" is not discoverable.
 *
 * Video keeps its own tap for play/pause here, which is why single-tap-to-close is images
 * only. Both are the conventional gesture for their medium; the close control covers both.
 */
@Composable
private fun DeckPreview(item: MediaItem, onClose: () -> Unit) {
    BackHandler(enabled = true, onBack = onClose)

    var scale by remember(item.id) { mutableFloatStateOf(1f) }
    var offset by remember(item.id) { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }

    // Pan is clamped against the container rather than the drawn image. The image is fitted,
    // so it is usually narrower or shorter than the box and this lets the user pull a little
    // past its edge — which is the forgiving failure. Clamping to the image instead would
    // need its intrinsic size, and would stop the drag dead at an edge the user cannot see.
    fun clamp(candidate: Offset, at: Float): Offset {
        val maxX = size.width * (at - 1f) / 2f
        val maxY = size.height * (at - 1f) / 2f
        return Offset(candidate.x.coerceIn(-maxX, maxX), candidate.y.coerceIn(-maxY, maxY))
    }

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(SwipeyDarkColors.canvas)
            .onSizeChanged { size = it }
            .pointerInput(item.id) {
                detectTapGestures(
                    onTap = {
                        // Zoomed in this would discard a position the user built by hand.
                        if (scale > 1f) reset() else if (!item.isVideo) onClose()
                    },
                    onDoubleTap = { position ->
                        if (scale > 1f) {
                            reset()
                        } else {
                            val centre = Offset(size.width / 2f, size.height / 2f)
                            val fromCentre = position - centre
                            scale = DoubleTapZoom
                            // The tapped point is what should stay put, so the content moves
                            // by however far that point is thrown outward by the new scale.
                            offset = clamp(fromCentre * (1f - DoubleTapZoom), DoubleTapZoom)
                        }
                    },
                )
            }
            .pointerInput(item.id) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val previous = scale
                    val next = (previous * zoom).coerceIn(1f, MaxZoom)
                    if (next == 1f) {
                        reset()
                    } else {
                        // Keep the content point under the centroid under the centroid.
                        // Screen position p of a content point q is centre + q * scale +
                        // offset, so solving for the offset that holds q fixed as scale
                        // changes gives this.
                        val centre = Offset(size.width / 2f, size.height / 2f)
                        val c = centroid - centre
                        scale = next
                        offset = clamp(c + pan - (c - offset) * (next / previous), next)
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Video is built here rather than through MediaCardContent, because the preview has
        // to hold the player and its controls apart: the picture belongs inside the zoom
        // layer below, and the controls very much do not. See VideoControls.
        val playback = if (item.isVideo) rememberVideoPlayback(item) else null
        val ambient = playback?.let { rememberVideoAmbient(it) }

        // The deck's ambient light, on the screen that has the most empty space of all: this
        // fits the clip to the whole display, so a 16:9 phone video leaves better than half
        // the page bare. Outside the zoom layer on purpose — magnifying a frame magnifies the
        // frame, and light in a room does not get bigger when you lean in. It also means a
        // clip zoomed to 3× is simply covering its own glow, which costs nothing to look at.
        if (ambient != null) VideoAmbientLayer(ambient)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
            contentAlignment = Alignment.Center,
        ) {
            if (playback != null) {
                VideoSurface(playback, Modifier.fillMaxSize(), ambient = ambient)
                // The frame is the play/pause control here, which is the conventional
                // gesture for video and the reason single-tap-to-close is images only.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClickLabel = if (playback.playing) Copy.VIDEO_PAUSE else Copy.VIDEO_PLAY,
                            role = Role.Button,
                        ) { playback.togglePlaying() },
                )
            } else {
                MediaCardContent(item, scale = ContentScale.Fit)
            }
        }

        // Outside the layer above, and that is the whole point: pinching magnifies the
        // frame, not the scrubber. At 3× a control drawn inside that layer would be three
        // times the size, panned halfway off the screen, and holding touch targets nobody
        // could find.
        if (playback != null) {
            VideoControls(
                playback = playback,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
                    .padding(horizontal = SwipeySpacing.md, vertical = SwipeySpacing.sm),
            )
        }

        SwipeyIconButton(
            icon = SwipeyIcons.Close,
            contentDescription = Copy.DECK_PREVIEW_CLOSE,
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(SwipeySpacing.md),
            size = SwipeySize.touchMin,
        )
    }
}

/**
 * The top chrome: how far through the album, and what is marked so far.
 *
 * No scrim any more. It had one because it used to sit on top of the photograph and needed
 * a ground to be legible against; the card is inset now, so this row has the page to itself
 * and a gradient over nothing would just be a grey band.
 *
 * ### Its height is fixed, and that is the point
 * The marked chip appears the moment the first card is binned and is absent before that, so
 * for as long as its 48dp touch target set this row's height, the header grew by ~30dp on
 * the first swipe of every session — and the card, which is measured from whatever height is
 * left, shrank underneath it. The photograph being judged got smaller as a side effect of
 * judging it.
 *
 * So the row reserves [SwipeySize.touchMin] whether the chip is there or not, and the gutter
 * below gives the difference back: the chrome is the same height at zero marked and at two
 * hundred, and the card is measured once and stays put. Any control added to this row later
 * must fit that reservation rather than extend it.
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
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
            )
            // One step down from xl, because the row below now stands a fixed 48dp rather
            // than the counter's 42. The chrome ends up 2dp shorter than it used to be with
            // nothing marked, and 32dp shorter than it used to be once something was —
            // which is the jump this is here to remove.
            .padding(bottom = SwipeySpacing.lg),
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
                // Reserved, not measured: the chip's touch target is the tallest thing this
                // row can hold, so the row is that tall from the first frame — before there
                // is a chip, and after the last mark is undone. See the KDoc.
                .heightIn(min = SwipeySize.touchMin)
                .padding(horizontal = SwipeySpacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SwipeyText(
                // `position` is the number of decisions already made, so the card on
                // screen is the one after it: +1 for a human ordinal, coerced for the
                // moment the last card is judged.
                Copy.deckCounter((position + 1).coerceAtMost(total.coerceAtLeast(1)), total),
                // One weight, filling — not `fill = false` plus a weighted Spacer, which is
                // what this was and what left the chip floating short of the gutter. Two
                // weights split the leftover space in half; the counter then declined its
                // half, and a Row hands unclaimed space to the *end*, so the surplus piled
                // up after the chip as a gap the size of the counter's unused slot. Filling
                // one weight leaves nothing to hand out, and the chip lands on the 16dp
                // margin every other right-hand edge in the app uses.
                modifier = Modifier.weight(1f),
                style = SwipeyTheme.typography.labelNumeric,
                color = colors.textPrimary,
                maxLines = 1,
            )
            // Hidden entirely when nothing is marked: a "0 marked" chip is a control that
            // leads somewhere empty.
            //
            // The glyph and the chevron are the whole point of this chip, not decoration on
            // it. Marking is obvious; *finishing* is not — everything binned here sits in a
            // list that only empties from Review, and this is the only way in. Stating a
            // count in a hairline pill described that list without ever suggesting it could
            // be opened, which is the same shape the app uses for captions that do nothing.
            // The bin says what kind of place it leads to and the chevron says that it leads
            // somewhere; between them the pill stops reading as a readout.
            //
            // The tone was already Bin before this — but tone only colours a glyph, and
            // there was no glyph, so it had been doing nothing at all.
            if (markedCount > 0) {
                SwipeyChip(
                    text = Copy.deckMarked(markedCount, formatBytes(markedBytes)),
                    tone = SwipeyTone.Bin,
                    onClick = onReview,
                    // The count is what the chip says; this is what it does. A screen
                    // reader gets both — "4 marked · 145 KB … double tap to review" —
                    // where sighted users get the verb from the chevron.
                    onClickLabel = Copy.DECK_REVIEW,
                    icon = SwipeyIcons.Bin,
                    trailingIcon = SwipeyIcons.ChevronRight,
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

/**
 * The card waiting underneath, drawn as a still frame.
 *
 * Never [VideoCard], even when the next item is a video. That would stand a second
 * ExoPlayer up for a clip nobody is watching yet, on the one screen where a dropped frame
 * is felt in the thumb — and it would start playing audio behind the photograph the user
 * is actually judging. Coil's video decoder is on the classpath (`coil.video`), so the
 * same content URI yields a frame for video and the image itself for a photo.
 *
 * The model key is identical to [MediaCardContent]'s, which is the point: by the time this
 * item becomes current, Coil serves it from the memory cache and the promotion is
 * seamless rather than a re-fetch.
 *
 * ### It has to be framed the way the promoted card will be
 * Which means the scale is not one decision but two, because the card itself isn't one:
 * a photograph is cropped to fill the card and a video is fitted inside it. Drawing both
 * cropped here — which is what this did — meant a clip appeared full-bleed under the card
 * being swiped and then, at the instant it was promoted, snapped back to its own aspect
 * with bands above and below. The user is judging the frame in front of them; it should not
 * be a different frame a moment later.
 *
 * A fitted video gets the same [VideoAmbientLayer] treatment the promoted card gets, built
 * from this very still — otherwise the bands would arrive dark and light up a beat later,
 * which is the same discontinuity in a quieter form. It is one extra draw of a bitmap Coil
 * has already decoded for the picture on top of it.
 *
 * No content description. This is not the item being judged; announcing it would put two
 * photographs into a screen reader's account of a screen that only offers a decision about
 * one of them.
 */
@Composable
private fun NextCardContent(item: MediaItem) {
    val model = contentUriFor(item.id, item.isVideo)

    if (item.isVideo) {
        Box(Modifier.fillMaxSize()) {
            VideoAmbientStill(model)
            AsyncImage(
                model = model,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                // Fit, matching VideoCard's player: a clip is never cropped, here or there.
                contentScale = ContentScale.Fit,
            )
        }
    } else {
        AsyncImage(
            model = model,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            // Crop, matching MediaCardContent: a photograph fills the card in both places.
            contentScale = ContentScale.Crop,
        )
    }
}

/**
 * How much of the screen's height a full pull covers.
 *
 * The shade tracks the finger over about a third of the screen. Combined with
 * [PullCommitFraction] this puts the commit point around 130dp of downward travel on a
 * normal phone: far enough that it is plainly a decision, near enough that it is one
 * movement of a thumb rather than a haul.
 */
private const val PullTravelFraction = 0.34f

/**
 * How far the pull must get before releasing opens rather than snaps back.
 *
 * Under halfway, because [PullFlingVelocity] carries the impatient case. A user who means
 * it flicks; a user who is exploring drags slowly and gets a long, reversible travel in
 * which to change their mind. Both are served without either threshold having to be a
 * compromise between them.
 */
private const val PullCommitFraction = 0.45f

/**
 * The downward speed at which a release opens regardless of how far it got.
 *
 * The reason distance alone was never going to feel right. A deliberate flick is over in
 * under a tenth of a second and covers very little ground — judged on distance it fails,
 * and the gesture feels dead no matter how low the bar goes. Judged on speed it succeeds
 * immediately, which lets the distance threshold stay high enough to ignore drift.
 *
 * px/s, matching what a VelocityTracker reports.
 */
private const val PullFlingVelocity = 1100f

/**
 * Where a released shade belongs: 1f open, 0f closed.
 *
 * Either a long enough pull or a fast enough flick. A flick that is *upward* at release
 * closes even from past the commit point — the last thing the finger did is a better
 * account of intent than where it happened to stop.
 *
 * Shared by the deck's pull and by the grid's overscroll, because those are the same
 * question asked from two places. They used to be two copies, and two copies of a
 * threshold are two chances for a gesture to end somewhere its twin would not have.
 *
 * @param position the shade's current 0f..1f.
 * @param velocity px/s at release, positive downward.
 */
private fun settleTarget(position: Float, velocity: Float): Float {
    val flungOpen = velocity > PullFlingVelocity
    val flungShut = velocity < -PullFlingVelocity
    return if (!flungShut && (flungOpen || position > PullCommitFraction)) 1f else 0f
}

/**
 * The palette the deck's three controls are drawn in.
 *
 * The dark palette with the two decision roles swapped for their signal colours, so
 * [SwipeyTone.Bin] and [SwipeyTone.Keep] resolve to green and red *here and nowhere else*.
 *
 * Done by handing the controls a palette rather than by teaching [SwipeyTone] a fourth and
 * fifth voice, or by letting a call site pass a colour. Both of those would have made the
 * signals reachable from anywhere, and the whole value of a red that means "this one is
 * going" is that it cannot turn up on a settings row or an error state. The tones still
 * carry meaning rather than colour; the deck is simply a place where those two meanings are
 * worth shouting. The same trick the surrounding chrome already uses to force the dark
 * palette over a photograph, one step further.
 *
 * Undo is unaffected: it is [SwipeyTone.Neutral] and has no decision to signal.
 */
private val DeckControlColors = SwipeyDarkColors.copy(
    keep = SwipeyDarkColors.keepSignal,
    bin = SwipeyDarkColors.binSignal,
)

/** How far the deck shrinks behind a fully open shade. */
private const val DeckRecede = 0.05f

/** How far the deck dims behind a fully open shade. */
private const val DeckDim = 0.45f

/**
 * A filmstrip thumbnail. Small enough to sit under the card, big enough to recognise.
 *
 * 34dp rather than the 30dp the strip started at. The strip is a timeline now and a
 * timeline is read out of the corner of the eye while the eye is on the photograph, which
 * a 30dp thumbnail was too small to survive — and the room came free when the tail count
 * went, so it cost nothing but two thumbnails of reach.
 */
private val FilmstripThumb = 34.dp

/** Between thumbnails. The same gutter the grid puts between its cells. */
private val FilmstripGap = SwipeySpacing.xs

/** How far the strip dissolves at each end. */
private val FilmstripFade = 28.dp

/** The ring around the current thumbnail. */
private val FilmstripRing = 1.5.dp

/**
 * How far back an undecided thumbnail sits, on either side of the current card.
 *
 * One weight for "nobody has judged this", whether it is still coming or was jumped over.
 * The alternative — dimming a skipped card the way a decided one is dimmed — would say
 * something about it that is not true.
 */
private const val FilmstripUndecidedAlpha = 0.5f

/** Decided and kept. A shade in front of what is coming, because it is settled. */
private const val FilmstripKeptAlpha = 0.6f

/** Decided and marked. Furthest back of the four: it is on its way out. */
private const val FilmstripBinnedAlpha = 0.45f

/** The decision badge on a decided thumbnail, and the glyph inside it. */
private val FilmstripBadge = 12.dp
private val FilmstripBadgeIcon = 9.dp

/** Holds the badge off the thumbnail's rounded corner. */
private val FilmstripBadgeInset = 1.dp

/**
 * The media itself: the card's whole surface, and the only thing on screen that isn't chrome.
 *
 * [dragProgress] reaches video only, and only on the deck — it drives the lag in the ambient
 * light behind a fitted clip (see [VideoAmbientLayer]). A photograph is cropped to the card
 * and has no space behind it for anything to lag in, and the preview has no drag to report.
 */
@Composable
fun MediaCardContent(
    item: MediaItem,
    scale: ContentScale = ContentScale.Crop,
    tapTogglesPlayback: Boolean = true,
    dragProgress: () -> Float = { 0f },
) {
    if (item.isVideo) {
        VideoCard(item, tapTogglesPlayback = tapTogglesPlayback, dragProgress = dragProgress)
    } else {
        AsyncImage(
            model = contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            // Crop on the card, so every card is the same rectangle and the strip and the
            // controls never move. That does hide the edges of anything wider than 7:10, and
            // the user is deciding whether to bin it — which is why the preview exists and
            // why it passes Fit. Cropping is only defensible while seeing the rest is one
            // tap away.
            contentScale = scale,
        )
    }
}
