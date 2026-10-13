package dev.praveensenpai.miru.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.ui.graphics.vector.ImageVector

/** Top-level bottom-navigation destinations. Five is the maximum. */
enum class TopDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", Icons.Filled.Home),
    Subscriptions("subscriptions", "Subs", Icons.Filled.Subscriptions),
    Library("library", "Library", Icons.Filled.LibraryMusic),
    Downloads("downloads", "Downloads", Icons.Filled.Download),
    You("you", "You", Icons.Filled.Person),
}

/** Player is a full-screen destination, outside the bottom bar. */
object Routes {
    const val PLAYER = "player/{videoId}"
    fun player(videoId: String) = "player/$videoId"

    const val CHANNEL = "channel/{channelId}"
    fun channel(channelId: String) = "channel/$channelId"
}