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
import androidx.compose.ui.zIndex
import com.bitchat.android.data.OfflineDataManager
import com.bitchat.android.ui.components.LanguageSelector
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Simple data models for this screen
data class SimpleQuiz(
    val id: String,
    val title: String,
    val questions: List<String>,
    val description: String = "",
    val timeLimit: Int = 15,
    val difficulty: String = "Easy",
    val subject: String = "General"
)

data class SimpleDocument(
    val title: String,
    val content: String,
    val type: String = "pdf",
    val filePath: String = ""
)

data class SimpleVideo(
    val title: String,
    val description: String,
    val duration: String
)

data class SharingNotification(
    val id: String,
    val teacherName: String,
    val documentName: String,
    val documentType: String,
    val size: String,
    val distance: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isAccepted: Boolean = false,
    val isReceived: Boolean = false
)

// Card Components
@Composable
fun SimpleQuizCard(
    title: String,
    description: String,
    onStartQuiz: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Quiz,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(onClick = onStartQuiz) {
                Text("Start")
            }
        }
    }
}

@Composable
fun SimpleDocumentCard(
    title: String,
    description: String,
    onOpenDocument: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Article,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(onClick = onOpenDocument) {
                Text("Read")
            }
        }
    }
}

@Composable
fun SimpleVideoCard(
    title: String,
    description: String,
    onOpenVideo: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Button(onClick = onOpenVideo) {
                Text("Watch")
            }
        }
    }
}

@Composable
fun InteractiveStudentResourcesScreen(
    modifier: Modifier = Modifier,
    dataManager: OfflineDataManager,
    onOpenQuiz: (SimpleQuiz) -> Unit = {},
    onOpenDocument: (String, SimpleDocument) -> Unit = { _, _ -> },
    onOpenVideo: (SimpleVideo) -> Unit = {},
    onOpenDeviceScanner: () -> Unit = {},
    onOpenReceivedFiles: () -> Unit = {}
) {
    // Notification system state
    var notifications by remember { mutableStateOf<List<SharingNotification>>(emptyList()) }
    var isReceivingContent by remember { mutableStateOf(false) }
    var receivingDocumentName by remember { mutableStateOf("") }
    var receivingProgress by remember { mutableStateOf(0f) }
    
    // Teacher sharing notifications will come from Bluetooth mesh
    // TODO: Listen for actual document sharing events from BitChat mesh service
    
    Box(modifier = modifier.fillMaxSize()) {
        // Language selector in top-right corner
        LanguageSelector(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .zIndex(1f)
        )
        
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Student Resources",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            item {
                // Device Scanner Card - NEW FEATURE
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
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🔍 Find Nearby Content",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Scan area for devices with educational content and get notes automatically",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Button(onClick = onOpenDeviceScanner) {
                        Text("Scan")
                    }
                }
            }
        }
        
        item {
            // Received Files Card - NEW FEATURE
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "📥 Received Files",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "View documents shared by teachers via mesh network",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                    
                    Button(onClick = onOpenReceivedFiles) {
                        Text("View")
                    }
                }
            }
        }
        
        item {
            Text(
                text = "📚 Interactive Quizzes",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        // No sample quizzes - will show when teacher creates them
        
        item {
            Text(
                text = "📄 Study Materials",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        // No sample documents - will show when teacher shares them
        
        item {
            Text(
                text = "🎥 Educational Videos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
        
        // No sample videos - will show when teacher shares them
        }
    
        // Notification system overlay
        if (notifications.isNotEmpty()) {
            StudentNotificationOverlay(
                notifications = notifications,
            onAcceptShare = { notification ->
                // Handle content acceptance
                isReceivingContent = true
                receivingDocumentName = notification.documentName
                receivingProgress = 0f
                
                // Remove notification
                notifications = notifications.filterNot { it.id == notification.id }
                
                // Simulate receiving content
                CoroutineScope(Dispatchers.Main).launch {
                    for (i in 1..100) {
                        delay(50)
                        receivingProgress = i / 100f
                    }
                    isReceivingContent = false
                    
                    // Add to offline storage (would work with proper OfflineDataManager method)
                    // dataManager.saveDocument would need to be added
                }
            },
            onDeclineShare = { notification ->
                notifications = notifications.filterNot { it.id == notification.id }
            }
        )
        }
        
        // Receiving content overlay
        if (isReceivingContent) {
            ReceivingContentOverlay(
                documentName = receivingDocumentName,
                progress = receivingProgress,
                isReceiving = true
            )
        }
    }
}