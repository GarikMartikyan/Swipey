package com.swipey.app.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
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
) : ViewModel() {

    private var session: SwipeSession = SwipeSession(emptyList())
    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state

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
        // for the entire queryAll() I/O window, and permanently if that
        // session happened to be exhausted-with-marks (DeckScreen's terminal
        // LaunchedEffect fires on the stale state and bounces straight to an empty
        // Review before this load() ever lands). `DeckUiState()`'s `loading = true`
        // default only applies to the field's very first value, never to a reload.
        session = SwipeSession(emptyList())
        _state.value = DeckUiState()
        viewModelScope.launch {
            // I4: queryAll() is two MediaStore queries, and a throw from either used to
            // reach viewModelScope's handler and take the process down. It is a pure read —
            // nothing is written before or after it here — so failing to an error state
            // costs nothing but the load itself.
            val ordered = queryCatching {
                val fetched = media.queryAll()
                // Sorting/shuffling up to 20,000 items is pure CPU work; keep it off Main
                // (fix round 1, Important 4) — queryAll() resumes on Main after its own IO
                // hop.
                withContext(Dispatchers.Default) {
                    // Every item, every time. A previous keep does not remove a photograph
                    // from the deck: the queue is the library as it stands, newest first,
                    // and a session is a fresh pass over the whole of it from the top. The
                    // only things missing are the ones MediaStore itself withholds —
                    // trashed items, which `queryAll` excludes because a bare collection
                    // query resolves to MATCH_EXCLUDE.
                    val filtered = fetched.filter { bucketId == null || it.bucketId == bucketId }
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
     * Records a decision against the current card.
     *
     * [itemId] must match the current card. SwipeCard reports the id of the card its
     * commit animation belongs to; if the deck has already moved on for any reason, this
     * is a no-op rather than applying the decision to whatever is now current (fix round 1,
     * Critical 2).
     *
     * Nothing is written to the database. A keep used to upsert a `KEEP` row so that later
     * sessions could filter the photograph out of the deck; the deck no longer filters, so
     * the row had no reader and the write was one Room upsert behind a mutex on every
     * right-swipe — hundreds a session — recording something the app had stopped acting on.
     * A mark is still session-local, as it always was: it becomes durable when the user
     * confirms and [com.swipey.app.data.TrashRepository] writes the trash bookkeeping.
     */
    fun swipe(itemId: Long, keep: Boolean) {
        if (session.current?.id != itemId) return
        if (keep) session.swipeRight() else session.swipeLeft()
        publish()
    }

    fun undo() {
        session.undo() ?: return
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
            // Drawn under the card being swiped, so the deck never reveals bare canvas
            // mid-gesture. Null on the last card, which is correct — there is nothing
            // behind it.
            next = session.peek(1),
            position = session.position,
            total = session.total,
            markedCount = session.markedCount,
            markedBytes = session.markedBytes,
            exhausted = session.isExhausted,
        )
    }
}
