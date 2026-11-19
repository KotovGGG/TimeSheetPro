package com.example.ui.navigation

import com.example.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    object Timer : Screen(
        route = "timer",
        labelRes = R.string.copy_timer,
        selectedIcon = Icons.Filled.Timer,
        unselectedIcon = Icons.Outlined.Timer
    )

    object Clients : Screen(
        route = "clients",
        labelRes = R.string.copy_clients,
        selectedIcon = Icons.Filled.People,
        unselectedIcon = Icons.Outlined.People
    )

    object Records : Screen(
        route = "records",
        labelRes = R.string.copy_records,
        selectedIcon = Icons.Filled.History,
        unselectedIcon = Icons.Outlined.History
    )

    object Settings : Screen(
        route = "settings",
        labelRes = R.string.copy_settings,
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
}
