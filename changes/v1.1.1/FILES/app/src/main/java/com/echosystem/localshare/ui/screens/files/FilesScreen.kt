package com.echosystem.localshare.ui.screens.files

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.Device
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.viewmodel.EchoViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilesScreen(viewModel: EchoViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val currentDir by viewModel.currentDir.collectAsState(initial = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem"))
    val files by viewModel.browserFiles.collectAsState(initial = emptyList())
    val selectedFiles by viewModel.selectedFiles.collectAsState(initial = emptySet<File>())
    val transfers by viewModel.transferProgress.collectAsState(initial = emptyList())
    val devices by viewModel.devices.collectAsState(initial = emptyList())
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showActionsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    var showSendToDeviceDialog by remember { mutableStateOf(false) }
    var showPreviewOverlay by remember { mutableStateOf<File?>(null) }
    
    val rootDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            var lastCompletedCount by remember { mutableIntStateOf(0) }
            var lastFailedCount by remember { mutableIntStateOf(0) }
            LaunchedEffect(transfers) {
                val completed = transfers.count { it.status == TransferStatus.COMPLETED }
                val failed = transfers.count { it.status == TransferStatus.FAILED }
                if (completed > lastCompletedCount) com.echosystem.localshare.util.HapticUtil.success(context)
                if (failed > lastFailedCount) com.echosystem.localshare.util.HapticUtil.error(context)
                lastCompletedCount = completed
                lastFailedCount = failed
            }

            Column {
                FolderTree(
                    currentDir = currentDir,
                    rootDir = rootDir,
                    onNavigate = { viewModel.navigateTo(it) }
                )
                AnimatedVisibility(
                    visible = selectedFiles.isNotEmpty(),
                    enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
                ) {
                    SelectionToolbar(
                        count = selectedFiles.size,
                        onClear = { viewModel.clearSelection() },
                        onShowActions = { showActionsSheet = true }
                    )
                }
            }
        },
        floatingActionButton = {
            if (selectedFiles.isEmpty()) {
                FloatingActionButton(
                    onClick = { 
                        com.echosystem.localshare.util.HapticUtil.lightTap(context)
                        viewModel.refreshBrowserFiles() 
                    },
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            ActiveTransfersSection(transfers = transfers.filter { it.status == TransferStatus.TRANSFERRING })

            if (files.isEmpty()) {
                EmptyFilesState(isRoot = (currentDir.absolutePath == rootDir.absolutePath))
            } else {
                FileGrid(
                    files = files,
                    selectedFiles = selectedFiles,
                    onFileClick = { file ->
                        if (selectedFiles.isNotEmpty()) {
                            viewModel.toggleSelection(file)
                        } else if (file.isDirectory) {
                            viewModel.navigateTo(file)
                        } else {
                            showPreviewOverlay = file
                        }
                    },
                    onFileLongClick = { file ->
                        viewModel.toggleSelection(file)
                    }
                )
            }
        }

        if (showActionsSheet) {
            FileActionsSheet(
                selectedFiles = selectedFiles,
                onDismiss = { showActionsSheet = false },
                onDelete = {
                    viewModel.deleteSelectedFiles()
                    showActionsSheet = false
                    scope.launch { snackbarHostState.showSnackbar("Items moved to oblivion.") }
                },
                onRename = {
                    showRenameDialog = selectedFiles.first()
                    showActionsSheet = false
                },
                onMove = { showActionsSheet = false },
                onShare = {
                    showSendToDeviceDialog = true
                    showActionsSheet = false
                },
                onWebPreview = { showActionsSheet = false }
            )
        }

        showRenameDialog?.let { file ->
            RenameFileDialog(
                currentName = file.name,
                onDismiss = { showRenameDialog = null },
                onConfirm = { newName ->
                    viewModel.renameFile(file, newName)
                    showRenameDialog = null
                    viewModel.clearSelection()
                }
            )
        }

        if (showSendToDeviceDialog) {
            SendToDeviceDialog(
                devices = devices,
                onDismiss = { showSendToDeviceDialog = false },
                onConfirm = { device ->
                    viewModel.sendSelectedFilesToDevice(device)
                    showSendToDeviceDialog = false
                    scope.launch { snackbarHostState.showSnackbar("Init transmission to ${device.name}") }
                }
            )
        }

        showPreviewOverlay?.let { file ->
            PreviewOverlay(file = file, onDismiss = { showPreviewOverlay = null })
        }
    }
}

@Composable
fun SelectionToolbar(count: Int, onClear: () -> Unit, onShowActions: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = {
                    com.echosystem.localshare.util.HapticUtil.lightTap(context)
                    onClear()
                }) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "$count items selected", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = {
                    com.echosystem.localshare.util.HapticUtil.lightTap(context)
                    onShowActions()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onPrimary,
                    contentColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Actions")
            }
        }
    }
}

@Composable
fun ActiveTransfersSection(transfers: List<FileTransfer>) {
    AnimatedVisibility(
        visible = transfers.isNotEmpty(),
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Synchronizing Mesh Resources", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(modifier = Modifier.height(12.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(transfers, key = { it.id }) { transfer ->
                    TransferProgressRow(transfer)
                }
            }
        }
    }
}

@Composable
fun TransferProgressRow(transfer: FileTransfer) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse), label = ""
    )
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(transfer.fileName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, modifier = Modifier.weight(1f))
            Text("${(transfer.progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { transfer.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).graphicsLayer { alpha = pulseAlpha },
            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun EmptyFilesState(isRoot: Boolean) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CreateNewFolder, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(if (isRoot) "Resonance Clear" else "Silent Void", style = MaterialTheme.typography.displayLarge)
        Text(
            if (isRoot) "Your echoSystem storage is clean and ready. Share files to see them vibrate here." else "This path contains no fragments. Drop something in to start the sync.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun RenameFileDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename System Object", style = MaterialTheme.typography.headlineSmall) },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Object Name") }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { Button(onClick = { onConfirm(name) }) { Text("Update") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun SendToDeviceDialog(devices: List<Device>, onDismiss: () -> Unit, onConfirm: (Device) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Mesh Beacon Node", style = MaterialTheme.typography.headlineSmall) },
        text = {
            if (devices.isEmpty()) {
                Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) {
                    Text("No nodes found.", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(devices) { device ->
                        Card(onClick = { onConfirm(device) }, modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(12.dp).fillMaxWidth(), Alignment.CenterVertically) {
                                Icon(Icons.Default.Devices, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                                    Text("IP: ${device.ip}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
