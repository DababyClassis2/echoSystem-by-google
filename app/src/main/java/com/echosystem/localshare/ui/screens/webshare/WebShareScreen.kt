package com.echosystem.localshare.ui.screens.webshare

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echosystem.localshare.web.WebShareViewModel

@Composable
fun WebShareScreen(
    viewModel: WebShareViewModel = hiltViewModel()
) {
    val isRunning by viewModel.isServerRunning.collectAsState()
    val url by viewModel.webShareUrl.collectAsState()
    val qrBitmap by viewModel.qrBitmapState.collectAsState()
    val sessions by viewModel.activeSessions.collectAsState()
    val pin by remember { derivedStateOf { viewModel.pairingManager.verifyPin("") ; "---" } } // Placeholder, need actual PIN from PairingManager
    
    // We can get the actual PIN from PairingManager via some logic or expose it in VM
    val genuinePin = remember { mutableStateOf("------") }
    LaunchedEffect(Unit) {
        genuinePin.value = viewModel.pairingManager.generatePin()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Branding
            Icon(
                Icons.Default.Language,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                "Web Portal 2.0",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Black
            )
            Text(
                "Access your local mesh from any browser without installing the proxy APK.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            WebShareControls(
                isRunning = isRunning,
                onStart = { viewModel.startWebShare() },
                onStop = { viewModel.stopWebShare() }
            )

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedVisibility(
                visible = isRunning,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    QrCanvas(bitmap = qrBitmap)
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            text = url,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Display Pairing PIN for Browser
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Shield Verification Key",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            genuinePin.value,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Black,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            letterSpacing = 4.sp
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    SessionList(sessions = sessions)
                }
            }
            
            if (!isRunning) {
                Spacer(modifier = Modifier.height(48.dp))
                EmptyPortalState()
            }
        }
    }
}

@Composable
fun EmptyPortalState() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(0.6f)
    ) {
        Icon(
            Icons.Default.Dns,
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "Waiting for Ignition",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Ignite the portal engine to start broadcasting the local mesh to your network.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
