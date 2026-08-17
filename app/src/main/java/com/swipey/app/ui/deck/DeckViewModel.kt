package com.swipey.app.ui.deck

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.HomePreferences
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.ResumePoint
import com.swipey.app.domain.DecidedItem
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

/**
 * How far the filmstrip can see in either direction from the card on screen.
 *
 * The strip is centred, so it reaches equally forward and back and the screen decides how
 * much of that it actually draws — five either side on a handset, more on anything wider.
 * Ten is the ceiling that keeps a foldable filled without asking a session of twenty
 * thousand for more thumbnails than any screen could show.
 */
private const val FilmstripReach = 10

data class DeckUiState(
    val loading: Boolean = true,
    val current: MediaItem? = null,
    val next: MediaItem? = null,
    /**
     * The current card and the few behind it, for the filmstrip. Index 0 is what is on
     * screen now, so the strip reads as "you are here, and this is what follows" rather
     * than as a separate list that happens to start one item later.
     */
    val upcoming: List<MediaItem> = emptyList(),
    /**
     * The filmstrip's other half: the few items the deck has already passed, oldest first,
     * each with what is currently true of it. Derived from the session's marks rather than
     * from its swipe history, so an item re-decided from the grid reads the same in the
     * strip as it does in the grid — see [SwipeSession.decided].
     */
    val decided: List<DecidedItem> = emptyList(),
    /** Every item in the session, in queue order. The grid's list. */
    val items: List<MediaItem> = emptyList(),
    /** Which of [items] are marked. Held here so the grid and the deck cannot disagree. */
    val markedIds: Set<Long> = emptySet(),
    /**
     * Which of [items] have been explicitly kept.
     *
     * The filmstrip's other half reads this. Marked and kept are two sets rather than one
     * flag because there is a third state — not yet judged — and it is reachable on both
     * sides of the current card now that the grid can start the deck anywhere.
     */
    val keptIds: Set<Long> = emptySet(),
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
    private val preferences: HomePreferences,
) : ViewModel() {

    private var session: SwipeSession = SwipeSession(emptyList())
    private val _state = MutableStateFlow(DeckUiState())
    val state: StateFlow<DeckUiState> = _state

    /**
     * Which queue this session is, so a decision can be written down as a place to come
     * back to. Set by [load] and read by [remember]; null before the first load, which is
     * the window in which there is nothing to remember anyway.
     */
    private var queueIdentity: ResumePoint? = null

    /**
     * I4: what [retry] replays. The Deck route builds its arguments from nav args and a
     * freshly generated shuffle seed, and calls load() from inside a `remember` that only
     * re-runs when those nav args change — so a retry button has no way to reconstruct
     * them from the call site. Keeping the last set here means retry re-runs exactly the
     * load that failed, same seed included, rather than a subtly different one.
     */
    private var lastLoad: (() -> Unit)? = null

    /**
     * @param startAfterId opens on the card following this one rather than at the top of the
     *   queue — Home's "Recent", carrying the user back to where they stopped. Ignored if the
     *   item is no longer in the queue, which then opens at the top. Home relies on that:
     *   when the photograph the bookmark named has been deleted and nothing in the queue
     *   preceded it, "deal after a card that isn't there" is precisely how it asks for the
     *   top. See `HomeViewModel.resolveResume`.
     */
    fun load(bucketId: Long?, sort: SortMode, shuffle: Boolean, seed: Long, startAfterId: Long? = null) {
        lastLoad = { load(bucketId, sort, shuffle, seed, startAfterId) }
        queueIdentity = ResumePoint(
            // Stands in until the first decision replaces it. Nothing reads the id before
            // then — `remember` overwrites it on every swipe.
            itemId = 0L,
            bucketId = bucketId,
            sort = sort.name,
            shuffle = shuffle,
            seed = seed,
        )
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
            // After the queue exists, because "the card after that one" is a fact about
            // this ordering and not about the library.
            if (startAfterId != null) session.startAfter(startAfterId)
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
        remember(itemId)
        publish()
    }

    /**
     * Writes down the photograph just decided on, as somewhere to come back to.
     *
     * Keyed to the *decision* rather than to the card on screen, which is the distinction
     * the user drew: scrolling the grid past a hundred pictures is looking, and a bookmark
     * that followed the eye would offer to resume somewhere they had never judged. Only
     * [swipe] and the grid's tick call this.
     *
     * Off the main thread because the very first touch of the preferences file is a
     * blocking disk read, and the deck is one of the two screens that can be reached before
     * Home has warmed it. Fire-and-forget: a bookmark that loses its last entry to a kill
     * costs a card of accuracy in an offer the user can decline.
     */
    private fun remember(itemId: Long) {
        val identity = queueIdentity ?: return
        // The whole photograph, not just its id: the bookmark records where it sat in this
        // queue's order so that Home can still find that position after the photograph
        // itself has been trashed — which is the ordinary end of a session. See
        // `HomePreferences.ResumePoint` and `domain/ResumeAnchor.kt`.
        val item = session.itemFor(itemId) ?: return
        viewModelScope.launch(Dispatchers.IO) {
            preferences.resumePoint = identity.copy(
                itemId = item.id,
                itemDateSec = item.dateAddedSec,
                itemSizeBytes = item.sizeBytes,
            )
        }
    }

    /**
     * Deals from [itemId], wherever it sits in the queue. The grid's tap on a photograph.
     *
     * Nothing is decided and nothing is remembered: jumping is navigation, and the bookmark
     * follows decisions. A jump forward leaves everything it passes undecided — see
     * [SwipeSession.jumpTo], and the filmstrip, which draws those as untouched rather than
     * as kept.
     */
    fun jumpTo(itemId: Long) {
        if (!session.jumpTo(itemId)) return
        publish()
    }

    fun undo() {
        session.undo() ?: return
        publish()
    }

    fun marked(): List<MediaItem> = session.marked()

    /**
     * The grid's edit: marks or unmarks any item without moving the deck.
     *
     * Republishes, so the deck's counter and chip agree with the grid the moment the tap
     * lands — they are two views of one set, and the only way they can disagree is if one
     * of them keeps its own copy.
     */
    fun setMarked(itemId: Long, marked: Boolean) {
        session.setMarked(itemId, marked)
        // A tick in the grid is a decision, so it moves the bookmark exactly as a swipe
        // does. Anything else would make "carry on" mean something different depending on
        // which of the two ways the user had judged the photograph.
        remember(itemId)
        publish()
    }

    fun unmark(id: Long) {
        session.unmark(id)
        publish()
    }

    /**
     * The items are in the phone's trash now, so they leave the session too.
     *
     * Called once a commit has been confirmed. The commit itself now ends the trip — the
     * user is sent to Home rather than back onto the card they broke off from — so nothing
     * renders this session again before the next [load] replaces it outright. It is kept
     * because a ViewModel that goes on listing photographs MediaStore will no longer serve
     * is a lie waiting for a second reader; see [SwipeSession.drop].
     */
    fun dropCommitted(ids: Set<Long>) {
        if (ids.isEmpty()) return
        session.drop(ids)
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
            upcoming = (0..FilmstripReach).mapNotNull { session.peek(it) },
            decided = session.decided(FilmstripReach),
            items = session.items,
            markedIds = session.markedIds,
            keptIds = session.keptIds,
            position = session.position,
            total = session.total,
            markedCount = session.markedCount,
            markedBytes = session.markedBytes,
            exhausted = session.isExhausted,
        )
    }
}
