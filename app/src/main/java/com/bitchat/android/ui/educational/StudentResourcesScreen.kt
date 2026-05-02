/*
 * UNUSED FILE - COMMENTED OUT
 * This file is replaced by InteractiveStudentResourcesScreen.kt
 * Kept for reference in case needed in future
 *
package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.dp

// Student view of uploaded files
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentResourcesScreen(
    onBackClick: () -> Unit,
    onOpenFile: (UploadedFile) -> Unit = {}
) {
    // Available files shared by teacher
    val availableFiles = remember {
        listOf(
            UploadedFile("1", "Math Chapter 1.pdf", FileType.PDF, 2048000, "2024-01-15", "Basic addition and subtraction"),
            UploadedFile("2", "Science Video - Plants.mp4", FileType.VIDEO, 52428800, "2024-01-14", "Introduction to plant biology"),
            UploadedFile("3", "English Worksheet.docx", FileType.WORD, 1024000, "2024-01-13", "Grammar exercises"),
            UploadedFile("4", "History Timeline.pdf", FileType.PDF, 3072000, "2024-01-12", "Ancient civilizations")
        )
    }

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
                text = "Learning Resources",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Resources by type
        val groupedFiles = availableFiles.groupBy { it.type }
        
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            groupedFiles.forEach { (type, files) ->
                item {
                    Text(
                        text = getFileTypeName(type),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                items(files) { file ->
                    StudentFileCard(
                        file = file,
                        onClick = { onOpenFile(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun StudentFileCard(
    file: UploadedFile,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = getFileTypeEmoji(file.type),
                style = MaterialTheme.typography.headlineSmall
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = file.description,
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = formatFileSize(file.size),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            
            Text(
                text = "→",
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}

private fun getFileTypeName(type: FileType): String {
    return when (type) {
        FileType.PDF -> "📄 Documents"
        FileType.WORD -> "📝 Worksheets"
        FileType.VIDEO -> "🎥 Videos"
        FileType.IMAGE -> "🖼️ Images"
        FileType.OTHER -> "📁 Other Files"
    }
}
*/

