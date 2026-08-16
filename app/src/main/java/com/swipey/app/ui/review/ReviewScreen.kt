package com.swipey.app.ui.review

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.swipey.app.data.contentUriFor
import com.swipey.app.domain.MediaItem
import com.swipey.app.domain.chunkedForRequest
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy

/**
 * F4 (Task 10 review): [committing] is not in the brief's 3-param signature (Ruling R6
 * pins that block verbatim), but disabling in-flight mutation is a correctness fix, not
 * a signature dispute — without it, Task 20's onCommit can call prepareTrash (which bakes
 * the marked URIs into the PendingIntents right there) while the grid and button stay
 * live, so unmarking a thumbnail or tapping Commit again during the dialog's beat has no
 * effect on what actually gets trashed. Task 20 MUST thread its own in-flight state in
 * here; there is no default because silently omitting it would defeat the fix.
 */
@Composable
fun ReviewScreen(
    items: List<MediaItem>,
    onUnmark: (Long) -> Unit,
    onCommit: () -> Unit,
    committing: Boolean,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Text(Copy.REVIEW_TITLE, style = MaterialTheme.typography.headlineSmall)
        Text(
            Copy.reviewHeader(items.size, formatBytes(items.sumOf { it.sizeBytes })),
            style = MaterialTheme.typography.bodyMedium,
        )

        if (items.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(Copy.REVIEW_EMPTY)
            }
        } else {
            LazyVerticalGrid(GridCells.Fixed(3), Modifier.weight(1f)) {
                items(items, key = { it.id }) { item ->
                    Box(
                        Modifier
                            .padding(2.dp)
                            .aspectRatio(1f)
                            .clickable(enabled = !committing) { onUnmark(item.id) },
                    ) {
                        AsyncImage(
                            model = contentUriFor(item.id, item.isVideo),
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            formatBytes(item.sizeBytes),
                            Modifier.align(Alignment.BottomStart).padding(4.dp),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }

        // Spec §9 rules 2, 4, 7 — stated before the user commits, not after.
        Text(Copy.TRASH_SIZE_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.SYSTEM_TRASH_NOTE, style = MaterialTheme.typography.bodySmall)
        Text(Copy.NO_PERMANENT_DELETE_NOTE, style = MaterialTheme.typography.bodySmall)

        val batches = items.chunkedForRequest().size
        if (batches > 1) {
            Text(Copy.multipleConfirmations(batches), style = MaterialTheme.typography.bodySmall)
        }

        Button(
            onClick = onCommit,
            enabled = items.isNotEmpty() && !committing,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            Text(Copy.reviewAction(items.size))
        }
    }
}
