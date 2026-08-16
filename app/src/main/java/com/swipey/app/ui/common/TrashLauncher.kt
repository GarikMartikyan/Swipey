package com.swipey.app.ui.common

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.swipey.app.data.RecoveryReport
import com.swipey.app.data.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a list of consent dialogs one after another, verifying after each.
 *
 * RESULT_OK is never trusted (spec §8): PermissionActivity sets it unconditionally.
 * The truth comes from TrashRepository.verifyAndResolve().
 *
 * F3: `queue`/`index`/`inFlight` are backed by the caller's [MutableState]s (in practice
 * [rememberSaveable] ones from [rememberTrashLauncher]) rather than plain fields, so a
 * fresh `TrashLauncher` built after Activity recreation (rotation, or process death +
 * restore) picks up exactly where the previous instance left off, instead of resetting
 * to an empty queue. `ActivityResultRegistry` persists its pending request across that
 * same recreation and will still deliver a result into whichever `TrashLauncher` exists
 * when it does — without this, that delivery would land on a reset queue and silently
 * drop every chunk after the one already approved, while still reporting a clean finish.
 */
class TrashLauncher(
    private val repository: TrashRepository,
    private val scope: CoroutineScope,
    private val launch: (IntentSenderRequest) -> Unit,
    private val onFinished: (RecoveryReport) -> Unit,
    private val queueState: MutableState<List<PendingIntent>>,
    private val indexState: MutableState<Int>,
    private val inFlightState: MutableState<Boolean>,
) {
    private var queue: List<PendingIntent>
        get() = queueState.value
        set(value) { queueState.value = value }
    private var index: Int
        get() = indexState.value
        set(value) { indexState.value = value }

    // F2: nothing previously guarded re-entry into start() — a second tap mid-sequence
    // (e.g. a slow prepareTrash/prepareRestore round-trip before the first dialog even
    // appears) would reset queue/index under an in-flight sequence and could launch two
    // overlapping consent-dialog chains against the same records. inFlight makes start()
    // a no-op while a sequence is running, cleared on every terminal path (both the
    // empty-queue branch and finish()) so a stuck flag can never wedge the launcher.
    // Fix round 2, Critical 2: the getter is public so a caller-owned "is something in
    // flight" flag (e.g. Review's `committing`) can be reconciled against this one after
    // an Activity recreation, rather than tracking a second flag that only agrees with
    // this one by coincidence. Reading it inside a @Composable is Compose-observable,
    // since it's backed by `inFlightState`, a `rememberSaveable` MutableState. The
    // setter stays private/internal-only — nothing outside this class may flip it.
    var inFlight: Boolean
        get() = inFlightState.value
        private set(value) { inFlightState.value = value }

    /**
     * Task 10 review finding: an empty [requests] list previously left `launchNext()`
     * returning immediately with nothing ever calling `onResult()` — the caller (e.g.
     * the Review screen after Commit) would hang forever with no navigation and no
     * error. `start()` must always terminate in a reported outcome, so the empty case
     * is handled here directly rather than relying on every caller to check first.
     */
    fun start(requests: List<PendingIntent>) {
        if (inFlight) return
        inFlight = true
        queue = requests
        index = 0
        if (queue.isEmpty()) {
            scope.launch { finish(repository.verifyAndResolve()) }
        } else {
            launchNext()
        }
    }

    private fun launchNext() {
        if (index >= queue.size) return
        launch(IntentSenderRequest.Builder(queue[index].intentSender).build())
    }

    suspend fun onResult() {
        index++
        if (index < queue.size) {
            launchNext()
        } else {
            finish(repository.verifyAndResolve())
        }
    }

    /** User cancelled: stop launching further chunks, but still verify what did land. */
    suspend fun onCancelled() {
        index = queue.size
        finish(repository.verifyAndResolve())
    }

    private fun finish(report: RecoveryReport) {
        inFlight = false
        onFinished(report)
    }
}

/** F3: PendingIntent is Parcelable; ArrayList<Parcelable> is Bundle-compatible. */
private val PendingIntentListSaver: Saver<List<PendingIntent>, ArrayList<PendingIntent>> = Saver(
    save = { ArrayList(it) },
    restore = { it.toList() },
)

/**
 * Wires an ActivityResultLauncher for StartIntentSenderForResult to a [TrashLauncher].
 *
 * Ruling R5: the brief lists `rememberTrashLauncher` under "Produces" but never defines
 * it. This composable is that missing piece — it owns the actual
 * `rememberLauncherForActivityResult` call and a coroutine scope, and hands back a
 * [TrashLauncher] whose `start()` can be called from plain (non-composable) callbacks.
 *
 * The activity result's resultCode is used only to decide whether to advance to the
 * next chunk's dialog or stop early — never as proof of success. PermissionActivity
 * sets RESULT_OK unconditionally, so [TrashLauncher.onResult] and
 * [TrashLauncher.onCancelled] both end by calling TrashRepository.verifyAndResolve(),
 * which is the only source of truth (spec §8).
 */
@Composable
fun rememberTrashLauncher(
    repository: TrashRepository,
    onFinished: (RecoveryReport) -> Unit,
): TrashLauncher {
    val scope = rememberCoroutineScope()
    val currentOnFinished = rememberUpdatedState(onFinished)

    // TrashLauncher's constructor needs the ActivityResultLauncher's `launch`, and the
    // ActivityResultLauncher's callback needs to call back into TrashLauncher — a
    // forward reference Compose has no local `lateinit` for. This one-element holder
    // breaks the cycle: the callback closes over the (stable) holder, not the launcher
    // itself, and the launcher is dropped into the holder right after construction.
    val holder = remember { arrayOfNulls<TrashLauncher>(1) }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        scope.launch {
            val launcher = holder[0] ?: return@launch
            if (result.resultCode == Activity.RESULT_OK) {
                launcher.onResult()
            } else {
                launcher.onCancelled()
            }
        }
    }

    // F3: rememberSaveable, not remember — see the class doc for why.
    val queueState = rememberSaveable(stateSaver = PendingIntentListSaver) {
        mutableStateOf<List<PendingIntent>>(emptyList())
    }
    val indexState = rememberSaveable { mutableStateOf(0) }
    val inFlightState = rememberSaveable { mutableStateOf(false) }

    return remember(repository) {
        TrashLauncher(
            repository = repository,
            scope = scope,
            launch = { request -> activityLauncher.launch(request) },
            onFinished = { report -> currentOnFinished.value(report) },
            queueState = queueState,
            indexState = indexState,
            inFlightState = inFlightState,
        ).also { holder[0] = it }
    }
}
