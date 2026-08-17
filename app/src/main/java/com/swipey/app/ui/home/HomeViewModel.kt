package com.swipey.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.HomePreferences
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.ResumePoint
import com.swipey.app.domain.Album
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.mostRecent
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
     * Where the last session stopped, and the photograph it stopped on — resolved against
     * the gallery that was just read, so a bookmark pointing at something since deleted
     * comes back null rather than as an offer that leads nowhere.
     *
     * Null is the ordinary state on a first run, and the Recent tile draws itself as
     * unavailable rather than absent: a control that appears only after you have used the
     * app is one you have to discover twice.
     */
    val resume: ResumeOffer? = null,
)

/**
 * The Recent tile's contents: where to go, and a picture of it.
 *
 * @property item the photograph the last decision was made on. Shown rather than the one
 *   that would be dealt next, because that is the one the user remembers — the next card is
 *   by definition one they have never seen.
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
                        // Resolved against the library as it stands. A bookmark outlives the
                        // photograph it names — the likeliest way to leave a session is to
                        // mark that photograph and commit it — and an offer to carry on from
                        // something that no longer exists is worse than no offer.
                        resume = point
                            ?.let { p -> all.firstOrNull { it.id == p.itemId }?.let { ResumeOffer(p, it) } },
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
