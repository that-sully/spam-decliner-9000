package com.example.spam_decliner_9000.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.spam_decliner_9000.ui.blocklist.BlocklistScreen
import com.example.spam_decliner_9000.ui.history.HistoryScreen
import com.example.spam_decliner_9000.ui.settings.SettingsScreen
import com.example.spam_decliner_9000.ui.theme.Spamdecliner9000Theme

// Navigation route constants
private const val ROUTE_HISTORY  = "history"
private const val ROUTE_LISTS    = "lists"
private const val ROUTE_SETTINGS = "settings"

private data class NavItem(
    val label: String,
    val icon: ImageVector,
    val route: String
)

private val NAV_ITEMS = listOf(
    NavItem("History",  Icons.Default.Phone,    ROUTE_HISTORY),
    NavItem("Lists",    Icons.Default.List,      ROUTE_LISTS),
    NavItem("Settings", Icons.Default.Settings,  ROUTE_SETTINGS)
)

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Spamdecliner9000Theme {
                SpamBlockerApp()
            }
        }
    }
}

@Composable
private fun SpamBlockerApp() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NAV_ITEMS.forEach { item ->
                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == item.route } == true,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HISTORY,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(ROUTE_HISTORY)  { HistoryScreen() }
            composable(ROUTE_LISTS)    { BlocklistScreen() }
            composable(ROUTE_SETTINGS) { SettingsScreen() }
        }
    }
}
