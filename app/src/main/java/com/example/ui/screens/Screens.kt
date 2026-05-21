package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.model.*
import com.example.ui.components.*
import com.example.ui.theme.*
import com.example.viewmodel.EchoViewModel
import android.content.Context
import androidx.compose.foundation.text.selection.SelectionContainer

// ==========================================
// 1. ONBOARDING SCREEN
// ==========================================
@Composable
fun OnboardingScreen(
    viewModel: EchoViewModel,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var currentPage by remember { mutableIntStateOf(0) }
    val pages = listOf(
        OnboardingPageData(
            title = "EchoSystem peer-to-peer",
            description = "Welcome to EchoSystem LocalShare. Move documents, media, and folders to other devices nearby instantly — absolutely offline.",
            icon = Icons.Rounded.AllInclusive
        ),
        OnboardingPageData(
            title = "Multi-Protocol Radar",
            description = "Automatically scans nearby environments using BLE beacons, mDNS/NSD local service trackers, and UDP broadcast channels simultaneously.",
            icon = Icons.Rounded.Radar
        ),
        OnboardingPageData(
            title = "Zero Size Restrictions",
            description = "Direct secure peer-to-peer pipelines run at full Wi-Fi channel rates. No cloud buffers, no account registration, no size boundaries.",
            icon = Icons.Rounded.OfflineShare
        ),
        OnboardingPageData(
            title = "Secure & Private",
            description = "Encryption handshakes and matching verify PIN displays. Give required system wireless permissions to start scanning safely.",
            icon = Icons.Rounded.Security
        )
    )

    val currentData = pages[currentPage]

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual decorative container with Professional Premium Metallic Vault logo
            Box(
                modifier = Modifier.padding(bottom = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                EnterpriseVaultLogo(size = 150.dp)
            }

            Spacer(Modifier.height(32.dp))

            AnimatedContent(
                targetState = currentData.title,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "title"
            ) { titleText ->
                Text(
                    text = titleText,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = currentData.description,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "desc"
            ) { descText ->
                Text(
                    text = descText,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }

        // Bottom controller row
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Page Dot Indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pages.forEachIndexed { index, _ ->
                    Box(
                        modifier = Modifier
                            .size(if (index == currentPage) 12.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (index == currentPage) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Action CTAs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (currentPage < pages.lastIndex) {
                    TextButton(onClick = {
                        viewModel.completeOnboarding()
                        onDismiss()
                    }) {
                        Text("Skip", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = { currentPage++ },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Next", style = MaterialTheme.typography.labelLarge)
                    }
                } else {
                    Button(
                        onClick = {
                            viewModel.completeOnboarding()
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("onboarding_complete_button"),
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Grant Permissions & Start", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

data class OnboardingPageData(val title: String, val description: String, val icon: ImageVector)

// ==========================================
// 2. HOME SCREEN (SCANNING RADAR & DEVICES)
// ==========================================
@Composable
fun HomeScreen(
    viewModel: EchoViewModel,
    onNavigateFileTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isScanning by viewModel.isScanning.collectAsState()
    val devices by viewModel.devicesList.collectAsState()
    val recentTransfers by viewModel.historyRecords.collectAsState()
    val protocolsEnabled by viewModel.protocols.collectAsState()

    // Core P2P physical/wireless permissions required for sound background socket file transfer operations
    val corePermissions = remember {
        val list = mutableListOf<String>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            list.add(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            list.add(android.Manifest.permission.POST_NOTIFICATIONS)
            list.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            list.add(android.Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            list.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
            list.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            list.add(android.Manifest.permission.BLUETOOTH_SCAN)
            list.add(android.Manifest.permission.BLUETOOTH_CONNECT)
            list.add(android.Manifest.permission.BLUETOOTH_ADVERTISE)
        }
        list
    }

    var basePermissionsGranted by remember {
        mutableStateOf(
            corePermissions.all {
                androidx.core.content.ContextCompat.checkSelfPermission(context, it) == android.content.pm.PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        basePermissionsGranted = results.values.all { it }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("home_screen_column")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        // RADAR PERMISSION OUTLINE EXPLANATION
        if (!basePermissionsGranted) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Radar Clearances Required",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "To scan for nearby offline peers, establish high-speed direct links, and transfer media securely (like Xender), EchoSystem requires system wireless and local storage permissions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(14.dp))
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                permissionLauncher.launch(corePermissions.toTypedArray())
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Grant All Required Radar Access", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // RADAR ACTION CARD
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isScanning) "Discoverability Radar Active" else "Discovery Scanner Off-line",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = if (isScanning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (isScanning) "Others on your local subnetwork see you automatically" else "Your device is hidden. Turn scan on to start connecting",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp, horizontal = 12.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    // Pulse circles container with elegant EnterpriseVaultLogo nested
                    Box(
                        modifier = Modifier.size(160.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        RadarAnimation(isActive = isScanning) {
                            EnterpriseVaultLogo(size = 80.dp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.toggleScanning()
                            },
                            shape = PillShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) MaterialTheme.colorScheme.outlineVariant 
                                                 else MaterialTheme.colorScheme.primary
                            ),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isScanning) Icons.Rounded.PowerSettingsNew else Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (isScanning) "Stop Radiating" else "Start Scan",
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (isScanning) MaterialTheme.colorScheme.onSurface 
                                            else MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                }
            }
        }

        // WEB SHARE PORTAL CARD
        item {
            val ipAddress = viewModel.getLocalIpAddress()
            val webPort by viewModel.webServerPort.collectAsState()
            val context = androidx.compose.ui.platform.LocalContext.current
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), shape = CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Language,
                            contentDescription = "Web Portal",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Web Sharing Portal Active",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Connect any PC/Phone browser to link:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(4.dp))
                        
                        val shareUrl = "http://$ipAddress:$webPort"
                        SelectionContainer {
                            Text(
                                text = shareUrl,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                        }
                    }

                    Spacer(Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("Web Share Link", "http://$ipAddress:$webPort")
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "Link copied to clipboard!", android.widget.Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape = CircleShape)
                            .size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // DEVICES SECTION
        item {
            SectionHeader(
                title = "NEARBY PEERS",
                count = devices.size,
                action = "Re-Scan" to {
                    viewModel.forceScanRefresh()
                }
            )
        }

        // MANUAL IP LINKING CARD
        item {
            val context = androidx.compose.ui.platform.LocalContext.current
            var expandManual by remember { mutableStateOf(false) }
            
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expandManual = !expandManual }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AddLink,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Bypass Hotspot / VPN Limits (Direct Link IP)",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Icon(
                        imageVector = if (expandManual) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                if (expandManual) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Connected to PdaNet, a VPN, client-isolated Wi-Fi, or direct proxy? Enter their local IP (e.g., 10.0.0.5 or 192.168.43.1) and port (default 8080) to target them directly.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            var ipInput by remember { mutableStateOf("") }
                            var portInput by remember { mutableStateOf("8080") }
                            var customNameInput by remember { mutableStateOf("") }
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedTextField(
                                    value = ipInput,
                                    onValueChange = { ipInput = it },
                                    label = { Text("IP Address") },
                                    placeholder = { Text("192.168.x.x") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(1.5f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                                
                                OutlinedTextField(
                                    value = portInput,
                                    onValueChange = { portInput = it },
                                    label = { Text("Port") },
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.bodyMedium,
                                    shape = MaterialTheme.shapes.small,
                                    modifier = Modifier.weight(0.7f),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                    )
                                )
                            }
                            
                            OutlinedTextField(
                                value = customNameInput,
                                onValueChange = { customNameInput = it },
                                label = { Text("Custom Nickname (Optional)") },
                                placeholder = { Text("Enter custom name") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            
                            Button(
                                onClick = {
                                    if (ipInput.trim().isNotEmpty()) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.connectDeviceByIp(ipInput, portInput, customNameInput)
                                        android.widget.Toast.makeText(context, "Direct IP Link added!", android.widget.Toast.LENGTH_SHORT).show()
                                        ipInput = ""
                                        customNameInput = ""
                                        expandManual = false
                                    }
                                },
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Bind Direct Link")
                            }
                        }
                    }
                }
            }
        }

        if (devices.isEmpty() && isScanning) {
            items(2) {
                DeviceCardSkeleton()
            }
        } else if (devices.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.WifiOff, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "No Peer Devices Detected",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Ensure other network peers are on the same local Wi-Fi, have EchoSystem open, or check active protocol flags.",
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, start = 8.dp, end = 8.dp)
                        )
                    }
                }
            }
        } else {
            items(devices, key = { it.id }) { device ->
                DeviceCard(
                    device = device,
                    onSendClick = {
                        onNavigateFileTab()
                    },
                    onPairClick = {
                        // Trust device directly / starts the verify pin Display
                        viewModel.initiateSendToDevice(device)
                    },
                    onUnpairClick = {
                        viewModel.unpairDevice(device.id)
                    }
                )
            }
        }

        // RECENT RECORDS SECTION
        if (recentTransfers.isNotEmpty()) {
            item {
                SectionHeader(
                    title = "RECENT TRANSFERS",
                    action = "Clear" to { viewModel.clearHistory() },
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(recentTransfers.take(3), key = { it.id }) { record ->
                TransferHistoryItem(record = record)
            }
        }
    }
}

// ==========================================
// 3. FILE BROWSER SCREEN
// ==========================================
@Composable
fun FileBrowserScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Images", "Videos", "Documents", "Audio")

    val fileList by viewModel.allFiles.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val devices by viewModel.devicesList.collectAsState()

    var showSendDeviceSheet by remember { mutableStateOf(false) }

    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importFiles(uris)
        }
    }

    // Sort/filter files based on active tab
    val filteredFiles = remember(selectedCategory, fileList) {
        if (selectedCategory == "All") fileList else fileList.filter { it.category == selectedCategory }
    }

    val totalSelectedSize = remember(selectedFileIds, fileList) {
        fileList.filter { it.id in selectedFileIds }.sumOf { it.sizeBytes }
    }

    Box(modifier = modifier.fillMaxSize().testTag("file_browser_root")) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Category Slider Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                categories.forEach { cat ->
                    val isSel = cat == selectedCategory
                    FilterChip(
                        selected = isSel,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat) },
                        shape = PillShape,
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (fileList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "No Files Imported Yet",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Import physical files from local storage to instantly stream them to nearby devices in real time.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            pickerLauncher.launch("*/*")
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Add Real Files to Share")
                    }
                }
            } else {
                // Document Lists & Visual Grids
            if (selectedCategory == "Images") {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { img ->
                        val isChecked = selectedFileIds.contains(img.id)
                        Card(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleFileSelection(img.id)
                                },
                            shape = MaterialTheme.shapes.medium,
                            border = BorderStroke(
                                width = if (isChecked) 3.dp else 1.dp,
                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            ),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                                                 else MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Image,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = img.name,
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = img.sizeFormatted,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                if (isChecked) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(20.dp)
                                            .align(Alignment.TopEnd)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(3.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp)
                ) {
                    items(filteredFiles, key = { it.id }) { item ->
                        val isChecked = selectedFileIds.contains(item.id)
                        val icon = when (item.category) {
                            "Videos"    -> Icons.Rounded.Movie
                            "Documents" -> Icons.Rounded.Description
                            "Audio"     -> Icons.Rounded.AudioFile
                            else        -> Icons.Rounded.InsertDriveFile
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewModel.toggleFileSelection(item.id)
                                },
                            shape = MaterialTheme.shapes.medium,
                            colors = CardDefaults.cardColors(
                                containerColor = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                 else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(
                                width = if (isChecked) 2.dp else 1.dp,
                                color = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isChecked) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                                             else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                    modifier = Modifier.size(44.dp)
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = if (isChecked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }

                                Spacer(Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "${item.description}  •  ${item.sizeFormatted}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.toggleFileSelection(item.id)
                                    },
                                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        } // Close else for fileList.isEmpty()
    } // Close outer Column

        // FLOATING ACTION FOOTER OVERLAY
        AnimatedVisibility(
            visible = selectedFileIds.isNotEmpty(),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${selectedFileIds.size} files chosen",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Size: ${formatSize(totalSelectedSize)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSendDeviceSheet = true
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text("Send Direct", style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Rounded.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // POPUP SHEET TARGET DEVICE SELECTOR
        if (showSendDeviceSheet) {
            Dialog(
                onDismissRequest = { showSendDeviceSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 0.dp),
                    shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Choose Target Device",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { showSendDeviceSheet = false }) {
                                Icon(Icons.Rounded.Close, contentDescription = "Close")
                            }
                        }

                        Text(
                            "Select which nearby peer receives these files.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )

                        if (devices.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No visible devices found. Turn scanner on.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.heightIn(max = 240.dp)
                            ) {
                                items(devices) { peer ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                showSendDeviceSheet = false
                                                viewModel.initiateSendToDevice(peer)
                                            },
                                        shape = MaterialTheme.shapes.medium,
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.PhoneAndroid,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(24.dp),
                                                contentDescription = null
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    peer.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    peer.ip,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(Icons.Rounded.ChevronRight, contentDescription = null)
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. TRANSFER TRACKER DASHBOARD
// ==========================================
@Composable
fun TransferTrackerScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val sessions by viewModel.transferSessions.collectAsState()
    val history by viewModel.historyRecords.collectAsState()

    var speedUnitText by remember { mutableStateOf("Simulation Mode active") }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("transfer_dashboard_body")
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
    ) {
        // ACTIVE RUNNING SESSIONS
        if (sessions.any { it.status == SessionStatus.ONGOING || it.status == SessionStatus.PAUSED }) {
            item {
                SectionHeader(title = "ACTIVE CHANNELS")
            }

            items(sessions.filter { it.status == SessionStatus.ONGOING || it.status == SessionStatus.PAUSED }, key = { it.id }) { s ->
                val overallPercent = s.progressPercent
                val isOngoing = s.status == SessionStatus.ONGOING

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (s.isSending) Icons.Rounded.ArrowCircleUp else Icons.Rounded.ArrowCircleDown,
                                    tint = if (s.isSending) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                    contentDescription = null,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = if (s.isSending) "Sending to ${s.remoteDevice.name}" else "Receiving from ${s.remoteDevice.name}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            if (isOngoing) {
                                // Dynamic signal stream particles flow
                                TransferParticlesAnimation(modifier = Modifier.width(60.dp).height(20.dp))
                            }
                        }

                        Spacer(Modifier.height(10.dp))

                        // Linear Progress Indicators
                        LinearProgressIndicator(
                            progress = { overallPercent },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(PillShape),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.outlineVariant
                        )

                        Spacer(Modifier.height(8.dp))

                        // Progress statistics
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Size: ${formatSize(s.progressBytes)} of ${formatSize(s.totalBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${(overallPercent * 100).toInt()}%",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            )
                        }

                        // Stream stats speed rates and cues
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        ) {
                            Text(
                                text = "Transfer Speed: ${s.speedFormatted}",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            if (isOngoing) {
                                Text(
                                    text = if (s.etaSeconds >= 0) "ETA: ${s.etaSeconds}s" else "ETA: Calculating...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Text(
                                    text = "PAUSED",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                    color = EchoWarning
                                )
                            }
                        }

                        // Queued files details expansion
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                s.files.take(2).forEach { file ->
                                    Row(
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = file.name,
                                            style = MaterialTheme.typography.labelSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (file.isCompleted) {
                                            Text(
                                                text = "Complt", 
                                                style = MaterialTheme.typography.labelSmall.copy(color = EchoSuccess, fontWeight = FontWeight.Bold)
                                            )
                                        } else {
                                            Text(
                                                text = "${(file.progressPercent * 100).toInt()}%", 
                                                style = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            )
                                        }
                                    }
                                }
                                if (s.files.size > 2) {
                                    Text(
                                        text = "+ ${s.files.size - 2} more files in queue...",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Controls
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isOngoing) {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.pauseSession(s.id)
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.outlineVariant),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Rounded.Pause, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pause", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onSurface))
                                }
                            } else {
                                Button(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.resumeSession(s.id)
                                    },
                                    modifier = Modifier.weight(1f).height(36.dp),
                                    shape = PillShape,
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    contentPadding = PaddingValues(0.dp)
                                ) {
                                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Resume", style = MaterialTheme.typography.labelMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer))
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.cancelSession(s.id)
                                },
                                modifier = Modifier.weight(1f).height(36.dp),
                                shape = PillShape,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Cancel", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }

        // HISTORIC TRANSFERS
        item {
            SectionHeader(
                title = "PAST RECORDS",
                count = history.size,
                action = if (history.isNotEmpty()) "Reset Logs" to { viewModel.clearHistory() } else null
            )
        }

        if (history.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Rounded.History, 
                            contentDescription = null, 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "History Logs Quiet",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Completed local file transfers and audit logs will automatically display here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }
        } else {
            items(history, key = { it.id }) { record ->
                TransferHistoryItem(record = record)
            }
        }
    }
}

// ==========================================
// 5. SECURITY TRUST DISPLAYS Display PIN displays
// ==========================================
@Composable
fun PairingDialog(
    active: EchoViewModel.PairingState,
    onConfirm: () -> Unit,
    onDecline: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    Dialog(
        onDismissRequest = onDecline,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Card(
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .testTag("pairing_dialog_card")
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Verified,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(16.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    text = "Pairing Connection",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${active.targetDevice.name} wants to pair",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${active.targetDevice.ip}  •  ${active.targetDevice.osName}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(20.dp))

                // PIN डिस्प्ले
                Text(
                    "Confirm displayed numeric PIN:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val part1 = active.pin.take(3)
                    val part2 = active.pin.drop(3)
                    
                    PinCell(part1)
                    Text("-", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    PinCell(part2)
                }

                Spacer(Modifier.height(12.dp))
                Text(
                    text = "Ensure matching values show on companion receiver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(18.dp))

                // Radial Countdown Ring Timer
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                    CircularProgressIndicator(
                        progress = { active.progressFraction },
                        color = if (active.progressFraction > 0.3f) MaterialTheme.colorScheme.primary 
                                else MaterialTheme.colorScheme.error,
                        trackColor = MaterialTheme.colorScheme.outlineVariant,
                        strokeWidth = 3.2.dp
                    )
                    Text(
                        text = "${active.secondsRemaining}s",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }

                Spacer(Modifier.height(24.dp))

                // Action Columns
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        },
                        shape = PillShape,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .testTag("pair_confirm_button")
                    ) {
                        Text("Trust Pair", style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDecline()
                        },
                        shape = PillShape,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                    ) {
                        Text("Decline", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
fun PinCell(text: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Box(
            modifier = Modifier
                .width(80.dp)
                .height(48.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
            )
        }
    }
}

// ==========================================
// 6. SYSTEM SETTINGS SCREEN (CASCADE FORM)
// ==========================================
@Composable
fun SettingsCascadeHeader(
    title: String,
    icon: ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                             else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(
            1.dp,
            if (expanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsScreen(
    viewModel: EchoViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val deviceName by viewModel.localDeviceName.collectAsState()
    val autoAccept by viewModel.autoAccept.collectAsState()
    val requirePairing by viewModel.requirePairing.collectAsState()
    val protocolsEnabled by viewModel.protocols.collectAsState()

    var editingName by remember { mutableStateOf(false) }
    var tempName by remember { mutableStateOf(deviceName) }

    // Cascade expansion states (compact, collapses default sizes from taking too much scroll room)
    var identityExpanded by remember { mutableStateOf(false) }
    var transceiversExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }
    var securityExpanded by remember { mutableStateOf(false) }
    var systemExpanded by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 40.dp)
    ) {
        // == 1. DEVICE IDENTITY CASCADE
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCascadeHeader(
                    title = "Device Identity",
                    icon = Icons.Rounded.AccountCircle,
                    expanded = identityExpanded,
                    onToggle = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        identityExpanded = !identityExpanded 
                    }
                )
                AnimatedVisibility(
                    visible = identityExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Local Identity Profile",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(12.dp))
                            if (editingName) {
                                OutlinedTextField(
                                    value = tempName,
                                    onValueChange = { tempName = it },
                                    label = { Text("Custom Device Name") },
                                    singleLine = true,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("device_name_field"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                    )
                                )
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    Button(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.setLocalDeviceName(tempName)
                                            editingName = false
                                        },
                                        shape = PillShape,
                                        modifier = Modifier.height(36.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Text("Save Name", style = MaterialTheme.typography.labelMedium)
                                    }
                                    TextButton(onClick = { editingName = false }) {
                                        Text("Cancel", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            } else {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column {
                                        Text(
                                            text = deviceName,
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                        Text(
                                            text = "Receiver ID: echo_${deviceName.lowercase().hashCode().toString(16).take(6)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            tempName = deviceName
                                            editingName = true
                                        },
                                        modifier = Modifier.testTag("edit_name_button")
                                    ) {
                                        Icon(Icons.Rounded.Edit, contentDescription = "Edit name", tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // == 2. DISCOVERY TRANSCEIVERS CASCADE
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCascadeHeader(
                    title = "Radio Transceivers",
                    icon = Icons.Rounded.Wifi,
                    expanded = transceiversExpanded,
                    onToggle = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        transceiversExpanded = !transceiversExpanded 
                    }
                )
                AnimatedVisibility(
                    visible = transceiversExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Hardware Channels",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Protocol.values().forEach { protocol ->
                                val isEnabled = protocolsEnabled[protocol] ?: true
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = protocol.displayName,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        val detail = when (protocol) {
                                            Protocol.BLE -> "Emits secure BLE advertisement pairing beacons."
                                            Protocol.NSD -> "Locates mDNS/DNS-SD LAN network sockets."
                                            Protocol.UDP -> "Transmits high-availability multicast discover pulses."
                                            Protocol.WIFI_DIRECT -> "Operates high-performance hardware direct-link clusters."
                                        }
                                        Text(
                                            text = detail,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = isEnabled,
                                        onCheckedChange = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            viewModel.toggleProtocol(protocol)
                                        },
                                        colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // == 3. THEME DRESSING CASCADE
        item {
            val currentPrefs by viewModel.themePreference.collectAsState()
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCascadeHeader(
                    title = "Visual Theme & Outfits",
                    icon = Icons.Rounded.Palette,
                    expanded = themeExpanded,
                    onToggle = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        themeExpanded = !themeExpanded 
                    }
                )
                AnimatedVisibility(
                    visible = themeExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Visual Clothes Styles",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(14.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                val themes = listOf(
                                    Triple(2, "Dark Cosmic (Default starry obsidian)", "The legendary zero-energy aesthetic dark theme template"),
                                    Triple(1, "Champagne Light", "A highly polished ivory silver professional style"),
                                    Triple(3, "Cyberpunk Oasis 🌌", "Glowing neon pink and electric cyan grid stream"),
                                    Triple(4, "Solar OLED", "Pitch-black background optimized for power reduction"),
                                    Triple(5, "Emerald Vault 🌲", "Soothing jade green style inspired by offline safety")
                                )
                                themes.forEach { (id, name, desc) ->
                                    val isSelected = currentPrefs == id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.setThemePreference(id)
                                            }
                                            .border(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .background(
                                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                                shape = MaterialTheme.shapes.small
                                            )
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSelected,
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                viewModel.setThemePreference(id)
                                            },
                                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                                        )
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = desc,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // == 4. SECURITY CASCADE
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCascadeHeader(
                    title = "Handshakes & Security",
                    icon = Icons.Rounded.Security,
                    expanded = securityExpanded,
                    onToggle = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        securityExpanded = !securityExpanded 
                    }
                )
                AnimatedVisibility(
                    visible = securityExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Handshake Protocols",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.8.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Mandatory PIN pairing", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text("Halts inbound transfers till verified display match confirmed.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = requirePairing,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setRequirePairing(it)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Trust pre-authorized peers", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                    Text("Bypasses pairing displayed alerts entirely for preloaded trusted devices.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = autoAccept,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        viewModel.setAutoAccept(it)
                                    },
                                    colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                }
            }
        }

        // == 5. SYSTEM RECOVERY CASCADE
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                SettingsCascadeHeader(
                    title = "System Recovery & Info",
                    icon = Icons.Rounded.Settings,
                    expanded = systemExpanded,
                    onToggle = { 
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        systemExpanded = !systemExpanded 
                    }
                )
                AnimatedVisibility(
                    visible = systemExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("EchoSystem P2P Share Suite", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                            Text("Version 1.1.0 • Enhanced Xender-Style P2P", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    // Robust restore configurations
                                    viewModel.setThemePreference(2)
                                    viewModel.setLocalDeviceName("EchoPeer")
                                    viewModel.setAutoAccept(false)
                                    viewModel.setRequirePairing(true)
                                    android.widget.Toast.makeText(context, "Full App Configurations Restored!", android.widget.Toast.LENGTH_SHORT).show()
                                },
                                shape = PillShape,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Restore Default App Configs")
                            }
                        }
                    }
                }
            }
        }
    }
}
