package com.swipey.app.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.db.ReviewedMediaEntity
import com.swipey.app.data.db.SwipeyDatabase
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.SortMode
import com.swipey.app.domain.SwipeSession
import com.swipey.app.domain.shuffledWithSeed
import com.swipey.app.domain.sortedFor
import com.swipey.app.ui.common.queryCatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

data class DeckUiState(
    val loading: Boolean = true,
    val current: MediaItem? = null,
    val next: MediaItem? = null,
    val position: Int = 0,
    val total: Int = 0,
    val markedCount: Int = 0,
    val markedBytes: Long = 0L,
    val exhausted: Boolean = false,
    /**
     * Whole-branch review, I4 (spec §12: "empty state with retry; never crash"). Set when
     * [DeckViewModel.load]'s reads throw. Deliberately distinct from `exhausted` — an
     * empty deck means "you're done here", a failed read means "we couldn't look" — and
     * from `loading`, which without this would otherwise hang on a spinner forever once
     * the throw was caught.
     */
    val failed: Boolean = false,
)

class DeckViewModel(
    private val media: MediaRepository,
    private val db: SwipeyDatabase,
) : ViewModel() {

    private var session: SwipeSession = SwipeSession(emptyList())
    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state

    // Serialises the KEEP upsert (swipe) against its delete (undo): both are fired
    // from independent viewModelScope.launch calls with no ordering guarantee of
    // their own, and an undo racing ahead of its upsert would leave a stale KEEP row
    // that silently excludes the photo forever (fix round 1, Important 5a).
    private val dbMutex = Mutex()

    /**
     * I4: what [retry] replays. The Deck route builds its arguments from nav args and a
     * freshly generated shuffle seed, and calls load() from inside a `remember` that only
     * re-runs when those nav args change — so a retry button has no way to reconstruct
     * them from the call site. Keeping the last set here means retry re-runs exactly the
     * load that failed, same seed included, rather than a subtly different one.
     */
    private var lastLoad: (() -> Unit)? = null

    fun load(bucketId: Long?, sort: SortMode, shuffle: Boolean, seed: Long) {
        lastLoad = { load(bucketId, sort, shuffle, seed) }
        // Fix round 2, Critical 1: this must run synchronously, before the coroutine
        // below ever suspends. DeckViewModel is shared (Activity-scoped) across every
        // Deck entry, and without this reset the *previous* album's session — its
        // current card, its marked count, its exhausted flag — stays live and visible
        // for the entire keptIds()/queryAll() I/O window, and permanently if that
        // session happened to be exhausted-with-marks (DeckScreen's terminal
        // LaunchedEffect fires on the stale state and bounces straight to an empty
        // Review before this load() ever lands). `DeckUiState()`'s `loading = true`
        // default only applies to the field's very first value, never to a reload.
        session = SwipeSession(emptyList())
        _state.value = DeckUiState()
        viewModelScope.launch {
            // I4: keptIds() is a Room read and queryAll() is two MediaStore queries, and a
            // throw from either used to reach viewModelScope's handler and take the process
            // down. Both are pure reads — nothing is written before or after them here — so
            // failing to an error state costs nothing but the load itself.
            val ordered = queryCatching {
                val kept = db.reviewed().keptIds()
                val fetched = media.queryAll()
                // Filtering/sorting/shuffling up to 20,000 items is pure CPU work; keep it
                // off Main (fix round 1, Important 4) — queryAll()/keptIds() both resume
                // on Main after their own IO hop. The .toSet() belongs in here too (Task 20
                // residue finding): it's still O(n) work over a list that can be large, and
                // leaving it outside this block put it back on Main.
                withContext(Dispatchers.Default) {
                    val keptIds = kept.toSet()
                    val filtered = fetched
                        .filter { it.id !in keptIds }
                        .filter { bucketId == null || it.bucketId == bucketId }
                    if (shuffle) filtered.shuffledWithSeed(seed) else filtered.sortedFor(sort)
                }
            }.getOrElse {
                _state.value = DeckUiState(loading = false, failed = true)
                return@launch
            }
            session = SwipeSession(ordered)
            publish()
        }
    }

    /** I4: re-runs the load that failed. See [lastLoad]. */
    fun retry() {
        lastLoad?.invoke()
    }

    /**
     * [itemId] must match the current card. SwipeCard reports the id of the card its
     * commit animation belongs to; if the deck has already moved on for any reason,
     * this is a no-op rather than applying the decision to whatever is now current
     * (fix round 1, Critical 2).
     */
    fun swipe(itemId: Long, keep: Boolean) {
        if (session.current?.id != itemId) return
        val item = if (keep) session.swipeRight() else session.swipeLeft()
        if (keep && item != null) {
            // Persisted immediately so a crash mid-session loses nothing (spec §10).
            viewModelScope.launch {
                dbMutex.withLock {
                    db.reviewed().upsert(ReviewedMediaEntity(item.id, "KEEP", System.currentTimeMillis()))
                }
            }
        }
        publish()
    }

    fun undo() {
        val undone = session.undo() ?: return
        viewModelScope.launch { dbMutex.withLock { db.reviewed().delete(undone.item.id) } }
        publish()
    }

    fun marked(): List<MediaItem> = session.marked()

    fun unmark(id: Long) {
        session.unmark(id)
        publish()
    }

    private fun publish() {
        _state.value = DeckUiState(
            loading = false,
            current = session.current,
            next = null,
            position = session.position,
            total = session.total,
            markedCount = session.markedCount,
            markedBytes = session.markedBytes,
            exhausted = session.isExhausted,
        )
    }
}
