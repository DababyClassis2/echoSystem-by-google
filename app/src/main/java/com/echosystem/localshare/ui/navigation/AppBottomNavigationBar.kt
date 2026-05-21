package com.echosystem.localshare.ui.navigation

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState

@Composable
fun AppBottomNavigationBar(navController: NavController) {
    val haptic = LocalHapticFeedback.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        tonalElevation = 8.dp
    ) {
        bottomTabItems.forEach { screen ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = if (currentRoute == screen.route) screen.filledIcon else screen.outlinedIcon,
                        contentDescription = screen.title
                    )
                },
                label = { Text(screen.title, style = MaterialTheme.typography.labelSmall) },
                selected = currentRoute == screen.route,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    navController.navigate(screen.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                modifier = Modifier.testTag("tab_${screen.route}")
            )
        }
    }
}
