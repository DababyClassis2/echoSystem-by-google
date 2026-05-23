package com.echosystem.localshare.ui.screens.devices

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.model.NsdState
import com.echosystem.localshare.viewmodel.EchoViewModel
import kotlinx.coroutines.launch

@Composable
fun DevicesScreen(viewModel: EchoViewModel) {
    val devices by viewModel.devices.collectAsState()
    val nsdState by viewModel.nsdState.collectAsState()
    val trustedDevices by viewModel.trustManager.trustedDevices.collectAsState()
    val trustedIds by viewModel.trustManager.trustedDeviceIds.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    var activeSendDevice by remember { mutableStateOf<Device?>(null) }
    var showRenameDialog by remember { mutableStateOf<Device?>(null) }
    var showPairDialog by remember { mutableStateOf<Device?>(null) }
    
    val scope = rememberCoroutineScope()
    val hostState = remember { SnackbarHostState() }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        activeSendDevice?.let { dev ->
            if (uris.isNotEmpty()) {
                viewModel.sendMultipleFilesToDevice(dev, uris)
                scope.launch { hostState.showSnackbar("Init transmission of ${uris.size} elements to ${dev.name}") }
            }
        }
        activeSendDevice = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.startDiscovery() },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh Scan")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // My Identity Section (Consolidated Receive Mode)
            MyIdentityBanner(ipAddress = viewModel.pairingManager.getLocalIp(), pin = pairingPin ?: "------")
            
            // Scanning Status Header
            ScanningHeader(nsdState = nsdState)

            if (devices.isEmpty()) {
                EmptyDevicesState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(devices, key = { it.id }) { device ->
                        DeviceCard(
                            device = device,
                            isTrusted = trustedIds.contains(device.id),
                            onClick = { selectedDevice = device }
                        )
                    }
                }
            }
        }

        // Bottom Sheet for Actions
        selectedDevice?.let { device ->
            val isTrusted = trustedIds.contains(device.id)
            val trustedDevice = trustedDevices.find { it.id == device.id }
            val permissions = trustedDevice?.permissions ?: emptySet()

            DeviceActionsSheet(
                device = device,
                isTrusted = isTrusted,
                onDismiss = { selectedDevice = null },
                onSendFiles = {
                    activeSendDevice = device
                    fileLauncher.launch("*/*")
                    selectedDevice = null
                },
                onRename = { 
                    showRenameDialog = device
                    selectedDevice = null
                },
                onBlock = {
                    viewModel.trustManager.setDeviceBlocked(device.id, true)
                    selectedDevice = null
                    scope.launch { hostState.showSnackbar("${device.name} restricted from network.") }
                },
                onRemoveTrust = {
                    viewModel.trustManager.setDeviceTrust(device.id, device.name, false)
                    selectedDevice = null
                    scope.launch { hostState.showSnackbar("Trust revoked for ${device.name}") }
                },
                onPair = {
                    showPairDialog = device
                    selectedDevice = null
                },
                onTogglePermission = { perm ->
                    val newPerms = if (permissions.contains(perm)) {
                        permissions - perm
                    } else {
                        permissions + perm
                    }
                    viewModel.trustManager.setDevicePermissions(device.id, newPerms)
                },
                currentPermissions = permissions
            )
        }

        // Dialogs
        showRenameDialog?.let { device ->
            RenameDeviceDialog(
                currentName = device.name,
                onDismiss = { showRenameDialog = null },
                onConfirm = { newName ->
                    viewModel.trustManager.renameDevice(device.id, newName)
                    showRenameDialog = null
                }
            )
        }

        showPairDialog?.let { device ->
            PairDeviceDialog(
                deviceName = device.name,
                onDismiss = { showPairDialog = null },
                onConfirm = { pin ->
                    viewModel.pairWithDevice(device, pin) { success, error ->
                        scope.launch {
                            if (success) hostState.showSnackbar("Successfully paired with ${device.name}")
                            else hostState.showSnackbar("Pairing failed: $error")
                        }
                    }
                    showPairDialog = null
                }
            )
        }
    }
}

@Composable
fun ScanningHeader(nsdState: NsdState) {
    val backgroundColor = when (nsdState) {
        NsdState.IDLE -> MaterialTheme.colorScheme.surfaceVariant
        NsdState.DISCOVERING -> MaterialTheme.colorScheme.primaryContainer
        NsdState.REGISTERED -> Color(0xFFC8E6C9) // Light Green
        NsdState.ERROR_DEGRADED -> MaterialTheme.colorScheme.errorContainer
        NsdState.OFFLINE -> MaterialTheme.colorScheme.surfaceDim
    }

    val contentColor = when (nsdState) {
        NsdState.DISCOVERING -> MaterialTheme.colorScheme.onPrimaryContainer
        NsdState.REGISTERED -> Color(0xFF2E7D32) // Dark Green
        NsdState.ERROR_DEGRADED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp, 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (nsdState == NsdState.DISCOVERING) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = contentColor
                )
            } else {
                Icon(
                    imageVector = when(nsdState) {
                        NsdState.REGISTERED -> Icons.Default.CloudDone
                        NsdState.ERROR_DEGRADED -> Icons.Default.Warning
                        else -> Icons.Default.Radar
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Text(
                text = when (nsdState) {
                    NsdState.IDLE -> "Radar Standby"
                    NsdState.DISCOVERING -> "Scanning for Nearby Nodes..."
                    NsdState.REGISTERED -> "Node Active & Discoverable"
                    NsdState.ERROR_DEGRADED -> "Network Degraded: Discovery Limited"
                    NsdState.OFFLINE -> "Radio Offline: Restart Required"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun EmptyDevicesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SensorsOff,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Isolated Node",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            "No devices found on the local mesh. Ensure other nodes are discoverable and on the same Wi-Fi.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun MyIdentityBanner(ipAddress: String, pin: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp, 12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "My Node Identity",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = ipAddress,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Black
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Shield Key",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = pin,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun RenameDeviceDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Registry Entry") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Device Alias") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun PairDeviceDialog(
    deviceName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Secure Pairing Link") },
        text = {
            Column {
                Text("Enter the 6-digit Shield Key displayed on $deviceName.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6) pin = it },
                    label = { Text("PIN Token") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    )
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pin) }, enabled = pin.length == 6) { Text("Link") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
