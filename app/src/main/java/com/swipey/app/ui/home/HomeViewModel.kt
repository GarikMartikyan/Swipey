package com.swipey.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.HomePreferences
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.domain.Album
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.mostRecent
import com.swipey.app.domain.shuffledWithSeed
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
 * @property totalCount every image and video, which is what "All media" means. Not
 *   filtered by what has already been kept: the caption says "All media", and a count that
 *   silently shrank as the user worked would be describing something else.
 * @property shuffleSeed the seed [shuffleFirst] was resolved with, and the seed the deck
 *   must be handed. These two travel together or the thumbnail is a lie — see [load].
 * @property shuffleFirst the item a shuffle started now would genuinely open on, or `null`
 *   when nothing is left to shuffle.
 * @property albumsAsGrid the persisted list/grid choice. Kept out of [load]'s write path
 *   (every update here is a `copy`) so a reload can never revert a toggle mid-flight.
 */
data class HomeUiState(
    val loading: Boolean = true,
    val failed: Boolean = false,
    val totalCount: Int = 0,
    val newest: MediaItem? = null,
    val albums: List<Album> = emptyList(),
    val shuffleSeed: Long = 0L,
    val shuffleFirst: MediaItem? = null,
    val albumsAsGrid: Boolean = false,
)

/**
 * Home's data, hoisted off the screen.
 *
 * Home used to need none — it was three static rows — and now it needs the whole gallery:
 * a hero, a cover per album, and the first card of a shuffle. That is one
 * [MediaRepository.queryAll] (~37ms for 2,573 items on the target device) plus one
 * `keptIds()` read, and it happens **here**, once per visit, rather than in three
 * `LaunchedEffect`s that would each pay for it again. Grouping, sorting and shuffling the
 * result is pure CPU work over a list that can run to five figures, so it goes to
 * [Dispatchers.Default]; the queries do their own IO hop internally.
 */
class HomeViewModel(
    private val media: MediaRepository,
    private val db: SwipeyDatabase,
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
     * Reads the gallery and resolves all three thumbnails.
     *
     * ### Why the shuffle is computed here and not at the tap
     * The deck deals `queryAll()` minus everything already kept, then shuffles what is
     * left with the seed it was given (`DeckViewModel.load`). So the item a shuffle opens
     * on is the first element of the **filtered** list's shuffle — not the first element
     * of the gallery's. Home applies the identical filter to the identical list from the
     * identical query, shuffles it with [seed], and keeps that seed in state so the tap can
     * hand the *same* one to the deck. Generate a fresh seed at navigation time instead and
     * the thumbnail stops matching the moment the user has kept a single photo.
     *
     * Two things this must not become: a cheaper "just pick the first unreviewed item"
     * (that is a different item), and a clever way to compute element 0 without shuffling
     * (that is a second implementation of an ordering the deck defines). Same list, same
     * filter, same call.
     */
    fun load(seed: Long = System.currentTimeMillis()) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, failed = false) }
            // Spec §12: a MediaStore or Room throw becomes a retryable error state, never a
            // crash. Both calls are pure reads, so failing costs nothing but the load.
            val loaded = queryCatching {
                val kept = db.reviewed().keptIds()
                val all = media.queryAll()
                withContext(Dispatchers.Default) {
                    val keptIds = kept.toSet()
                    Loaded(
                        totalCount = all.size,
                        newest = all.mostRecent(),
                        albums = all.toAlbums(),
                        shuffleFirst = all
                            .filter { it.id !in keptIds }
                            .shuffledWithSeed(seed)
                            .firstOrNull(),
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
                    shuffleSeed = seed,
                    shuffleFirst = loaded.shuffleFirst,
                )
            }
        }
    }

    /** Replays the load that failed, with a new seed — nothing was shown to contradict. */
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

    /** The four fields [load] computes together, so they can only be published together. */
    private class Loaded(
        val totalCount: Int,
        val newest: MediaItem?,
        val albums: List<Album>,
        val shuffleFirst: MediaItem?,
    )
}
