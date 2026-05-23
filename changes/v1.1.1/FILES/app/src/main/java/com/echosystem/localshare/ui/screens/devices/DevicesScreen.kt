package com.echosystem.localshare.ui.screens.devices

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
import com.echosystem.localshare.model.NsdState
import com.echosystem.localshare.viewmodel.EchoViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DevicesScreen(viewModel: EchoViewModel) {
    val devices by viewModel.devices.collectAsState()
    val nsdState by viewModel.nsdState.collectAsState()
    val trustedIds by viewModel.trustManager.trustedDeviceIds.collectAsState()
    val pairingPin by viewModel.pairingPin.collectAsState()
    
    var selectedDevice by remember { mutableStateOf<Device?>(null) }
    val scope = rememberCoroutineScope()
    val hostState = remember { SnackbarHostState() }
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        MyIdentityBanner(ipAddress = viewModel.pairingManager.getLocalIp(), pin = pairingPin ?: "------")
        ScanningHeader(nsdState = nsdState)

        if (devices.isEmpty()) {
            EmptyDevicesState(onScanAgain = { 
                com.echosystem.localshare.util.HapticUtil.lightTap(context)
                viewModel.startDiscovery() 
            })
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(devices, key = { it.id }) { device ->
                    DeviceCard(
                        device = device,
                        isTrusted = trustedIds.contains(device.id),
                        pairingResults = viewModel.pairingResults,
                        onClick = { selectedDevice = device }
                    )
                }
            }
        }
    }

    FloatingActionButton(
        onClick = { 
            com.echosystem.localshare.util.HapticUtil.lightTap(context)
            viewModel.startDiscovery() 
        },
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.padding(16.dp)
    ) { Icon(Icons.Default.Refresh, "Refresh") }
}

@Composable
fun ScanningHeader(nsdState: NsdState) {
    Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp, 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (nsdState == NsdState.DISCOVERING) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Radar, null, Modifier.size(16.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = when (nsdState) {
                NsdState.IDLE -> "Radar Standby"
                NsdState.DISCOVERING -> "Scanning for Nearby Nodes..."
                NsdState.REGISTERED -> "Node Active & Discoverable"
                else -> "Registry Active"
            }, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun EmptyDevicesState(onScanAgain: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.Radar, null, Modifier.size(64.dp), MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Radar Silence", style = MaterialTheme.typography.displayLarge)
        Text("No nodes found in the echo registry. Ensure friends have the portal open or check your network frequency.", textAlign = androidx.compose.ui.text.style.TextAlign.Center, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp, bottom = 24.dp))
        Button(onClick = onScanAgain) {
            Icon(Icons.Default.Refresh, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Scan Again")
        }
    }
}

@Composable
fun MyIdentityBanner(ipAddress: String, pin: String) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp, 12.dp).fillMaxWidth(), Alignment.CenterVertically, Arrangement.SpaceBetween) {
            Column {
                Text("My Node Identity", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                Text(ipAddress, style = MaterialTheme.typography.titleMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Shield Key", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary, shape = MaterialTheme.shapes.small) {
                    Text(pin, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
