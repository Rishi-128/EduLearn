package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// File storage management for teachers
data class UploadedFile(
    val id: String,
    val name: String,
    val type: FileType,
    val size: Long,
    val uploadDate: String,
    val description: String = "",
    val filePath: String = ""
)

enum class FileType {
    PDF, WORD, VIDEO, IMAGE, OTHER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileManagerScreen(
    onBackClick: () -> Unit,
    onUploadNewFile: () -> Unit = {},
    onDeleteFile: (UploadedFile) -> Unit = {},
    onShareFile: (UploadedFile, List<String>, Boolean) -> Unit = { _, _, _ -> }, // file, selectedStudentIds, shareWithAll
    connectedStudents: List<SelectableStudent> = emptyList()
) {
    // Uploaded files - will be populated from actual uploads
    val uploadedFiles = remember {
        mutableStateListOf<UploadedFile>()
        // TODO: Load files from storage or Bluetooth mesh
    }
    
    // State for student selection dialog
    var showStudentSelection by remember { mutableStateOf(false) }
    var fileToShare by remember { mutableStateOf<UploadedFile?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "File Manager",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Upload new file button
        Button(
            onClick = onUploadNewFile,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("📁 Upload New File")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Files list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(uploadedFiles) { file ->
                FileItemCard(
                    file = file,
                    onDelete = { onDeleteFile(file) },
                    onShare = { 
                        fileToShare = file
                        showStudentSelection = true
                    }
                )
            }
        }
    }
    
    // Student Selection Dialog
    if (showStudentSelection && fileToShare != null) {
        StudentSelectionDialog(
            students = connectedStudents,
            onDismiss = { 
                showStudentSelection = false
                fileToShare = null
            },
            onConfirm = { selectedIds, shareWithAll ->
                fileToShare?.let { file ->
                    onShareFile(file, selectedIds, shareWithAll)
                }
                showStudentSelection = false
                fileToShare = null
            }
        )
    }
}

@Composable
private fun FileItemCard(
    file: UploadedFile,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = file.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = file.description,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "${formatFileSize(file.size)} • ${file.uploadDate}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Text(
                    text = getFileTypeEmoji(file.type),
                    style = MaterialTheme.typography.headlineSmall
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Share with Students")
                }
                
                OutlinedButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            }
        }
    }
}

internal fun getFileTypeEmoji(type: FileType): String {
    return when (type) {
        FileType.PDF -> "📄"
        FileType.WORD -> "📝"
        FileType.VIDEO -> "🎥"
        FileType.IMAGE -> "🖼️"
        FileType.OTHER -> "📁"
    }
}

internal fun formatFileSize(bytes: Long): String {
    val mb = bytes / (1024 * 1024)
    return if (mb > 0) "${mb}MB" else "${bytes / 1024}KB"
}
