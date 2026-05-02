package com.bitchat.android.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceDetectionDebugScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Auto-refresh every 2 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            refreshTrigger++
        }
    }
    
    // Get status (refreshes on trigger)
    val bluetoothStatus = remember(refreshTrigger) { getBluetoothStatus(context) }
    val locationStatus = remember(refreshTrigger) { getLocationStatus(context) }
    val permissionsStatus = remember(refreshTrigger) { getPermissionsStatus(context) }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Device Detection Debug") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Device Detection Status",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                "Auto-refreshing every 2 seconds...",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Bluetooth Status
            StatusCard(
                title = "Bluetooth",
                isOk = bluetoothStatus.enabled,
                details = listOf(
                    "Adapter Available: ${bluetoothStatus.adapterExists}",
                    "Bluetooth Enabled: ${bluetoothStatus.enabled}",
                    "BLE Supported: ${bluetoothStatus.bleSupported}"
                )
            )
            
            // Location Status
            StatusCard(
                title = "Location Services",
                isOk = locationStatus.enabled,
                details = listOf(
                    "GPS Enabled: ${locationStatus.gpsEnabled}",
                    "Network Enabled: ${locationStatus.networkEnabled}",
                    "Any Enabled: ${locationStatus.enabled}"
                )
            )
            
            // Permissions Status
            StatusCard(
                title = "Permissions",
                isOk = permissionsStatus.allGranted,
                details = buildList {
                    add("All Required: ${permissionsStatus.allGranted}")
                    add("")
                    add("Bluetooth Permissions:")
                    permissionsStatus.bluetooth.forEach { add("  $it") }
                    add("")
                    add("Location Permissions:")
                    permissionsStatus.location.forEach { add("  $it") }
                }
            )
            
            // Overall Status
            val canScan = bluetoothStatus.enabled && locationStatus.enabled && permissionsStatus.allGranted
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (canScan) 
                        Color(0xFF4CAF50).copy(alpha = 0.2f)
                    else 
                        Color(0xFFF44336).copy(alpha = 0.2f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        if (canScan) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (canScan) Color(0xFF4CAF50) else Color(0xFFF44336),
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            if (canScan) "READY TO SCAN" else "CANNOT SCAN",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (canScan) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                        Text(
                            if (canScan) 
                                "All prerequisites met for device detection"
                            else 
                                "Fix the issues above to enable device detection",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Instructions
            if (!canScan) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "How to Fix:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (!bluetoothStatus.enabled) {
                            Text("1. Go to Settings → Bluetooth → Turn ON")
                        }
                        
                        if (!locationStatus.enabled) {
                            Text("2. Go to Settings → Location → Turn ON")
                        }
                        
                        if (!permissionsStatus.allGranted) {
                            Text("3. Grant all permissions when app asks")
                            Text("   Or: Settings → Apps → BitChat → Permissions")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    isOk: Boolean,
    details: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOk) 
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    if (isOk) Icons.Default.CheckCircle else Icons.Default.Cancel,
                    contentDescription = null,
                    tint = if (isOk) Color(0xFF4CAF50) else Color(0xFFF44336)
                )
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            details.forEach { detail ->
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(start = if (detail.startsWith(" ")) 0.dp else 32.dp)
                )
            }
        }
    }
}

data class BluetoothStatus(
    val adapterExists: Boolean,
    val enabled: Boolean,
    val bleSupported: Boolean
)

data class LocationStatus(
    val gpsEnabled: Boolean,
    val networkEnabled: Boolean,
    val enabled: Boolean
)

data class PermissionsStatus(
    val bluetooth: List<String>,
    val location: List<String>,
    val allGranted: Boolean
)

private fun getBluetoothStatus(context: Context): BluetoothStatus {
    val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    val adapter = bluetoothManager?.adapter
    
    return BluetoothStatus(
        adapterExists = adapter != null,
        enabled = adapter?.isEnabled == true,
        bleSupported = context.packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_BLUETOOTH_LE)
    )
}

private fun getLocationStatus(context: Context): LocationStatus {
    val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    val gps = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
    val network = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
    
    return LocationStatus(
        gpsEnabled = gps,
        networkEnabled = network,
        enabled = gps || network
    )
}

private fun getPermissionsStatus(context: Context): PermissionsStatus {
    val bluetoothPerms = mutableListOf<String>()
    val locationPerms = mutableListOf<String>()
    
    // Check Bluetooth permissions
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        bluetoothPerms.add("BLUETOOTH_SCAN: ${checkPerm(context, Manifest.permission.BLUETOOTH_SCAN)}")
        bluetoothPerms.add("BLUETOOTH_CONNECT: ${checkPerm(context, Manifest.permission.BLUETOOTH_CONNECT)}")
        bluetoothPerms.add("BLUETOOTH_ADVERTISE: ${checkPerm(context, Manifest.permission.BLUETOOTH_ADVERTISE)}")
    } else {
        bluetoothPerms.add("BLUETOOTH: ${checkPerm(context, Manifest.permission.BLUETOOTH)}")
        bluetoothPerms.add("BLUETOOTH_ADMIN: ${checkPerm(context, Manifest.permission.BLUETOOTH_ADMIN)}")
    }
    
    // Check Location permissions
    locationPerms.add("FINE_LOCATION: ${checkPerm(context, Manifest.permission.ACCESS_FINE_LOCATION)}")
    locationPerms.add("COARSE_LOCATION: ${checkPerm(context, Manifest.permission.ACCESS_COARSE_LOCATION)}")
    
    val allGranted = (bluetoothPerms + locationPerms).all { it.contains("GRANTED") }
    
    return PermissionsStatus(
        bluetooth = bluetoothPerms,
        location = locationPerms,
        allGranted = allGranted
    )
}

private fun checkPerm(context: Context, permission: String): String {
    return if (ActivityCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
        "GRANTED ✓"
    } else {
        "DENIED ✗"
    }
}
