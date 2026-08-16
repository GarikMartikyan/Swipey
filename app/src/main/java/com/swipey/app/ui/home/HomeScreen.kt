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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.Album
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyButton
import com.swipey.app.ui.design.SwipeyButtonVariant
import com.swipey.app.ui.design.SwipeyCard
import com.swipey.app.ui.design.SwipeyDarkColors
import com.swipey.app.ui.design.SwipeyDivider
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
 * The front door: one big way in, one small one, every album, and the Bin.
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
 * The hero is the most recent item on the device, each album's cover is that album's most
 * recent item, and the Shuffle thumbnail is the card a shuffle started right now would
 * actually open on — see [HomeViewModel.load], which resolves all three from one pass and
 * carries the shuffle's seed through to the deck so the last of those stays true.
 *
 * The whole page is one [LazyColumn]: every album is listed, nothing is truncated, and the
 * Bin footer scrolls off with the rest rather than sitting in a fixed bar.
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    binCount: Int,
    onAllMedia: () -> Unit,
    onSort: () -> Unit,
    onShuffle: (Long) -> Unit,
    onAlbum: (Album) -> Unit,
    onBin: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasContent = state.albums.isNotEmpty()

    SwipeyScreen {
        LazyColumn(Modifier.fillMaxSize()) {
            item(key = "header") {
                HomeHeader(
                    // A toggle over an empty or unread album section controls nothing, and
                    // an inert control is worse than an absent one.
                    showToggle = hasContent,
                    albumsAsGrid = state.albumsAsGrid,
                    onToggle = viewModel::setAlbumsAsGrid,
                )
            }

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
                        ShuffleRow(
                            first = state.shuffleFirst,
                            onShuffle = { onShuffle(state.shuffleSeed) },
                        )
                    }

                    item(key = "albumsHeader") {
                        SwipeyText(
                            Copy.HOME_ALBUMS,
                            modifier = Modifier.padding(top = SwipeySpacing.xl, bottom = SwipeySpacing.sm),
                            style = SwipeyTheme.typography.title,
                            color = SwipeyTheme.colors.textPrimary,
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
                                // The Bin footer draws the rule that closes the list.
                                divider = index < state.albums.lastIndex,
                            )
                        }
                    }
                }

                !state.loading -> item(key = "empty") { HomeMessage(Copy.HOME_EMPTY, body = Copy.HOME_EMPTY_BODY) }
            }

            item(key = "bin") { BinFooter(binCount, onBin) }
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
 * The foot of a gradient under a caption that sits on a photograph.
 *
 * [com.swipey.app.ui.design.SwipeyColors.scrim]'s 60% is tuned to dim a whole screen
 * behind a sheet; over an arbitrary photograph — which may be a snow field — it leaves
 * white text at roughly 2.5:1. At 90% the title measures ~17:1 and the caption line
 * ~5.5:1 whatever is underneath. It is a gradient rather than a bar so it darkens the
 * picture only where the words are.
 */
private val CaptionScrim = Color.Black.copy(alpha = 0.9f)

// ---------------------------------------------------------------------------
// Header
// ---------------------------------------------------------------------------

@Composable
private fun HomeHeader(showToggle: Boolean, albumsAsGrid: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.lg),
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
            // Takes the slack so the toggle stays pinned right as the wordmark grows with
            // the user's font scale.
            modifier = Modifier.weight(1f),
            style = SwipeyTheme.typography.display,
            color = SwipeyTheme.colors.textPrimary,
        )
        if (showToggle) {
            // The glyph shows the layout the next tap goes *to*, and the description says
            // so out loud — a toggle whose icon means "you are here" and one whose icon
            // means "tap for this" are indistinguishable without it.
            SwipeyIconButton(
                icon = if (albumsAsGrid) SwipeyIcons.ListRows else SwipeyIcons.Grid,
                contentDescription = if (albumsAsGrid) Copy.HOME_SHOW_LIST else Copy.HOME_SHOW_GRID,
                onClick = { onToggle(!albumsAsGrid) },
                size = SwipeySize.touchMin,
                iconSize = 20.dp,
            )
        }
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
 * badges give — a dark-palette disc with dark-palette ink, in both themes — at the app's
 * 48dp floor.
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
            .scale(scale)
            .size(SwipeySize.touchMin)
            .clip(CircleShape)
            .background(CaptionScrim)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        SwipeyIcon(icon, contentDescription = contentDescription, tint = SwipeyDarkColors.textPrimary, size = 20.dp)
    }
}

/**
 * The dark ramp a caption sits on. Flat at [CaptionScrim] over the bottom third, so the
 * words never land on the part of the gradient that is still translucent.
 */
@Composable
private fun BoxScope.CaptionGradient(heightFraction: Float) {
    Box(
        Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth()
            .fillMaxHeight(heightFraction)
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.7f to CaptionScrim,
                    1f to CaptionScrim,
                ),
            ),
    )
}

// ---------------------------------------------------------------------------
// Shuffle
// ---------------------------------------------------------------------------

@Composable
private fun ShuffleRow(first: MediaItem?, onShuffle: () -> Unit) {
    SwipeyRow(
        title = Copy.HOME_SHUFFLE,
        // "Starts on this one" is only said when there is a "this one" to point at. With
        // everything already kept the deck has nothing to deal, and the row says so rather
        // than promising a picture it isn't showing — it stays tappable, because the deck's
        // own all-caught-up state is the honest place to land.
        subtitle = if (first != null) Copy.HOME_SHUFFLE_SUB else Copy.HOME_SHUFFLE_EMPTY,
        onClick = onShuffle,
        leading = { MediaThumb(first?.id, first?.isVideo == true, ThumbSize) },
    )
}

// ---------------------------------------------------------------------------
// Albums
// ---------------------------------------------------------------------------

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
 * The Bin is deliberately last and behind its own rule: it is the only row on this screen
 * that doesn't start a session.
 */
@Composable
private fun BinFooter(binCount: Int, onBin: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = SwipeySpacing.md, bottom = SwipeySpacing.lg),
    ) {
        SwipeyDivider()
        SwipeyRow(
            title = Copy.HOME_BIN,
            trailing = Copy.homeBinSubtitle(binCount),
            onClick = onBin,
            divider = false,
            leading = {
                SwipeyIcon(
                    SwipeyIcons.Bin,
                    // Decorative: the row is titled "Bin" right beside it.
                    contentDescription = null,
                    tint = SwipeyTheme.colors.textSecondary,
                    size = 20.dp,
                )
            },
        )
    }
}
