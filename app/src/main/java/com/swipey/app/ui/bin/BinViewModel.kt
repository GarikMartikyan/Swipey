package com.swipey.app.ui.bin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.TrashRepository
import com.swipey.app.domain.BinEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BinUiState(
    val loading: Boolean = true,
    val entries: List<BinEntry> = emptyList(),
    val vanishedCount: Int = 0,
    val otherItemsCount: Int = 0,
    val selected: Set<Long> = emptySet(),
)

class BinViewModel(
    private val repository: TrashRepository,
    private val media: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BinUiState())
    val state: StateFlow<BinUiState> = _state

    /** Reconciles on every open, per spec §8. Also resolves any PENDING_* rows. */
    fun refresh() {
        viewModelScope.launch {
            repository.verifyAndResolve()
            val view = repository.binView()

            // Ruling R7: the design spec requires a footer noting how many other
            // items sit in the system trash that Swipey didn't put there.
            // reconcileBin() only ever emits a BinEntry for a resolution of Keep or
            // MarkTrashed, and both of those require live.isTrashed == true, so every
            // entry here is already counted in queryTrashed()'s MATCH_ONLY row set.
            // What's left over is trash Swipey has no local record for at all — put
            // there by another app (Google Photos, Files, ...). coerceAtLeast(0)
            // guards against the two independent queries racing a state change
            // between them.
            val totalTrashed = media.queryTrashed().size
            val otherItems = (totalTrashed - view.entries.size).coerceAtLeast(0)

            _state.value = BinUiState(
                loading = false,
                entries = view.entries,
                vanishedCount = view.vanished.size,
                otherItemsCount = otherItems,
                selected = emptySet(),
            )
        }
    }

    fun toggle(id: Long) {
        val selected = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (id in selected) selected - id else selected + id,
        )
    }

    fun selectedRecords() = _state.value.entries
        .filter { it.record.mediaId in _state.value.selected }
        .map { it.record }
}
