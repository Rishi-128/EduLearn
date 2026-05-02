package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Device Scanner Screen - Automatically detects nearby devices and shares content
 * This eliminates the need for manual file sharing or links
 */

data class NearbyDevice(
    val id: String,
    val name: String,
    val type: DeviceType,
    val distance: String,
    val hasContent: Boolean = false,
    val contentItems: List<String> = emptyList()
)

enum class DeviceType {
    TEACHER, STUDENT, ADMIN
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScannerScreen(
    onBackClick: () -> Unit
) {
    var isScanning by remember { mutableStateOf(false) }
    var nearbyDevices by remember { mutableStateOf<List<NearbyDevice>>(emptyList()) }
    var scanStatus by remember { mutableStateOf("Ready to scan") }
    var contentShared by remember { mutableStateOf<List<String>>(emptyList()) }
    
    // Auto-scan effect
    LaunchedEffect(isScanning) {
        if (isScanning) {
            scanStatus = "Scanning for devices..."
            delay(2000)
            
            // Scan for nearby devices via Bluetooth
            val foundDevices = listOf<NearbyDevice>()
            // TODO: Integrate with Bluetooth mesh to find actual nearby devices
            
            nearbyDevices = foundDevices
            scanStatus = "Found ${foundDevices.size} devices"
            
            // Auto-share content from teacher devices
            delay(1000)
            val teacherDevices = foundDevices.filter { it.type == DeviceType.TEACHER && it.hasContent }
            if (teacherDevices.isNotEmpty()) {
                scanStatus = "Receiving content..."
                delay(2000)
                contentShared = teacherDevices.flatMap { it.contentItems }
                scanStatus = "Content received successfully!"
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        TopAppBar(
            title = { Text("Device Scanner") },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            }
        )
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Scan Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "📡 Local Device Scanner",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Automatically finds nearby devices and shares educational content without internet",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (isScanning) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(scanStatus)
                    } else {
                        Button(
                            onClick = { isScanning = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Start Scanning")
                        }
                    }
                }
            }
            
            // Status
            if (scanStatus.isNotEmpty() && !isScanning) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(scanStatus)
                    }
                }
            }
            
            // Content Received
            if (contentShared.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Download,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Content Received (${contentShared.size} items)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        contentShared.forEach { content ->
                            Text(
                                text = "✅ $content",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            // Nearby Devices
            if (nearbyDevices.isNotEmpty()) {
                Text(
                    text = "📱 Nearby Devices",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nearbyDevices) { device ->
                        DeviceCard(device = device)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceCard(device: NearbyDevice) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        when (device.type) {
                            DeviceType.TEACHER -> Icons.Default.School
                            DeviceType.STUDENT -> Icons.Default.Person
                            DeviceType.ADMIN -> Icons.Default.AdminPanelSettings
                        },
                        contentDescription = null,
                        tint = when (device.type) {
                            DeviceType.TEACHER -> MaterialTheme.colorScheme.primary
                            DeviceType.STUDENT -> MaterialTheme.colorScheme.secondary
                            DeviceType.ADMIN -> MaterialTheme.colorScheme.tertiary
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${device.type.name.lowercase().replaceFirstChar { it.uppercase() }} • ${device.distance} away",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                if (device.hasContent) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = "Has Content",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (device.hasContent && device.contentItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Available Content:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium
                )
                device.contentItems.take(3).forEach { content ->
                    Text(
                        text = "• $content",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                    )
                }
                if (device.contentItems.size > 3) {
                    Text(
                        text = "... and ${device.contentItems.size - 3} more",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp, top = 2.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}