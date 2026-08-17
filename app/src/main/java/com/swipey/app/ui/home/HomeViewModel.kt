package com.swipey.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.HomePreferences
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.ResumePoint
import com.swipey.app.domain.Album
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.QueuePlace
import com.swipey.app.domain.SortMode
import com.swipey.app.domain.mostRecent
import com.swipey.app.domain.resumeAnchor
import com.swipey.app.domain.toAlbums
import com.swipey.app.ui.common.queryCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Everything Home draws, resolved from one pass over the gallery.
 *
 * @property newest the most recent item on the device — the hero's thumbnail.
 * @property totalCount every image and video, which is what "All media" means — and now
 *   also exactly what the deck will queue, since the deck no longer withholds anything the
 *   user has already swiped.
 * @property albumsAsGrid the persisted list/grid choice. Kept out of [load]'s write path
 *   (every update here is a `copy`) so a reload can never revert a toggle mid-flight.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val totalCount: Int = 0,
    val newest: MediaItem? = null,
    val albums: List<Album> = emptyList(),
    val albumsAsGrid: Boolean = false,
    /**
     * Where the last session stopped, and a photograph of it — resolved against the gallery
     * that was just read, so the offer always leads somewhere that exists.
     *
     * Null now means only one thing: there is no queue left to resume. On a first run, and
     * when the album the bookmark named has been emptied. A bookmark whose *own* photograph
     * has gone is no longer null — see [resolveResume].
     *
     * The Recent tile draws null as unavailable rather than absent: a control that appears
     * only after you have used the app is one you have to discover twice.
     */
    val resume: ResumeOffer? = null,
)

/**
 * The Recent tile's contents: where to go, and a picture of it.
 *
 * @property item the photograph to show. The one the last decision was made on while it is
 *   still there — shown rather than the one that would be dealt next, because that is the
 *   one the user remembers — and its nearest surviving neighbour once it is not.
 * @property point the queue to deal and the card to deal *after*. Its `itemId` is the
 *   bookmarked photograph while that photograph exists, and a surviving stand-in for its
 *   position once it does not; either way it names the card the deck resumes behind. See
 *   [resolveResume].
 */
data class ResumeOffer(val point: ResumePoint, val item: MediaItem)

/**
 * Home's data, hoisted off the screen.
 *
 * Home used to need none — it was three static rows — and now it needs the whole gallery:
 * a hero, a cover per album, and the first card of a shuffle. That is one
 * [MediaRepository.queryAll] (~37ms for 2,573 items on the target device), and it happens
 * **here**, once per visit, rather than in three `LaunchedEffect`s that would each pay for
 * it again. No database read at all: nothing on Home depends on what has been swiped
 * before. Grouping, sorting and shuffling the result is pure CPU work over a list that can
 * run to five figures, so it goes to [Dispatchers.Default]; the query does its own IO hop
 * internally.
 */
class HomeViewModel(
    private val media: MediaRepository,
    private val preferences: HomePreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state

    /**
     * Home reloads on every entry (a commit or a restore elsewhere changes what it should
     * say), so two loads can overlap when the user leaves and returns quickly. Cancelling
     * the previous one keeps a slow first pass from landing on top of a fresh second and
     * putting a stale seed back into state.
     */
    private var loadJob: Job? = null

    init {
        // Off Main: the first SharedPreferences read blocks on the file being parsed.
        viewModelScope.launch {
            val grid = withContext(Dispatchers.IO) { preferences.albumsAsGrid }
            _state.update { it.copy(albumsAsGrid = grid) }
        }
    }

    /**
     * Reads the gallery and resolves what Home draws.
     *
     * No shuffle happens here any more. It used to, because the Shuffle row showed the
     * photograph that row would open on and the only way to know which one that was, was to
     * run the deck's exact shuffle and take element zero — a full pass over a list that can
     * run to five figures, on every visit to Home, to produce one thumbnail. The row shows
     * a glyph now, so the shuffle happens once, in the deck, when it is actually needed.
     */
    fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            // Spec §12: a MediaStore or Room throw becomes a retryable error state, never a
            // crash. Both calls are pure reads, so failing costs nothing but the load.
            // Read alongside the gallery rather than in `init`, because it changes while
            // Home is off screen — every swipe of the session the user just came back from
            // moved it. The first touch of the file blocks on parsing it, hence the hop.
            val point = withContext(Dispatchers.IO) { preferences.resumePoint }

            val loaded = queryCatching {
                val all = media.queryAll()
                withContext(Dispatchers.Default) {
                    Loaded(
                        totalCount = all.size,
                        newest = all.mostRecent(),
                        albums = all.toAlbums(),
                        resume = point?.let { resolveResume(it, all) },
                    )
                }
            }.getOrElse {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            _state.update {
                it.copy(
                    loading = false,
                    failed = false,
                    totalCount = loaded.totalCount,
                    newest = loaded.newest,
                    albums = loaded.albums,
                    resume = loaded.resume,
                )
            }
        }
    }

    /** Replays the load that failed. */
    fun retry() = load()

    fun setAlbumsAsGrid(grid: Boolean) {
        if (_state.value.albumsAsGrid == grid) return
        // Shown immediately; persisted off Main behind it. The toggle is instant either
        // way, and a write lost to a kill in that window costs one tap.
        _state.update { it.copy(albumsAsGrid = grid) }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { preferences.albumsAsGrid = grid }
        }
    }

    /** The fields [load] computes together, so they can only be published together. */
    private class Loaded(
        val totalCount: Int,
        val newest: MediaItem?,
        val albums: List<Album>,
        val resume: ResumeOffer?,
    )
}

/**
 * Turns the bookmark into an offer, against the library as it stands.
 *
 * ### The photograph is usually gone
 * This used to be one `firstOrNull { it.id == point.itemId }`, and it went null on the
 * commonest path there is: the bookmark follows the user's last *decision*, the last
 * decision of a session is very often a mark, and a mark is a photograph on its way to the
 * bin. Delete what you were looking at and the tile that exists to take you back to it went
 * dark — and worse than dark, because the deck's `startAfter` also silently fails on a
 * missing id and deals from the top of the queue instead.
 *
 * So a dead bookmark is now resolved to its nearest surviving neighbour instead: the card
 * that *followed* it, which is where the user was going next, and failing that the card
 * before it, which is the end of a queue they have finished. Only an empty queue is null
 * now. See [resumeAnchor], which does the finding.
 *
 * ### The neighbour comes from the bookmarked queue, not the gallery
 * A resume into one album that offered a picture from another would be describing a queue
 * the tap will not deal. The filter is the same one the deck applies to build that queue.
 */
internal fun resolveResume(point: ResumePoint, all: List<MediaItem>): ResumeOffer? {
    val queue = if (point.bucketId == null) all else all.filter { it.bucketId == point.bucketId }

    val date = point.itemDateSec
    val size = point.itemSizeBytes
    // A bookmark from before the place was recorded can still be honoured exactly, and
    // cannot be honoured approximately — there is nothing to measure a neighbour against.
    // One visit's worth of the old behaviour, until the next decision rewrites it.
    if (date == null || size == null) {
        return queue.firstOrNull { it.id == point.itemId }?.let { ResumeOffer(point, it) }
    }

    val anchor = queue.resumeAnchor(
        place = QueuePlace(point.itemId, date, size),
        // A shuffle has no order that survives a deletion: the same seed over a library one
        // picture shorter is a different permutation, so there is no "the card after that
        // one" left to find. Date order stands in, and the offer degrades to "a picture from
        // near where you stopped" — which is all a resumed shuffle was ever able to promise,
        // since the deck reshuffles the surviving library on arrival either way.
        order = if (point.shuffle) SortMode.NEWEST else point.sortMode(),
    ) ?: return null

    // Null `after` means nothing in the queue precedes the bookmark any more, so there is
    // no card to deal behind. Keeping the dead id is how that is said: the deck's
    // `startAfter` finds nothing and opens at the top, which is exactly the position the
    // bookmark named.
    return ResumeOffer(point.copy(itemId = anchor.after?.id ?: point.itemId), anchor.item)
}

/**
 * The bookmark's sort, or newest-first if the file holds something that is not one.
 *
 * `SortMode.valueOf` would throw, and this runs inside `queryCatching` — so a single
 * unrecognised string would take Home to its "we couldn't look" state, blaming a gallery
 * read for a bookmark nobody can parse.
 */
private fun ResumePoint.sortMode(): SortMode =
    SortMode.entries.firstOrNull { it.name == sort } ?: SortMode.NEWEST
