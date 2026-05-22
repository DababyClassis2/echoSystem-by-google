package com.echosystem.localshare.ui.screens.files

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.echosystem.localshare.model.FileTransfer
import com.echosystem.localshare.model.TransferStatus
import com.echosystem.localshare.viewmodel.EchoViewModel
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun FilesScreen(viewModel: EchoViewModel) {
    val currentDir by viewModel.currentDir.collectAsState(initial = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem"))
    val files by viewModel.browserFiles.collectAsState(initial = emptyList())
    val selectedFiles by viewModel.selectedFiles.collectAsState(initial = emptySet<File>())
    val transfers by viewModel.transferProgress.collectAsState(initial = emptyList())
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    
    var showActionsSheet by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf<File?>(null) }
    
    val rootDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                FolderTree(
                    currentDir = currentDir,
                    rootDir = rootDir,
                    onNavigate = { viewModel.navigateTo(it) }
                )
                if (selectedFiles.isNotEmpty()) {
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
                    onClick = { viewModel.refreshBrowserFiles() },
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
            // Real-time Transfers Section
            ActiveTransfersSection(transfers = transfers.filter { it.status == TransferStatus.ONGOING })

            if (files.isEmpty()) {
                EmptyFilesState()
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
                            // Single click without selection could open preview
                            // For UI 2.0 we'll favor long-press for multi-select
                        }
                    },
                    onFileLongClick = { file ->
                        viewModel.toggleSelection(file)
                    }
                )
            }
        }

        // Bottom Sheets & Dialogs
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
                onMove = {
                    scope.launch { snackbarHostState.showSnackbar("Move logic pending registry mapping.") }
                    showActionsSheet = false
                },
                onShare = {
                    // Logic to trigger system share intent would go here
                    showActionsSheet = false
                },
                onWebPreview = {
                    // Logic to navigate to WebShare or open URL
                    showActionsSheet = false
                }
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
    }
}

@Composable
fun SelectionToolbar(
    count: Int,
    onClear: () -> Unit,
    onShowActions: () -> Unit
) {
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
                IconButton(onClick = onClear) {
                    Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$count items selected",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Button(
                onClick = onShowActions,
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
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Sync, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Synchronizing Mesh Resources",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
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
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = transfer.fileName,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "${(transfer.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Black
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { transfer.progress },
            modifier = Modifier.fillMaxWidth().height(4.dp).background(Color.Transparent),
            trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.1f),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun EmptyFilesState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.FolderOpen,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Null Directory",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black
        )
        Text(
            "This sector of the filesystem is currently devoid of resources.",
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun RenameFileDialog(
    currentName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename System Object") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Object Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) { Text("Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
