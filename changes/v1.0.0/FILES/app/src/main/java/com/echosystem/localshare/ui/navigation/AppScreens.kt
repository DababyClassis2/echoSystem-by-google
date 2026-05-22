package com.echosystem.localshare.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val filledIcon: ImageVector, val outlinedIcon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home, Icons.Outlined.Home)
    object Send : Screen("send", "Send", Icons.Filled.Upload, Icons.Outlined.Upload)
    object Receive : Screen("receive", "Receive", Icons.Filled.Download, Icons.Outlined.Download)
    object History : Screen("history", "History", Icons.Filled.History, Icons.Outlined.History)
    object WebShare : Screen("webshare", "WebShare", Icons.Filled.Language, Icons.Outlined.Language)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
    object Developer : Screen("developer", "Developer", Icons.Filled.DeveloperMode, Icons.Outlined.DeveloperMode)
    object TrustedDevices : Screen("trusted_devices", "Trusted Devices", Icons.Filled.Security, Icons.Outlined.Security)
}

val bottomTabItems = listOf(
    Screen.Home,
    Screen.Send,
    Screen.Receive,
    Screen.WebShare
)
