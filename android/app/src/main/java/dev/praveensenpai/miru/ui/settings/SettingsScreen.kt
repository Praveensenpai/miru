package dev.praveensenpai.miru.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.praveensenpai.miru.BuildConfig
import dev.praveensenpai.miru.data.settings.VideoQuality

/**
 * You tab: account placeholder, playback preferences, and About.
 *
 * There is no account system (and, being serverless, no login to hold), so the
 * tab is honest about that and instead surfaces the one setting that matters
 * today: the video quality ceiling applied by the player.
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
) {
    val quality by viewModel.quality.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SectionHeader("Playback")
        VideoQuality.entries.forEach { option ->
            QualityRow(
                label = option.label(),
                selected = option == quality,
                onSelect = { viewModel.setQuality(option) },
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

        SectionHeader("About")
        AboutRow("Miru", BuildConfig.VERSION_NAME)
        AboutRow("Engine", "On-device InnerTube (serverless)")
        AboutRow("License", "MIT")
        Text(
            text = "Miru talks to YouTube directly from your device. " +
                "No account, no backend, nothing leaves the phone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

/** Human-readable label for a quality ceiling. */
private fun VideoQuality.label(): String = when (this) {
    VideoQuality.AUTO -> "Auto (best available)"
    VideoQuality.UHD_2160 -> "2160p (4K)"
    VideoQuality.QHD_1440 -> "1440p (2K)"
    VideoQuality.FHD_1080 -> "1080p"
    VideoQuality.HD_720 -> "720p"
    VideoQuality.SD_480 -> "480p"
    VideoQuality.LOW_360 -> "360p"
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun QualityRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun AboutRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
