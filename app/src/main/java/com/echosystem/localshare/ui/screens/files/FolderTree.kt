package com.echosystem.localshare.ui.screens.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.io.File

@Composable
fun FolderTree(
    currentDir: File,
    rootDir: File,
    onNavigate: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val pathParts = mutableListOf<File>()
    var temp: File? = currentDir
    while (temp != null && temp.absolutePath.startsWith(rootDir.absolutePath)) {
        pathParts.add(0, temp)
        if (temp == rootDir) break
        temp = temp.parentFile
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceColorAtElevation(1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            itemsIndexed(pathParts) { index, file ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PathSegment(
                        name = if (file == rootDir) "echoSystem" else file.name,
                        icon = if (file == rootDir) Icons.Default.Dns else Icons.Default.Folder,
                        isActive = file == currentDir,
                        onClick = { onNavigate(file) }
                    )
                    
                    if (index < pathParts.size - 1) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PathSegment(
    name: String,
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 8.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (isActive) MaterialTheme.colorScheme.primary 
                          else MaterialTheme.colorScheme.onSurfaceVariant
        )
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isActive) FontWeight.Black else FontWeight.Medium
        )
    }
}
