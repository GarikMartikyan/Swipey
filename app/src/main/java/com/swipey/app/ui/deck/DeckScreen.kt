package com.swipey.app.ui.deck

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy

@Composable
fun DeckScreen(
    viewModel: DeckViewModel,
    onReview: () -> Unit,
    onDone: () -> Unit,
    // Fix round 2, Important 5: invoked once the user confirms discarding a Back press
    // with marks pending — the caller performs the actual navigation (a plain
    // `navController.popBackStack()`), mirroring what an un-intercepted Back would have
    // done.
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Whether a swipe decision is currently mid fly-off animation; while true the
    // Bin/Undo/Keep buttons below are disabled so a tap can't record a decision
    // against a card that's already committing to a different one (fix round 1,
    // Critical 2).
    var committing by remember { mutableStateOf(false) }
    var showDiscardConfirm by remember { mutableStateOf(false) }

    // Fix round 2, Important 5: DECK_BACK_CONFIRM/DECK_DISCARD/DECK_REVIEW were dead
    // copy — nothing intercepted Back, so marked-but-uncommitted items were silently
    // abandoned. Only intercepts when there's something to lose; with nothing marked,
    // Back falls through to the default pop untouched.
    BackHandler(enabled = state.markedCount > 0) {
        showDiscardConfirm = true
    }

    if (showDiscardConfirm) {
        AlertDialog(
            onDismissRequest = { showDiscardConfirm = false },
            title = { Text(Copy.DECK_BACK_CONFIRM) },
            confirmButton = {
                TextButton(onClick = { showDiscardConfirm = false; onBack() }) {
                    Text(Copy.DECK_DISCARD)
                }
            },
            dismissButton = {
                // Not a "cancel" — takes the user to Review instead, so the marks they
                // were about to lose have somewhere useful to go.
                TextButton(onClick = { showDiscardConfirm = false; onReview() }) {
                    Text(Copy.DECK_REVIEW)
                }
            },
        )
    }

    // Terminal states, spec §4. Only the "marked" path auto-navigates: exhausted with
    // nothing marked stops here instead (fix round 1, Important 5b) so the session's
    // last decision — otherwise unreachable the instant it lands — stays undoable.
    LaunchedEffect(state.exhausted, state.markedCount) {
        if (state.exhausted && !state.loading && state.markedCount > 0) {
            onReview()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    // I4 / spec §12: a read that threw lands here instead of crashing. Checked before the
    // exhausted branch below so a failed load never renders as "nothing left to review" —
    // telling the user their album is empty when it was never read would be a lie, and one
    // that invites them to move on rather than retry.
    if (state.failed) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Copy.LOAD_FAILED, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = { viewModel.retry() }) { Text(Copy.RETRY) }
            TextButton(onClick = onDone) { Text(Copy.RESULT_DONE) }
        }
        return
    }

    if (state.exhausted && state.markedCount == 0) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Copy.DECK_EMPTY_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(Copy.DECK_NOTHING_MARKED)
            Spacer(Modifier.height(16.dp))
            // NEW 2: state.position doubles as "history size" — SwipeSession.position is
            // incremented by every advance() and decremented by every undo(), so it's
            // exactly zero iff there is nothing left to undo (empty album, or an album
            // where every item already has a KEEP row). A dead, always-disabled-in-effect
            // Undo button here would be confusing; hide it instead.
            if (state.position > 0) {
                TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
            }
            Button(onClick = onDone) { Text("Done") }
        }
        return
    }

    val currentId = state.current?.id

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${state.position} / ${state.total}", style = MaterialTheme.typography.labelLarge)
            if (state.markedCount > 0) {
                TextButton(onClick = onReview) {
                    Text("${state.markedCount} marked · ${formatBytes(state.markedBytes)}")
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val item = state.current
            if (item == null) {
                Text(Copy.DECK_EMPTY_TITLE)
            } else {
                SwipeCard(
                    itemId = item.id,
                    onSwiped = { id, keep -> viewModel.swipe(id, keep) },
                    onCommittingChanged = { committing = it },
                ) {
                    MediaCardContent(item)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(
                onClick = { currentId?.let { viewModel.swipe(it, keep = false) } },
                enabled = !committing && currentId != null,
            ) { Text("Bin") }
            TextButton(onClick = { viewModel.undo() }, enabled = !committing) { Text("Undo") }
            Button(
                onClick = { currentId?.let { viewModel.swipe(it, keep = true) } },
                enabled = !committing && currentId != null,
            ) { Text("Keep") }
        }
    }
}

@Composable
fun MediaCardContent(item: com.swipey.app.domain.MediaItem) {
    if (item.isVideo) {
        VideoCard(item)
    } else {
        coil3.compose.AsyncImage(
            model = com.swipey.app.data.contentUriFor(item.id, item.isVideo),
            contentDescription = item.displayName,
            modifier = Modifier.fillMaxSize(),
            contentScale = androidx.compose.ui.layout.ContentScale.Fit,
        )
    }
}
