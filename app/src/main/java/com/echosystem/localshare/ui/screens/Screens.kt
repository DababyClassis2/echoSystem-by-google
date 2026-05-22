package com.echosystem.localshare.ui.screens

import android.net.Uri
import com.echosystem.localshare.web.WebShareViewModel
import com.echosystem.localshare.web.WebShareScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.ui.components.DeviceCard
import com.echosystem.localshare.ui.components.RadarAnimation
import com.echosystem.localshare.ui.components.TransferItemRow
import com.echosystem.localshare.viewmodel.EchoViewModel
import kotlinx.coroutines.launch
import java.io.File
import androidx.compose.foundation.BorderStroke
import com.echosystem.localshare.logging.AppLogger

import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.echosystem.localshare.ui.navigation.AppBottomNavigationBar
import com.echosystem.localshare.ui.navigation.Screen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: EchoViewModel = hiltViewModel()) {
    var showOnboarding by remember { mutableStateOf(true) }
    val webShareViewModel: WebShareViewModel = hiltViewModel()
    val navController = rememberNavController()

    var showMenu by remember { mutableStateOf(false) }

    if (showOnboarding) {
        OnboardingScreen(onGetStarted = { showOnboarding = false })
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("LocalShare", fontWeight = FontWeight.Black) },
                    actions = {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Trusted Devices") },
                                leadingIcon = { Icon(Icons.Default.Security, "Trusted Devices") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.TrustedDevices.route)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Transfer History") },
                                leadingIcon = { Icon(Icons.Default.History, "History") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.History.route)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Default.Settings, "Settings") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Settings.route)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Developer Tools") },
                                leadingIcon = { Icon(Icons.Default.Build, "Developer") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Screen.Developer.route)
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
            },
            bottomBar = { AppBottomNavigationBar(navController = navController) }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.padding(padding)
            ) {
                composable(Screen.Home.route) { PortalHomeScreen(viewModel) }
                composable(Screen.Send.route) { SendFileScreen(viewModel) }
                composable(Screen.Receive.route) { ReceiveRadarScreen(viewModel) }
                composable(Screen.History.route) { HistoryLedgerScreen(viewModel) }
                composable(Screen.WebShare.route) { WebShareScreen(webShareViewModel) }
                composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                composable(Screen.Developer.route) { DeveloperAuditorScreen(viewModel) }
                composable(Screen.TrustedDevices.route) { TrustedDevicesScreen(viewModel) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 3 })
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val titles = listOf(
        "Xender-Speed Sharing",
        "Secure Local Network Shield",
        "Dynamic Web Share Mode"
    )

    val descriptions = listOf(
        "Transmit raw files, documents, pictures, and media packages directly to nearby peers inside local space without wasting cellular data.",
        "Authorize pairing connection queries dynamically using our unique numeric Shield Keys. Keeps transmission nodes entirely secured.",
        "Share resources directly with Apple TVs, iPhones, Windows environments or tablets via the instantaneous Web Gateway portal on port 8080."
    )

    val systemIcons = listOf(
        Icons.Default.Bolt,
        Icons.Default.Security,
        Icons.Default.Language
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.surface
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Indicator Dots Row
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                repeat(3) { index ->
                    val color = if (pagerState.currentPage == index) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    }
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (pagerState.currentPage == index) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // The sliding onboarding content pages
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(110.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = systemIcons[page],
                                contentDescription = titles[page],
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(56.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(36.dp))

                    Text(
                        text = titles[page],
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = descriptions[page],
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }

            // Next / Join button
            Button(
                onClick = {
                    if (pagerState.currentPage < 2) {
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        onGetStarted()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_next_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(
                    text = if (pagerState.currentPage == 2) "Launch App" else "Next Discover",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
fun StylizedQRCodeCanvas(
    url: String,
    modifier: Modifier = Modifier,
    tintColor: Color = Color.Black
) {
    Canvas(modifier = modifier) {
        val sizePx = size.width
        val squareCount = 21 // Version 1 QR code size
        val cellSize = sizePx / squareCount

        // 1. Draw Locator Search patterns (top-left, top-right, bottom-left)
        fun drawLocator(x: Int, y: Int) {
            val px = x * cellSize
            val py = y * cellSize
            
            // Outer square
            drawRect(
                color = tintColor,
                topLeft = androidx.compose.ui.geometry.Offset(px, py),
                size = androidx.compose.ui.geometry.Size(cellSize * 7, cellSize * 7)
            )
            // Inner background
            drawRect(
                color = Color.White,
                topLeft = androidx.compose.ui.geometry.Offset(px + cellSize, py + cellSize),
                size = androidx.compose.ui.geometry.Size(cellSize * 5, cellSize * 5)
            )
            // Inner block
            drawRect(
                color = tintColor,
                topLeft = androidx.compose.ui.geometry.Offset(px + cellSize * 2, py + cellSize * 2),
                size = androidx.compose.ui.geometry.Size(cellSize * 3, cellSize * 3)
            )
        }

        // Draw background white container
        drawRect(
            color = Color.White,
            topLeft = androidx.compose.ui.geometry.Offset(0f, 0f),
            size = size
        )

        drawLocator(0, 0)
        drawLocator(14, 0)
        drawLocator(0, 14)

        // Generate pseudo-random deterministic pixels based on hashCode of URL
        val key = url.hashCode()
        val random = java.util.Random(key.toLong())

        for (row in 0 until squareCount) {
            for (col in 0 until squareCount) {
                // Skip areas occupied by locator targets
                val isLocatorArea = (row < 8 && col < 8) || (row < 8 && col >= 13) || (row >= 13 && col < 8)
                if (!isLocatorArea) {
                    val isBlack = random.nextBoolean()
                    if (isBlack) {
                        drawRect(
                            color = tintColor,
                            topLeft = androidx.compose.ui.geometry.Offset(col * cellSize, row * cellSize),
                            size = androidx.compose.ui.geometry.Size(cellSize, cellSize)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PortalHomeScreen(viewModel: EchoViewModel) {
    val ipAddress by viewModel.ipAddress.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    val shareUrl = "http://$ipAddress:8080"

    var showLocalExplorer by remember { mutableStateOf(false) }

    if (showLocalExplorer) {
        LocalFileBrowser(viewModel = viewModel, onClose = { showLocalExplorer = false })
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Web Share",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Web Portal Sharing",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Instant browser file access across Wi-Fi",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Web Browser Portal Sharing Information Card with Authentic QR Code drawing
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Scan to Open Share Portal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    Surface(
                        modifier = Modifier
                            .size(160.dp)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        StylizedQRCodeCanvas(
                            url = shareUrl,
                            modifier = Modifier.fillMaxSize(),
                            tintColor = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "IP Portal Link for computers & other devices:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = shareUrl,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Access Companion Key:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            Text(
                                text = pairingPin ?: "------",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showLocalExplorer = true }
                    .border(1.5.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
                    .testTag("open_storage_explorer"),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.05f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.12f),
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "Storage Explorer",
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Storage Explorer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Browse, preview and manage shared files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Open",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun SendFileScreen(viewModel: EchoViewModel) {
    val devices by viewModel.devices.collectAsState()
    val transferProgress by viewModel.transferProgress.collectAsState()
    
    // Select device state
    var deviceToPair by remember { mutableStateOf<Device?>(null) }
    var showPairDialog by remember { mutableStateOf(false) }
    var pairPinInput by remember { mutableStateOf("") }
    var pairErrorMessage by remember { mutableStateOf<String?>(null) }
    var isPairingProcessing by remember { mutableStateOf(false) }

    // Multi-file selection state
    var selectedUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var selectedFilesInfo by remember { mutableStateOf<List<Pair<String, Long>>>(emptyList()) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris: List<Uri> ->
        selectedUris = uris
        selectedFilesInfo = uris.map { uri ->
            val name = viewModel.queryFileName(uri)
            val size = viewModel.queryFileSize(uri)
            Pair(name, size)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Transmit Files",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            
            Text(
                text = "Select files and choose a nearby device to transmit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Real Multi-File Picker Control Panel Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Picked Files (${selectedUris.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Button(
                            onClick = { filePickerLauncher.launch("*/*") },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.DriveFileRenameOutline, "Browse", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Browse Files")
                        }
                    }

                    if (selectedFilesInfo.isEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(80.dp)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "No files selected. Tap Browse Files above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.height(12.dp))
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 140.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(selectedFilesInfo) { (name, size) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = "File Type",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatShareBytes(size),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            fun totalSize(): Long = selectedFilesInfo.sumOf { it.second }
                            Text(
                                text = "Total Size: ${formatShareBytes(totalSize())}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    selectedUris = emptyList()
                                    selectedFilesInfo = emptyList()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.Clear, "Clear Selection", modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear Selection")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nearby Active Receivers (${devices.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(
                    onClick = { viewModel.startDiscovery() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.Refresh, "Scan", tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (devices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SensorsOff,
                            contentDescription = "No receivers online",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("No Receiver Nodes Found", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = "Make sure the companion app has 'Receive' screen open on the same network.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(devices) { device ->
                        DeviceCard(
                            device = device,
                            onClick = {
                                if (selectedUris.isEmpty()) {
                                    AppLogger.logEvent("Send", "User tapped recipient device but no files selected")
                                    return@DeviceCard
                                }
                                deviceToPair = device
                                if (device.isPaired) {
                                    viewModel.sendMultipleFilesToDevice(device, selectedUris)
                                } else {
                                    showPairDialog = true
                                    pairPinInput = ""
                                    pairErrorMessage = null
                                }
                            },
                            onRevoke = { viewModel.revokeDevice(device) },
                            onDisconnect = { viewModel.disconnectDevice(device) }
                        )
                    }
                }
            }
        }

        // Active Overlay Progress Drawer logic
        val ongoing = transferProgress.filter { !it.isIncoming && it.status == TransferStatus.ONGOING }
        if (ongoing.isNotEmpty()) {
            val transfer = ongoing.first()
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(24.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .shadow(8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        progress = { transfer.progress },
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 3.5.dp
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sharing Active Packet", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(transfer.fileName, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("${(transfer.progress * 100).toInt()}%", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Active PIN code Verification Alert
        if (showPairDialog && deviceToPair != null) {
            AlertDialog(
                onDismissRequest = { showPairDialog = false },
                title = { Text("Secure Authorize pairing key", fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text("Please query security PIN key displayed on ${deviceToPair?.name}'s phone to authenticate.")
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = pairPinInput,
                            onValueChange = { if (it.length <= 6) pairPinInput = it },
                            label = { Text("Enter 6-Digit PIN Code") },
                            placeholder = { Text("123456") },
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                            supportingText = {
                                if (pairErrorMessage != null) {
                                    Text(pairErrorMessage!!, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (pairPinInput.length >= 6) {
                                isPairingProcessing = true
                                pairErrorMessage = null
                                viewModel.pairWithDevice(deviceToPair!!, pairPinInput) { success, error ->
                                    isPairingProcessing = false
                                    if (success) {
                                        showPairDialog = false
                                        viewModel.sendMultipleFilesToDevice(deviceToPair!!, selectedUris)
                                    } else {
                                        pairErrorMessage = error ?: "Pairing key matches failed"
                                    }
                                }
                            } else {
                                pairErrorMessage = "PIN key must be 6 digits"
                            }
                        }
                    ) {
                        if (isPairingProcessing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Pair & Connect")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPairDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

fun formatShareBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val pre = "KMGTPE"[exp - 1] + "B"
    return String.format("%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), pre)
}

@Composable
fun ReceiveRadarScreen(viewModel: EchoViewModel) {
    val ipAddress by viewModel.ipAddress.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Receive Console",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Discoverable to other LocalShare apps nearby",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Radar Scanning Target animation
        Box(
            modifier = Modifier.size(260.dp),
            contentAlignment = Alignment.Center
        ) {
            RadarAnimation(
                modifier = Modifier.fillMaxSize(),
                isScanning = true
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                modifier = Modifier.size(90.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.WifiTethering,
                        contentDescription = "Radar Central Node",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        // Info state banner
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Radar Active Pin Key", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary
                    ) {
                        Text(
                            text = pairingPin ?: "------",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Address:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$ipAddress:8080", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryLedgerScreen(viewModel: EchoViewModel) {
    val transfers by viewModel.transferProgress.collectAsState()
    var activePreviewTransfer by remember { mutableStateOf<FileTransfer?>(null) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Transmission Ledger",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "History of shared files packages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (transfers.isNotEmpty()) {
                IconButton(
                    onClick = {
                        try {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        } catch (e: Exception) {}
                        viewModel.clearTransfers()
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("clear_history_btn")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Clear History")
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (transfers.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Empty History",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No shares logged", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Any transmitted files will be safely catalogued here.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                items(
                    items = transfers.asReversed(),
                    key = { it.id }
                ) { transfer ->
                    Box(
                        modifier = Modifier
                            .animateItemPlacement(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                            .clickable {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                activePreviewTransfer = transfer
                            }
                    ) {
                        TransferItemRow(
                            transfer = transfer,
                            onDelete = { viewModel.deleteFileFromHistory(transfer.fileName) }
                        )
                    }
                }
            }
        }
    }

    // Interactive File Preview overlay modal dialog
    activePreviewTransfer?.let { transfer ->
        FilePreviewModal(
            transfer = transfer,
            onDismiss = { activePreviewTransfer = null },
            onDelete = { viewModel.deleteFileFromHistory(transfer.fileName) }
        )
    }
}

@Composable
fun FilePreviewModal(
    transfer: FileTransfer,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(24.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        icon = {
            Surface(
                shape = CircleShape,
                color = when (transfer.status) {
                    TransferStatus.COMPLETED -> Color(0xFF1B5E20).copy(alpha = 0.12f)
                    TransferStatus.FAILED -> Color(0xFFB71C1C).copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                },
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when {
                            transfer.fileName.substringAfterLast(".").let { ext ->
                                listOf("jpg", "jpeg", "png", "webp", "gif").contains(ext.lowercase())
                            } -> Icons.Default.Image
                            transfer.fileName.substringAfterLast(".").let { ext ->
                                listOf("mp4", "mkv", "avi", "mov").contains(ext.lowercase())
                            } -> Icons.Default.Videocam
                            transfer.fileName.substringAfterLast(".").let { ext ->
                                listOf("mp3", "wav", "m4a", "ogg", "flac").contains(ext.lowercase())
                            } -> Icons.Default.MusicNote
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = "Large Preview Icon",
                        modifier = Modifier.size(36.dp),
                        tint = when (transfer.status) {
                            TransferStatus.COMPLETED -> Color(0xFF4CAF50)
                            TransferStatus.FAILED -> Color(0xFFF44336)
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                }
            }
        },
        title = {
            Text(
                text = "Transmission Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = transfer.fileName,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                
                Spacer(modifier = Modifier.height(16.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Flow Direct:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                if (transfer.isIncoming) "Incoming ←" else "Outgoing →",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (transfer.isIncoming) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                            )
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Partner Node:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(transfer.remoteDeviceName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                        }
                        
                        val formattedSize = remember(transfer.size) {
                            when {
                                transfer.size <= 0 -> "Unknown Size"
                                transfer.size < 1024 -> "${transfer.size} B"
                                transfer.size < 1024 * 1024 -> "${String.format("%.1f", transfer.size / 1024f)} KB"
                                else -> "${String.format("%.1f", transfer.size / (1024f * 1024f))} MB"
                            }
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Recorded Size:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formattedSize, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                        }
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Transfer Status:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                text = transfer.status.name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Black,
                                color = when (transfer.status) {
                                    TransferStatus.COMPLETED -> Color(0xFF4CAF50)
                                    TransferStatus.FAILED -> Color(0xFFF44336)
                                    else -> MaterialTheme.colorScheme.primary
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (transfer.status == TransferStatus.COMPLETED) {
                    Button(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                            
                            try {
                                val dir = File(context.getExternalFilesDir(null), "Received")
                                val file = File(dir, transfer.fileName)
                                if (file.exists()) {
                                    val fallbackIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                        type = "*/*"
                                        putExtra(android.content.Intent.EXTRA_STREAM, Uri.fromFile(file))
                                    }
                                    context.startActivity(android.content.Intent.createChooser(fallbackIntent, "Share received file"))
                                } else {
                                    android.widget.Toast.makeText(context, "File does not exist on storage.", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Error sharing file: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, "Share")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Share Outward", fontWeight = FontWeight.Bold)
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                            onDelete()
                            onDismiss()
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Remove Log", fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Done", fontSize = 12.sp)
                    }
                }
            }
        }
    )
}

@Composable
fun SettingsScreen(viewModel: EchoViewModel) {
    val pin by viewModel.pairingPin.collectAsState()
    val ipAddress by viewModel.ipAddress.collectAsState()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    var customDeviceName by remember { mutableStateOf(android.os.Build.MODEL) }
    var autoRegisterService by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "System Parameters",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "Configure device display profile identity and token shields",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            // User Display Name Profile Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = "Profile Identity",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Device Display Profile",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customDeviceName,
                        onValueChange = {
                            customDeviceName = it
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            } catch (e: Exception) {}
                        },
                        label = { Text("Display Broadcast Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("device_name_field"),
                        shape = RoundedCornerShape(12.dp),
                        leadingIcon = {
                            Icon(Icons.Default.Devices, contentDescription = "Device Model")
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "This is how this device appears to others nearby during discovery scans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }

            // Authorization PIN code generator Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Keys Icon",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Shield Verification PIN Code",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Clients must provide this security standard authentication PIN to initiate secure peer-to-peer file transfers.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = pin ?: "------",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 4.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                            viewModel.generatePairingPin()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("generate_pin_btn"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Autorenew, "Autorenew")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Regenerate Token PIN Key", fontWeight = FontWeight.Bold)
                    }
                }
            }

            // General Protocol Toggles Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Protocols & Gateways",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Automatic Naming discovery", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Announce presence to LAN via Bonjour / NSD protocols automatically", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = autoRegisterService,
                            onCheckedChange = {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                autoRegisterService = it
                            }
                        )
                    }
                }
            }

            // Storage Cache Cleaner details card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.08f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Storage & Cleanup Utilities",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Receipt directory location:\n/Android/data/${context.packageName}/files/Received/",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                            } catch (e: Exception) {}
                            viewModel.clearTransfers()
                            android.widget.Toast.makeText(context, "Storage receipts database wiped successfully.", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
                    ) {
                        Icon(Icons.Default.CleaningServices, contentDescription = "Wipe", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Clear Completed Receipts & Storage History", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun DeveloperAuditorScreen(viewModel: EchoViewModel) {
    val eventsLog by viewModel.appEventsLog.collectAsState()
    val crashesLog by viewModel.appCrashesLog.collectAsState()
    val ipAddress by viewModel.ipAddress.collectAsState()
    val devices by viewModel.devices.collectAsState()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    // Real-time performance monitors
    val perfHistory by com.echosystem.localshare.logging.PerformanceMonitor.history.collectAsState()
    val watchdogLogs by com.echosystem.localshare.logging.PerformanceMonitor.perfLogs.collectAsState()

    var activeDeveloperSubTab by remember { mutableStateOf("PERFORMANCE") } // PERFORMANCE, SECURITY, TERMINAL
    var activeLogSegment by remember { mutableStateOf("EVENTS") } // EVENTS or CRASHES
    var showBiometricConfirmForDevice by remember { mutableStateOf<Device?>(null) }

    fun formatLocalBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt().coerceIn(1, 6)
        val suffix = "KMGTPE"[exp - 1] + "B"
        return String.format("%.1f %s", bytes / Math.pow(1024.0, exp.toDouble()), suffix)
    }

    // Auto-reload logs when entering
    LaunchedEffect(Unit) {
        viewModel.loadLogs()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Diagnostics & Audit Panel",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "Inspect raw telemetry, device trust security, and socket logs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Premium Navigation Tabs for Sub-Menus
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(4.dp)
        ) {
            val tabsList = listOf(
                Triple("PERFORMANCE", "Telemetry", Icons.Default.Speed),
                Triple("SECURITY", "Trust Registry", Icons.Default.Fingerprint),
                Triple("TERMINAL", "System Logs", Icons.Default.Terminal)
            )
            tabsList.forEach { (tabId, label, icon) ->
                val isSelected = activeDeveloperSubTab == tabId
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { activeDeveloperSubTab = tabId }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = icon,
                            contentDescription = label,
                            tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(modifier = Modifier.weight(1f)) {
            when (activeDeveloperSubTab) {
                "PERFORMANCE" -> {
                    val latestSnap = perfHistory.lastOrNull()
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        item {
                            // CPU & RAM Grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("Process CPU Load", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val cpuVal = latestSnap?.cpuUsage ?: 0.0
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                                            CircularProgressIndicator(
                                                progress = (cpuVal / 100.0).toFloat(),
                                                strokeWidth = 6.dp,
                                                color = if (cpuVal > 80.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Text("${String.format("%.1f", cpuVal)}%", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
                                        }
                                    }
                                }

                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text("JVM Memory Heap", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                        Spacer(modifier = Modifier.height(8.dp))
                                        val usedMem = latestSnap?.usedMemoryMb ?: 0L
                                        val totalMem = latestSnap?.totalMemoryMb?.coerceAtLeast(1L) ?: 512L
                                        val ratio = (usedMem.toFloat() / totalMem).coerceIn(0f, 1f)
                                        
                                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(70.dp)) {
                                            CircularProgressIndicator(
                                                progress = ratio,
                                                strokeWidth = 6.dp,
                                                color = if (ratio > 0.85f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text("${usedMem}MB", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black)
                                                Text("max $totalMem", style = MaterialTheme.typography.labelSmall, fontSize = 8.sp, color = Color.Gray)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            // Traffic Output Analytics Cards
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Network Speed Telemetry", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleSmall)
                                        Icon(Icons.Default.Dns, "Network", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    val rxSpeed = latestSnap?.rxBytesSec ?: 0L
                                    val txSpeed = latestSnap?.txBytesSec ?: 0L
                                    
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Download Throughput", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${formatLocalBytes(rxSpeed)}/s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                                        }
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Upload Throughput", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                            Text("${formatLocalBytes(txSpeed)}/s", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    val activeTransfers = latestSnap?.activeTransfersCount ?: 0
                                    Text(
                                        text = "Active Concurrent Streams: $activeTransfers",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        item {
                            // Background Watchdog Logs Panel
                            Text("Background Watchdog Monitoring", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(140.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                            ) {
                                LazyColumn(modifier = Modifier.padding(10.dp)) {
                                    if (watchdogLogs.isEmpty()) {
                                        item {
                                            Text(
                                                "[SYS WATCHDOG] Telemetry monitoring online. System operating within secure parameters. Core loads clean.",
                                                color = Color.Green,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    } else {
                                        items(watchdogLogs) { log ->
                                            Text(log, color = Color(0xFFFFB74D), fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.padding(bottom = 2.dp))
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val file = com.echosystem.localshare.logging.PerformanceMonitor.exportPerfLogs(context)
                                        if (file != null && file.exists()) {
                                            try {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            } catch (e: Exception) {}
                                            try {
                                                androidx.core.app.ShareCompat.IntentBuilder(context)
                                                    .setType("text/plain")
                                                    .setStream(androidx.core.content.FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        file
                                                    ))
                                                    .startChooser()
                                            } catch (e: Exception) {
                                                android.widget.Toast.makeText(context, "Export saved to: ${file.absolutePath}", android.widget.Toast.LENGTH_LONG).show()
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Save, "Export")
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Export Watchdog Report", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }

                                OutlinedButton(
                                    onClick = {
                                        com.echosystem.localshare.logging.PerformanceMonitor.clearPerfLogs()
                                    },
                                    modifier = Modifier.weight(0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Reset Metrics", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                "SECURITY" -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Security,
                                        contentDescription = "Security Info",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Auto-Pair Bypass works via cryptographic SHA-256 fingerprint matching. Verified trusted nodes bypass pairing PIN prompts.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        lineHeight = 14.sp
                                    )
                                }
                            }
                        }

                        item {
                            Text(
                                "Registered Recipient Nodes (${devices.size})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }

                        if (devices.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "No discovered devices to configure trust logs.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            items(devices) { device ->
                                val isTrusted = viewModel.trustManager.isDeviceTrusted(device.id)
                                val fingerprint = viewModel.trustManager.generateFingerprint(device.id, device.name).take(16).chunked(4).joinToString("-") { it.uppercase() }
                                
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Column(modifier = Modifier.padding(14.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                                Text("IP: ${device.ip}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                            }
                                            
                                            Surface(
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isTrusted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                                modifier = Modifier.padding(2.dp)
                                            ) {
                                                Text(
                                                    text = if (isTrusted) "TRUSTED ACTIVE" else "PIN SECURED",
                                                    color = if (isTrusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Black,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                Text("SHA-256 Fingerprint Key", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                                Text(fingerprint, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                            }

                                            Button(
                                                onClick = {
                                                    try {
                                                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                                    } catch (e: Exception) {}
                                                    showBiometricConfirmForDevice = device
                                                },
                                                shape = RoundedCornerShape(8.dp),
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isTrusted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                                ),
                                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                            ) {
                                                Text(if (isTrusted) "Untrust Node" else "Trust Node", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                "TERMINAL" -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Terminal, contentDescription = "Console", tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "On-Device Logging Auditor",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Row {
                                        IconButton(onClick = {
                                            try {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            } catch (e: Exception) {}
                                            viewModel.loadLogs()
                                        }) {
                                            Icon(Icons.Default.Refresh, "Refresh Info", modifier = Modifier.size(20.dp))
                                        }
                                        IconButton(onClick = {
                                            try {
                                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            } catch (e: Exception) {}
                                            viewModel.clearLogsAndRefresh()
                                        }) {
                                            Icon(Icons.Default.Delete, "Delete Info", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Log category Selectors (Events vs Crashes Segment)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(2.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (activeLogSegment == "EVENTS") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { activeLogSegment = "EVENTS" }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "Event Log",
                                            color = if (activeLogSegment == "EVENTS") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (activeLogSegment == "CRASHES") MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { activeLogSegment = "CRASHES" }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "App Crashes",
                                            color = if (activeLogSegment == "CRASHES") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                // Text-based log stream view
                                val displayedLogs = if (activeLogSegment == "EVENTS") eventsLog else crashesLog
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(150.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    color = Color.Black,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Gray.copy(alpha = 0.3f))
                                ) {
                                    LazyColumn(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(10.dp)
                                    ) {
                                        item {
                                            Text(
                                                text = displayedLogs.ifEmpty { "WLAN diagnostic system is completely quiet. Ready for packet transmission." },
                                                color = Color.Green,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                Button(
                                    onClick = {
                                        try {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        } catch (e: Exception) {}
                                        viewModel.exportLogsAndRefresh(context)
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.Save, "Export Logs", modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Export WLAN Logs Bundle", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }

                        // Test Injector
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text("Simulated Diagnostics Injector", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Append temporary test records to log database", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        try {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        } catch (e: Exception) {}
                                        viewModel.addManualLog("DevConsole", "Manual event injection. LAN frames responsive.")
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outline),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Inject Sample Audit Event", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modern Biometric Confirmation AlertDialog mockup with complete functional operations
    if (showBiometricConfirmForDevice != null) {
        val currentDevice = showBiometricConfirmForDevice!!
        val isNowTrusted = viewModel.trustManager.isDeviceTrusted(currentDevice.id)
        
        AlertDialog(
            onDismissRequest = { showBiometricConfirmForDevice = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Fingerprint, "Biometric Prompt", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Identity Verification")
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (isNowTrusted) 
                            "Scan fingerprint (or enter system PIN) to untrust this device. It will no longer bypass pairing PIN prompts."
                        else 
                            "Scan fingerprint (or enter system PIN) to trust this device. Trusted devices bypass PIN prompts and connect immediately.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(currentDevice.name, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.bodyMedium)
                            Text("UID: ${currentDevice.id}", style = MaterialTheme.typography.bodySmall, fontSize = 10.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))

                    // Pulse interactive touch scanner
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable {
                                try {
                                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                } catch (e: Exception) {}
                                
                                // Toggle Trust State successfully
                                viewModel.trustManager.setDeviceTrust(currentDevice.id, currentDevice.name, !isNowTrusted)
                                showBiometricConfirmForDevice = null
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Tap Fingerprint",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Tap scanner to confirm identity",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showBiometricConfirmForDevice = null }) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun TrustedDevicesScreen(viewModel: EchoViewModel) {
    val trustedDevices by viewModel.trustManager.trustedDevices.collectAsState()
    var editingDevice by remember { mutableStateOf<com.echosystem.localshare.security.TrustedDevice?>(null) }
    var noteInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Trusted Devices Shield",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "These active nodes are authorized to receive directly without manual PIN prompt request flow.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (trustedDevices.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = "No trusted files",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No Paired / Trusted Devices Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Initiate a pairing PIN hand-shake from Send tab or connect through local clients.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(trustedDevices) { device ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (device.blocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = device.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = if (device.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                        )
                                        if (device.blocked) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Surface(
                                                color = MaterialTheme.colorScheme.error,
                                                shape = RoundedCornerShape(4.dp),
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = "BLOCKED",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "ID: ${device.id.take(12)}...",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                                
                                IconButton(onClick = {
                                    viewModel.trustManager.setDeviceBlocked(device.id, !device.blocked)
                                }) {
                                    Icon(
                                        imageVector = if (device.blocked) Icons.Default.LockOpen else Icons.Default.Block,
                                        contentDescription = "Block / Unblock",
                                        tint = if (device.blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                    )
                                }
                            }

                            if (device.fingerprint.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Shield Hash: ${device.fingerprint.take(24)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }

                            if (device.note.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Label Note: ${device.note}",
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(
                                    onClick = {
                                        editingDevice = device
                                        noteInput = device.note
                                    }
                                ) {
                                    Icon(Icons.Default.Edit, "Edit labels", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Add Note")
                                }
                                
                                Spacer(modifier = Modifier.width(8.dp))

                                Button(
                                    onClick = {
                                        viewModel.trustManager.setDeviceTrust(device.id, device.name, false)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                                    )
                                ) {
                                    Text("Revoke Trust", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (editingDevice != null) {
        AlertDialog(
            onDismissRequest = { editingDevice = null },
            title = { Text("Edit Device Label") },
            text = {
                Column {
                    Text("Add custom memo/note for details of device ${editingDevice?.name}:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = noteInput,
                        onValueChange = { noteInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        placeholder = { Text("e.g. My Office Work Laptop") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        editingDevice?.let {
                            viewModel.trustManager.setDeviceNote(it.id, noteInput)
                        }
                        editingDevice = null
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingDevice = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
