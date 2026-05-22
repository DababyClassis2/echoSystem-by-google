package com.echosystem.localshare.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.NoteAdd
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.echosystem.localshare.viewmodel.EchoViewModel
import java.io.File

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LocalFileBrowser(
    viewModel: EchoViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val rootDir = File(android.os.Environment.getExternalStorageDirectory(), "echoSystem")
    if (!rootDir.exists()) {
        rootDir.mkdirs()
    }

    var currentDir by remember { mutableStateOf(rootDir) }
    var filesList by remember { mutableStateOf<List<File>>(emptyList()) }
    val backStack = remember { mutableStateListOf<File>() }

    // Multi-select state (Task 7)
    var selectedFiles by remember { mutableStateOf(setOf<File>()) }
    var isSelectionMode by remember { mutableStateOf(false) }

    // Directory creation state
    var showMkDirDialog by remember { mutableStateOf(false) }
    var newFolderNameInput by remember { mutableStateOf("") }

    // Preview state
    var previewFile by remember { mutableStateOf<File?>(null) }
    var textPreviewContent by remember { mutableStateOf<String?>(null) }

    // Load files
    val refreshFiles = {
        if (!currentDir.exists()) {
            currentDir.mkdirs()
        }
        val raw = currentDir.listFiles()?.toList() ?: emptyList()
        filesList = raw.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    LaunchedEffect(currentDir) {
        refreshFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (isSelectionMode) "${selectedFiles.size} selected" else (if (currentDir == rootDir) "echoSystem Root" else currentDir.name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isSelectionMode) {
                                isSelectionMode = false
                                selectedFiles = emptySet()
                            } else if (backStack.isNotEmpty()) {
                                currentDir = backStack.removeLast()
                            } else {
                                onClose()
                            }
                        }
                    ) {
                        Icon(if (isSelectionMode) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            // Massive deletion or action
                            selectedFiles.forEach { it.delete() }
                            selectedFiles = emptySet()
                            isSelectionMode = false
                            refreshFiles()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showMkDirDialog = true }) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                        }
                        IconButton(onClick = refreshFiles) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (filesList.isEmpty()) {
                EmptyState()
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filesList) { file ->
                        val isSelected = selectedFiles.contains(file)
                        FileItemRow(
                            file = file,
                            isSelected = isSelected,
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    selectedFiles = if (isSelected) selectedFiles - file else selectedFiles + file
                                    if (selectedFiles.isEmpty()) isSelectionMode = false
                                } else {
                                    if (file.isDirectory) {
                                        backStack.add(currentDir)
                                        currentDir = file
                                    } else {
                                        previewFile = file
                                    }
                                }
                            },
                            onLongClick = {
                                if (!isSelectionMode) {
                                    isSelectionMode = true
                                    selectedFiles = setOf(file)
                                }
                            }
                        )
                    }
                }
            }

            // Dialogs...
            if (showMkDirDialog) {
                // ... same dialog logic but staged ...
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Outlined.Folder, "Empty", tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.4f), modifier = Modifier.size(72.dp))
        Text("Folder is Empty", fontWeight = FontWeight.Bold)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileItemRow(
    file: File,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(0.3f)
        )
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, "Icon", tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(16.dp))
            Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isSelectionMode) {
                Checkbox(checked = isSelected, onCheckedChange = { onClick() })
            }
        }
    }
}
