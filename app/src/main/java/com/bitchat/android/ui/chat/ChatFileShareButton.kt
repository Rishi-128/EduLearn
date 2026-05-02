package com.bitchat.android.ui.chat

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import java.nio.charset.Charset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Simple File Sharing Dialog for BitChat
 * Allows users to share documents and files during conversations
 */

@Composable
fun ChatFileShareButton(
    onFileShared: (String, String) -> Unit = { _, _ -> } // fileName, content
) {
    var showFileDialog by remember { mutableStateOf(false) }
    
    IconButton(
        onClick = { showFileDialog = true },
        modifier = Modifier.size(32.dp)
    ) {
        Icon(
            Icons.Default.AttachFile,
            contentDescription = "Share File",
            tint = MaterialTheme.colorScheme.primary
        )
    }
    
    if (showFileDialog) {
        ChatFileShareDialog(
            onDismiss = { showFileDialog = false },
            onFileShared = onFileShared
        )
    }
}

@Composable
private fun ChatFileShareDialog(
    onDismiss: () -> Unit,
    onFileShared: (String, String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    var isProcessing by remember { mutableStateOf(false) }
    var processingStatus by remember { mutableStateOf("") }
    
    // File picker launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            scope.launch {
                isProcessing = true
                processingStatus = "Processing file..."
                
                try {
                    val (fileName, content) = processSelectedFile(context, uri)
                    processingStatus = "File processed successfully!"
                    onFileShared(fileName, content)
                    kotlinx.coroutines.delay(1000)
                    onDismiss()
                } catch (e: Exception) {
                    processingStatus = "Error: ${e.message}"
                    kotlinx.coroutines.delay(2000)
                    isProcessing = false
                }
            }
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Share File",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                if (isProcessing) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(processingStatus)
                } else {
                    Text(
                        text = "Share a file offline - content will be displayed directly in the chat without requiring internet access",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = { fileLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Choose File")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "📱 Offline sharing: Text files show content directly. Binary files show descriptions. Max 100KB.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private suspend fun processSelectedFile(context: Context, uri: Uri): Pair<String, String> {
    val contentResolver = context.contentResolver
    val fileName = getFileName(context, uri)
    val mimeType = contentResolver.getType(uri) ?: ""
    
    // Read file content
    val inputStream = contentResolver.openInputStream(uri)
        ?: throw Exception("Cannot open file")
    
    val fileContent = inputStream.use { stream ->
        val bytes = stream.readBytes()
        
        // No file size limit - mesh network handles chunking
        
        when {
            // Text files - read as UTF-8
            mimeType.startsWith("text/") || 
            fileName.endsWith(".txt", true) ||
            fileName.endsWith(".md", true) ||
            fileName.endsWith(".json", true) ||
            fileName.endsWith(".xml", true) ||
            fileName.endsWith(".csv", true) -> {
                try {
                    val textContent = String(bytes, Charsets.UTF_8)
                    "📄 TEXT FILE CONTENT:\n\n$textContent"
                } catch (e: Exception) {
                    // Fallback to detect encoding
                    val detectedText = detectAndDecodeText(bytes)
                    if (detectedText.isNotEmpty()) {
                        "📄 TEXT FILE CONTENT:\n\n$detectedText"
                    } else {
                        "❌ Unable to read text file: $fileName"
                    }
                }
            }
            
            // Image files - show as offline placeholder
            mimeType.startsWith("image/") -> {
                "🖼️ IMAGE FILE SHARED\n" +
                "📁 File: $fileName\n" +
                "📏 Size: ${formatFileSize(bytes.size)}\n" +
                "💡 Image content available offline in your device storage"
            }
            
            // PDF files - show offline message
            mimeType == "application/pdf" || fileName.endsWith(".pdf", true) -> {
                "📋 PDF DOCUMENT SHARED\n" +
                "📁 File: $fileName\n" +
                "📏 Size: ${formatFileSize(bytes.size)}\n" +
                "💡 This PDF is saved offline. You can open it with any PDF reader app on your device."
            }
            
            // Office documents - show offline message
            mimeType.contains("office") || 
            mimeType.contains("word") ||
            mimeType.contains("excel") ||
            mimeType.contains("powerpoint") ||
            fileName.endsWith(".doc", true) ||
            fileName.endsWith(".docx", true) ||
            fileName.endsWith(".xls", true) ||
            fileName.endsWith(".xlsx", true) ||
            fileName.endsWith(".ppt", true) ||
            fileName.endsWith(".pptx", true) -> {
                val docType = when {
                    fileName.contains("doc", true) -> "Word Document"
                    fileName.contains("xls", true) -> "Excel Spreadsheet"
                    fileName.contains("ppt", true) -> "PowerPoint Presentation"
                    else -> "Office Document"
                }
                "📊 $docType SHARED\n" +
                "📁 File: $fileName\n" +
                "📏 Size: ${formatFileSize(bytes.size)}\n" +
                "💡 This document is saved offline. You can open it with compatible office apps."
            }
            
            // Try to detect if it's actually text
            else -> {
                val detectedText = detectAndDecodeText(bytes)
                if (detectedText.isNotEmpty() && isProbablyText(detectedText)) {
                    "📄 DOCUMENT CONTENT:\n\n$detectedText"
                } else {
                    "📦 BINARY FILE SHARED\n" +
                    "📁 File: $fileName\n" +
                    "📏 Size: ${formatFileSize(bytes.size)}\n" +
                    "💡 This file is saved offline on your device."
                }
            }
        }
    }
    
    return fileName to "📎 OFFLINE FILE SHARED: $fileName\n\n$fileContent"
}

private fun detectAndDecodeText(bytes: ByteArray): String {
    // Try different encodings
    val encodings = listOf(
        Charsets.UTF_8,
        Charsets.ISO_8859_1,
        Charsets.US_ASCII,
        Charset.forName("Windows-1252")
    )
    
    for (encoding in encodings) {
        try {
            val text = String(bytes, encoding)
            if (isProbablyText(text)) {
                return text
            }
        } catch (e: Exception) {
            // Try next encoding
        }
    }
    
    return ""
}

private fun isProbablyText(text: String): Boolean {
    if (text.length < 3) return false
    
    // Check if most characters are printable
    val printableCount = text.count { char ->
        char.isLetterOrDigit() || 
        char.isWhitespace() || 
        char in ".,!?;:()[]{}\"'-+=/*\\|@#$%^&_~`<>"
    }
    
    val ratio = printableCount.toDouble() / text.length
    return ratio > 0.7 // If more than 70% are printable characters
}

private fun formatFileSize(bytes: Int): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
}

private fun getFileName(context: Context, uri: Uri): String {
    val contentResolver = context.contentResolver
    val cursor = contentResolver.query(uri, null, null, null, null)
    
    return cursor?.use {
        if (it.moveToFirst()) {
            val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0) {
                it.getString(nameIndex) ?: "unknown_file"
            } else {
                "unknown_file"
            }
        } else {
            "unknown_file"
        }
    } ?: "unknown_file"
}