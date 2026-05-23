package com.echosystem.localshare.ui.navigation

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.echosystem.localshare.ui.screens.*
import com.echosystem.localshare.viewmodel.EchoViewModel

import com.echosystem.localshare.ui.screens.devices.DevicesScreen
import com.echosystem.localshare.ui.screens.files.FilesScreen
import com.echosystem.localshare.ui.screens.webshare.WebShareScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EchoViewModel = hiltViewModel()) {
    var showOnboarding by remember { mutableStateOf(true) }
    val navController = rememberNavController()
    var showMenu by remember { mutableStateOf(false) }

    if (showOnboarding) {
        OnboardingScreen(onFinish = { showOnboarding = false })
    } else {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Text(
                            "echoSystem",
                            fontWeight = FontWeight.Black,
                            style = MaterialTheme.typography.titleLarge,
                            fontFamily = FontFamily.SansSerif
                        )
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp)
                    ),
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "Control Center")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.width(200.dp)
                        ) {
                            Text(
                                "Control Center",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(16.dp, 8.dp)
                            )
                            HorizontalDivider(modifier = Modifier.padding(bottom = 4.dp))
                            ControlMenuItem(Screen.TrustedDevices, "Trusted Registry", Icons.Default.Security) {
                                showMenu = false
                                navController.navigate(Screen.TrustedDevices.route)
                            }
                            ControlMenuItem(Screen.Permissions, "Permissions", Icons.Default.AdminPanelSettings) {
                                showMenu = false
                                navController.navigate(Screen.Permissions.route)
                            }
                            ControlMenuItem(Screen.History, "Transfer History", Icons.Default.History) {
                                showMenu = false
                                navController.navigate(Screen.History.route)
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            ControlMenuItem(Screen.Settings, "Settings", Icons.Default.Settings) {
                                showMenu = false
                                navController.navigate(Screen.Settings.route)
                            }
                            ControlMenuItem(Screen.Developer, "Dev Tools", Icons.Default.Terminal) {
                                showMenu = false
                                navController.navigate(Screen.Developer.route)
                            }
                        }
                    }
                )
            },
            bottomBar = { AppBottomNavigationBar(navController = navController) }
        ) { padding ->
            EchoNavHost(
                navController = navController,
                viewModel = viewModel,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ControlMenuItem(
    screen: Screen,
    label: String,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyMedium) },
        leadingIcon = {
            Icon(
                imageVector = icon ?: screen.outlinedIcon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        onClick = onClick
    )
}

@Composable
fun EchoNavHost(
    navController: androidx.navigation.NavHostController,
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val incomingPairing by viewModel.incomingPairingRequest.collectAsState()

    Box(modifier = modifier) {
        NavHost(
            navController = navController,
            startDestination = Screen.Devices.route,
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }) + fadeIn()
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }) + fadeIn()
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
            }
        ) {
            composable(Screen.Devices.route) { DevicesScreen(viewModel) }
            composable(Screen.Files.route) { FilesScreen(viewModel) }
            composable(Screen.WebShare.route) { WebShareScreen() }
            
            composable(Screen.History.route) { HistoryScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
            composable(Screen.Developer.route) { DeveloperScreen(viewModel, onBack = { navController.popBackStack() }) }
            
            composable(Screen.TrustedDevices.route) { 
                SecurityManager(viewModel = viewModel, onClose = { navController.popBackStack() }) 
            }
            composable(Screen.Permissions.route) { 
                SecurityManager(viewModel = viewModel, onClose = { navController.popBackStack() }) 
            }
        }

        // Shield Guard: Global Discovery Dialog
        incomingPairing?.let { request ->
            AlertDialog(
                onDismissRequest = { viewModel.clearIncomingPairing() },
                title = { Text("Shield Guard Discovery", fontWeight = FontWeight.Black) },
                icon = { Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                text = {
                    Column {
                        Text("An external node is attempting to establish a secure link.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(16.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(Modifier.padding(16.dp).fillMaxWidth()) {
                                Text(request.deviceName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("ID: ${request.deviceId}", style = MaterialTheme.typography.labelSmall)
                                Text("PIN Token: ${request.pin}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.acceptPairing(request) }) {
                        Text("Open Access")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { viewModel.blockDeviceFromPairing(request) }) {
                            Text("Block Node", color = MaterialTheme.colorScheme.error)
                        }
                        TextButton(onClick = { viewModel.rejectPairing(request) }) {
                            Text("Reject")
                        }
                    }
                }
            )
        }
    }
}
