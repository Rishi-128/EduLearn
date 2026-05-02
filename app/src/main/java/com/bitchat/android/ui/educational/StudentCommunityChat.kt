package com.bitchat.android.ui.educational

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * WhatsApp-like Community Chat for Students - Completely Offline via Bluetooth Mesh
 * 
 * Features:
 * - Group chat for all connected students
 * - Doubt clearing and peer help
 * - Real-time message delivery via Bluetooth mesh
 * - Offline-first with message sync when peers reconnect
 * - WhatsApp-like UI with modern Material 3 design
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentCommunityChat(
    onBackClick: () -> Unit,
    viewModel: StudentCommunityChatViewModel = viewModel()
) {
    val messages by viewModel.messages.collectAsState()
    val connectedStudents by viewModel.connectedStudents.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    
    var messageText by remember { mutableStateOf(TextFieldValue("")) }
    val listState = rememberLazyListState()
    
    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        CommunityHeader(
            onBackClick = onBackClick,
            connectedStudents = connectedStudents,
            isConnected = isConnected
        )
        
        // Messages list
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    WelcomeMessage()
                }
            }
            
            items(messages) { message ->
                MessageBubble(
                    message = message,
                    isFromCurrentUser = message.senderId == currentUser.id,
                    currentUser = currentUser
                )
            }
        }
        
        // Message input
        MessageInputSection(
            messageText = messageText,
            onMessageTextChange = { messageText = it },
            onSendMessage = {
                if (messageText.text.trim().isNotEmpty()) {
                    viewModel.sendMessage(messageText.text.trim())
                    messageText = TextFieldValue("")
                }
            },
            isConnected = isConnected
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CommunityHeader(
    onBackClick: () -> Unit,
    connectedStudents: List<Student>,
    isConnected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "📚 Student Community",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                
                val statusText = if (isConnected) {
                    "${connectedStudents.size} students online"
                } else {
                    "Connecting..."
                }
                
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                )
            }
            
            // Connection indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isConnected) Color.Green else Color.Red
                    )
            )
        }
    }
}

@Composable
private fun WelcomeMessage() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Welcome to Student Community!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = """
                    🔗 Connect with nearby students via Bluetooth
                    💬 Ask questions and help each other
                    📚 Share study tips and doubt clearing
                    🌐 Completely offline - no internet needed!
                    
                    Start chatting to connect with other students nearby!
                """.trimIndent(),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: CommunityMessage,
    isFromCurrentUser: Boolean,
    currentUser: Student
) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isFromCurrentUser) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        if (!isFromCurrentUser) {
            // Other user's avatar
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = message.senderName.first().uppercase(),
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(modifier = Modifier.width(8.dp))
        }
        
        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isFromCurrentUser) {
                Alignment.End
            } else {
                Alignment.Start
            }
        ) {
            Card(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isFromCurrentUser) 16.dp else 4.dp,
                    bottomEnd = if (isFromCurrentUser) 4.dp else 16.dp
                ),
                colors = CardDefaults.cardColors(
                    containerColor = when {
                        isFromCurrentUser -> MaterialTheme.colorScheme.primary
                        message.messageType == MessageType.DOUBT_QUESTION -> 
                            MaterialTheme.colorScheme.errorContainer
                        message.messageType == MessageType.HELPFUL_ANSWER -> 
                            MaterialTheme.colorScheme.primaryContainer
                        message.messageType == MessageType.STUDY_TIP -> 
                            MaterialTheme.colorScheme.secondaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                border = if (message.messageType == MessageType.DOUBT_QUESTION && !isFromCurrentUser) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                } else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    if (!isFromCurrentUser) {
                        Text(
                            text = message.senderName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            isFromCurrentUser -> MaterialTheme.colorScheme.onPrimary
                            message.messageType == MessageType.DOUBT_QUESTION -> 
                                MaterialTheme.colorScheme.onErrorContainer
                            message.messageType == MessageType.HELPFUL_ANSWER -> 
                                MaterialTheme.colorScheme.onPrimaryContainer
                            message.messageType == MessageType.STUDY_TIP -> 
                                MaterialTheme.colorScheme.onSecondaryContainer
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = timeFormat.format(Date(message.timestamp)),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isFromCurrentUser) {
                                MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            }
                        )
                        
                        if (isFromCurrentUser && message.isDelivered) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Done,
                                contentDescription = "Delivered",
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
            
            if (message.messageType != MessageType.GENERAL_CHAT) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val (icon, label, color) = when (message.messageType) {
                        MessageType.DOUBT_QUESTION -> Triple(
                            Icons.Default.Help, 
                            "Doubt Question", 
                            MaterialTheme.colorScheme.error
                        )
                        MessageType.HELPFUL_ANSWER -> Triple(
                            Icons.Default.Lightbulb, 
                            "Helpful Answer", 
                            MaterialTheme.colorScheme.primary
                        )
                        MessageType.STUDY_TIP -> Triple(
                            Icons.Default.School, 
                            "Study Tip", 
                            MaterialTheme.colorScheme.secondary
                        )
                        else -> Triple(Icons.Default.Chat, "Message", MaterialTheme.colorScheme.outline)
                    }
                    
                    Icon(
                        icon,
                        contentDescription = label,
                        modifier = Modifier.size(16.dp),
                        tint = color
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        
        if (isFromCurrentUser) {
            Spacer(modifier = Modifier.width(8.dp))
        }
    }
}

@Composable
private fun MessageInputSection(
    messageText: TextFieldValue,
    onMessageTextChange: (TextFieldValue) -> Unit,
    onSendMessage: () -> Unit,
    isConnected: Boolean
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp
    ) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(
                            if (isConnected) {
                                "Ask a doubt or help others..."
                            } else {
                                "Connecting to nearby students..."
                            }
                        )
                    },
                    enabled = isConnected,
                    maxLines = 4,
                    shape = RoundedCornerShape(24.dp)
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                FloatingActionButton(
                    onClick = {
                        if (isConnected && messageText.text.trim().isNotEmpty()) {
                            onSendMessage()
                        }
                    },
                    modifier = Modifier.size(48.dp),
                    containerColor = if (isConnected && messageText.text.trim().isNotEmpty()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send Message"
                    )
                }
            }
            
            // Quick action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        onMessageTextChange(TextFieldValue("🤔 I have a doubt about: "))
                    },
                    enabled = isConnected
                ) {
                    Icon(Icons.Default.Help, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Ask Doubt")
                }
                
                OutlinedButton(
                    onClick = {
                        onMessageTextChange(TextFieldValue("💡 I can help with: "))
                    },
                    enabled = isConnected
                ) {
                    Icon(Icons.Default.Lightbulb, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Offer Help")
                }
            }
        }
    }
}

// ViewModel for managing community chat state
class StudentCommunityChatViewModel : ViewModel() {
    
    private val _messages = MutableStateFlow<List<CommunityMessage>>(emptyList())
    val messages: StateFlow<List<CommunityMessage>> = _messages.asStateFlow()
    
    private val _connectedStudents = MutableStateFlow<List<Student>>(emptyList())
    val connectedStudents: StateFlow<List<Student>> = _connectedStudents.asStateFlow()
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _currentUser = MutableStateFlow(
        Student(
            id = "student_${System.currentTimeMillis()}",
            name = "You",
            deviceId = "device_${System.currentTimeMillis()}"
        )
    )
    val currentUser: StateFlow<Student> = _currentUser.asStateFlow()
    
    // Channel name for student community
    private val COMMUNITY_CHANNEL = "#students"
    
    init {
        initializeCommunityChat()
    }
    
    private fun initializeCommunityChat() {
        viewModelScope.launch {
            // TODO: Connect to existing BitChat mesh service
            // For now, simulate connection
            _isConnected.value = true
            
            // Connected students will be populated from Bluetooth mesh
            _connectedStudents.value = emptyList()
            // TODO: Integrate with BitChat mesh service to get real connected students
            
            // Messages will be received via Bluetooth mesh
            _messages.value = emptyList()
        }
    }
    
    fun sendMessage(content: String) {
        viewModelScope.launch {
            val messageType = detectMessageType(content)
            
            val newMessage = CommunityMessage(
                id = "msg_${System.currentTimeMillis()}",
                senderId = _currentUser.value.id,
                senderName = _currentUser.value.name,
                content = content,
                timestamp = System.currentTimeMillis(),
                messageType = messageType,
                isDelivered = true // Simulate instant delivery
            )
            
            _messages.value = _messages.value + newMessage
            
            // Send message via mesh network
            sendMessageToMesh(newMessage)
        }
    }
    
    private fun detectMessageType(content: String): MessageType {
        return when {
            content.contains("doubt", ignoreCase = true) ||
            content.contains("🤔", ignoreCase = true) ||
            content.contains("help me", ignoreCase = true) ||
            content.contains("question", ignoreCase = true) -> MessageType.DOUBT_QUESTION
            
            content.contains("can help", ignoreCase = true) ||
            content.contains("💡", ignoreCase = true) ||
            content.contains("answer", ignoreCase = true) -> MessageType.HELPFUL_ANSWER
            
            content.contains("tip", ignoreCase = true) ||
            content.contains("study", ignoreCase = true) -> MessageType.STUDY_TIP
            
            else -> MessageType.GENERAL_CHAT
        }
    }
    
    private fun sendMessageToMesh(message: CommunityMessage) {
        // TODO: Integrate with existing BluetoothMeshService
        // This would send the message to the COMMUNITY_CHANNEL
        // using the existing mesh infrastructure:
        // 
        // meshService?.sendMessage(
        //     content = "[${message.messageType}] ${message.content}",
        //     channel = COMMUNITY_CHANNEL
        // )
        //
        // For now, this is a placeholder that demonstrates the integration point
    }
    
    fun receiveMessage(message: CommunityMessage) {
        viewModelScope.launch {
            // Only add if not already in list (prevent duplicates)
            if (_messages.value.none { it.id == message.id }) {
                _messages.value = _messages.value + message
            }
        }
    }
    
    fun updateConnectedStudents(students: List<Student>) {
        viewModelScope.launch {
            _connectedStudents.value = students
            _isConnected.value = students.isNotEmpty()
        }
    }
    
    // Function to be called when mesh service reports new peers
    fun onPeersUpdated(peerIds: List<String>, peerNicknames: Map<String, String>) {
        viewModelScope.launch {
            val students = peerIds.map { peerId ->
                Student(
                    id = peerId,
                    name = peerNicknames[peerId] ?: "Student",
                    deviceId = peerId,
                    isOnline = true
                )
            }
            updateConnectedStudents(students)
        }
    }
}

// Data classes
data class Student(
    val id: String,
    val name: String,
    val deviceId: String,
    val isOnline: Boolean = true
)

data class CommunityMessage(
    val id: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long,
    val messageType: MessageType = MessageType.GENERAL_CHAT,
    val isDelivered: Boolean = false
)

enum class MessageType {
    GENERAL_CHAT,
    DOUBT_QUESTION,
    HELPFUL_ANSWER,
    STUDY_TIP
}