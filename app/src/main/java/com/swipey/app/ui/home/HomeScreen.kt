package com.swipey.app.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.ResumePoint
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.Album
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyCaptionGradient
import com.swipey.app.ui.design.SwipeyCaptionScrim
import com.swipey.app.ui.design.SwipeyCard
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyDisabledAlpha
import com.swipey.app.ui.design.SwipeyDrawer
import com.swipey.app.ui.design.SwipeyIcon
import com.swipey.app.ui.design.SwipeyIconButton
import com.swipey.app.ui.design.SwipeyIcons
import com.swipey.app.ui.design.SwipeyMark
import com.swipey.app.ui.design.SwipeyMotion
import com.swipey.app.ui.design.SwipeyProgressBar
import com.swipey.app.ui.design.SwipeyRadius
import com.swipey.app.ui.design.SwipeyRow
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySize
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/**
 * The front door: one big way in, one small one, and every album.
 *
 * ### Why a hero rather than a run of rows
 * Home used to be three equal rows — All media, Albums, Shuffle — which made three
 * identically-weighted choices out of one obvious one. Almost every session is "start at
 * the newest thing and work back", so that choice gets the top of the screen and a picture
 * of what it will deal first, and the other orders move behind the sort control in the
 * hero's corner. That is what keeps the hero's caption — "newest first" — literally true
 * while the four-way chooser stays one tap away.
 *
 * ### Every thumbnail on this screen is the real thing
 * The hero is the most recent item on the device and each album's cover is that album's
 * most recent item — both resolved from one pass in [HomeViewModel.load] rather than
 * guessed at. Shuffle is the exception, and deliberately so: it shows a glyph, because a
 * row that promised the photograph it would open on could only keep that promise by
 * shuffling the entire library on every visit to this screen.
 *
 * The whole page is one [LazyColumn]: every album is listed and nothing is truncated. It is
 * also, now, only ways into a session — the Bin has moved behind the burger in the header,
 * where [HomeMenu] explains why.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    binCount: Int,
    onAllMedia: () -> Unit,
    onSort: () -> Unit,
    onShuffle: (Long) -> Unit,
    /** Reopens the remembered queue on the card after the last decision. See [ShuffleAndRecentRow]. */
    onResume: (ResumePoint) -> Unit,
    onAlbum: (Album) -> Unit,
    onBin: () -> Unit,
    onSettings: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasContent = state.albums.isNotEmpty()
    var menuOpen by remember { mutableStateOf(false) }

    SwipeyScreen(
        overlay = {
            HomeMenu(
                visible = menuOpen,
                binCount = binCount,
                onDismiss = { menuOpen = false },
                // Closed before it navigates, so coming back from the Bin does not land on
                // a drawer the user has no memory of leaving open.
                onBin = { menuOpen = false; onBin() },
                onSettings = { menuOpen = false; onSettings() },
            )
        },
    ) {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") { HomeHeader(onMenu = { menuOpen = true }) }

            item(key = "progress") {
                // The app's loading language everywhere: a 2dp rule, never a spinner. The
                // spacer holds its line open on a refresh so the page below doesn't jump.
                if (state.loading) {
                    SwipeyProgressBar(progress = null)
                } else {
                    Spacer(Modifier.height(SwipeySize.progress))
                }
            }

            when {
                // Checked before the empty branch: "Nothing to swipe yet" after a query
                // threw would tell the user their gallery is empty, which is both false and
                // alarming. Spec §12 — empty state with retry, never a crash.
                state.failed -> item(key = "failed") { HomeMessage(Copy.LOAD_FAILED, onRetry = viewModel::retry) }

                hasContent -> {
                    item(key = "hero") {
                        HeroCard(
                            newest = state.newest,
                            totalCount = state.totalCount,
                            onOpen = onAllMedia,
                            onSort = onSort,
                        )
                    }

                    item(key = "shuffle") {
                        ShuffleAndRecentRow(
                            resume = state.resume,
                            onShuffle = { onShuffle(System.currentTimeMillis()) },
                            onResume = onResume,
                        )
                    }

                    item(key = "albumsHeader") {
                        AlbumsHeader(
                            albumsAsGrid = state.albumsAsGrid,
                            onToggle = viewModel::setAlbumsAsGrid,
                        )
                    }

                    if (state.albumsAsGrid) {
                        // A LazyVerticalGrid cannot nest inside a LazyColumn — it has no
                        // bounded height to measure against — so the two-column layout is
                        // built from pairs, which also keeps the whole page one scroller.
                        // Keys are prefixed so the two layouts occupy disjoint key spaces:
                        // flipping the toggle is then a clean swap rather than the same key
                        // being handed a row where it had a pair of tiles.
                        items(state.albums.chunked(2), key = { "tiles-${it.first().bucketId}" }) { pair ->
                            AlbumTileRow(pair, onAlbum)
                        }
                    } else {
                        itemsIndexed(state.albums, key = { _, album -> "row-${album.bucketId}" }) { index, album ->
                            AlbumRow(
                                album = album,
                                onPick = onAlbum,
                                // No rule under the last album: the Bin footer used to draw
                                // the one that closed the list, and with the Bin moved into
                                // the menu there is nothing below it to be separated from.
                                divider = index < state.albums.lastIndex,
                            )
                        }
                    }
                }

                !state.loading -> item(key = "empty") { HomeMessage(Copy.HOME_EMPTY, body = Copy.HOME_EMPTY_BODY) }
            }

            // The page ends on the albums now. Padding rather than a row, so the last one
            // clears the navigation bar instead of sitting on it.
            item(key = "foot") { Spacer(Modifier.height(SwipeySpacing.xl)) }
        }
    }
}

// ---------------------------------------------------------------------------
// Metrics
// ---------------------------------------------------------------------------

/** The hero's proportions. Wide enough to read as a banner, short enough to leave the shuffle row above the fold. */
private const val HeroAspect = 16f / 10f

/** An album's and the shuffle's thumbnail. Matches [SwipeySize.touchPrimary], so a row's floor is the picture's height. */
private val ThumbSize = 56.dp

/**
 * The albums list/grid toggle's disc, and the glyph inside it.
 *
 * Deliberately under the app's 48dp control size. This one is a section heading's control,
 * not a screen's, and at 48dp it drew a circle the size of the word "Albums" next to the
 * word "Albums" — two things claiming the same weight, when only one of them is the
 * heading. 32dp reads as an accessory to the line it sits on. The tappable area is still
 * 48dp; see [SwipeyIconButton].
 */
private val ToggleSize = 32.dp
private val ToggleIcon = 18.dp

/**
 * The header's burger, and the glyph inside it.
 *
 * Its own size rather than [ToggleSize]'s, because the two controls answer to different
 * neighbours. The albums toggle sits beside a 22sp heading and has to stay under its
 * weight; this one sits beside a 34sp wordmark, where the same 32dp disc read as a
 * footnote — it is the only control on the screen's title line and the way to everything
 * that is not a session, so it should look like it can be hit without aiming.
 *
 * Still under the 48dp control size, which is the point: the lockup stays the loudest
 * thing on the line. The tappable area is 48dp regardless; see [SwipeyIconButton].
 */
private val MenuSize = 40.dp
private val MenuIconSize = 22.dp

/**
 * The disc a control on a photograph is drawn at.
 *
 * Under the 48dp target it carries. The hero's sort control used to fill its whole target,
 * which put a 48dp black disc in the corner of the one picture on Home that is meant to be
 * looked at — big enough to read as part of the composition rather than as something
 * resting on top of it. Shrinking the disc while the target stays put costs nothing: the
 * area a thumb can hit is unchanged, and only the ink moved.
 */
private val PhotoButtonSize = 40.dp
private val PhotoButtonIcon = 18.dp

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader(onMenu: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // The bottom gutter is trimmed by what the burger's 48dp touch target adds
            // around a 40dp line of type — 4dp above and 4dp below, since the row centres —
            // so the lockup keeps the distance from the hero card it had before there was a
            // control on this line to grow it.
            .padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // The lockup. The mark is measured from the wordmark's type size rather than fixed
        // in dp, so the two scale together: at 200% font scale a fixed-size mark would sit
        // beside a wordmark twice its height and read as a stray bullet. 0.82 puts the
        // mark a little above the cap height of the "S", which is where a mark sits.
        val markHeight = with(LocalDensity.current) {
            SwipeyTheme.typography.display.fontSize.toDp() * 0.82f
        }
        SwipeyMark(height = markHeight)
        Spacer(Modifier.width(SwipeySpacing.md))

        SwipeyText(
            Copy.APP_NAME,
            // Bounded rather than free: at a large font scale an unweighted wordmark would
            // measure past the gutter instead of wrapping inside it.
            modifier = Modifier.weight(1f),
            style = SwipeyTheme.typography.display,
            color = SwipeyTheme.colors.textPrimary,
        )

        SwipeyIconButton(
            icon = SwipeyIcons.Menu,
            contentDescription = Copy.HOME_MENU,
            onClick = onMenu,
            size = MenuSize,
            iconSize = MenuIconSize,
        )
    }
}

// ---------------------------------------------------------------------------
// Hero
// ---------------------------------------------------------------------------

@Composable
private fun HeroCard(newest: MediaItem?, totalCount: Int, onOpen: () -> Unit, onSort: () -> Unit) {
    SwipeyCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
        // The hairline is for cards made of text, where it is what separates a surface from
        // the page behind it. This card is a photograph, and it already has an edge — its
        // own — so a ring around it is a second rectangle drawn a hairline outside the first
        // one. It also has nothing consistent to sit against: over a dark frame it vanishes
        // and over a bright one it reads as a line the picture did not ask for.
        bordered = false,
        // Zero, so the photograph reaches the card's own rounded edge; the caption
        // supplies its own inset over the top of it.
        contentPadding = PaddingValues(0.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(HeroAspect),
        ) {
            if (newest != null) {
                AsyncImage(
                    model = contentUriFor(newest.id, newest.isVideo),
                    // Decorative: the caption over it is the card's accessible name, and
                    // describing the user's own most recent photo is not this app's job.
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            CaptionGradient(heightFraction = 0.55f)
            Column(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(SwipeySpacing.lg),
            ) {
                SwipeyText(
                    Copy.HOME_ALL_MEDIA,
                    style = SwipeyTheme.typography.title,
                    // Dark-palette ink in both themes: the ground here is a dimmed
                    // photograph, not the canvas.
                    color = SwipeyDarkColors.textPrimary,
                    maxLines = 1,
                )
                SwipeyText(
                    Copy.homeAllMediaCaption(totalCount),
                    style = SwipeyTheme.typography.labelNumeric,
                    color = SwipeyDarkColors.textSecondary,
                    // Two, not one: at a 200% font scale this sentence is wider than a
                    // phone, and truncating it would drop "newest first" — which is the
                    // half that tells the user what the tap does.
                    maxLines = 2,
                )
            }
            PhotoIconButton(
                icon = SwipeyIcons.Sort,
                contentDescription = Copy.HOME_SORT_ACTION,
                onClick = onSort,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(SwipeySpacing.sm),
            )
        }
    }
}

/**
 * An icon-only control sitting on a photograph.
 *
 * Not [SwipeyIconButton]: a Neutral ghost is a hairline ring in the *theme's* colours over
 * a transparent ground, which over an arbitrary image is both invisible and, in the light
 * palette, near-black ink on a dark picture. This is the same answer `BinScreen`'s tile
 * badges give — a dark-palette disc with dark-palette ink, in both themes.
 *
 * Two boxes, not one, for the same reason [SwipeyIconButton] uses two: the outer carries
 * the app's 48dp touch floor and the inner is what the eye sees, so the disc can be sized
 * for the photograph it sits on without taking the target down with it.
 */
@Composable
private fun PhotoIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = SwipeyMotion.press(),
        label = "photoIconPress",
    )

    Box(
        modifier
            .size(SwipeySize.touchMin)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .scale(scale)
                .size(PhotoButtonSize)
                .clip(CircleShape)
                .background(SwipeyCaptionScrim),
            contentAlignment = Alignment.Center,
        ) {
            SwipeyIcon(
                icon,
                contentDescription = contentDescription,
                tint = SwipeyDarkColors.textPrimary,
                size = PhotoButtonIcon,
            )
        }
    }
}

/**
 * Home's caption ramp, which is now the app's: see [SwipeyCaptionGradient]. Kept as a
 * one-line alias so the call sites below still read in Home's own vocabulary, and so the
 * fractions they pass stay next to the shapes they were chosen for.
 */
@Composable
private fun BoxScope.CaptionGradient(heightFraction: Float) = SwipeyCaptionGradient(heightFraction)

// ---------------------------------------------------------------------------
// Shuffle
// ---------------------------------------------------------------------------

/**
 * The two ways into a deck that are not an album: a random order, and the one you were
 * already in.
 *
 * ### Why they share a line
 * They are the same kind of offer — a queue, one tap away, needing no choice made about it —
 * and they are the two shortcuts a returning user reaches for. As full-width rows they cost
 * two of the four rows above the fold and pushed the albums off the screen. Side by side
 * they cost one, and reading them together is the point: *anything, or where I was*.
 *
 * The cost is the subtitle, which now has half the width and gets one line. That is why
 * Recent's says which queue rather than which photograph — the thumbnail already names the
 * photograph, and it does it better than a filename would.
 *
 * ### Shuffle is unchanged
 * A glyph, not a photograph, and its seed generated at the tap. The row used to show the
 * picture the shuffle would open on, captioned "Starts on this one" — honest, but it made a
 * promise the row did not need to make, and keeping it true meant shuffling the entire
 * library on every visit to Home purely to find element zero.
 *
 * @param resume null before the user has swiped anything, or once the queue they swiped has
 *   nothing left in it — not merely because the photograph they stopped on was deleted,
 *   which is the ordinary end of a session and now resolves to its neighbour
 *   (`HomeViewModel.resolveResume`). The tile draws null as unavailable rather than
 *   vanishing: a control that appears only once you have used the app is one you have to
 *   find twice.
 */
@Composable
private fun ShuffleAndRecentRow(
    resume: ResumeOffer?,
    onShuffle: () -> Unit,
    onResume: (ResumePoint) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = SwipeySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
    ) {
        PairTile(
            title = Copy.HOME_SHUFFLE,
            subtitle = Copy.HOME_SHUFFLE_SUB,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
            leading = {
                Box(
                    Modifier
                        .size(PairThumb)
                        .clip(RoundedCornerShape(SwipeyRadius.card))
                        .background(SwipeyTheme.colors.surfaceStrong),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeyIcon(
                        SwipeyIcons.Shuffle,
                        contentDescription = null,
                        tint = SwipeyTheme.colors.textSecondary,
                        size = 20.dp,
                    )
                }
            },
        )

        PairTile(
            title = Copy.HOME_RECENT,
            subtitle = when {
                resume == null -> Copy.HOME_RECENT_NONE
                resume.point.shuffle -> Copy.HOME_RECENT_SHUFFLE
                // The album it was, not the album this photograph happens to live in: a
                // whole-library pass reaches every album, and naming one of them would be
                // describing a queue the tap will not deal.
                resume.point.bucketId == null -> Copy.HOME_ALL_MEDIA
                else -> resume.item.bucketName
            },
            onClick = resume?.let { { onResume(it.point) } },
            onClickLabel = Copy.HOME_RECENT_ACTION,
            modifier = Modifier.weight(1f),
            leading = {
                if (resume != null) {
                    MediaThumb(resume.item.id, resume.item.isVideo, PairThumb)
                } else {
                    Box(
                        Modifier
                            .size(PairThumb)
                            .clip(RoundedCornerShape(SwipeyRadius.card))
                            .background(SwipeyTheme.colors.surfaceStrong),
                    )
                }
            },
        )
    }
}

/**
 * Half a row: a square, a name, and a line about it.
 *
 * Not [SwipeyRow] with a width constraint. That row is built to run the width of the page —
 * it carries a chevron, a divider and a trailing slot, and all three read as "this is a list"
 * rather than "this is a card". At half width the chevron alone would take a fifth of the
 * space the subtitle needs.
 *
 * A null [onClick] is the unavailable state: dimmed to [SwipeyDisabledAlpha] and inert, with
 * no click semantics at all, so a screen reader is not offered a button that does nothing.
 */
@Composable
private fun PairTile(
    title: String,
    subtitle: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    leading: @Composable () -> Unit,
) {
    val colors = SwipeyTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    Row(
        modifier
            .clip(RoundedCornerShape(SwipeyRadius.card))
            // The pressed state dims the ground rather than scaling, exactly as SwipeyRow
            // does — the two are read as siblings and should answer a thumb the same way.
            .background(if (pressed) colors.surfaceStrong else colors.surface)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        role = Role.Button,
                        onClickLabel = onClickLabel,
                        onClick = onClick,
                    )
                } else {
                    Modifier.alpha(SwipeyDisabledAlpha)
                },
            )
            .padding(SwipeySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Spacer(Modifier.width(SwipeySpacing.sm))
        Column(Modifier.weight(1f)) {
            SwipeyText(
                title,
                style = SwipeyTheme.typography.body,
                color = colors.textPrimary,
                maxLines = 1,
            )
            SwipeyText(
                subtitle,
                style = SwipeyTheme.typography.label,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** The square on a half-width tile. Smaller than [ThumbSize], which a full-width row can afford. */
private val PairThumb = 44.dp

// ---------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------

/**
 * The section heading, with the layout toggle on its right.
 *
 * The toggle used to live up in the app header beside the wordmark, which put it about as
 * far as it could get from the only thing it changes and made it look like a screen-level
 * control — Home's one setting, next to Home's title. Sitting at the end of the "Albums"
 * line it reads as what it is: this list's own switch. That also retires the
 * `showToggle` flag it needed up there, because this heading is only composed on the
 * branch where there are albums to lay out.
 *
 * It is drawn at [ToggleSize] rather than the app's usual 48dp so it sits *under* the
 * heading's weight instead of beside it; [SwipeyIconButton] still hands it a 48dp target.
 */
@Composable
private fun AlbumsHeader(albumsAsGrid: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // Trimmed from the xl/sm this heading carried as a lone line of text: the
            // control's invisible touch box is 16dp taller than the words, and letting it
            // eat that much of the gap keeps the run of albums where it was on the page.
            .padding(top = SwipeySpacing.lg, bottom = SwipeySpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwipeyText(
            Copy.HOME_ALBUMS,
            // Takes the slack so the toggle stays pinned right as the heading grows with
            // the user's font scale.
            modifier = Modifier.weight(1f),
            style = SwipeyTheme.typography.title,
            color = SwipeyTheme.colors.textPrimary,
        )
        // The glyph shows the layout the next tap goes *to*, and the description says so
        // out loud — a toggle whose icon means "you are here" and one whose icon means
        // "tap for this" are indistinguishable without it.
        SwipeyIconButton(
            icon = if (albumsAsGrid) SwipeyIcons.ListRows else SwipeyIcons.Grid,
            contentDescription = if (albumsAsGrid) Copy.HOME_SHOW_LIST else Copy.HOME_SHOW_GRID,
            onClick = { onToggle(!albumsAsGrid) },
            size = ToggleSize,
            iconSize = ToggleIcon,
        )
    }
}

@Composable
private fun AlbumRow(album: Album, onPick: (Album) -> Unit, divider: Boolean) {
    SwipeyRow(
        title = album.name,
        subtitle = Copy.albumSubtitle(album.itemCount, formatBytes(album.totalBytes)),
        onClick = { onPick(album) },
        divider = divider,
        leading = { MediaThumb(album.coverId, album.coverIsVideo, ThumbSize) },
    )
}

@Composable
private fun AlbumTileRow(pair: List<Album>, onPick: (Album) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = SwipeySpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(SwipeySpacing.sm),
    ) {
        pair.forEach { album ->
            AlbumTile(album, onPick, Modifier.weight(1f))
        }
        // An odd last album keeps its column rather than stretching across the row.
        if (pair.size == 1) Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun AlbumTile(album: Album, onPick: (Album) -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }

    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(SwipeyRadius.card))
            .background(SwipeyTheme.colors.surface)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = { onPick(album) },
            ),
    ) {
        AsyncImage(
            model = contentUriFor(album.coverId, album.coverIsVideo),
            // Decorative: the label printed across the bottom of this tile names it.
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        CaptionGradient(heightFraction = 0.6f)
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(SwipeySpacing.md),
        ) {
            SwipeyText(
                album.name,
                style = SwipeyTheme.typography.body,
                color = SwipeyDarkColors.textPrimary,
                maxLines = 1,
            )
            SwipeyText(
                Copy.albumSubtitle(album.itemCount, formatBytes(album.totalBytes)),
                style = SwipeyTheme.typography.labelNumeric,
                color = SwipeyDarkColors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Shared pieces
// ---------------------------------------------------------------------------

/**
 * A square thumbnail. [id] is `null` only where there is genuinely nothing to show — an
 * exhausted shuffle — in which case the surface-coloured square holds the row's rhythm
 * without pretending to be a picture.
 */
@Composable
private fun MediaThumb(id: Long?, isVideo: Boolean, size: Dp) {
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(SwipeyRadius.card))
            .background(SwipeyTheme.colors.surface),
    ) {
        if (id != null) {
            AsyncImage(
                model = contentUriFor(id, isVideo),
                // Decorative: the row's title is its accessible name.
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

/**
 * The two states with nothing to list. Same shape either way — centred, one message, an
 * optional single ghost action — because "we couldn't look" and "there's nothing there"
 * differ in what they say, not in how much noise they should make.
 */
@Composable
private fun LazyItemScope.HomeMessage(
    title: String,
    body: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier
            .fillParentMaxHeight(0.55f)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        SwipeyText(
            title,
            style = SwipeyTheme.typography.body,
            color = SwipeyTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
        if (body != null) {
            Spacer(Modifier.height(SwipeySpacing.sm))
            SwipeyText(
                body,
                style = SwipeyTheme.typography.label,
                color = SwipeyTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
        if (onRetry != null) {
            Spacer(Modifier.height(SwipeySpacing.lg))
            SwipeyButton(text = Copy.RETRY, onClick = onRetry, variant = SwipeyButtonVariant.Ghost)
        }
    }
}

// ---------------------------------------------------------------------------
// Footer
// ---------------------------------------------------------------------------

/**
 * Everything on this screen that is not a way into a session.
 *
 * Which is the reason it is a drawer rather than more of the page. Home's whole body — the
 * hero, the shuffle row, the albums — answers one question, "what am I about to swipe
 * through", and the Bin used to sit at the foot of that list answering a different one.
 * Moving it behind the burger leaves the page saying one thing, and costs the Bin nothing:
 * its count travels with it and is read here exactly as it was read there.
 *
 * Settings is listed and disabled rather than omitted — see [Copy.MENU_SETTINGS].
 */
@Composable
private fun HomeMenu(
    visible: Boolean,
    binCount: Int,
    onDismiss: () -> Unit,
    onBin: () -> Unit,
    onSettings: () -> Unit,
) {
    SwipeyDrawer(visible = visible, onDismiss = onDismiss, title = Copy.MENU_TITLE) {
        SwipeyRow(
            title = Copy.HOME_BIN,
            trailing = Copy.homeBinSubtitle(binCount),
            onClick = onBin,
            leading = {
                SwipeyIcon(
                    SwipeyIcons.Bin,
                    // Decorative: the row is titled "Bin" right beside it.
                    contentDescription = null,
                    tint = SwipeyTheme.colors.textSecondary,
                    size = MenuIcon,
                )
            },
        )
        SwipeyRow(
            title = Copy.MENU_SETTINGS,
            onClick = onSettings,
            divider = false,
            leading = {
                SwipeyIcon(
                    SwipeyIcons.Settings,
                    contentDescription = null,
                    tint = SwipeyTheme.colors.textSecondary,
                    size = MenuIcon,
                )
            },
        )
    }
}

/** The glyph beside a menu row. A shade under the 24dp default, to sit under the title. */
private val MenuIcon = 20.dp
