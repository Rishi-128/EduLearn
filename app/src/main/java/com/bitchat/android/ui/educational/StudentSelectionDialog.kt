package com.bitchat.android.ui.educational

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Data class representing a student for selection
 */
data class SelectableStudent(
    val id: String,
    val name: String,
    val peerID: String? = null, // BLE peer ID if connected
    val isConnected: Boolean = false,
    val isSelected: Boolean = false
)

/**
 * Dialog for selecting students to share files with
 * Teacher can choose to share with all students or select specific ones
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentSelectionDialog(
    students: List<SelectableStudent>,
    onDismiss: () -> Unit,
    onConfirm: (selectedStudentIds: List<String>, shareWithAll: Boolean) -> Unit
) {
    var shareWithAll by remember { mutableStateOf(true) }
    var selectedStudents by remember { mutableStateOf<Set<String>>(emptySet()) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("📤 Share File With")
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Share with all option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shareWithAll = true },
                    colors = CardDefaults.cardColors(
                        containerColor = if (shareWithAll) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = shareWithAll,
                            onClick = { shareWithAll = true }
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "📢 All Students",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Share with everyone in the class",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Select specific students option
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { shareWithAll = false },
                    colors = CardDefaults.cardColors(
                        containerColor = if (!shareWithAll) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = !shareWithAll,
                                onClick = { shareWithAll = false }
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "👥 Select Students",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Choose specific students to share with",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                        
                        // Show student list only when "Select Students" is chosen
                        if (!shareWithAll) {
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            if (students.isEmpty()) {
                                Text(
                                    text = "No students connected. Students need to be nearby and connected via Bluetooth.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                // Select/Deselect all
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${selectedStudents.size} of ${students.size} selected",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Row {
                                        TextButton(
                                            onClick = { selectedStudents = students.map { it.id }.toSet() }
                                        ) {
                                            Text("Select All")
                                        }
                                        TextButton(
                                            onClick = { selectedStudents = emptySet() }
                                        ) {
                                            Text("Clear")
                                        }
                                    }
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                // Student list with checkboxes
                                LazyColumn(
                                    modifier = Modifier.heightIn(max = 200.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    items(students) { student ->
                                        StudentSelectionItem(
                                            student = student,
                                            isSelected = selectedStudents.contains(student.id),
                                            onSelectionChanged = { selected ->
                                                selectedStudents = if (selected) {
                                                    selectedStudents + student.id
                                                } else {
                                                    selectedStudents - student.id
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Show connected count
                Spacer(modifier = Modifier.height(12.dp))
                val connectedCount = students.count { it.isConnected }
                Text(
                    text = "💡 $connectedCount student(s) currently connected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(selectedStudents.toList(), shareWithAll)
                },
                enabled = shareWithAll || selectedStudents.isNotEmpty()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (shareWithAll) "Share with All" else "Share with Selected")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StudentSelectionItem(
    student: SelectableStudent,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(!isSelected) }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChanged
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = student.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        // Connection status indicator
        if (student.isConnected) {
            Icon(
                Icons.Default.Wifi,
                contentDescription = "Connected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Icon(
                Icons.Default.WifiOff,
                contentDescription = "Not Connected",
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
