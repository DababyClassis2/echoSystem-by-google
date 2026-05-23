package com.echosystem.localshare.ui.screens.devices

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.DevicePermission
import com.echosystem.localshare.model.DeviceStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceActionsSheet(
    device: Device,
    isTrusted: Boolean,
    onDismiss: () -> Unit,
    onSendFiles: () -> Unit,
    onRename: () -> Unit,
    onBlock: () -> Unit,
    onRemoveTrust: () -> Unit,
    onPair: () -> Unit,
    onTogglePermission: (DevicePermission) -> Unit,
    currentPermissions: Set<DevicePermission>
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
    ) {
        DeviceActionsContent(
            device = device,
            isTrusted = isTrusted,
            onSendFiles = onSendFiles,
            onRename = onRename,
            onBlock = onBlock,
            onRemoveTrust = onRemoveTrust,
            onPair = onPair,
            onTogglePermission = onTogglePermission,
            currentPermissions = currentPermissions
        )
    }
}

@Composable
fun DeviceActionsContent(
    device: Device,
    isTrusted: Boolean,
    onSendFiles: () -> Unit,
    onRename: () -> Unit,
    onBlock: () -> Unit,
    onRemoveTrust: () -> Unit,
    onPair: () -> Unit,
    onTogglePermission: (DevicePermission) -> Unit,
    currentPermissions: Set<DevicePermission>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Devices,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
                Text(
                    text = "ID: ${device.id.take(12)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Primary Actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ActionIconButton(
                icon = Icons.Default.FileUpload,
                label = "Transmit",
                onClick = onSendFiles,
                enabled = device.status == DeviceStatus.CONNECTED
            )
            ActionIconButton(
                icon = if (isTrusted) Icons.Default.VerifiedUser else Icons.Default.LockOpen,
                label = if (isTrusted) "Trusted" else "Pair Device",
                onClick = onPair,
                enabled = device.status != DeviceStatus.CONNECTED
            )
            ActionIconButton(
                icon = Icons.Default.Edit,
                label = "Rename",
                onClick = onRename
            )
        }

        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

        // Permissions Section
        if (isTrusted) {
            Text(
                "Network Permissions",
                modifier = Modifier.padding(16.dp, 16.dp, 16.dp, 8.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            
            DevicePermission.entries.forEach { permission ->
                PermissionToggleItem(
                    permission = permission,
                    isEnabled = currentPermissions.contains(permission),
                    onToggle = { onTogglePermission(permission) }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }

        // Dangerous Actions
        ListItem(
            headlineContent = { Text("Block Device") },
            supportingContent = { Text("Prevent all future connection attempts.") },
            leadingContent = { Icon(Icons.Default.Block, null, tint = MaterialTheme.colorScheme.error) },
            modifier = Modifier.clickable { onBlock() }
        )
        
        if (isTrusted) {
            ListItem(
                headlineContent = { Text("Remove Trust", color = MaterialTheme.colorScheme.error) },
                supportingContent = { Text("Revoke all saved permissions for this node.") },
                leadingContent = { Icon(Icons.Default.DeleteForever, null, tint = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable { onRemoveTrust() }
            )
        }
    }
}

@Composable
fun ActionIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(if (enabled) 1f else 0.4f)
    ) {
        FilledIconButton(
            onClick = onClick,
            enabled = enabled,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
fun PermissionToggleItem(
    permission: DevicePermission,
    isEnabled: Boolean,
    onToggle: () -> Unit
) {
    ListItem(
        headlineContent = { 
            Text(
                permission.name.replace("_", " ").lowercase().capitalize(),
                style = MaterialTheme.typography.bodyMedium
            ) 
        },
        trailingContent = {
            Switch(
                checked = isEnabled,
                onCheckedChange = { onToggle() },
                thumbContent = if (isEnabled) {
                    { Icon(Icons.Default.Check, null, Modifier.size(12.dp)) }
                } else null
            )
        },
        leadingContent = {
            Icon(
                imageVector = when(permission) {
                    DevicePermission.BROWSE_FILES -> Icons.Outlined.FolderCopy
                    DevicePermission.UPLOAD_FILES -> Icons.Outlined.UploadFile
                    DevicePermission.DOWNLOAD_FILES -> Icons.Outlined.FileDownload
                    DevicePermission.MANAGE_PERMISSIONS -> Icons.Outlined.AdminPanelSettings
                    DevicePermission.DELETE_FILES -> Icons.Outlined.Delete
                },
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

private fun String.capitalize() = this.replaceFirstChar { it.uppercase() }
