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

    fun load(bucketId: Long?, sort: SortMode, shuffle: Boolean, seed: Long) {
        viewModelScope.launch {
            val kept = db.reviewed().keptIds()
            val fetched = media.queryAll()
            // Filtering/sorting/shuffling up to 20,000 items is pure CPU work; keep it
            // off Main (fix round 1, Important 4) — queryAll()/keptIds() both resume
            // on Main after their own IO hop. The .toSet() belongs in here too (Task 20
            // residue finding): it's still O(n) work over a list that can be large, and
            // leaving it outside this block put it back on Main.
            val ordered = withContext(Dispatchers.Default) {
                val keptIds = kept.toSet()
                val filtered = fetched
                    .filter { it.id !in keptIds }
                    .filter { bucketId == null || it.bucketId == bucketId }
                if (shuffle) filtered.shuffledWithSeed(seed) else filtered.sortedFor(sort)
            }
            session = SwipeSession(ordered)
            publish()
        }
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
