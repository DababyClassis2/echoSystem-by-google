package com.echosystem.localshare.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.echosystem.localshare.ui.components.DeviceCard
import com.echosystem.localshare.ui.components.RadarAnimation
import com.echosystem.localshare.viewmodel.EchoViewModel

@Composable
fun MainScreen(viewModel: EchoViewModel = hiltViewModel()) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, "Home") },
                    label = { Text("Share") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.History, "History") },
                    label = { Text("History") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Settings, "Settings") },
                    label = { Text("Settings") },
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                0 -> HomeScreen(viewModel)
                1 -> HistoryScreen()
                2 -> SettingsScreen(viewModel)
            }
        }
    }
}

@Composable
fun HomeScreen(viewModel: EchoViewModel) {
    val devices by viewModel.devices.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Discovering Devices", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(32.dp))
        RadarAnimation(modifier = Modifier.size(200.dp))
        Spacer(Modifier.height(32.dp))
        
        Button(onClick = { viewModel.startDiscovery() }) {
            Text("Start Discovery")
        }
        
        Spacer(Modifier.height(16.dp))
        
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(devices) { device ->
                DeviceCard(device = device, onClick = {
                    // Logic to send file would go here
                })
            }
        }
    }
}

@Composable
fun HistoryScreen() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("No transfers yet")
    }
}

@Composable
fun SettingsScreen(viewModel: EchoViewModel) {
    val pin by viewModel.pairingPin.collectAsState()
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Pairing PIN", style = MaterialTheme.typography.titleMedium)
                Text("Share this PIN with other devices to pair them securely.")
                Spacer(Modifier.height(8.dp))
                Text(pin ?: "----", style = MaterialTheme.typography.displayMedium)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { viewModel.generatePairingPin() }) {
                    Text("Generate New PIN")
                }
            }
        }
    }
}
