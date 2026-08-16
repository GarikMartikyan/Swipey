package com.swipey.app.ui.bin

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun BinScreen(viewModel: BinViewModel, onRestore: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(Copy.BIN_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        if (state.vanishedCount > 0) {
            // Ruling R14: vanished covers every non-entry outcome, including items
            // restored elsewhere (e.g. in Google Photos), not just items genuinely
            // gone from the device. Copy.vanishedNotice is worded to be accurate for
            // both causes — it must not be reworded to imply destruction.
            Text(Copy.vanishedNotice(state.vanishedCount), style = MaterialTheme.typography.bodySmall)
        }

        if (state.entries.isEmpty() && !state.loading) {
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
            onClick = onRestore,
            enabled = state.selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text("${Copy.BIN_RESTORE} ${state.selected.size}")
        }
    }
}
