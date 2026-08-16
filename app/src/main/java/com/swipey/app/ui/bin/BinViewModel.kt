package com.swipey.app.ui.bin

import android.app.PendingIntent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.RecoveryReport
import com.swipey.app.data.TrashRepository
import com.swipey.app.domain.BinEntry
import com.swipey.app.ui.common.Copy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class BinUiState(
    val loading: Boolean = true,
    val entries: List<BinEntry> = emptyList(),
    val vanishedCount: Int = 0,
    val otherItemsCount: Int = 0,
    val selected: Set<Long> = emptySet(),
    val restoreMessage: String? = null,
)

class BinViewModel(
    // Public: BinScreen needs this to build its own rememberTrashLauncher for the
    // restore flow (see Task 10 review finding below) — one TrashRepository instance,
    // not a second one Task 20 could accidentally pass in mismatched.
    val repository: TrashRepository,
    private val media: MediaRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BinUiState())
    val state: StateFlow<BinUiState> = _state

    /** Reconciles on every open, per spec §8. Also resolves any PENDING_* rows. */
    fun refresh() {
        viewModelScope.launch {
            repository.verifyAndResolve()
            reload(restoreMessage = null)
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

    /**
     * Marks the current selection PENDING_RESTORE and builds one consent-dialog request
     * per chunk, via TrashRepository.prepareRestore — which collapses the mark-then-build
     * steps into one suspend call so the durable row is always committed before any
     * PendingIntent can be obtained (mirrors the trash-side ordering guarantee).
     */
    suspend fun beginRestore(): List<PendingIntent> {
        val records = selectedRecords()
        if (records.isEmpty()) return emptyList()
        return repository.prepareRestore(records)
    }

    /**
     * The restore flow's terminal callback (called from BinScreen's TrashLauncher).
     *
     * Task 10 review finding: this deliberately does NOT hand [report] on for
     * navigation to ResultScreen. A cancelled or partially-declined restore still
     * reports those ids in RecoveryReport.confirmedTrashed — that field means
     * "currently trashed", not "just trashed by this action" — so routing it to
     * ResultScreen would tell the user "N items moved to trash" immediately after
     * they cancelled a restore. Restore stays on the Bin: the grid refreshes itself
     * from the DB state TrashLauncher already committed via verifyAndResolve(), plus
     * an honest per-item message (spec §9 rule 6) scoped to just the ids this
     * attempt touched.
     */
    fun onRestoreFinished(attemptedIds: List<Long>, report: RecoveryReport) {
        viewModelScope.launch {
            val message = if (attemptedIds.isEmpty()) {
                null
            } else {
                Copy.restoreOutcome(attemptedIds.count { it in report.restored }, attemptedIds.size)
            }
            reload(restoreMessage = message)
        }
    }

    /** Re-reads reconciled state. Does not itself call verifyAndResolve() — see call sites. */
    private suspend fun reload(restoreMessage: String?) {
        val view = repository.binView()

        // Ruling R7: footer noting how many other items sit in the system trash that
        // Swipey didn't put there. reconcileBin() only emits a BinEntry for a
        // resolution of Keep or MarkTrashed, and both require live.isTrashed == true,
        // so every entry is already one of the rows queryTrashed() (MATCH_ONLY)
        // returns; the remainder is trash Swipey has no local record for at all.
        // coerceAtLeast(0) guards the two independent queries racing a state change.
        val totalTrashed = media.queryTrashed().size
        val otherItems = (totalTrashed - view.entries.size).coerceAtLeast(0)

        _state.value = BinUiState(
            loading = false,
            entries = view.entries,
            vanishedCount = view.vanished.size,
            otherItemsCount = otherItems,
            selected = emptySet(),
            restoreMessage = restoreMessage,
        )
    }
}
