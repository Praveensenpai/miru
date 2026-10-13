package dev.praveensenpai.miru.ui.channel

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.praveensenpai.miru.ui.components.VideoCardItem

/** A channel's page: its recent videos, reached from a subscription. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelScreen(
    channelId: String,
    onVideoClick: (String) -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: ChannelViewModel = viewModel(
        key = channelId,
        factory = ChannelViewModelFactory(channelId),
    )
    val state by viewModel.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = {
                    val name = (state as? ChannelUiState.Ready)?.channel?.name ?: "Channel"
                    Text(text = name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (val s = state) {
                is ChannelUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is ChannelUiState.Error -> {
                    TextButton(
                        onClick = viewModel::refresh,
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Text("Could not load channel — retry")
                    }
                }

                is ChannelUiState.Ready -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(s.channel.items, key = { it.id }) { video ->
                            VideoCardItem(
                                video = video,
                                onClick = { onVideoClick(video.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}