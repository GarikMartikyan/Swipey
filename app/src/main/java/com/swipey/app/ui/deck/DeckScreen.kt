package com.swipey.app.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    // Whether a swipe decision is currently mid fly-off animation; while true the
    // Bin/Undo/Keep buttons below are disabled so a tap can't record a decision
    // against a card that's already committing to a different one (fix round 1,
    // Critical 2).
    var committing by remember { mutableStateOf(false) }

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

    if (state.exhausted && state.markedCount == 0) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(Copy.DECK_EMPTY_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(Copy.DECK_NOTHING_MARKED)
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
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
