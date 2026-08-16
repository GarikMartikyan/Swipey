package com.swipey.app.ui.albums

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.swipey.app.domain.SortMode
import com.swipey.app.ui.common.Copy

private val labels = listOf(
    SortMode.NEWEST to Copy.SORT_NEWEST,
    SortMode.OLDEST to Copy.SORT_OLDEST,
    SortMode.LARGEST to Copy.SORT_LARGEST,
    SortMode.SMALLEST to Copy.SORT_SMALLEST,
)

@Composable
fun SortChooserScreen(onPick: (SortMode) -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(Copy.SORT_TITLE, style = MaterialTheme.typography.headlineSmall)
        labels.forEach { (mode, label) ->
            Text(
                label,
                Modifier.fillMaxWidth().clickable { onPick(mode) }.padding(vertical = 16.dp),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
