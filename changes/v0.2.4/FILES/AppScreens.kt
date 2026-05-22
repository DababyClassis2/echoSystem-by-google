package com.echosystem.localshare.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector) {
    // Main Tabs
    object Devices : Screen("devices", "Devices", Icons.Filled.Devices, Icons.Outlined.Devices)
    object Files : Screen("files", "Files", Icons.Filled.Folder, Icons.Outlined.Folder)
    object WebShare : Screen("webshare", "WebShare", Icons.Filled.Language, Icons.Outlined.Language)
    
    // Control Center / Deep Links
    object TrustedDevices : Screen("trusted_devices", "Registry", Icons.Filled.Security, Icons.Outlined.Security)
    object Permissions : Screen("permissions", "Permissions", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings)
    object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object Developer : Screen("developer", "Dev Tools", Icons.Filled.DeveloperMode, Icons.Outlined.DeveloperMode)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

val bottomTabItems = listOf(
    Screen.Devices,
    Screen.Files,
    Screen.WebShare
)
