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
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.MeshFileTransferService
import java.io.File
import com.bitchat.android.ui.educational.formatFileSize

/**
 * Teacher Document Sharing Screen - Mesh Network Integrated
 * Uses BluetoothMeshService to detect students and share files via mesh
 */

data class NearbyStudent(
    val peerID: String,
    val name: String,
    val isOnline: Boolean = true,
    val hasReceivedContent: Boolean = false
)

data class SharedDocument(
    val id: String,
    val fileName: String,
    val fileType: String,
    val size: String,
    val sharedAt: Long = System.currentTimeMillis()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherDocumentSharingScreen(
    meshService: BluetoothMeshService,
    fileTransferService: MeshFileTransferService,
    onBackClick: () -> Unit
) {
    var nearbyStudents by remember { mutableStateOf<List<NearbyStudent>>(emptyList()) }
    var sharedDocuments by remember { mutableStateOf<List<SharedDocument>>(emptyList()) }
    var shareStatus by remember { mutableStateOf("") }
    var isSharing by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    // Observe transfer progress
    val transferProgress by fileTransferService.transferProgress.collectAsState()
    
    // Auto-refresh nearby students from mesh network
    LaunchedEffect(Unit) {
        while (true) {
            val connectedPeers = meshService.getVerifiedPeersForEducation()
            nearbyStudents = connectedPeers.map { (peerID, peerInfo) ->
                NearbyStudent(
                    peerID = peerID,
                    name = peerInfo.nickname,
                    isOnline = true
                )
            }
            kotlinx.coroutines.delay(2000)
        }
    }
    
    // File picker for documents
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                try {
                    Log.d("TeacherDocShare", "File picker returned URI: $uri")
                    
                    // Get file details
                    val fileName = getFileNameFromUri(context, uri) ?: "document"
                    val fileType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val fileSize = getFileSizeFromUri(context, uri)
                    
                    Log.d("TeacherDocShare", "File details - Name: $fileName, Type: $fileType, Size: $fileSize bytes")
                    
                    if (fileSize == 0L) {
                        shareStatus = "Error: File is empty"
                        Log.e("TeacherDocShare", "File size is 0")
                        kotlinx.coroutines.delay(3000)
                        shareStatus = ""
                        return@launch
                    }
                    
                    // Copy URI content to temp file
                    val tempFile = File(context.cacheDir, fileName)
                    Log.d("TeacherDocShare", "Copying file to temp location: ${tempFile.absolutePath}")
                    
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            val bytesCopied = input.copyTo(output)
                            Log.d("TeacherDocShare", "Copied $bytesCopied bytes to temp file")
                        }
                    }
                    
                    if (!tempFile.exists() || tempFile.length() == 0L) {
                        shareStatus = "Error: Failed to copy file"
                        Log.e("TeacherDocShare", "Temp file doesn't exist or is empty")
                        kotlinx.coroutines.delay(3000)
                        shareStatus = ""
                        return@launch
                    }
                    
                    // Add to shared documents list
                    val newDoc = SharedDocument(
                        id = "doc_${System.currentTimeMillis()}",
                        fileName = fileName,
                        fileType = fileType.split("/").lastOrNull()?.uppercase() ?: "FILE",
                        size = formatFileSize(fileSize)
                    )
                    sharedDocuments = sharedDocuments + newDoc
                    
                    Log.d("TeacherDocShare", "Nearby students count: ${nearbyStudents.size}")
                    
                    if (nearbyStudents.isEmpty()) {
                        shareStatus = "No students connected! Please wait for students to connect."
                        Log.w("TeacherDocShare", "No students connected, but file will still be sent via mesh")
                        // Still send the file - it will be broadcast and relayed through mesh
                    }
                    
                    // Start sharing via mesh (broadcast to all students)
                    isSharing = true
                    shareStatus = "Sharing $fileName (${formatFileSize(fileSize)}) via mesh..."
                    
                    Log.d("TeacherDocShare", "Starting file transfer via MeshFileTransferService")
                    Log.d("TeacherDocShare", "File: $fileName (${formatFileSize(fileSize)})")
                    Log.d("TeacherDocShare", "Recipients: ${if (nearbyStudents.isEmpty()) "broadcast mode" else "${nearbyStudents.size} nearby students"}")
                    
                    // Verify services are available
                    if (fileTransferService == null) {
                        shareStatus = "Error: File transfer service not available"
                        Log.e("TeacherDocShare", "FileTransferService is null!")
                        isSharing = false
                        kotlinx.coroutines.delay(3000)
                        shareStatus = ""
                        return@launch
                    }
                    
                    if (meshService == null) {
                        shareStatus = "Error: Mesh service not available"
                        Log.e("TeacherDocShare", "MeshService is null!")
                        isSharing = false
                        kotlinx.coroutines.delay(3000)
                        shareStatus = ""
                        return@launch
                    }
                    
                    fileTransferService.sendFile(
                        filePath = tempFile.absolutePath,
                        fileName = fileName,
                        fileType = fileType,
                        recipientPeerID = null // Broadcast to all students
                    )
                    
                    Log.d("TeacherDocShare", "File transfer initiated successfully for ${formatFileSize(fileSize)}")
                    shareStatus = "✅ ${formatFileSize(fileSize)} file sent! Broadcasting to ${if (nearbyStudents.isEmpty()) "mesh network" else "${nearbyStudents.size} students"}."
                    kotlinx.coroutines.delay(3000)
                    isSharing = false
                    shareStatus = ""
                    
                } catch (e: Exception) {
                    Log.e("TeacherDocShare", "Failed to share file: ${e.message}", e)
                    shareStatus = "Failed to share file: ${e.message}"
                    isSharing = false
                    kotlinx.coroutines.delay(5000)
                    shareStatus = ""
                }
            }
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        TopAppBar(
            title = { Text("Share Documents") },
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
            // Teacher Sharing Controls
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "📚 Mesh Document Sharing",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "Share documents instantly to all connected students via mesh network",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { fileLauncher.launch("*/*") },
                        enabled = nearbyStudents.isNotEmpty() && !isSharing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Upload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (nearbyStudents.isEmpty()) "No students connected"
                            else "Share Document (${nearbyStudents.size} students)"
                        )
                    }
                }
            }
            
            // Share Status
            if (shareStatus.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSharing) MaterialTheme.colorScheme.tertiaryContainer
                                       else MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isSharing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(shareStatus)
                    }
                }
            }
            
            // Transfer Progress
            if (transferProgress.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Transfer Progress",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        transferProgress.forEach { (fileID, progress) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                LinearProgressIndicator(
                                    progress = progress,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("${(progress * 100).toInt()}%")
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
            
            // Nearby Students
            if (nearbyStudents.isNotEmpty()) {
                Text(
                    text = "👥 Connected Students (${nearbyStudents.size})",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(nearbyStudents) { student ->
                        StudentCard(student = student)
                    }
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No students connected",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Students will appear when they open the app",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Shared Documents
            if (sharedDocuments.isNotEmpty()) {
                Text(
                    text = "📄 Shared Documents",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sharedDocuments) { doc ->
                        SharedDocumentCard(document = doc)
                    }
                }
            }
        }
    }
}

// Helper function to get file name from URI
private fun getFileNameFromUri(context: android.content.Context, uri: Uri): String? {
    var fileName: String? = null
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (cursor.moveToFirst()) {
            fileName = cursor.getString(nameIndex)
        }
    }
    return fileName
}

// Helper function to get file size from URI
private fun getFileSizeFromUri(context: android.content.Context, uri: Uri): Long {
    var fileSize = 0L
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        if (cursor.moveToFirst()) {
            fileSize = cursor.getLong(sizeIndex)
        }
    }
    return fileSize
}

@Composable
fun StudentCard(student: NearbyStudent) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Peer ID: ${student.peerID.take(8)}...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            AssistChip(
                onClick = { },
                label = { Text("✓ Connected") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

@Composable
fun SharedDocumentCard(document: SharedDocument) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${document.fileType} • ${document.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Icon(
                Icons.Default.Share,
                contentDescription = "Shared",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}