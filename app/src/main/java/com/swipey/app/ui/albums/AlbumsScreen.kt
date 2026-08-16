package com.swipey.app.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.domain.Album
import com.swipey.app.domain.formatBytes

@Composable
fun AlbumsScreen(albums: List<Album>, onPick: (Album) -> Unit) {
    if (albums.isEmpty()) {
        Text("No albums found", Modifier.padding(24.dp))
        return
    }
    LazyColumn(Modifier.fillMaxSize()) {
        items(albums, key = { it.bucketId }) { album ->
            Column(
                Modifier.fillMaxWidth().clickable { onPick(album) }.padding(16.dp),
            ) {
                Text(album.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${album.itemCount} items · ${formatBytes(album.totalBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
