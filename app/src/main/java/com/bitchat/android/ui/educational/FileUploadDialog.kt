package com.bitchat.android.ui.educational

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitchat.android.storage.EduLearnFileStorage
import kotlinx.coroutines.launch

/**
 * Simple File Upload Dialog for Teachers - Completely Offline
 * Allows teachers to upload documents and videos that students can access
 */
@Composable
fun FileUploadDialog(
    onDismiss: () -> Unit,
    onFileUploaded: (String, String, String) -> Unit = { _, _, _ -> } // fileName, fileType, fileId
) {
    val context = LocalContext.current
    val fileStorage = remember { EduLearnFileStorage(context) }
    val scope = rememberCoroutineScope()
    
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableStateOf("") }
    
    // Document picker launcher
    val documentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isUploading = true
                uploadProgress = "Uploading document..."
                
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val fileName = getFileNameFromUri(context, uri)
                    
                    inputStream?.use { stream ->
                        val fileId = fileStorage.saveDocument(stream, fileName)
                        if (fileId != null) {
                            onFileUploaded(fileName, "document", fileId)
                            uploadProgress = "Document uploaded successfully!"
                        } else {
                            uploadProgress = "Failed to upload document"
                        }
                    }
                } catch (e: Exception) {
                    uploadProgress = "Upload failed: ${e.message}"
                }
                
                isUploading = false
            }
        }
    }
    
    // Video picker launcher
    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isUploading = true
                uploadProgress = "Uploading video..."
                
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val fileName = getFileNameFromUri(context, uri)
                    
                    inputStream?.use { stream ->
                        val fileId = fileStorage.saveVideo(stream, fileName)
                        if (fileId != null) {
                            onFileUploaded(fileName, "video", fileId)
                            uploadProgress = "Video uploaded successfully!"
                        } else {
                            uploadProgress = "Failed to upload video"
                        }
                    }
                } catch (e: Exception) {
                    uploadProgress = "Upload failed: ${e.message}"
                }
                
                isUploading = false
            }
        }
    }
    
    AlertDialog(
        onDismissRequest = { if (!isUploading) onDismiss() },
        title = {
            Text("Upload Educational Content")
        },
        text = {
            if (isUploading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(uploadProgress)
                }
            } else {
                Column {
                    Text("Choose file type to upload:")
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { 
                            documentLauncher.launch("application/pdf")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("📄 Upload Document (PDF)")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Button(
                        onClick = { 
                            videoLauncher.launch("video/*")
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🎥 Upload Video")
                    }
                    
                    if (uploadProgress.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(uploadProgress)
                    }
                }
            }
        },
        confirmButton = {
            if (!isUploading) {
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    )
}

// Helper function to get file name from URI
private fun getFileNameFromUri(context: Context, uri: Uri): String {
    var fileName = "unknown_file"
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                fileName = it.getString(nameIndex) ?: "unknown_file"
            }
        }
    }
    return fileName
}

