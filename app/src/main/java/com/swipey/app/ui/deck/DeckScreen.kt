package com.swipey.app.ui.deck

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

    // Terminal states, spec §4.
    LaunchedEffect(state.exhausted, state.markedCount) {
        if (state.exhausted && !state.loading) {
            if (state.markedCount > 0) onReview() else onDone()
        }
    }

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

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
                SwipeCard(key = item.id, onSwiped = { keep -> viewModel.swipe(keep) }) {
                    MediaCardContent(item)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Button(onClick = { viewModel.swipe(keep = false) }) { Text("Bin") }
            TextButton(onClick = { viewModel.undo() }) { Text("Undo") }
            Button(onClick = { viewModel.swipe(keep = true) }) { Text("Keep") }
        }
    }
}

@Composable
fun MediaCardContent(item: com.swipey.app.domain.MediaItem) {
    coil3.compose.AsyncImage(
        model = com.swipey.app.data.contentUriFor(item.id, item.isVideo),
        contentDescription = item.displayName,
        modifier = Modifier.fillMaxSize(),
        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
    )
}
