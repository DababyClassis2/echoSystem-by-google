package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.Protocol
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.EchoViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppContainer()
            }
        }
    }
}

enum class NavigationTab(val title: String, val icon: ImageVector, val tag: String) {
    HOME("Home", Icons.Rounded.Home, "tab_home"),
    FILES("Files", Icons.Rounded.FolderOpen, "tab_files"),
    TRANSFERS("Transfers", Icons.Rounded.SwapHoriz, "tab_transfers"),
    SETTINGS("Settings", Icons.Rounded.Tune, "tab_settings")
}

@Composable
fun MainAppContainer(
    viewModel: EchoViewModel = viewModel()
) {
    val hasCompletedOnboarding by viewModel.hasCompletedOnboarding.collectAsState()
    val activePairing by viewModel.activePairing.collectAsState()

    var showOnboarding by remember { mutableStateOf(false) }

    // Synchronize onboarding state
    LaunchedEffect(hasCompletedOnboarding) {
        showOnboarding = !hasCompletedOnboarding
    }

    if (showOnboarding) {
        OnboardingScreen(
            viewModel = viewModel,
            onDismiss = { showOnboarding = false }
        )
    } else {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Adaptive design layout selectors
            val configuration = LocalConfiguration.current
            val isExpanded = configuration.screenWidthDp >= 600

            if (isExpanded) {
                // Expanded layouts: Flanking side NavigationRail
                ExpandedLayout(viewModel = viewModel)
            } else {
                // Compact layouts: Standard lower Bottom navigation bars
                CompactLayout(viewModel = viewModel)
            }

            // Automatic Display PIN dialog overlays
            activePairing?.let { pairing ->
                PairingDialog(
                    active = pairing,
                    onConfirm = { viewModel.confirmCredentialsAndPair() },
                    onDecline = { viewModel.declinePairing() }
                )
            }
        }
    }
}

@Composable
fun CompactLayout(viewModel: EchoViewModel) {
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }

    Scaffold(
        contentWindowInsets = WindowInsets.safeDrawing,
        modifier = Modifier.testTag("compact_scaffold"),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
            ) {
                NavigationTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.testTag(tab.tag)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Screen switching using elegant transitions
            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    fadeIn(tween(250)) togetherWith fadeOut(tween(200))
                },
                label = "compact_tab"
            ) { target ->
                when (target) {
                    NavigationTab.HOME -> HomeScreen(
                        viewModel = viewModel,
                        onNavigateFileTab = { selectedTab = NavigationTab.FILES }
                    )
                    NavigationTab.FILES -> FileBrowserScreen(
                        viewModel = viewModel
                    )
                    NavigationTab.TRANSFERS -> TransferTrackerScreen(
                        viewModel = viewModel
                    )
                    NavigationTab.SETTINGS -> SettingsScreen(
                        viewModel = viewModel
                    )
                }
            }
        }
    }
}

@Composable
fun ExpandedLayout(viewModel: EchoViewModel) {
    var selectedTab by remember { mutableStateOf(NavigationTab.FILES) }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // Vertical Navigation Rail
        NavigationRail(
            containerColor = MaterialTheme.colorScheme.surface,
            header = {
                Icon(
                    imageVector = Icons.Rounded.AllInclusive,
                    contentDescription = "EchoSystem",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(vertical = 20.dp)
                        .size(32.dp)
                )
            }
        ) {
            Spacer(Modifier.weight(1f))
            
            // Files, Transfers, and Settings tabs run in the flanking rail
            val railTabs = listOf(NavigationTab.FILES, NavigationTab.TRANSFERS, NavigationTab.SETTINGS)
            railTabs.forEach { tab ->
                NavigationRailItem(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    icon = {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.title,
                            modifier = Modifier.testTag(tab.tag)
                        )
                    },
                    label = { Text(tab.title) },
                    colors = NavigationRailItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Spacer(Modifier.weight(1f))
        }

        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Master-Detail Two Pane Layout
        Row(modifier = Modifier.weight(1f)) {
            // Lefthand Master Pane: Always show scanner & nearby devices
            Box(
                modifier = Modifier
                    .weight(1.0f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(top = 8.dp)
            ) {
                HomeScreen(
                    viewModel = viewModel,
                    onNavigateFileTab = { selectedTab = NavigationTab.FILES }
                )
            }

            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Righthand Detail Pane: Show active detailed tabs (Files, Active list, Settings)
            Box(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                    .padding(top = 8.dp)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(tween(200)) togetherWith fadeOut(tween(150))
                    },
                    label = "expanded_detail"
                ) { target ->
                    when (target) {
                        NavigationTab.FILES -> FileBrowserScreen(
                            viewModel = viewModel
                        )
                        NavigationTab.TRANSFERS -> TransferTrackerScreen(
                            viewModel = viewModel
                        )
                        NavigationTab.SETTINGS -> SettingsScreen(
                            viewModel = viewModel
                        )
                        else -> Box(Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
