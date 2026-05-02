package com.bitchat.android.ui.educational

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.mesh.MeshFileTransferService
import java.io.File
import java.io.FileOutputStream

/**
 * Student File Sharing Screen
 * Allows students to share files with other students via Bluetooth mesh
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFileSharingScreen(
    fileTransferService: MeshFileTransferService,
    connectedPeers: List<String> = emptyList(),
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var isSharing by remember { mutableStateOf(false) }
    var shareStatus by remember { mutableStateOf("") }
    
    // File picker launcher - supports multiple file types
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            selectedFileUri = it
            selectedFileName = it.lastPathSegment ?: "selected_file"
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Share Files") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            // Info Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "📤 Share with Classmates",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Share notes, documents, or files with nearby students",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Connection Status
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (connectedPeers.isNotEmpty()) 
                            MaterialTheme.colorScheme.tertiaryContainer 
                        else 
                            MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (connectedPeers.isNotEmpty()) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (connectedPeers.isNotEmpty()) 
                                "🟢 ${connectedPeers.size} peer(s) connected" 
                            else 
                                "🔴 No peers connected - Move closer to classmates",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // File Selection Section
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Select File to Share",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // File type buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("application/pdf") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📄 PDF")
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🖼️ Image")
                            }
                            OutlinedButton(
                                onClick = { filePickerLauncher.launch("*/*") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("📁 Any")
                            }
                        }
                        
                        // Selected file display
                        selectedFileName?.let { fileName ->
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = {
                                            selectedFileUri = null
                                            selectedFileName = null
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Close,
                                            contentDescription = "Remove"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            
            // Share Button
            item {
                Button(
                    onClick = {
                        selectedFileUri?.let { uri ->
                            try {
                                isSharing = true
                                shareStatus = "Preparing file..."
                                
                                // Copy file to cache
                                val inputStream = context.contentResolver.openInputStream(uri)
                                val fileName = selectedFileName ?: "shared_file"
                                val extension = fileName.substringAfterLast('.', "dat")
                                val tempFile = File(context.cacheDir, "share_${System.currentTimeMillis()}.$extension")
                                
                                FileOutputStream(tempFile).use { output ->
                                    inputStream?.copyTo(output)
                                }
                                inputStream?.close()
                                
                                // Determine file type
                                val fileType = when {
                                    extension.lowercase() in listOf("pdf") -> "document"
                                    extension.lowercase() in listOf("jpg", "jpeg", "png", "gif") -> "image"
                                    extension.lowercase() in listOf("mp4", "mkv", "avi") -> "video"
                                    else -> "file"
                                }
                                
                                // Send via mesh network (broadcast to all)
                                fileTransferService.sendFile(
                                    filePath = tempFile.absolutePath,
                                    fileName = fileName,
                                    fileType = fileType,
                                    recipientPeerID = null // null = broadcast to all
                                )
                                
                                shareStatus = "File shared with all connected peers!"
                                Toast.makeText(context, "File shared successfully!", Toast.LENGTH_SHORT).show()
                                
                                // Clear selection
                                selectedFileUri = null
                                selectedFileName = null
                                
                            } catch (e: Exception) {
                                shareStatus = "Error: ${e.message}"
                                Toast.makeText(context, "Failed to share: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFileUri != null && !isSharing && connectedPeers.isNotEmpty()
                ) {
                    if (isSharing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isSharing) "Sharing..." else "Share with All Peers")
                }
                
                if (selectedFileUri != null && connectedPeers.isEmpty()) {
                    Text(
                        text = "⚠️ No peers connected. Move closer to classmates to share.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
            
            // Status
            if (shareStatus.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (shareStatus.startsWith("Error")) 
                                MaterialTheme.colorScheme.errorContainer 
                            else 
                                MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Text(
                            text = shareStatus,
                            modifier = Modifier.padding(16.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            
            // Tips
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "💡 Tips",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "• For files under 1MB: Use Bluetooth mesh (this screen)\n" +
                                   "• For large files (videos): Use WiFi Direct from main menu\n" +
                                   "• Stay close to peers for faster transfers",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}
