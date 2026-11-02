package com.anireku.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.anireku.app.ui.detail.DetailScreen
import com.anireku.app.ui.home.HomeScreen
import com.anireku.app.ui.library.LibraryScreen
import com.anireku.app.ui.search.SearchScreen
import com.anireku.app.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    Home("home", "Home", Icons.Filled.Home),
    Search("search", "Search", Icons.Filled.Search),
    Library("library", "Library", Icons.Filled.VideoLibrary),
    Settings("settings", "Setelan", Icons.Filled.Settings),
}

@Composable
fun AniRekuNavHost() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route ?: Tab.Home.route

    Scaffold(
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = Tab.Home.route,
            modifier = Modifier.padding(inner),
        ) {
            composable(Tab.Home.route) {
                HomeScreen(
                    onOpenAnime = { id, source -> nav.navigate("detail/$source/$id") },
                )
            }
            composable(Tab.Search.route) {
                SearchScreen(onOpenAnime = { id, source -> nav.navigate("detail/$source/$id") })
            }
            composable(Tab.Library.route) {
                LibraryScreen(onOpenAnime = { id, source -> nav.navigate("detail/$source/$id") })
            }
            composable(Tab.Settings.route) {
                SettingsScreen()
            }
            composable("detail/{source}/{id}") { entry ->
                DetailScreen(
                    animeId = entry.arguments?.getString("id").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}
