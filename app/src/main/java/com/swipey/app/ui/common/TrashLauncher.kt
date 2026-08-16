package com.swipey.app.ui.common

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.swipey.app.data.RecoveryReport
import com.swipey.app.data.TrashRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Runs a list of consent dialogs one after another, verifying after each.
 *
 * RESULT_OK is never trusted (spec §8): PermissionActivity sets it unconditionally.
 * The truth comes from TrashRepository.verifyAndResolve().
 */
class TrashLauncher(
    private val repository: TrashRepository,
    private val scope: CoroutineScope,
    private val launch: (IntentSenderRequest) -> Unit,
    private val onFinished: (RecoveryReport) -> Unit,
) {
    private var queue: List<PendingIntent> = emptyList()
    private var index = 0

    /**
     * Task 10 review finding: an empty [requests] list previously left `launchNext()`
     * returning immediately with nothing ever calling `onResult()` — the caller (e.g.
     * the Review screen after Commit) would hang forever with no navigation and no
     * error. `start()` must always terminate in a reported outcome, so the empty case
     * is handled here directly rather than relying on every caller to check first.
     */
    fun start(requests: List<PendingIntent>) {
        queue = requests
        index = 0
        if (queue.isEmpty()) {
            scope.launch { onFinished(repository.verifyAndResolve()) }
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
            onFinished(repository.verifyAndResolve())
        }
    }

    /** User cancelled: stop launching further chunks, but still verify what did land. */
    suspend fun onCancelled() {
        index = queue.size
        onFinished(repository.verifyAndResolve())
    }
}

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

    return remember(repository) {
        TrashLauncher(
            repository = repository,
            scope = scope,
            launch = { request -> activityLauncher.launch(request) },
            onFinished = { report -> currentOnFinished.value(report) },
        ).also { holder[0] = it }
    }
}
