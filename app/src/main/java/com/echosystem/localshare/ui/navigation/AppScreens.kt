package com.echosystem.localshare.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(
    val route: String,
    val title: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector
) {
    object Onboarding : Screen("onboarding", "Onboarding", Icons.Filled.Launch, Icons.Outlined.Launch)
    object Devices : Screen("nodes", "Radar", Icons.Filled.Radar, Icons.Outlined.Radar)
    object Files : Screen("files", "Storage", Icons.Filled.Folder, Icons.Outlined.Folder)
    object WebShare : Screen("webportal", "Portal", Icons.Filled.Language, Icons.Outlined.Language)
    object History : Screen("history", "Ledger", Icons.Filled.History, Icons.Outlined.History)
    object Settings : Screen("settings", "Shield", Icons.Filled.Settings, Icons.Outlined.Settings)
    object TrustedDevices : Screen("trusted", "Trusted", Icons.Filled.Security, Icons.Outlined.Security)
    object Permissions : Screen("permissions", "Permissions", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    object Developer : Screen("developer", "Dev Tools", Icons.Filled.BugReport, Icons.Outlined.BugReport)
}

val bottomTabItems = listOf(
    Screen.Devices,
    Screen.Files,
    Screen.WebShare,
    Screen.History,
    Screen.Settings
)
