package dev.praveensenpai.miru.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.praveensenpai.miru.data.library.toVideoCard
import dev.praveensenpai.miru.ui.components.PlaceholderScreen
import dev.praveensenpai.miru.ui.components.VideoCardItem

/**
 * Library tab.
 *
 * Currently watch history, newest first. Playlists and Watch Later are future
 * additions; this tab is the natural home for them.
 */
@Composable
fun LibraryScreen(
    onVideoClick: (String) -> Unit,
    viewModel: LibraryViewModel = viewModel(),
) {
    val history by viewModel.history.collectAsState()

    if (history.isEmpty()) {
        PlaceholderScreen(
            icon = Icons.Filled.LibraryMusic,
            title = "Nothing watched yet",
            message = "Videos you watch appear here, newest first.",
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "History",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(onClick = viewModel::clear) { Text("Clear") }
            }
        }
        items(history, key = { it.videoId }) { entry ->
            VideoCardItem(
                video = entry.toVideoCard(),
                onClick = { onVideoClick(entry.videoId) },
            )
        }
    }
}