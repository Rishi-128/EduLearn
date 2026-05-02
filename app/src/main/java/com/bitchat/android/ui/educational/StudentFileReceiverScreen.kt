package com.bitchat.android.ui.educational

import android.content.ContentValues
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.clickable
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
import com.bitchat.android.mesh.ReceivedFile
import com.bitchat.android.util.FileOpener
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import com.bitchat.android.ui.educational.formatFileSize

/**
 * Student File Receiver Screen
 * Displays received files from teachers via mesh network
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentFileReceiverScreen(
    fileTransferService: MeshFileTransferService,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    
    // Observe received files
    val receivedFiles by fileTransferService.receivedFiles.collectAsState()
    
    // Observe transfer progress
    val transferProgress by fileTransferService.transferProgress.collectAsState()
    
    // Track active downloads
    val activeDownloads = transferProgress.filter { it.value < 1.0f }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Received Files") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (receivedFiles.isNotEmpty()) {
                        IconButton(onClick = { fileTransferService.clearReceivedFiles() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Clear All")
                        }
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
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "📥 Files from Teachers",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Tap any file to open it",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
            
            // Active Downloads
            if (activeDownloads.isNotEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "Receiving Files (${activeDownloads.size})",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            activeDownloads.forEach { (fileID, progress) ->
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = fileID.take(12) + "...",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            text = "${(progress * 100).toInt()}%",
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LinearProgressIndicator(
                                        progress = progress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // Received Files Header
            if (receivedFiles.isNotEmpty()) {
                item {
                    Text(
                        text = "📄 Received Files (${receivedFiles.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Files list
                items(receivedFiles.reversed()) { file ->
                    ReceivedFileCard(
                        file = file,
                        onOpenFile = {
                            val fileToOpen = File(file.filePath)
                            FileOpener.openFile(context, fileToOpen)
                        },
                        onDownloadFile = {
                            val fileToDownload = File(file.filePath)
                            downloadFileToDownloads(context, fileToDownload, file.fileName)
                        }
                    )
                }
            } else {
                // Empty State
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "No files received yet",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Files shared by teachers will appear here automatically",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReceivedFileCard(
    file: ReceivedFile,
    onOpenFile: () -> Unit,
    onDownloadFile: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // File info section
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // File type icon
                Icon(
                    when {
                        file.fileType.contains("pdf", ignoreCase = true) -> Icons.Default.Description
                        file.fileType.contains("image", ignoreCase = true) -> Icons.Default.Image
                        file.fileType.contains("video", ignoreCase = true) -> Icons.Default.VideoLibrary
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                // File details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "From: ${file.senderName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${formatFileSize(file.fileSize)} • ${formatTimestamp(file.timestamp)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            // Action buttons - LARGE AND VISIBLE
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Open button - FULL WIDTH
                Button(
                    onClick = onOpenFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Default.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("OPEN FILE", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                
                // Download button - FULL WIDTH
                OutlinedButton(
                    onClick = onDownloadFile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE TO DOWNLOADS", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

// Helper function to format timestamp
fun formatTimestamp(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hr ago"
        else -> {
            val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}

/**
 * Download file to public Downloads folder
 */
fun downloadFileToDownloads(context: android.content.Context, file: File, fileName: String) {
    try {
        Toast.makeText(context, "📥 Starting download...", Toast.LENGTH_SHORT).show()
        
        if (!file.exists()) {
            Toast.makeText(context, "❌ File not found: ${file.absolutePath}", Toast.LENGTH_LONG).show()
            return
        }
        
        // For Android 10+ (API 29+), use MediaStore
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, getMimeTypeFromFileName(fileName))
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            
            val uri = context.contentResolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            if (uri != null) {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    file.inputStream().use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                Toast.makeText(
                    context,
                    "✅ Downloaded to Downloads folder!\n$fileName",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(context, "❌ Failed to create download file", Toast.LENGTH_LONG).show()
            }
        } else {
            // For Android 9 and below
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val destFile = File(downloadsDir, fileName)
            
            file.copyTo(destFile, overwrite = true)
            
            Toast.makeText(
                context,
                "✅ Downloaded to Downloads folder!\n$fileName",
                Toast.LENGTH_LONG
            ).show()
        }
        
    } catch (e: Exception) {
        Toast.makeText(
            context,
            "❌ Download failed: ${e.message}",
            Toast.LENGTH_LONG
        ).show()
        android.util.Log.e("StudentFileReceiver", "Download failed", e)
    }
}

/**
 * Get MIME type from file name
 */
private fun getMimeTypeFromFileName(fileName: String): String {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    return when (extension) {
        "pdf" -> "application/pdf"
        "doc" -> "application/msword"
        "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        "xls" -> "application/vnd.ms-excel"
        "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
        "ppt" -> "application/vnd.ms-powerpoint"
        "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
        "txt" -> "text/plain"
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "mp3" -> "audio/mpeg"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }
}
