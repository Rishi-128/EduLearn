package com.bitchat.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.R
import com.bitchat.android.data.UserManager
import com.bitchat.android.data.UserRole

@Composable
fun TeacherMainContent(
    userManager: UserManager,
    meshService: com.bitchat.android.mesh.BluetoothMeshService? = null,
    onNavigateToQuiz: () -> Unit,
    onNavigateToFileSharing: () -> Unit = {},
    onNavigateToWiFiDirectSharing: () -> Unit = {}
) {
    // Get REAL-TIME mesh-connected students instead of predefined list
    val meshConnectedPeers = remember { mutableStateOf<Map<String, com.bitchat.android.mesh.PeerInfo>>(emptyMap()) }
    
    // Auto-refresh connected students from mesh network
    LaunchedEffect(meshService) {
        while (meshService != null) {
            meshConnectedPeers.value = meshService.getVerifiedPeersForEducation()
            kotlinx.coroutines.delay(2000) // Refresh every 2 seconds
        }
    }
    
    // Extract real-time students from mesh
    val realTimeStudents = meshConnectedPeers.value.map { (peerID, peerInfo) ->
        com.bitchat.android.data.User(
            id = peerID,
            name = peerInfo.nickname,
            email = "${peerInfo.nickname.lowercase()}@mesh.local",
            role = com.bitchat.android.data.UserRole.STUDENT,
            isLoggedIn = true
        )
    }
    
    val onlineStudentIds = realTimeStudents.map { it.id }
    
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Quick Actions Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = stringResource(R.string.quick_actions),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNavigateToQuiz,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.create_quiz))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = onNavigateToFileSharing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share Files")
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // WiFi Direct Video Sharing Button
                Button(
                    onClick = { onNavigateToWiFiDirectSharing() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2196F3)
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("⚡ Share Files (WiFi Direct)")
                }
                
                Text(
                    text = "Fast transfer: 10-20 MB/s for files >1MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        // Online Students Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.students_online_now),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.students_currently_online, onlineStudentIds.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (onlineStudentIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    realTimeStudents.filter { student -> onlineStudentIds.contains(student.id) }.forEach { student ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(Color(0xFF4CAF50), shape = CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(student.name, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        
        // All Registered Students Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Group,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.all_registered_students),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.total_students, realTimeStudents.size),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                if (realTimeStudents.isEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No students connected. Students will appear here when they connect via Bluetooth mesh.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                } else {
                    Spacer(modifier = Modifier.height(12.dp))
                    realTimeStudents.forEach { student ->
                        val isOnline = onlineStudentIds.contains(student.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (isOnline) Color(0xFF4CAF50) else Color.Gray,
                                        shape = CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = student.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = student.email,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (isOnline) {
                                    Color(0xFF4CAF50).copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                }
                            ) {
                                Text(
                                    text = if (isOnline) "Online" else "Offline",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isOnline) Color(0xFF2E7D32) else Color.Gray
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
