package com.swipey.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.ui.common.Copy

@Composable
fun HomeScreen(
    binCount: Int,
    onAllMedia: () -> Unit,
    onAlbums: () -> Unit,
    onShuffle: () -> Unit,
    onBin: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(Copy.APP_NAME, style = MaterialTheme.typography.headlineMedium)
        HomeEntry(Copy.HOME_ALL_MEDIA, Copy.HOME_ALL_MEDIA_SUB, onAllMedia)
        HomeEntry(Copy.HOME_ALBUMS, Copy.HOME_ALBUMS_SUB, onAlbums)
        HomeEntry(Copy.HOME_SHUFFLE, Copy.HOME_SHUFFLE_SUB, onShuffle)
        HomeEntry(Copy.HOME_BIN, "$binCount items", onBin)
    }
}

@Composable
private fun HomeEntry(title: String, subtitle: String, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
