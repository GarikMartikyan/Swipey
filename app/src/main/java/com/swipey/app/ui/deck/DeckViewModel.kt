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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

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

    fun load(bucketId: Long?, sort: SortMode, shuffle: Boolean, seed: Long) {
        viewModelScope.launch {
            val kept = db.reviewed().keptIds().toSet()
            val all = media.queryAll()
                .filter { it.id !in kept }
                .filter { bucketId == null || it.bucketId == bucketId }
            val ordered = if (shuffle) all.shuffledWithSeed(seed) else all.sortedFor(sort)
            session = SwipeSession(ordered)
            publish()
        }
    }

    fun swipe(keep: Boolean) {
        val item = if (keep) session.swipeRight() else session.swipeLeft()
        if (keep && item != null) {
            // Persisted immediately so a crash mid-session loses nothing (spec §10).
            viewModelScope.launch {
                db.reviewed().upsert(ReviewedMediaEntity(item.id, "KEEP", System.currentTimeMillis()))
            }
        }
        publish()
    }

    fun undo() {
        val undone = session.undo() ?: return
        viewModelScope.launch { db.reviewed().delete(undone.item.id) }
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
