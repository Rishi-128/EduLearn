package com.bitchat.android.ui.educational

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CellTower
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.Classroom
import com.bitchat.android.data.ClassEnrollment
import com.bitchat.android.data.EnrollmentStatus
import com.bitchat.android.data.OfflineDataManager
import com.bitchat.android.data.SharedContentManager
import com.bitchat.android.data.UserManager
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.google.gson.Gson

// Teacher Dashboard with REAL-TIME offline data and Class Management
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnhancedTeacherDashboard(
    dataManager: OfflineDataManager,
    sharedContentManager: SharedContentManager,
    meshService: BluetoothMeshService? = null,
    onNavigateBack: () -> Unit,
    onNavigateToDocumentSharing: () -> Unit = {},
    onNavigateToWiFiDirectSharing: () -> Unit = {},
    onUploadDocument: () -> Unit = {},
    onUploadVideo: () -> Unit = {}
) {
    val context = LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    val userManager = remember { UserManager.getInstance(context) }
    val currentUser = userManager.getCurrentUser()
    val teacherId = currentUser?.id ?: ""
    val teacherName = currentUser?.name ?: "Teacher"
    val gson = remember { Gson() }
    
    // Class management state
    var classes by remember { mutableStateOf<List<Classroom>>(emptyList()) }
    var showCreateClassDialog by remember { mutableStateOf(false) }
    var selectedClassForDetails by remember { mutableStateOf<Classroom?>(null) }
    
    // Broadcast function
    val broadcastClass: (Classroom) -> Unit = { classroom ->
        if (meshService != null) {
            val classData = classManager.getClassBroadcastData(classroom.id)
            if (classData != null) {
                val payload = gson.toJson(classData).toByteArray(Charsets.UTF_8)
                meshService.broadcastClassAnnouncement(payload)
                Toast.makeText(context, "📡 Broadcasting ${classroom.name}...", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Mesh service not available", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Refresh classes on launch
    LaunchedEffect(Unit) {
        classes = classManager.getTeacherClasses(teacherId)
    }
    
    // Real-time data from shared content manager (updated by both teacher and student activities)
    val studentActivities by sharedContentManager.studentActivities.collectAsState()
    val allStudents = studentActivities.values.toList().sortedByDescending { it.averageScore }
    val uploadedFiles by sharedContentManager.uploadedFiles.collectAsState()
    
    // Calculate real-time statistics
    val totalStudents = allStudents.size
    val activeStudents = allStudents.count { 
        System.currentTimeMillis() - it.lastActivity < 24 * 60 * 60 * 1000 // Active in last 24 hours
    }
    val averageScore = if (allStudents.isNotEmpty()) {
        allStudents.map { it.averageScore }.average().toInt()
    } else 0
    val totalQuizzes = allStudents.sumOf { it.quizzesCompleted }
    val totalVideosWatched = allStudents.sumOf { it.videosWatched }
    
    // Dialog states
    var showUploadDialog by remember { mutableStateOf(false) }
    var showQuizCreationDialog by remember { mutableStateOf(false) }
    val uploadedFilesCount = uploadedFiles.size
    
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
            TextButton(onClick = onNavigateBack) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "EduLearn Teacher Dashboard",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // CLASS MANAGEMENT SECTION - New!
            item {
                ClassManagementCard(
                    classes = classes,
                    onCreateClass = { showCreateClassDialog = true },
                    onViewClassDetails = { classroom -> selectedClassForDetails = classroom },
                    onRefresh = { classes = classManager.getTeacherClasses(teacherId) },
                    onBroadcastClass = broadcastClass
                )
            }
            
            item {
                // Real-time class summary card
                RealTimeClassSummaryCard(
                    totalStudents = totalStudents,
                    activeStudents = activeStudents,
                    averageScore = averageScore,
                    totalQuizzes = totalQuizzes,
                    totalVideosWatched = totalVideosWatched
                )
            }

            item {
                // Enhanced quick actions with document sharing
                EnhancedQuickActionsCard(
                    onCreateQuiz = { showQuizCreationDialog = true },
                    onUploadDocument = onUploadDocument,
                    onUploadVideo = onUploadVideo,
                    onNavigateToDocumentSharing = onNavigateToDocumentSharing,
                    onNavigateToWiFiDirectSharing = onNavigateToWiFiDirectSharing
                )
            }

            item {
                // Students needing attention (real-time) - convert StudentActivity to StudentProgress
                val strugglingStudents = allStudents.filter { it.averageScore < 60 && it.quizzesCompleted > 0 }
                    .map { activity ->
                        com.bitchat.android.data.StudentProgress(
                            studentId = activity.studentId,
                            studentName = activity.studentName,
                            quizzesCompleted = activity.quizzesCompleted,
                            totalQuizScore = activity.totalScore,
                            videosWatched = activity.videosWatched,
                            totalWatchTimeMinutes = activity.videosWatched * 5, // Estimate 5 min per video
                            averageScore = activity.averageScore,
                            lastActivity = activity.lastActivity,
                            needsAttention = activity.averageScore < 60
                        )
                    }
                if (strugglingStudents.isNotEmpty()) {
                    RealTimeAttentionNeededCard(students = strugglingStudents, onViewStudent = {})
                }
            }

            item {
                // Real-time student performance list - convert StudentActivity to StudentProgress
                val studentProgressList = allStudents.map { activity ->
                    com.bitchat.android.data.StudentProgress(
                        studentId = activity.studentId,
                        studentName = activity.studentName,
                        quizzesCompleted = activity.quizzesCompleted,
                        totalQuizScore = activity.totalScore,
                        videosWatched = activity.videosWatched,
                        totalWatchTimeMinutes = activity.videosWatched * 5, // Estimate 5 min per video
                        averageScore = activity.averageScore,
                        lastActivity = activity.lastActivity,
                        needsAttention = activity.averageScore < 60
                    )
                }
                RealTimeStudentPerformanceCard(students = studentProgressList, onViewStudent = {})
            }
        }
    }
    
    // File Upload Dialog
    if (showUploadDialog) {
        FileUploadDialog(
            onDismiss = { showUploadDialog = false },
            onFileUploaded = { fileName, fileType, fileId ->
                // Add uploaded file to shared content so students can access it
                sharedContentManager.addUploadedFile(fileName, fileType, fileId, "Uploaded by teacher")
            }
        )
    }
    
    // Quiz Creation Dialog
    if (showQuizCreationDialog) {
        QuizCreationDialog(
            sharedContentManager = sharedContentManager,
            classManager = classManager,
            classes = classes,
            onDismiss = { showQuizCreationDialog = false }
        )
    }
    
    // Create Class Dialog
    if (showCreateClassDialog) {
        CreateClassDialog(
            classManager = classManager,
            teacherId = teacherId,
            teacherName = teacherName,
            onDismiss = { showCreateClassDialog = false },
            onClassCreated = {
                showCreateClassDialog = false
                classes = classManager.getTeacherClasses(teacherId)
            }
        )
    }
    
    // Class Details Bottom Sheet
    if (selectedClassForDetails != null) {
        ClassDetailsSheet(
            classroom = selectedClassForDetails!!,
            classManager = classManager,
            onDismiss = { selectedClassForDetails = null },
            onEnrollmentChanged = {
                classes = classManager.getTeacherClasses(teacherId)
            }
        )
    }
}

@Composable
private fun WorkingQuickActionsCard(
    onCreateQuiz: () -> Unit,
    onUploadFiles: () -> Unit,
    uploadedFilesCount: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📚 Teacher Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // File Upload Button - WORKING!
            Button(
                onClick = onUploadFiles,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Upload Files (Documents & Videos)")
            }
            
            if (uploadedFilesCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ $uploadedFilesCount files uploaded this session",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Additional Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateQuiz,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Create Quiz")
                }

                OutlinedButton(
                    onClick = { /* Export functionality could be added here */ },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export Data")
                }
            }
        }
    }
}

@Composable
private fun EnhancedQuickActionsCard(
    onCreateQuiz: () -> Unit,
    onUploadDocument: () -> Unit,
    onUploadVideo: () -> Unit,
    onNavigateToDocumentSharing: () -> Unit,
    onNavigateToWiFiDirectSharing: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Teacher Actions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // First row - Upload actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUploadDocument,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📄 Upload Document")
                }
                
                Button(
                    onClick = onUploadVideo,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🎥 Upload Video")
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // NEW: WiFi Direct Fast Video Sharing
            Button(
                onClick = onNavigateToWiFiDirectSharing,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2196F3)
                )
            ) {
                Icon(Icons.Default.Wifi, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("⚡ Share Videos (WiFi Direct)")
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Fast transfer: 10-20 MB/s • Perfect for large videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Document Sharing with Student Detection (Bluetooth)
            OutlinedButton(
                onClick = onNavigateToDocumentSharing,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("📡 Share Documents (Bluetooth)")
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Second row - Other actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCreateQuiz,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Create Quiz")
                }

                OutlinedButton(
                    onClick = { android.util.Log.d("TeacherDashboard", "Export Data clicked") },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Export Data")
                }
            }
        }
    }
}

// Real-time teacher dashboard components
@Composable
fun RealTimeClassSummaryCard(
    totalStudents: Int,
    activeStudents: Int,
    averageScore: Int,
    totalQuizzes: Int,
    totalVideosWatched: Int
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📊 Class Overview (Live Data)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem("👥 Students", "$activeStudents/$totalStudents")
                StatItem("⭐ Avg Score", if (averageScore > 0) "${averageScore}%" else "N/A")
                StatItem("📝 Quizzes", totalQuizzes.toString())
                StatItem("🎥 Videos", totalVideosWatched.toString())
            }
            
            if (totalStudents > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "✅ Data updates in real-time as students complete activities",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
fun RealTimeAttentionNeededCard(
    students: List<com.bitchat.android.data.StudentProgress>,
    onViewStudent: (com.bitchat.android.data.StudentProgress) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "⚠️ Students Needing Attention",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            students.forEach { student ->
                StudentAttentionItem(student = student, onClick = { onViewStudent(student) })
            }
        }
    }
}

@Composable
fun StudentAttentionItem(
    student: com.bitchat.android.data.StudentProgress,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(student.studentName)
            Text("${student.averageScore}% avg")
        }
    }
}

@Composable
fun RealTimeStudentPerformanceCard(
    students: List<com.bitchat.android.data.StudentProgress>,
    onViewStudent: (com.bitchat.android.data.StudentProgress) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "📈 Student Performance (Live)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (students.isEmpty()) {
                Text(
                    text = "No student activity yet. Data will appear as students complete quizzes and watch videos.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                students.forEach { student ->
                    RealTimeStudentItem(student = student, onClick = { onViewStudent(student) })
                }
            }
        }
    }
}

@Composable
fun RealTimeStudentItem(
    student: com.bitchat.android.data.StudentProgress,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.studentName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Quizzes: ${student.quizzesCompleted} | Videos: ${student.videosWatched}",
                    style = MaterialTheme.typography.bodySmall
                )
                if (student.lastActivity > 0) {
                    Text(
                        text = "Last active: ${formatLastActivity(student.lastActivity)}",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (student.averageScore > 0) "${student.averageScore}%" else "No scores",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        student.averageScore >= 80 -> MaterialTheme.colorScheme.primary
                        student.averageScore >= 60 -> MaterialTheme.colorScheme.secondary
                        student.averageScore > 0 -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                )
                
                if (student.averageScore < 60 && student.quizzesCompleted > 0) {
                    Text(
                        text = "Needs help",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

// Helper function
private fun formatLastActivity(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val hours = diff / (1000 * 60 * 60)
    val days = hours / 24
    
    return when {
        hours < 1 -> "Just now"
        hours < 24 -> "${hours}h ago"
        days == 1L -> "1 day ago"
        else -> "${days} days ago"
    }
}

// ===================== CLASS MANAGEMENT COMPONENTS =====================

/**
 * Class Management Card - Shows all classes and allows creating new ones
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassManagementCard(
    classes: List<Classroom>,
    onCreateClass: () -> Unit,
    onViewClassDetails: (Classroom) -> Unit,
    onRefresh: () -> Unit,
    onBroadcastClass: (Classroom) -> Unit = {}
) {
    val context = LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.School,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My Classes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
                
                Button(
                    onClick = onCreateClass,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Create Class")
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            if (classes.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No classes yet",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Create a class to share quizzes & documents securely with students",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Horizontal scrolling list of classes
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(classes) { classroom ->
                        val enrollments = classManager.getClassEnrollments(classroom.id)
                        val pendingCount = enrollments.count { it.status == EnrollmentStatus.PENDING }
                        val approvedCount = enrollments.count { it.status == EnrollmentStatus.APPROVED }
                        
                        ClassChip(
                            classroom = classroom,
                            studentCount = approvedCount,
                            pendingCount = pendingCount,
                            onClick = { onViewClassDetails(classroom) },
                            onBroadcast = { onBroadcastClass(classroom) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClassChip(
    classroom: Classroom,
    studentCount: Int,
    pendingCount: Int,
    onClick: () -> Unit,
    onBroadcast: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = classroom.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )
                
                // Broadcast button
                IconButton(
                    onClick = onBroadcast,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Default.Share,
                        contentDescription = "Broadcast to students",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            if (classroom.subject.isNotBlank()) {
                Text(
                    text = classroom.subject,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Code badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = classroom.code,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Student count
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.People,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "$studentCount",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
            
            // Pending badge if any
            if (pendingCount > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = "⏳ $pendingCount pending",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * Create Class Dialog
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClassDialog(
    classManager: ClassManager,
    teacherId: String,
    teacherName: String,
    onDismiss: () -> Unit,
    onClassCreated: () -> Unit
) {
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create New Class")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Students will need the class code and password to join.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = className,
                    onValueChange = { className = it; errorMessage = "" },
                    label = { Text("Class Name *") },
                    placeholder = { Text("e.g., 10th Grade Section A") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    placeholder = { Text("e.g., Mathematics") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = "" },
                    label = { Text("Class Password *") },
                    placeholder = { Text("Min 4 characters") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                )
                
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        className.isBlank() -> errorMessage = "Class name is required"
                        password.length < 4 -> errorMessage = "Password must be at least 4 characters"
                        else -> {
                            // Generate class code from name (uppercase, no spaces, max 8 chars)
                            val generatedCode = className.uppercase().replace(" ", "").take(8) + 
                                System.currentTimeMillis().toString().takeLast(4)
                            
                            val result = classManager.createClass(
                                name = className.trim(),
                                code = generatedCode,
                                password = password,
                                teacherId = teacherId,
                                teacherName = teacherName,
                                subject = subject.trim(),
                                description = ""
                            )
                            result.fold(
                                onSuccess = { onClassCreated() },
                                onFailure = { errorMessage = it.message ?: "Failed to create class" }
                            )
                        }
                    }
                },
                enabled = className.isNotBlank() && password.length >= 4
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Class Details Bottom Sheet - Shows enrollments and allows approve/reject
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClassDetailsSheet(
    classroom: Classroom,
    classManager: ClassManager,
    onDismiss: () -> Unit,
    onEnrollmentChanged: () -> Unit
) {
    var enrollments by remember { mutableStateOf(classManager.getClassEnrollments(classroom.id)) }
    
    val pendingEnrollments = enrollments.filter { it.status == EnrollmentStatus.PENDING }
    val approvedEnrollments = enrollments.filter { it.status == EnrollmentStatus.APPROVED }
    
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header
            Text(
                text = classroom.name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Code: ${classroom.code} • ${approvedEnrollments.size} students",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Pending Requests Section
            if (pendingEnrollments.isNotEmpty()) {
                Text(
                    text = "⏳ Pending Requests (${pendingEnrollments.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                pendingEnrollments.forEach { enrollment ->
                    EnrollmentRequestCard(
                        enrollment = enrollment,
                        onApprove = {
                            classManager.approveEnrollment(enrollment.id, classroom.teacherId)
                            enrollments = classManager.getClassEnrollments(classroom.id)
                            onEnrollmentChanged()
                        },
                        onReject = {
                            classManager.rejectEnrollment(enrollment.id)
                            enrollments = classManager.getClassEnrollments(classroom.id)
                            onEnrollmentChanged()
                        }
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
            }
            
            // Approved Students Section
            Text(
                text = "✅ Approved Students (${approvedEnrollments.size})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            if (approvedEnrollments.isEmpty()) {
                Text(
                    text = "No approved students yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                approvedEnrollments.forEach { enrollment ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = enrollment.studentName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = enrollment.studentEmail,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            TextButton(
                                onClick = {
                                    classManager.revokeAccess(enrollment.id)
                                    enrollments = classManager.getClassEnrollments(classroom.id)
                                    onEnrollmentChanged()
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("Revoke", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EnrollmentRequestCard(
    enrollment: ClassEnrollment,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = enrollment.studentName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = enrollment.studentEmail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onApprove,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = "Approve")
                }
                
                IconButton(
                    onClick = onReject,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Reject")
                }
            }
        }
    }
}
