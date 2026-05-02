package com.bitchat.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.data.OfflineDataManager
import com.bitchat.android.data.SharedContentManager
import com.bitchat.android.data.UserManager
import com.bitchat.android.ui.educational.*
import com.bitchat.android.ui.auth.AuthenticationScreen
import com.bitchat.android.ui.auth.UserProfileScreen
import com.bitchat.android.bluetooth.BluetoothDocumentService
import com.bitchat.android.bluetooth.BluetoothSharingScreen
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.mesh.MeshFileTransferService
import com.bitchat.android.quiz.QuizDistributionService
import com.bitchat.android.quiz.TeacherQuizDashboard
import com.bitchat.android.quiz.StudentQuizScreen
import com.bitchat.android.ui.wifidirect.WifiDirectScreen
import com.bitchat.android.model.EducationalContent
import com.bitchat.android.model.EducationalContentType
// Import quiz model with alias to avoid conflict with educational.Quiz
import com.bitchat.android.quiz.Quiz as DistributedQuiz

sealed class EducationalScreen {
    object Main : EducationalScreen()
    object DeviceDebug : EducationalScreen() // NEW: Debug screen for device detection
    object StudentResources : EducationalScreen()
    object DeviceScanner : EducationalScreen()
    object TeacherDocumentSharing : EducationalScreen()
    object StudentFileReceiver : EducationalScreen()
    object StudentFileSharing : EducationalScreen() // NEW: Students can share files
    object UserProfile : EducationalScreen()
    object BluetoothSharing : EducationalScreen()
    object TeacherQuizDashboard : EducationalScreen()
    object StudentQuizDashboard : EducationalScreen()
    object StudentCommunityChat : EducationalScreen()
    object VideoUpload : EducationalScreen()
    object WifiDirectSharing : EducationalScreen()
    data class QuizScreen(val quiz: Quiz) : EducationalScreen()
    data class DocumentViewer(val title: String, val content: DocumentContent) : EducationalScreen()
    data class VideoPlayer(val video: OfflineVideo) : EducationalScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleEducationalScreen(
    quizDistributionService: QuizDistributionService? = null,
    meshService: BluetoothMeshService? = null,
    fileTransferService: MeshFileTransferService? = null,
    chatViewModel: ChatViewModel? = null,
    onBackToModeSelection: (() -> Unit)? = null
) {
    var currentScreen by remember { mutableStateOf<EducationalScreen>(EducationalScreen.Main) }
    val context = LocalContext.current
    val dataManager = remember { OfflineDataManager(context) }
    val userManager = remember { 
        UserManager.getInstance(context).apply { 
            ensureDemoDataExists() 
        } 
    }
    val sharedContentManager = remember { SharedContentManager(context) }
    val bluetoothService = remember { BluetoothDocumentService(context) }
    // Use provided quiz service (with mesh) or create fallback
    val quizService = quizDistributionService ?: remember { QuizDistributionService(context) }
    
    // Authentication state
    val isLoggedIn by userManager.isLoggedIn.collectAsState()
    val currentUser by userManager.currentUser.collectAsState()
    
    // Show authentication screen if not logged in
    if (!isLoggedIn) {
        AuthenticationScreen(
            userManager = userManager,
            chatViewModel = chatViewModel,
            onAuthSuccess = {
                currentScreen = EducationalScreen.Main
            }
        )
        return
    }
    
    when (val screen = currentScreen) {
        EducationalScreen.Main -> {
            // Show role-specific main screen
            currentUser?.let { user ->
                when (user.role) {
                    com.bitchat.android.data.UserRole.TEACHER -> {
                        // Teacher Main Screen
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Teacher Dashboard") },
                                    navigationIcon = {
                                        onBackToModeSelection?.let {
                                            IconButton(onClick = it) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                            }
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { currentScreen = EducationalScreen.DeviceDebug }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Device Debug")
                                        }
                                        IconButton(onClick = { currentScreen = EducationalScreen.UserProfile }) {
                                            Icon(Icons.Default.Person, contentDescription = "Profile")
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                TeacherMainContent(
                                    userManager = userManager,
                                    meshService = meshService,
                                    onNavigateToQuiz = { currentScreen = EducationalScreen.TeacherQuizDashboard },
                                    onNavigateToFileSharing = { currentScreen = EducationalScreen.TeacherDocumentSharing },
                                    onNavigateToWiFiDirectSharing = { currentScreen = EducationalScreen.WifiDirectSharing }
                                )
                            }
                        }
                    }
                    com.bitchat.android.data.UserRole.STUDENT -> {
                        // Student Main Screen
                        Scaffold(
                            topBar = {
                                TopAppBar(
                                    title = { Text("Student Dashboard") },
                                    navigationIcon = {
                                        onBackToModeSelection?.let {
                                            IconButton(onClick = it) {
                                                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                                            }
                                        }
                                    },
                                    actions = {
                                        IconButton(onClick = { currentScreen = EducationalScreen.UserProfile }) {
                                            Icon(Icons.Default.Person, contentDescription = "Profile")
                                        }
                                    }
                                )
                            }
                        ) { padding ->
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(padding)
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                StudentMainContent(
                                    userManager = userManager,
                                    onNavigateToQuiz = { currentScreen = EducationalScreen.StudentQuizDashboard },
                                    onNavigateToResources = { currentScreen = EducationalScreen.StudentResources },
                                    onNavigateToReceivedFiles = { currentScreen = EducationalScreen.StudentFileReceiver },
                                    onNavigateToWiFiDirectReceiving = { currentScreen = EducationalScreen.WifiDirectSharing },
                                    onNavigateToShareFiles = { currentScreen = EducationalScreen.StudentFileSharing }
                                )
                            }
                        }
                    }
                }
            }
        }
        EducationalScreen.DeviceDebug -> {
            DeviceDetectionDebugScreen(
                onBack = { currentScreen = EducationalScreen.Main }
            )
        }
        EducationalScreen.UserProfile -> {
            currentUser?.let { user ->
                UserProfileScreen(
                    user = user,
                    userManager = userManager,
                    onBack = { currentScreen = EducationalScreen.Main },
                    onLogout = { currentScreen = EducationalScreen.Main }
                )
            }
        }
        EducationalScreen.StudentResources -> {
            Column {
                TopAppBar(
                    title = { Text("Student Resources") },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = EducationalScreen.Main }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                InteractiveStudentResourcesScreen(
                    dataManager = dataManager,
                    onOpenQuiz = { simpleQuiz ->
                        // Use actual quiz data instead of empty list
                        val quiz = when (simpleQuiz.id) {
                            "math_001" -> OfflineQuizData.mathQuiz
                            "science_001" -> OfflineQuizData.scienceQuiz
                            else -> {
                                // Fallback with basic math questions
                                Quiz(
                                    id = simpleQuiz.id,
                                    title = simpleQuiz.title,
                                    subject = simpleQuiz.subject,
                                    questions = listOf(
                                        QuizQuestion(
                                            id = "fallback_1",
                                            question = "What is 2 + 2?",
                                            options = listOf("3", "4", "5", "6"),
                                            correctAnswer = 1,
                                            explanation = "2 + 2 equals 4"
                                        ),
                                        QuizQuestion(
                                            id = "fallback_2",
                                            question = "What is 10 - 5?",
                                            options = listOf("3", "4", "5", "6"),
                                            correctAnswer = 2,
                                            explanation = "10 - 5 equals 5"
                                        )
                                    )
                                )
                            }
                        }
                        currentScreen = EducationalScreen.QuizScreen(quiz)
                    },
                    onOpenDocument = { title, simpleDoc ->
                        // Convert SimpleDocument to DocumentContent for compatibility  
                        val content = DocumentContent.TextDocument(
                            text = simpleDoc.content
                        )
                        currentScreen = EducationalScreen.DocumentViewer(title, content)
                    },
                    onOpenVideo = { simpleVideo ->
                        // Convert SimpleVideo to OfflineVideo
                        val video = OfflineVideo(
                            id = "video_${System.currentTimeMillis()}",
                            title = simpleVideo.title,
                            subject = "Educational",
                            description = simpleVideo.description,
                            durationSeconds = parseDurationToSeconds(simpleVideo.duration)
                        )
                        currentScreen = EducationalScreen.VideoPlayer(video)
                    },
                    onOpenDeviceScanner = {
                        currentScreen = EducationalScreen.DeviceScanner
                    }
                )
            }
        }
        EducationalScreen.DeviceScanner -> {
            DeviceScannerScreen(
                onBackClick = { currentScreen = EducationalScreen.StudentResources }
            )
        }
        EducationalScreen.TeacherDocumentSharing -> {
            if (meshService != null && fileTransferService != null) {
                TeacherDocumentSharingScreen(
                    meshService = meshService,
                    fileTransferService = fileTransferService,
                    onBackClick = { currentScreen = EducationalScreen.Main }
                )
            } else {
                // Fallback UI when services not available
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Document sharing requires mesh network connection")
                }
            }
        }
        EducationalScreen.StudentFileReceiver -> {
            if (fileTransferService != null) {
                StudentFileReceiverScreen(
                    fileTransferService = fileTransferService,
                    onBackClick = { currentScreen = EducationalScreen.Main }
                )
            } else {
                // Fallback UI when service not available
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("File receiver requires mesh network connection")
                }
            }
        }
        EducationalScreen.StudentFileSharing -> {
            if (fileTransferService != null && meshService != null) {
                val connectedPeers = meshService.getVerifiedPeersForEducation()
                StudentFileSharingScreen(
                    fileTransferService = fileTransferService,
                    connectedPeers = connectedPeers.keys.toList(),
                    onBackClick = { currentScreen = EducationalScreen.Main }
                )
            } else {
                // Fallback UI when service not available
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("File sharing requires mesh network connection")
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { currentScreen = EducationalScreen.Main }) {
                            Text("Go Back")
                        }
                    }
                }
            }
        }
        is EducationalScreen.QuizScreen -> {
            InteractiveQuizScreen(
                quiz = screen.quiz,
                dataManager = dataManager,
                onQuizComplete = { score, total ->
                    // Quiz completion handled by dataManager
                },
                onBackClick = { currentScreen = EducationalScreen.StudentResources }
            )
        }
        is EducationalScreen.DocumentViewer -> {
            BuiltInDocumentViewer(
                documentTitle = screen.title,
                documentContent = screen.content,
                onBackClick = { currentScreen = EducationalScreen.StudentResources }
            )
        }
        is EducationalScreen.VideoPlayer -> {
            OfflineVideoPlayer(
                video = screen.video,
                dataManager = dataManager,
                onBackClick = { currentScreen = EducationalScreen.StudentResources }
            )
        }
        EducationalScreen.BluetoothSharing -> {
            BluetoothSharingScreen(
                bluetoothService = bluetoothService,
                onNavigateBack = { currentScreen = EducationalScreen.Main }
            )
        }
        EducationalScreen.TeacherQuizDashboard -> {
            TeacherQuizDashboard(
                quizService = quizService,
                userManager = userManager,
                onNavigateBack = { currentScreen = EducationalScreen.Main }
            )
        }
        EducationalScreen.StudentQuizDashboard -> {
            currentUser?.let { user ->
                StudentQuizScreen(
                    quizService = quizService,
                    studentName = user.name,
                    onNavigateBack = { currentScreen = EducationalScreen.Main }
                )
            }
        }
        EducationalScreen.StudentCommunityChat -> {
            StudentCommunityChat(
                onBackClick = { currentScreen = EducationalScreen.Main }
            )
        }
        EducationalScreen.WifiDirectSharing -> {
            // Pass user role and mesh service for permission management
            WifiDirectScreen(
                userRole = currentUser?.role ?: com.bitchat.android.data.UserRole.STUDENT,
                meshService = meshService
            )
        }
        EducationalScreen.VideoUpload -> {
            Column {
                TopAppBar(
                    title = { Text("Upload Video") },
                    navigationIcon = {
                        IconButton(onClick = { currentScreen = EducationalScreen.Main }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                
                var showUploadDialog by remember { mutableStateOf(true) }
                
                if (showUploadDialog) {
                    FileUploadDialog(
                        onDismiss = { 
                            showUploadDialog = false
                            currentScreen = EducationalScreen.Main
                        },
                        onFileUploaded = { fileName, fileType, fileId ->
                            // Add the uploaded file to shared content
                            sharedContentManager.addUploadedFile(fileName, fileType, fileId, "Educational video uploaded by teacher")
                            showUploadDialog = false
                            currentScreen = EducationalScreen.Main
                        }
                    )
                }
                
                // Show a message while dialog is open
                if (showUploadDialog) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Choose a video file to upload...")
                    }
                }
            }
        }
    }
}

/* OLD UI - COMMENTED OUT - Now using TeacherMainContent and StudentMainContent
@Composable
private fun EducationalMainContent(
    userManager: UserManager,
    currentUser: com.bitchat.android.data.User?,
    onTeacherClick: () -> Unit,
    onStudentClick: () -> Unit,
    onProfileClick: () -> Unit,
    onBluetoothClick: () -> Unit,
    onQuizClick: () -> Unit,
    onCommunityClick: () -> Unit,
    onBackToModeSelection: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Back button to mode selection (if provided)
        if (onBackToModeSelection != null) {
            TextButton(
                onClick = onBackToModeSelection,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back to Mode Selection"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Mode Selection")
            }
        }
        
        // User Profile Header
        currentUser?.let { user ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // User Avatar
                    Card(
                        modifier = Modifier.size(60.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Person,
                                contentDescription = null,
                                modifier = Modifier.size(30.dp),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.width(16.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Welcome back!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = user.name,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (user.role == com.bitchat.android.data.UserRole.TEACHER) "Teacher" else "Student",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    // Profile Button
                    IconButton(onClick = onProfileClick) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Profile Settings",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
        
        // Main Content Based on Role
        currentUser?.let { user ->
            when (user.role) {
                com.bitchat.android.data.UserRole.TEACHER -> {
                    // Teacher Dashboard Access
                    Card(
                        onClick = onTeacherClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.School,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Teacher Dashboard",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Create quizzes, share documents, and manage students",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
                
                com.bitchat.android.data.UserRole.STUDENT -> {
                    // Student Resources Access
                    Card(
                        onClick = onStudentClick,
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Student Resources",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Access quizzes, documents, videos, and receive shared content",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
            
            // Common Features for All Users
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bluetooth Document Sharing
                Card(
                    onClick = onBluetoothClick,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Bluetooth Sharing",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = "Share documents wirelessly",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
                
                // Quiz System
                Card(
                    onClick = onQuizClick,
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.Quiz,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (user.role == com.bitchat.android.data.UserRole.TEACHER) "Quiz Dashboard" else "My Quizzes",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            text = if (user.role == com.bitchat.android.data.UserRole.TEACHER) "Create & manage quizzes" else "Take assigned quizzes",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
            
            // Student Community Chat (New Feature!)
            if (user.role == com.bitchat.android.data.UserRole.STUDENT) {
                Spacer(modifier = Modifier.height(16.dp))
                
                Card(
                    onClick = onCommunityClick,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Group,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "📚 Student Community Chat",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Connect with nearby students • Ask doubts • Share study tips",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            
            // Features Overview
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "� Available Features",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val features = when (user.role) {
                        com.bitchat.android.data.UserRole.TEACHER -> listOf(
                            "📝 Create and distribute quizzes in real-time",
                            "📄 Share documents via Bluetooth",
                            "👥 Monitor student progress and analytics",
                            "📊 View detailed quiz statistics",
                            "🔄 Real-time quiz distribution to students"
                        )
                        com.bitchat.android.data.UserRole.STUDENT -> listOf(
                            "📚 Take quizzes as they're assigned",
                            "📥 Receive documents via Bluetooth",
                            "🎥 Watch educational videos",
                            "📡 Connect with nearby teachers",
                            "� Join student community chat for doubts",
                            "�📊 Track your quiz progress"
                        )
                    }
                    
                    features.forEach { feature ->
                        Text(
                            text = feature,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
*/

/**
 * Parse duration string like "5:30" to seconds
 */
private fun parseDurationToSeconds(duration: String): Int {
    return try {
        val parts = duration.split(":")
        when (parts.size) {
            1 -> parts[0].toIntOrNull() ?: 300 // Just seconds
            2 -> {
                val minutes = parts[0].toIntOrNull() ?: 0
                val seconds = parts[1].toIntOrNull() ?: 0
                minutes * 60 + seconds
            }
            3 -> {
                val hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1].toIntOrNull() ?: 0
                val seconds = parts[2].toIntOrNull() ?: 0
                hours * 3600 + minutes * 60 + seconds
            }
            else -> 300 // Default 5 minutes
        }
    } catch (e: Exception) {
        300 // Default 5 minutes on error
    }
}