package com.echosystem.localshare.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.viewmodel.EchoViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(viewModel: EchoViewModel) {
    val transfers by viewModel.transferProgress.collectAsState()
    var activePreviewTransfer by remember { mutableStateOf<FileTransfer?>(null) }
    val context = LocalContext.current

    // [V1.1.1] Monitor for transfer edge completions to trigger haptics
    var lastCompletedCount by remember { mutableIntStateOf(0) }
    var lastFailedCount by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(transfers) {
        val completed = transfers.count { it.status == TransferStatus.COMPLETED }
        val failed = transfers.count { it.status == TransferStatus.FAILED }
        
        if (completed > lastCompletedCount) {
            com.echosystem.localshare.util.HapticUtil.success(context)
        }
        if (failed > lastFailedCount) {
            com.echosystem.localshare.util.HapticUtil.error(context)
        }
        
        lastCompletedCount = completed
        lastFailedCount = failed
    }

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
                    style = MaterialTheme.typography.displayLarge,
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
                        com.echosystem.localshare.util.HapticUtil.lightTap(context)
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
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "Empty History",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "The Ledger is Empty",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your data silence is golden. Transferred items and mesh synchronization logs will appear here.",
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
                                com.echosystem.localshare.util.HapticUtil.lightTap(context)
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

    activePreviewTransfer?.let { transfer ->
        FilePreviewModal(
            transfer = transfer,
            onDismiss = { activePreviewTransfer = null },
            onDelete = {
                viewModel.deleteFileFromHistory(transfer.fileName)
                activePreviewTransfer = null
            }
        )
    }
}

@Composable
fun TransferItemRow(
    transfer: FileTransfer,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        color = when (transfer.status) {
                            TransferStatus.COMPLETED -> Color(0xFFC8E6C9).copy(alpha = 0.6f)
                            TransferStatus.FAILED -> Color(0xFFFFCDD2).copy(alpha = 0.6f)
                            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (transfer.status) {
                        TransferStatus.COMPLETED -> Icons.Default.CheckCircle
                        TransferStatus.FAILED -> Icons.Default.Error
                        else -> Icons.Default.Sync
                    },
                    contentDescription = null,
                    tint = when (transfer.status) {
                        TransferStatus.COMPLETED -> Color(0xFF2E7D32)
                        TransferStatus.FAILED -> Color(0xFFC62828)
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = if (transfer.isIncoming) "From: ${transfer.remoteDeviceName}" else "To: ${transfer.remoteDeviceName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Clear, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FilePreviewModal(
    transfer: FileTransfer,
    onDismiss: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(40.dp)) },
        title = { Text(transfer.fileName, style = MaterialTheme.typography.displayLarge) },
        text = {
            Column {
                Text("Remote Contact: ${transfer.remoteDeviceName}")
                Text("Direction: ${if (transfer.isIncoming) "Incoming Packet" else "Outgoing Packet"}")
                Text("Status Flag: ${transfer.status}")
                if (transfer.size > 0) {
                    Text("Byte Size: ${transfer.size}")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        },
        dismissButton = {
            TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text("Remove Records")
            }
        }
    )
}
