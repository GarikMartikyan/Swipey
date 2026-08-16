package com.swipey.app.ui.albums

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.swipey.app.domain.Album
import com.swipey.app.domain.formatBytes
import com.swipey.app.ui.common.Copy
import com.swipey.app.ui.design.SwipeyRow
import com.swipey.app.ui.design.SwipeyScreen
import com.swipey.app.ui.design.SwipeySpacing
import com.swipey.app.ui.design.SwipeyText
import com.swipey.app.ui.design.SwipeyTheme

/**
 * Every folder on the device, biggest first — the order [com.swipey.app.domain.toAlbums]
 * already returns them in, since the album worth cleaning up is almost always the
 * largest one.
 *
 * One [SwipeyRow] per album: the folder's name, and `count · size` underneath it. The
 * size is the reason to open the row, so it is on the row and not behind it.
 */
@Composable
fun AlbumsScreen(albums: List<Album>, onPick: (Album) -> Unit) {
    SwipeyScreen {
        Column(Modifier.fillMaxSize()) {
            SwipeyText(
                Copy.HOME_ALBUMS,
                modifier = Modifier.padding(top = SwipeySpacing.xxl, bottom = SwipeySpacing.lg),
                style = SwipeyTheme.typography.display,
                color = SwipeyTheme.colors.textPrimary,
            )

            if (albums.isEmpty()) {
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(bottom = SwipeySpacing.xxxl),
                    contentAlignment = Alignment.Center,
                ) {
                    SwipeyText(
                        Copy.ALBUMS_EMPTY,
                        style = SwipeyTheme.typography.body,
                        color = SwipeyTheme.colors.textSecondary,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(albums, key = { it.bucketId }) { album ->
                        SwipeyRow(
                            title = album.name,
                            subtitle = "${album.itemCount} items · ${formatBytes(album.totalBytes)}",
                            onClick = { onPick(album) },
                        )
                    }
                }
            }
        }
    }
}
