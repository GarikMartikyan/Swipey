package com.swipey.app.ui.bin

import android.app.PendingIntent
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swipey.app.data.MediaRepository
import com.swipey.app.data.RecoveryReport
import com.swipey.app.data.TrashRepository
import com.swipey.app.domain.BinEntry
import com.swipey.app.domain.MediaAccess
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.queryCatching
import com.swipey.app.ui.permission.currentMediaAccess
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
    // F2: the UX half of the double-restore guard — BinScreen disables the Restore
    // button while this is true. TrashLauncher's own inFlight flag is the correctness
    // half (a no-op re-entry into start()); this is what stops the second tap.
    val restoring: Boolean = false,
    /**
     * The delete-side twin of [restoring], and separate from it on purpose: the two
     * buttons disable together while either is in flight, but the screen has to be able to
     * say *which* irreversible thing it is in the middle of, and a shared flag cannot.
     */
    val deleting: Boolean = false,
    /**
     * Whole-branch review, I4 (spec §12: "empty state with retry; never crash"). Distinct
     * from an empty [entries] list: "your bin is empty" and "we couldn't read your bin"
     * are opposite claims, and only one of them is safe to make after a query threw. The
     * grid is suppressed while this is set so a stale or empty selection can't be
     * restored against state nobody managed to read.
     */
    val failed: Boolean = false,
)

class BinViewModel(
    // Public: BinScreen needs this to build its own rememberTrashLauncher for the
    // restore flow (see Task 10 review finding below) — one TrashRepository instance,
    // not a second one Task 20 could accidentally pass in mismatched.
    val repository: TrashRepository,
    private val media: MediaRepository,
    // F1: needed to guard the "other items" footer the same way 371056c guarded
    // verifyAndResolve()/binView() — see reload(). Expected to be the application
    // context, mirroring TrashRepository's own Context param.
    private val context: Context,
) : ViewModel() {

    private val _state = MutableStateFlow(BinUiState())
    val state: StateFlow<BinUiState> = _state

    /** Reconciles on every open, per spec §8. Also resolves any PENDING_* rows. */
    fun refresh() {
        viewModelScope.launch {
            // I4: verifyAndResolve() and reload() are both MediaStore + Room round trips,
            // and an uncaught throw from either reached viewModelScope's handler and killed
            // the process — on the one screen whose whole job is showing the user that
            // their photos are still recoverable. verifyAndResolve() reads every live row
            // before writing anything, so a throw leaves the records untouched and this is
            // purely a display failure.
            queryCatching {
                repository.verifyAndResolve()
                reload(restoreMessage = null)
            }.onFailure { _state.value = BinUiState(loading = false, failed = true) }
        }
    }

    fun toggle(id: Long) {
        val selected = _state.value.selected
        _state.value = _state.value.copy(
            selected = if (id in selected) selected - id else selected + id,
        )
    }

    /**
     * Selects every item the Bin is currently showing, or clears the selection if that is
     * already all of them.
     *
     * Scoped to [BinUiState.entries] rather than to "everything in the phone's trash",
     * which is the distinction the footer exists to draw: items other apps trashed are
     * counted there but never listed here, and a Select all that silently reached them
     * would hand the Delete button things the user has never seen.
     */
    fun toggleSelectAll() {
        val entries = _state.value.entries
        val all = entries.map { it.record.mediaId }.toSet()
        _state.value = _state.value.copy(
            selected = if (_state.value.selected.size == all.size) emptySet() else all,
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
        _state.value = _state.value.copy(restoring = true)
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
            // I4: reload() queries MediaStore twice. This is the launcher's terminal
            // callback, so a throw here would crash right after a restore dialog — the
            // restore itself already happened and was already recorded by
            // verifyAndResolve(); only the redraw failed.
            queryCatching { reload(restoreMessage = message) }
                .onFailure { _state.value = BinUiState(loading = false, failed = true) }
        }
    }

    /**
     * Fix round 2, Critical 2 (restore side): `beginRestore()` sets `restoring = true`
     * before its suspend work, same as the trash-side commit path — if that work throws
     * (e.g. `prepareRestore`'s Room write), `TrashLauncher.start()` is never reached and
     * nothing else would ever clear the flag, leaving the grid permanently disabled.
     * The call site (BinScreen) catches that failure and calls this to reset state and
     * report it honestly, reusing the existing `restoreMessage` slot rather than adding
     * new UI.
     */
    fun onRestoreFailed() {
        _state.value = _state.value.copy(restoring = false, restoreMessage = Copy.RESTORE_FAILED)
    }

    /**
     * Builds the consent-dialog requests that permanently delete the current selection.
     *
     * No durable row is written first, unlike [beginRestore] — see
     * [TrashRepository.buildDeleteRequests] for why a deletion needs no bookkeeping to be
     * verifiable afterwards. The flag is still set before the binder work, for the same
     * reason the restore side sets it: the window between this tap and the dialog appearing
     * is long enough to tap again.
     */
    fun beginDelete(): List<PendingIntent> {
        val records = selectedRecords()
        if (records.isEmpty()) return emptyList()
        _state.value = _state.value.copy(deleting = true)
        return repository.buildDeleteRequests(records)
    }

    /**
     * The delete flow's terminal callback.
     *
     * Counted from [RecoveryReport.vanished] — the ids the reconciliation pass could no
     * longer find live rows for — and never from the dialog's result code, which
     * MediaProvider sets to OK regardless. That is also why a part-approved batch reports
     * honestly: whatever survived is still in the list behind this message.
     */
    fun onDeleteFinished(attemptedIds: List<Long>, report: RecoveryReport) {
        viewModelScope.launch {
            val message = if (attemptedIds.isEmpty()) {
                null
            } else {
                Copy.deleteOutcome(attemptedIds.count { it in report.vanished }, attemptedIds.size)
            }
            queryCatching { reload(restoreMessage = message) }
                .onFailure { _state.value = BinUiState(loading = false, failed = true) }
        }
    }

    /** The delete-side equivalent of [onRestoreFailed]. */
    fun onDeleteFailed() {
        _state.value = _state.value.copy(deleting = false, restoreMessage = Copy.DELETE_FAILED)
    }

    /** Re-reads reconciled state. Does not itself call verifyAndResolve() — see call sites. */
    private suspend fun reload(restoreMessage: String?) {
        val view = repository.binView()

        // Ruling R7 / F1 (Task 10 review): footer noting how many other items sit in
        // the system trash that Swipey didn't put there. reconcileBin() only emits a
        // BinEntry for a resolution of Keep or MarkTrashed, and both require
        // live.isTrashed == true, so every entry is already one of the rows
        // queryTrashed() (MATCH_ONLY) returns; the remainder is trash Swipey has no
        // local record for at all. coerceAtLeast(0) guards the two independent
        // queries racing a state change.
        //
        // binView() (371056c) now returns empty rather than querying when access
        // isn't FULL — but MediaRepository.queryTrashed() has no such guard. Without
        // this check, under PARTIAL access queryTrashed() still returns rows while
        // view.entries is forced to empty, so the subtraction would attribute every
        // row — including Swipey's own trashed items, for which it still holds
        // records — to "other apps". Under DENIED access queryTrashed() would throw
        // SecurityException outright. So: no FULL access, no footer, no query.
        val otherItems = if (currentMediaAccess(context) == MediaAccess.FULL) {
            (media.queryTrashed().size - view.entries.size).coerceAtLeast(0)
        } else {
            0
        }

        _state.value = BinUiState(
            loading = false,
            entries = view.entries,
            vanishedCount = view.vanished.size,
            otherItemsCount = otherItems,
            selected = emptySet(),
            restoreMessage = restoreMessage,
            restoring = false,
            deleting = false,
        )
    }
}
