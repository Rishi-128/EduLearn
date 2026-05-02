package com.bitchat.android.ui.wifidirect

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.wifidirect.GOPermissionRequest
import com.bitchat.android.wifidirect.GORequestStatus
import com.bitchat.android.wifidirect.WifiDirectPermissionManager
import java.text.SimpleDateFormat
import java.util.*

/**
 * Teacher's view for managing WiFi Direct Group Owner permission requests
 * Shows pending requests from students and allows approve/deny actions
 */
@Composable
fun TeacherGOPermissionPanel(
    permissionManager: WifiDirectPermissionManager,
    modifier: Modifier = Modifier
) {
    val pendingRequests by permissionManager.pendingRequests.collectAsState()
    val approvedStudents by permissionManager.approvedStudents.collectAsState()
    
    var expandedPanel by remember { mutableStateOf(true) }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (pendingRequests.isNotEmpty()) 
                MaterialTheme.colorScheme.errorContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Wifi,
                        contentDescription = null,
                        tint = if (pendingRequests.isNotEmpty()) 
                            MaterialTheme.colorScheme.error 
                        else 
                            MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "WiFi Direct Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    if (pendingRequests.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text("${pendingRequests.size}")
                        }
                    }
                }
                
                IconButton(onClick = { expandedPanel = !expandedPanel }) {
                    Icon(
                        imageVector = if (expandedPanel) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expandedPanel) "Collapse" else "Expand"
                    )
                }
            }
            
            AnimatedVisibility(visible = expandedPanel) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // Pending Requests Section
                    if (pendingRequests.isNotEmpty()) {
                        Text(
                            text = "⏳ Pending Requests",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        pendingRequests.forEach { request ->
                            GORequestCard(
                                request = request,
                                onApprove = { permissionManager.approveRequest(request) },
                                onDeny = { permissionManager.denyRequest(request, "Request denied by teacher") }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        Text(
                            text = "✅ No pending permission requests",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    // Approved Students Section
                    if (approvedStudents.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "✅ Approved Students (${approvedStudents.size})",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "These students can create WiFi Direct groups to share files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GORequestCard(
    request: GOPermissionRequest,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = request.studentName,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Requested at ${timeFormatter.format(Date(request.timestamp))}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Reason
            Text(
                text = "📝 Reason: ${request.reason}",
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDeny,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Deny")
                }
                
                Button(
                    onClick = onApprove,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Approve")
                }
            }
        }
    }
}

/**
 * Student's view for requesting WiFi Direct Group Owner permission
 */
@Composable
fun StudentGORequestPanel(
    permissionManager: WifiDirectPermissionManager,
    isTeacher: Boolean,
    onPermissionGranted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requestStatus by permissionManager.myRequestStatus.collectAsState()
    var reasonText by remember { mutableStateOf("Share study materials with classmates") }
    var showReasonDialog by remember { mutableStateOf(false) }
    
    // If teacher, they don't need permission
    if (isTeacher) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Teacher Mode",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "You can create WiFi Direct groups without permission",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        return
    }
    
    // Student view
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = when (requestStatus) {
                is GORequestStatus.Approved -> MaterialTheme.colorScheme.primaryContainer
                is GORequestStatus.Denied, is GORequestStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                is GORequestStatus.Pending -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "WiFi Direct Sharing Permission",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            when (val status = requestStatus) {
                is GORequestStatus.None -> {
                    Text(
                        text = "To share large files (videos, documents) via WiFi Direct, you need permission from your teacher.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { showReasonDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Request Permission")
                    }
                }
                
                is GORequestStatus.Pending -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "⏳ Waiting for approval...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Request sent to ${status.teacherName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
                
                is GORequestStatus.Approved -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "✅ Permission Granted!",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Approved by ${status.teacherName}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onPermissionGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Wifi, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Sharing")
                    }
                }
                
                is GORequestStatus.Denied -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Cancel,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "❌ Request Denied",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = status.reason,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showReasonDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Again")
                    }
                }
                
                is GORequestStatus.Failed -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "⚠️ Request Failed",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = status.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showReasonDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Try Again")
                    }
                }
                
                is GORequestStatus.Revoked -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "⚠️ Permission Revoked",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Your teacher has revoked your sharing permission",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { showReasonDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Request Again")
                    }
                }
            }
        }
    }
    
    // Reason dialog
    if (showReasonDialog) {
        AlertDialog(
            onDismissRequest = { showReasonDialog = false },
            title = { Text("Request Permission") },
            text = {
                Column {
                    Text("Why do you need to share files via WiFi Direct?")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = reasonText,
                        onValueChange = { reasonText = it },
                        label = { Text("Reason") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        permissionManager.requestGOPermission(reasonText)
                        showReasonDialog = false
                    }
                ) {
                    Text("Send Request")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReasonDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
