package com.example.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.clients.ClientsScreen
import com.example.ui.screens.records.RecordsScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.timer.TimerScreen
import com.example.ui.screens.timer.TimerStateManager

@Composable
fun MainAppNavigation(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val savedSessionId by TimerStateManager.savedNotificationEntry.collectAsStateWithLifecycle()
    val showLabels = with(LocalDensity.current) {
        androidx.compose.ui.platform.LocalWindowInfo.current.containerSize.width.toDp() >= 600.dp
    } || LocalDensity.current.fontScale <= 1.3f
    val screens = listOf(
        Screen.Timer,
        Screen.Records,
        Screen.Clients,
        Screen.Settings
    )

    Scaffold(
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .testTag("bottom_nav_bar"),
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp
            ) {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                screens.forEach { screen ->
                    val label = stringResource(screen.labelRes)
                    val isSelected = currentRoute == screen.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentRoute != screen.route) {
                                navController.selectTab(screen)
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        icon = {
                            if (showLabels) Icon(
                                imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                contentDescription = null
                            ) else com.example.ui.theme.AppTooltip(label) {
                                Icon(if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = label)
                            }
                        },
                        label = if (showLabels) ({ Text(label) }) else null,
                        modifier = Modifier.testTag("nav_item_${screen.route}")
                    )
                }
            }
        },
        modifier = modifier
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Timer.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Timer.route) {
                TimerScreen()
            }
            composable(Screen.Records.route) {
                RecordsScreen(savedSessionId = savedSessionId)
            }
            composable(Screen.Clients.route) {
                ClientsScreen()
            }
            composable(Screen.Settings.route) {
                SettingsScreen()
            }
        }
    }

    LaunchedEffect(savedSessionId) {
        if (savedSessionId != null && navController.currentDestination?.route != Screen.Records.route) {
            navController.selectTab(Screen.Records)
        }
    }
}

private fun NavHostController.selectTab(screen: Screen) {
    navigate(screen.route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
