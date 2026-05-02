package com.bitchat.android.bluetooth

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.provider.OpenableColumns
import android.net.Uri
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import java.text.SimpleDateFormat
import java.util.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BluetoothSharingScreen(
    bluetoothService: BluetoothDocumentService,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    val discoveredDevices by bluetoothService.discoveredDevices.collectAsState()
    val isScanning by bluetoothService.isScanning.collectAsState()
    val sharingStatus by bluetoothService.sharingStatus.collectAsState()
    val receivedFiles by bluetoothService.receivedFiles.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    var showDocumentPicker by remember { mutableStateOf(false) }
    var selectedDevice by remember { mutableStateOf<BluetoothDeviceInfo?>(null) }
    var showPermissionDialog by remember { mutableStateOf(false) }
    
    // Check permissions and Bluetooth availability
    LaunchedEffect(Unit) {
        if (!bluetoothService.isBluetoothAvailable()) {
            showPermissionDialog = true
        } else if (!bluetoothService.hasRequiredPermissions()) {
            showPermissionDialog = true
        } else {
            bluetoothService.startServer()
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Document Sharing",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Share Documents") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Received Files") }
            )
        }
        
        // Content based on selected tab
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> ShareDocumentsTab(
                    discoveredDevices = discoveredDevices,
                    isScanning = isScanning,
                    sharingStatus = sharingStatus,
                    onStartScan = { bluetoothService.startDiscovery() },
                    onStopScan = { bluetoothService.stopDiscovery() },
                    onDeviceSelected = { device ->
                        selectedDevice = device
                        showDocumentPicker = true
                    }
                )
                1 -> ReceivedFilesTab(
                    receivedFiles = receivedFiles
                )
            }
        }
    }
    
    // Document Picker Dialog
    if (showDocumentPicker && selectedDevice != null) {
        DocumentPickerDialog(
            device = selectedDevice!!,
            onDismiss = { 
                showDocumentPicker = false
                selectedDevice = null
            },
            onDocumentSelected = { document ->
                bluetoothService.shareDocument(selectedDevice!!, document)
                showDocumentPicker = false
                selectedDevice = null
            }
        )
    }
    
    // Permission Dialog
    if (showPermissionDialog) {
        PermissionRequiredDialog(
            onDismiss = { showPermissionDialog = false },
            onGrantPermissions = { 
                showPermissionDialog = false
                // In a real app, you would request permissions here
            }
        )
    }
}

@Composable
private fun ShareDocumentsTab(
    discoveredDevices: List<BluetoothDeviceInfo>,
    isScanning: Boolean,
    sharingStatus: SharingStatus,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onDeviceSelected: (BluetoothDeviceInfo) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Scan Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nearby Devices",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (isScanning) {
                        FilledTonalButton(
                            onClick = onStopScan,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Stop")
                        }
                    } else {
                        FilledTonalButton(onClick = onStartScan) {
                            Icon(Icons.Default.Search, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Scan")
                        }
                    }
                }
                
                if (isScanning) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Scanning for devices...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Sharing Status
        AnimatedVisibility(
            visible = sharingStatus !is SharingStatus.Idle,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            SharingStatusCard(sharingStatus)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Device List
        if (discoveredDevices.isNotEmpty()) {
            Text(
                text = "Available Devices (${discoveredDevices.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(discoveredDevices) { device ->
                    DeviceCard(
                        device = device,
                        onClick = { onDeviceSelected(device) }
                    )
                }
            }
        } else if (!isScanning) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No devices found",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Tap scan to discover nearby devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun ReceivedFilesTab(
    receivedFiles: List<ReceivedFile>
) {
    if (receivedFiles.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.FileDownload,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No received files",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Files shared with you will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(receivedFiles) { file ->
                ReceivedFileCard(file = file)
            }
        }
    }
}

@Composable
private fun DeviceCard(
    device: BluetoothDeviceInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Bluetooth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (device.isConnected) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color.Green, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Icon(
                Icons.Default.Share,
                contentDescription = "Share",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SharingStatusCard(status: SharingStatus) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is SharingStatus.Error -> MaterialTheme.colorScheme.errorContainer
                is SharingStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                is SharingStatus.Connecting -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Connecting to ${status.deviceName}...")
                }
                is SharingStatus.Sending -> {
                    CircularProgressIndicator(
                        progress = status.progress,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Sending to ${status.deviceName}")
                        Text(
                            "${(status.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is SharingStatus.Success -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Successfully shared to ${status.deviceName}")
                }
                is SharingStatus.Error -> {
                    Icon(Icons.Default.Error, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(status.message)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun ReceivedFileCard(file: ReceivedFile) {
    val context = LocalContext.current
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "From: ${file.senderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Size: ${formatFileSize(file.content.size)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = { openReceivedFile(context, file) }
                ) {
                    Text("Open")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Received: ${dateFormat.format(Date(file.receivedAt))}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DocumentPickerDialog(
    device: BluetoothDeviceInfo,
    onDismiss: () -> Unit,
    onDocumentSelected: (DocumentToShare) -> Unit
) {
    val context = LocalContext.current
    
    // File picker launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val fileName = getFileNameFromUri(context, uri) ?: "unknown_file"
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                
                inputStream?.use { stream ->
                    val content = stream.readBytes()
                    val document = DocumentToShare(
                        name = fileName,
                        type = mimeType,
                        content = content,
                        senderName = "Current User"
                    )
                    onDocumentSelected(document)
                }
            } catch (e: Exception) {
                // Handle error - could show a toast or error message
                android.util.Log.e("DocumentPicker", "Error reading file", e)
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Share Document")
        },
        text = {
            Column {
                Text("Choose a document to share with ${device.name}")
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { documentLauncher.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Upload, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pick Document from Device")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Select any file type (PDF, DOC, TXT, images, etc.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PermissionRequiredDialog(
    onDismiss: () -> Unit,
    onGrantPermissions: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Permissions Required")
        },
        text = {
            Text(
                "Bluetooth document sharing requires the following permissions:\n\n" +
                "• Bluetooth access\n" +
                "• Location access\n" +
                "• Bluetooth admin\n\n" +
                "Please grant these permissions to use document sharing."
            )
        },
        confirmButton = {
            FilledTonalButton(onClick = onGrantPermissions) {
                Text("Grant Permissions")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun getFileNameFromUri(context: Context, uri: Uri): String? {
    var fileName: String? = null
    
    // First try to get the display name from the URI
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val displayNameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (displayNameIndex != -1) {
                fileName = cursor.getString(displayNameIndex)
            }
        }
    }
    
    // If display name is not available, try to extract from the URI path
    if (fileName == null) {
        fileName = uri.lastPathSegment
    }
    
    // If still null, provide a default name
    return fileName ?: "document_${System.currentTimeMillis()}"
}

/**
 * Open a received file using the appropriate app
 */
private fun openReceivedFile(context: Context, file: ReceivedFile) {
    try {
        // Get the file from local storage
        val directory = File(context.filesDir, "received_documents")
        val localFile = File(directory, "${file.id}_${file.name}")
        
        // If file doesn't exist, write it from the ReceivedFile content
        if (!localFile.exists()) {
            directory.mkdirs()
            localFile.writeBytes(file.content)
        }
        
        // Create a content URI using FileProvider
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            localFile
        )
        
        // Determine MIME type
        val mimeType = file.type.ifEmpty {
            when (localFile.extension.lowercase()) {
                "pdf" -> "application/pdf"
                "txt" -> "text/plain"
                "doc" -> "application/msword"
                "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                "xls" -> "application/vnd.ms-excel"
                "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                "ppt" -> "application/vnd.ms-powerpoint"
                "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                "jpg", "jpeg" -> "image/jpeg"
                "png" -> "image/png"
                "gif" -> "image/gif"
                "mp4" -> "video/mp4"
                "mp3" -> "audio/mpeg"
                else -> "application/octet-stream"
            }
        }
        
        // Create intent to view the file
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        
        // Try to open the file
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // No app can handle this file type, show chooser
            val chooser = Intent.createChooser(intent, "Open with")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        }
        
    } catch (e: Exception) {
        android.util.Log.e("ReceivedFile", "Error opening file: ${file.name}", e)
        Toast.makeText(
            context,
            "Error opening file: ${e.message}",
            Toast.LENGTH_SHORT
        ).show()
    }
}

/**
 * Format file size in human-readable format
 */
private fun formatFileSize(size: Int): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> "${size / 1024} KB"
        else -> String.format("%.2f MB", size / (1024.0 * 1024.0))
    }
}