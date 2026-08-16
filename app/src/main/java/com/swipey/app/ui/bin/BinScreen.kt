package com.swipey.app.ui.bin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.common.rememberTrashLauncher
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BinScreen(viewModel: BinViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { viewModel.refresh() }

    // Task 10 review finding: the restore flow must never render ResultScreen (see
    // BinViewModel.onRestoreFinished for why). It is entirely self-contained here —
    // launch, verify, and land back on this same Bin, refreshed. There is no
    // `onRestore` callback for a caller to misroute; Task 20 has nothing to wire wrong.
    var attemptedIds by remember { mutableStateOf<List<Long>>(emptyList()) }
    val trashLauncher = rememberTrashLauncher(repository = viewModel.repository) { report ->
        viewModel.onRestoreFinished(attemptedIds, report)
    }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(Copy.BIN_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        state.restoreMessage?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }

        if (state.vanishedCount > 0) {
            // Ruling R14: vanished covers every non-entry outcome, including items
            // restored elsewhere (e.g. in Google Photos), not just items genuinely
            // gone from the device. Copy.vanishedNotice is worded to be accurate for
            // both causes — it must not be reworded to imply destruction.
            Text(Copy.vanishedNotice(state.vanishedCount), style = MaterialTheme.typography.bodySmall)
        }

        // I4 / spec §12. Checked before the empty branch: "Nothing here" after a query
        // threw would tell the user their trashed photos are gone, which is both false and
        // the single most alarming thing this screen could say.
        if (state.failed) {
            Column(
                Modifier.weight(1f).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(Copy.BIN_LOAD_FAILED)
                Button(onClick = { viewModel.refresh() }) { Text(Copy.RETRY) }
            }
        } else if (state.entries.isEmpty() && !state.loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Copy.BIN_EMPTY)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f)) {
                items(state.entries, key = { it.record.mediaId }) { entry ->
                    val selected = entry.record.mediaId in state.selected
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clickable { viewModel.toggle(entry.record.mediaId) },
                    ) {
                        AsyncImage(
                            model = contentUriFor(entry.record.mediaId, entry.record.isVideo),
                            contentDescription = entry.record.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        if (selected) {
                            Box(Modifier.fillMaxSize().background(Color(0x662E7D32)))
                        }
                        Column(Modifier.align(Alignment.BottomStart).padding(4.dp)) {
                            Text(formatBytes(entry.record.sizeBytes), style = MaterialTheme.typography.labelSmall)
                            entry.expiresAtSec?.let {
                                val date = Instant.ofEpochSecond(it)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("d MMM"))
                                Text(Copy.expiresAtLeast(date), style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }

        // Spec §9 rule 5.
        Text(Copy.RESTORE_CONFIRM_NOTE, style = MaterialTheme.typography.bodySmall)

        // Ruling R7: footer noting how many other items sit in the system trash
        // that Swipey did not put there — otherwise queryTrashed()/binOtherItems
        // are both dead code and the design spec goes unmet.
        if (state.otherItemsCount > 0) {
            Text(Copy.binOtherItems(state.otherItemsCount), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = {
                val ids = state.selected.toList()
                attemptedIds = ids
                scope.launch {
                    // Fix round 2, Critical 2 (restore side): without this, a throw
                    // from beginRestore() (e.g. its Room write) would leave
                    // `restoring` latched true forever — trashLauncher.start() is
                    // never reached, so nothing else resets it.
                    try {
                        trashLauncher.start(viewModel.beginRestore())
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        viewModel.onRestoreFailed()
                    }
                }
            },
            // F2: disabled for the whole in-flight window, not just until the click
            // handler returns — beginRestore() is a suspend Room write + binder round
            // trip before the dialog even appears, during which state.selected is
            // otherwise unchanged and a second tap would start a second sequence.
            //
            // Fix round 2 re-review, restore-side latch: `state.restoring` alone is not
            // enough. The catch block above resets `restoring` on any throw out of
            // `trashLauncher.start(...)`, but if that throw happened *after* `start()`
            // already flipped `inFlight = true` (e.g. `launchNext()`'s
            // `activityLauncher.launch(request)` itself throwing), nothing outside
            // TrashLauncher can ever clear `inFlight` again — a retry's `start()` would
            // then be a silent no-op at `if (inFlight) return`, latching `restoring` true
            // forever with no dialog and no callback to reset it. Deriving `enabled` from
            // both flags, mirroring the trash side's `committing = preparing ||
            // trashLauncher.inFlight` (SwipeyApp.kt), closes that gap: the button cannot
            // re-enable while a stuck `inFlight` would make a retry a no-op.
            enabled = state.selected.isNotEmpty() && !state.restoring && !trashLauncher.inFlight,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            // F8(b): was composed at this call site; now lives in Copy.kt.
            Text(Copy.binRestoreAction(state.selected.size))
        }
    }
}
