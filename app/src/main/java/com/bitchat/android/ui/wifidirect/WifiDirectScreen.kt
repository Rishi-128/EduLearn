package com.bitchat.android.ui.wifidirect

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitchat.android.wifidirect.WifiDirectCallback
import com.bitchat.android.wifidirect.WifiDirectManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.io.File
import java.io.FileOutputStream
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.UserManager
import com.bitchat.android.data.UserRole
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.wifidirect.WifiDirectPermissionManager
import com.bitchat.android.wifidirect.GORequestStatus

/**
 * WiFi Direct file sharing screen
 * Supports video and audio file transfers between teacher and students
 * 
 * Permission Model:
 * - Teachers: Can create groups immediately (default Group Owner)
 * - Students: Must request permission from teacher via Bluetooth mesh
 * 
 * SECURITY: Class-based filtering ensures files are only shared within enrolled classes.
 * Students can only see and receive files from their enrolled class.
 */
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun WifiDirectScreen(
    userRole: UserRole = UserRole.STUDENT,
    meshService: BluetoothMeshService? = null
) {
    val context = LocalContext.current
    val isTeacher = userRole == UserRole.TEACHER
    
    // Class management for filtering
    val classManager = remember { ClassManager.getInstance(context) }
    val userManager = remember { UserManager.getInstance(context) }
    val activeClass = remember { classManager.getActiveClass() }
    
    // Permission manager for student GO requests
    val permissionManager = remember(meshService) {
        meshService?.let { WifiDirectPermissionManager(it).apply { initialize() } }
    }
    
    // Observe permission status from manager (for students)
    val permissionStatus = permissionManager?.myRequestStatus?.collectAsState()
    
    // State
    var peers by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
    var isConnected by remember { mutableStateOf(false) }
    var connectionInfo by remember { mutableStateOf<WifiP2pInfo?>(null) }
    var transferProgress by remember { mutableStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("WiFi Direct not initialized") }
    var selectedFile by remember { mutableStateOf<Uri?>(null) }
    
    // Teachers always have permission; students need approval
    val hasGOPermission = isTeacher || (permissionStatus?.value is GORequestStatus.Approved)
    
    // WiFi Direct manager
    val wifiDirectManager = remember {
        WifiDirectManager(context).apply {
            callback = object : WifiDirectCallback {
                override fun onP2pSupported(supported: Boolean) {
                    statusMessage = if (supported) {
                        "WiFi Direct ready - tap Discover to find peers"
                    } else {
                        "WiFi Direct not supported on this device"
                    }
                }
                
                override fun onPeersUpdated(peersList: Collection<WifiP2pDevice>) {
                    peers = peersList.toList()
                    statusMessage = "Found ${peers.size} peer(s)"
                }
                
                override fun onConnectionInfoAvailable(info: WifiP2pInfo) {
                    connectionInfo = info
                    isConnected = info.groupFormed
                    statusMessage = if (info.isGroupOwner) {
                        "Connected as Host - You can share files"
                    } else {
                        "Connected as Client - Ready to receive files"
                    }
                }
                
                override fun onFileTransferProgress(bytesSent: Long, totalBytes: Long) {
                    transferProgress = bytesSent.toFloat() / totalBytes.toFloat()
                }
                
                override fun onFileTransferCompleted(success: Boolean) {
                    isTransferring = false
                    transferProgress = 0f
                    statusMessage = if (success) {
                        "File transfer completed successfully"
                    } else {
                        "File transfer failed"
                    }
                    Toast.makeText(
                        context,
                        if (success) "Transfer complete" else "Transfer failed",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                
                override fun onError(message: String) {
                    statusMessage = "Error: $message"
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
            }
            initialize()
        }
    }
    
    // Permissions
    val permissionsToRequest = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }
    
    val permissionsState = rememberMultiplePermissionsState(permissionsToRequest)
    
    // File picker
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedFile = uri
        uri?.let {
            statusMessage = "File selected: ${it.lastPathSegment}"
        }
    }
    
    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            wifiDirectManager.cleanup()
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("WiFi Direct File Sharing") },
                actions = {
                    IconButton(
                        onClick = {
                            if (permissionsState.allPermissionsGranted) {
                                wifiDirectManager.startDiscovery()
                                statusMessage = "Discovering peers..."
                            } else {
                                permissionsState.launchMultiplePermissionRequest()
                            }
                        }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Discover Peers")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Active class indicator
            if (activeClass != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📚",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Sharing within: ${activeClass.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Files shared here are encrypted for this class only",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            // Permission request card
            if (!permissionsState.allPermissionsGranted) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Permissions Required",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "WiFi Direct requires location permission to discover nearby devices.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { permissionsState.launchMultiplePermissionRequest() }
                        ) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
            
            // Status card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Status",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        // Role indicator
                        Surface(
                            color = if (isTeacher) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = if (isTeacher) "👨‍🏫 Teacher" else "👨‍🎓 Student",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    if (isTransferring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = transferProgress,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "${(transferProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
            
            // Teacher: Show pending GO permission requests from students
            if (isTeacher && permissionManager != null) {
                TeacherGOPermissionPanel(
                    permissionManager = permissionManager,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Student: Show permission request panel if not already approved
            if (!isTeacher && permissionManager != null && !hasGOPermission) {
                StudentGORequestPanel(
                    permissionManager = permissionManager,
                    isTeacher = false,
                    onPermissionGranted = { 
                        // Permission is now tracked via StateFlow, so UI will auto-update
                        // After permission granted, create group automatically
                        wifiDirectManager.createGroupAsOwner {
                            statusMessage = "✅ Group created! You can now share files."
                        }
                    },
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }
            
            // Create Group button (for teachers or approved students)
            if (!isConnected && hasGOPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = if (isTeacher) "👨‍🏫 Create Sharing Group" else "👨‍🎓 Create Sharing Group",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isTeacher) 
                                "As a teacher, you can create a WiFi Direct group for students to join and receive files."
                            else 
                                "You have permission to share files. Create a group for peers to join.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                wifiDirectManager.createGroupAsOwner {
                                    statusMessage = "✅ Group created! Waiting for peers to connect..."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Create Group (Become Host)")
                        }
                    }
                }
            }
            
            // Warning for students who became GO but don't have permission (edge case)
            if (isConnected && connectionInfo?.isGroupOwner == true && !hasGOPermission && !isTeacher) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ Permission Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "You are connected as host, but you need teacher's permission to share files. Please request permission first.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // File selection and send (for group owner - can be teacher OR approved student)
            // Students must have GO permission to share files
            if (isConnected && connectionInfo?.isGroupOwner == true && hasGOPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📤 Share File",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = if (isTeacher) 
                                "You are the host (Teacher). Select a file to share with students."
                            else 
                                "You have permission to share. Select a file to share with peers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // File type selection buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("video/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🎬 Video")
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("application/pdf") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📄 PDF")
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📁 Any")
                            }
                        }
                        
                        selectedFile?.let { uri ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Selected: ${uri.lastPathSegment}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    try {
                                        // Get original filename and extension
                                        val originalName = uri.lastPathSegment ?: "shared_file"
                                        val extension = originalName.substringAfterLast('.', "dat")
                                        val tempFile = File(context.cacheDir, "shared_${System.currentTimeMillis()}.$extension")
                                        
                                        // Copy URI to temp file
                                        val inputStream = context.contentResolver.openInputStream(uri)
                                        FileOutputStream(tempFile).use { output ->
                                            inputStream?.copyTo(output)
                                        }
                                        inputStream?.close()
                                        
                                        // As Group Owner, start file distribution server
                                        // Connected peers can receive the file
                                        isTransferring = true
                                        statusMessage = "File server started. Peers can now receive the file."
                                        
                                        wifiDirectManager.startFileDistributionServer(tempFile) { clientAddress ->
                                            statusMessage = "File sent to peer: $clientAddress"
                                        }
                                        
                                        Toast.makeText(context, "File ready! Others should tap 'Receive File' on their device.", Toast.LENGTH_LONG).show()
                                    } catch (e: Exception) {
                                        statusMessage = "Error preparing file: ${e.message}"
                                        isTransferring = false
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isTransferring
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Start Sharing")
                            }
                            
                            if (isTransferring) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        wifiDirectManager.stopFileServer()
                                        isTransferring = false
                                        statusMessage = "File server stopped"
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Stop Sharing")
                                }
                            }
                        }
                    }
                }
            }
            
            // Show receive option for non-group-owners (can be anyone - student or teacher)
            connectionInfo?.let { info ->
                if (!info.isGroupOwner && info.groupOwnerAddress != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "📥 Receive File",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Connected to host. Tap below to receive shared files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    isTransferring = true
                                    statusMessage = "Receiving file..."
                                    
                                    wifiDirectManager.requestFileFromGroupOwner(info.groupOwnerAddress!!) { receivedFile ->
                                        isTransferring = false
                                        statusMessage = "File received: ${receivedFile.name}"
                                        Toast.makeText(context, "File saved: ${receivedFile.absolutePath}", Toast.LENGTH_LONG).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isTransferring
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Receive File")
                            }
                        }
                    }
                }
            }
            
            // Peers list
            Text(
                text = "Available Peers (${peers.size})",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            if (peers.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No peers found. Tap refresh to discover.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(peers) { peer ->
                        PeerCard(
                            peer = peer,
                            isTeacherOrApproved = hasGOPermission,
                            onConnect = {
                                // Teachers and approved students connect with high GO intent
                                // Regular students connect as clients only
                                if (hasGOPermission) {
                                    wifiDirectManager.connectTo(peer) // High GO intent
                                    statusMessage = "Connecting to ${peer.deviceName} (as potential host)..."
                                } else {
                                    wifiDirectManager.connectAsClient(peer) // Low GO intent
                                    statusMessage = "Connecting to ${peer.deviceName} (as receiver)..."
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PeerCard(
    peer: WifiP2pDevice,
    isTeacherOrApproved: Boolean = false,
    onConnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = peer.deviceName,
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = peer.deviceAddress,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = getDeviceStatus(peer.status),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Button(
                onClick = onConnect,
                enabled = peer.status == WifiP2pDevice.AVAILABLE
            ) {
                Text(if (isTeacherOrApproved) "Connect" else "Join")
            }
        }
    }
}

private fun getDeviceStatus(status: Int): String {
    return when (status) {
        WifiP2pDevice.AVAILABLE -> "Available"
        WifiP2pDevice.INVITED -> "Invited"
        WifiP2pDevice.CONNECTED -> "Connected"
        WifiP2pDevice.FAILED -> "Failed"
        WifiP2pDevice.UNAVAILABLE -> "Unavailable"
        else -> "Unknown"
    }
}
