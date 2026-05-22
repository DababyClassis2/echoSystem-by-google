package com.echosystem.localshare.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.model.TrustedDevice
import com.echosystem.localshare.viewmodel.EchoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityManager(
    viewModel: EchoViewModel,
    onClose: () -> Unit
) {
    val trustedDevices by viewModel.trustManager.trustedDevices.collectAsState()
    var selectedDevice by remember { mutableStateOf<TrustedDevice?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Access & Permissions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (trustedDevices.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No known devices yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(trustedDevices) { device ->
                        TrustedDeviceRow(
                            device = device,
                            onClick = { selectedDevice = device }
                        )
                    }
                }
            }
        }

        selectedDevice?.let { device ->
            PermissionDialog(
                device = device,
                onDismiss = { selectedDevice = null },
                onUpdate = { updatedPerms ->
                    viewModel.trustManager.setDevicePermissions(device.id, updatedPerms)
                    selectedDevice = null
                },
                onBlock = { isBlocked ->
                    viewModel.trustManager.setDeviceBlocked(device.id, isBlocked)
                    selectedDevice = null
                }
            )
        }
    }
}

@Composable
fun TrustedDeviceRow(
    device: TrustedDevice,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (device.blocked) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (device.blocked) Icons.Default.Block else Icons.Default.Devices,
                contentDescription = null,
                tint = if (device.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(device.name, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Permissions: ${if (device.permissions.isEmpty()) "None" else device.permissions.size.toString() + " active"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PermissionDialog(
    device: TrustedDevice,
    onDismiss: () -> Unit,
    onUpdate: (Set<DevicePermission>) -> Unit,
    onBlock: (Boolean) -> Unit
) {
    var perms by remember { mutableStateOf(device.permissions) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Permissions: ${device.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PermissionToggle(
                    title = "Browse Files",
                    icon = Icons.Default.Folder,
                    checked = perms.contains(DevicePermission.BROWSE_FILES),
                    onCheckedChange = { if (it) perms = perms + DevicePermission.BROWSE_FILES else perms = perms - DevicePermission.BROWSE_FILES }
                )
                PermissionToggle(
                    title = "Upload Files",
                    icon = Icons.Default.Upload,
                    checked = perms.contains(DevicePermission.UPLOAD_FILES),
                    onCheckedChange = { if (it) perms = perms + DevicePermission.UPLOAD_FILES else perms = perms - DevicePermission.UPLOAD_FILES }
                )
                PermissionToggle(
                    title = "Download Files",
                    icon = Icons.Default.Download,
                    checked = perms.contains(DevicePermission.DOWNLOAD_FILES),
                    onCheckedChange = { if (it) perms = perms + DevicePermission.DOWNLOAD_FILES else perms = perms - DevicePermission.DOWNLOAD_FILES }
                )
                PermissionToggle(
                    title = "Delete Files",
                    icon = Icons.Default.Delete,
                    checked = perms.contains(DevicePermission.DELETE_FILES),
                    onCheckedChange = { if (it) perms = perms + DevicePermission.DELETE_FILES else perms = perms - DevicePermission.DELETE_FILES }
                )
                PermissionToggle(
                    title = "Admin (Manage Others)",
                    icon = Icons.Default.AdminPanelSettings,
                    checked = perms.contains(DevicePermission.MANAGE_PERMISSIONS),
                    onCheckedChange = { if (it) perms = perms + DevicePermission.MANAGE_PERMISSIONS else perms = perms - DevicePermission.MANAGE_PERMISSIONS }
                )
                
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                
                Button(
                    onClick = { onBlock(!device.blocked) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (device.blocked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (device.blocked) "Unblock Device" else "Block Device")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onUpdate(perms) }) {
                Text("Save Changes")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionToggle(
    title: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.width(12.dp))
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
