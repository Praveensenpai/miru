package dev.praveensenpai.miru.ui.subscriptions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import dev.praveensenpai.miru.data.library.SubscriptionEntity
import dev.praveensenpai.miru.ui.components.PlaceholderScreen

/**
 * Subscriptions tab.
 *
 * A plain list of channels: avatar, name, and an unsubscribe action. Tapping a
 * row opens that channel's videos. No feed aggregation — this is the honest,
 * local-first version.
 */
@Composable
fun SubscriptionsScreen(
    onChannelClick: (String) -> Unit,
    viewModel: SubscriptionsViewModel = viewModel(),
) {
    val subscriptions by viewModel.subscriptions.collectAsState()

    if (subscriptions.isEmpty()) {
        PlaceholderScreen(
            icon = Icons.Filled.Subscriptions,
            title = "No subscriptions yet",
            message = "Subscribe from a video and the channel shows up here.",
        )
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(subscriptions, key = { it.channelId }) { channel ->
            SubscriptionRow(
                channel = channel,
                onClick = { onChannelClick(channel.channelId) },
                onUnsubscribe = { viewModel.unsubscribe(channel.channelId) },
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    channel: SubscriptionEntity,
    onClick: () -> Unit,
    onUnsubscribe: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = channel.avatar,
            contentDescription = channel.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        TextButton(onClick = onUnsubscribe) {
            Text("Unsubscribe")
        }
    }
}