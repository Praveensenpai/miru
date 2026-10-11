package dev.praveensenpai.miru

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.praveensenpai.miru.ui.components.PlaceholderScreen
import dev.praveensenpai.miru.ui.downloads.DownloadsScreen
import dev.praveensenpai.miru.ui.home.HomeScreen
import dev.praveensenpai.miru.ui.nav.Routes
import dev.praveensenpai.miru.ui.nav.TopDestination
import dev.praveensenpai.miru.ui.player.PlayerScreen
import dev.praveensenpai.miru.ui.search.SearchScreen
import dev.praveensenpai.miru.ui.theme.MiruTheme

/**
 * Single-activity host.
 *
 * Five bottom-nav destinations plus two full-screen routes (search, player) that
 * sit above the bar. Navigation state is the single source of truth for which
 * chrome is visible.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MiruTheme {
                MiruRoot()
            }
        }
    }
}

@Composable
private fun MiruRoot() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    // Search and player are full-screen: no bottom bar.
    val showBottomBar = TopDestination.entries.any { dest ->
        currentDestination?.hierarchy?.any { it.route == dest.route } == true
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    TopDestination.entries.forEach { dest ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == dest.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(dest.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = dest.icon,
                                    contentDescription = dest.label,
                                )
                            },
                            label = { Text(dest.label) },
                        )
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopDestination.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            composable(TopDestination.Home.route) {
                HomeScreen(
                    onVideoClick = { id -> navController.navigate(Routes.player(id)) },
                    onSearchClick = { navController.navigate("search") },
                )
            }

            composable(TopDestination.Subscriptions.route) {
                PlaceholderScreen(
                    icon = Icons.Filled.Subscriptions,
                    title = "Subscriptions",
                    message = "Channel subscriptions and sync arrive in v2.",
                )
            }

            composable(TopDestination.Library.route) {
                PlaceholderScreen(
                    icon = Icons.Filled.LibraryMusic,
                    title = "Library",
                    message = "Playlists, watch later, and history arrive in v2.",
                )
            }

            composable(TopDestination.Downloads.route) {
                DownloadsScreen()
            }

            composable(TopDestination.You.route) {
                PlaceholderScreen(
                    icon = Icons.Filled.Person,
                    title = "You",
                    message = "Account, auth status, and settings live here in v2.",
                )
            }

            composable("search") {
                SearchScreen(
                    onVideoClick = { id -> navController.navigate(Routes.player(id)) },
                    onBack = { navController.popBackStack() },
                )
            }

            composable(Routes.PLAYER) { entry ->
                val videoId = entry.arguments?.getString("videoId").orEmpty()
                PlayerScreen(
                    videoId = videoId,
                    onBack = { navController.popBackStack() },
                )
            }
        }
    }
}