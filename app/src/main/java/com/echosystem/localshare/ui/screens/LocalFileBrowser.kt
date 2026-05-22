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
                            text = if (currentDir == rootDir) "echoSystem Root" else currentDir.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("file_browser_dir_title")
                        )
                        Text(
                            text = currentDir.absolutePath.replace(android.os.Environment.getExternalStorageDirectory().absolutePath, "Internal Storage"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (backStack.isNotEmpty()) {
                                currentDir = backStack.removeLast()
                            } else {
                                onClose()
                            }
                        },
                        modifier = Modifier.testTag("file_browser_back")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Go Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMkDirDialog = true }) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "New Folder")
                    }
                    IconButton(onClick = refreshFiles) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(3.dp)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (filesList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Folder,
                        contentDescription = "Empty Directory",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(72.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Folder is Empty",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "No files or subdirectories discovered in this directory yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filesList) { file ->
                        FileItemRow(
                            file = file,
                            onClick = {
                                if (file.isDirectory) {
                                    backStack.add(currentDir)
                                    currentDir = file
                                    viewModel.addManualLog("Browser", "Navigated down securely to subdirectory: ${file.name}")
                                } else {
                                    previewFile = file
                                    if (file.extension.lowercase() in listOf("txt", "log", "json", "xml", "md", "html", "css", "js")) {
                                        try {
                                            textPreviewContent = file.readText()
                                        } catch (e: Exception) {
                                            textPreviewContent = "Error parsing text preview content:\n${e.localizedMessage}"
                                        }
                                    } else {
                                        textPreviewContent = null
                                    }
                                    viewModel.addManualLog("Browser", "Opened resource preview card for file: ${file.name}")
                                }
                            },
                            onDelete = {
                                val success = if (file.isDirectory) file.deleteRecursively() else file.delete()
                                if (success) {
                                    Toast.makeText(context, "Item trashed successfully.", Toast.LENGTH_SHORT).show()
                                    viewModel.addManualLog("Browser", "Permanently trashed file resource: ${file.name}")
                                    refreshFiles()
                                } else {
                                    Toast.makeText(context, "Failed to remove item resource.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Text / Image Preview Dialog
            previewFile?.let { file ->
                AlertDialog(
                    onDismissRequest = {
                        previewFile = null
                        textPreviewContent = null
                    },
                    title = {
                        Text(
                            text = file.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (file.extension.lowercase() in listOf("jpg", "png", "jpeg", "webp", "gif")) {
                                AsyncImage(
                                    model = file,
                                    contentDescription = file.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(250.dp)
                                        .clip(RoundedCornerShape(12.dp)),
                                    contentScale = ContentScale.Fit
                                )
                            } else if (textPreviewContent != null) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth().weight(1f)
                                ) {
                                    LazyColumn(modifier = Modifier.padding(8.dp)) {
                                        item {
                                            Text(
                                                text = textPreviewContent!!,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Default description info
                                Column(
                                    modifier = Modifier.padding(vertical = 16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = when (file.extension.lowercase()) {
                                            "mp4", "mkv", "mov", "avi" -> Icons.Default.Movie
                                            "mp3", "wav", "m4a", "flac" -> Icons.Default.MusicNote
                                            "pdf" -> Icons.Default.PictureAsPdf
                                            else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                                        },
                                        contentDescription = "Metadata icon",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(64.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Formatting extension: ${file.extension.uppercase()} File",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Size category: ${formatBytes(file.length())}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    // Open file with system default intent
                                    try {
                                        val uri = androidx.core.content.FileProvider.getUriForFile(
                                            context,
                                            "${context.packageName}.provider",
                                            file
                                        )
                                        val intent = Intent(Intent.ACTION_VIEW).apply {
                                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Open file with"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "Could not open item natively: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = "Natively Open")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open")
                            }

                            TextButton(
                                onClick = {
                                    previewFile = null
                                    textPreviewContent = null
                                }
                            ) {
                                Text("Back")
                            }
                        }
                    }
                )
            }

            // Create folder dialog
            if (showMkDirDialog) {
                AlertDialog(
                    onDismissRequest = { showMkDirDialog = false },
                    title = { Text("Secure New Directory") },
                    text = {
                        OutlinedTextField(
                            value = newFolderNameInput,
                            onValueChange = { newFolderNameInput = it },
                            label = { Text("Directory Folder Name") },
                            placeholder = { Text("e.g., family_pictures") },
                            modifier = Modifier.fillMaxWidth().testTag("new_folder_input")
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (newFolderNameInput.trim().isNotEmpty()) {
                                    val newChild = File(currentDir, newFolderNameInput.trim())
                                    if (newChild.exists()) {
                                        Toast.makeText(context, "Directory already established.", Toast.LENGTH_SHORT).show()
                                    } else {
                                        val ok = newChild.mkdirs()
                                        if (ok) {
                                            Toast.makeText(context, "Folder created securely.", Toast.LENGTH_SHORT).show()
                                            viewModel.addManualLog("Browser", "Created new secure subdirectory folder: ${newFolderNameInput.trim()}")
                                            refreshFiles()
                                        } else {
                                            Toast.makeText(context, "Failed creating directory structure.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                    newFolderNameInput = ""
                                    showMkDirDialog = false
                                }
                            },
                            modifier = Modifier.testTag("new_folder_submit")
                        ) {
                            Text("Create")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            newFolderNameInput = ""
                            showMkDirDialog = false
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FileItemRow(
    file: File,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("file_row_${file.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (file.isDirectory) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (file.isDirectory) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (file.isDirectory) Icons.Default.Folder
                    else when (file.extension.lowercase()) {
                        "png", "jpg", "jpeg", "webp", "gif" -> Icons.Default.Image
                        "mp4", "mkv", "mov", "avi" -> Icons.Default.Movie
                        "mp3", "wav", "m4a", "flac" -> Icons.Default.MusicNote
                        "pdf" -> Icons.Default.PictureAsPdf
                        "txt", "log", "json", "xml", "csv", "md" -> Icons.AutoMirrored.Filled.NoteAdd
                        else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                    },
                    contentDescription = file.name,
                    tint = if (file.isDirectory) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (file.isDirectory) "Directory Folder" else "${file.extension.uppercase()} • ${formatBytes(file.length())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_${file.name}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Trash",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                )
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val k = 1024f
    val sizes = listOf("B", "KB", "MB", "GB", "TB")
    val i = Math.floor(Math.log(bytes.toDouble()) / Math.log(k.toDouble())).toInt()
    val num = bytes / Math.pow(k.toDouble(), i.toDouble())
    return "${String.format("%.1f", num)} ${sizes[i]}"
}
