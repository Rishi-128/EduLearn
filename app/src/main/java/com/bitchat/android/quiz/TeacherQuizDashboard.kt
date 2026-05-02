package com.bitchat.android.quiz

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bitchat.android.ui.components.LanguageSelector
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeacherQuizDashboard(
    quizService: QuizDistributionService,
    userManager: com.bitchat.android.data.UserManager,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    val createdQuizzes by quizService.createdQuizzes.collectAsState()
    val activeQuizzes by quizService.activeQuizzes.collectAsState()
    val distributionStatus by quizService.distributionStatus.collectAsState()
    val quizSubmissions by quizService.quizSubmissions.collectAsState()
    
    // Real-time user activity tracking
    val onlineStudents by userManager.onlineUsers.collectAsState()
    val userActivityEvents by userManager.userActivityEvents.collectAsState()
    
    var selectedTab by remember { mutableStateOf(0) }
    var showCreateQuiz by remember { mutableStateOf(false) }
    var selectedQuiz by remember { mutableStateOf<Quiz?>(null) }
    var showQuizDetails by remember { mutableStateOf(false) }
    var showQuizResults by remember { mutableStateOf(false) }
    var showSendDialog by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Text(
                    text = "Quiz Dashboard",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Language selector
                LanguageSelector()
                
                // Send Quiz button - sends Active quizzes via mesh
                IconButton(
                    onClick = {
                        val sendableQuizzes = createdQuizzes.filter { 
                            it.status == QuizStatus.Active || it.status == QuizStatus.Draft 
                        }
                        if (sendableQuizzes.isNotEmpty()) {
                            showSendDialog = true
                        } else {
                            android.widget.Toast.makeText(
                                context,
                                "No quizzes available to send. Create or activate a quiz first.",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    enabled = createdQuizzes.any { it.status == QuizStatus.Active || it.status == QuizStatus.Draft }
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Send Quiz")
                }
                IconButton(onClick = { showCreateQuiz = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Quiz")
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Distribution Status
        AnimatedVisibility(
            visible = distributionStatus !is DistributionStatus.Idle,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            DistributionStatusCard(distributionStatus)
        }
        
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("All Quizzes") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Active") }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { selectedTab = 2 },
                text = { Text("Analytics") }
            )
        }
        
        // Content based on selected tab
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> AllQuizzesTab(
                    quizzes = createdQuizzes,
                    onQuizClick = { quiz ->
                        selectedQuiz = quiz
                        showQuizDetails = true
                    },
                    onDistributeQuiz = { quiz ->
                        quizService.distributeQuiz(quiz)
                    },
                    onEndQuiz = { quiz ->
                        quizService.endQuiz(quiz.id)
                    }
                )
                1 -> ActiveQuizzesTab(
                    activeQuizzes = activeQuizzes,
                    submissions = quizSubmissions,
                    onQuizClick = { quiz ->
                        selectedQuiz = quiz
                        showQuizResults = true
                    },
                    onEndQuiz = { quiz ->
                        quizService.endQuiz(quiz.id)
                    }
                )
                2 -> AnalyticsTab(
                    quizzes = createdQuizzes,
                    quizService = quizService
                )
            }
        }
    }
    
    // Create Quiz Dialog
    if (showCreateQuiz) {
        CreateQuizDialog(
            onDismiss = { showCreateQuiz = false },
            onQuizCreated = { title, description, questions, timeLimit, targetClass ->
                quizService.createQuiz(title, description, questions, timeLimit, targetClass)
                showCreateQuiz = false
            }
        )
    }
    
    // Quiz Details Dialog
    if (showQuizDetails && selectedQuiz != null) {
        QuizDetailsDialog(
            quiz = selectedQuiz!!,
            onDismiss = {
                showQuizDetails = false
                selectedQuiz = null
            },
            onDistribute = { quiz ->
                quizService.distributeQuiz(quiz)
                showQuizDetails = false
                selectedQuiz = null
            }
        )
    }
    
    // Quiz Results Dialog
    if (showQuizResults && selectedQuiz != null) {
        QuizResultsDialog(
            quiz = selectedQuiz!!,
            submissions = quizSubmissions.filter { it.quizId == selectedQuiz!!.id },
            statistics = quizService.getQuizStatistics(selectedQuiz!!.id),
            onDismiss = {
                showQuizResults = false
                selectedQuiz = null
            }
        )
    }
    
    // Send Quiz Dialog - Select which quiz to send via mesh
    if (showSendDialog) {
        val sendableQuizzes = createdQuizzes.filter { 
            it.status == QuizStatus.Active || it.status == QuizStatus.Draft 
        }
        AlertDialog(
            onDismissRequest = { showSendDialog = false },
            title = { Text("Send Quiz via Mesh") },
            text = {
                Column {
                    Text("Select a quiz to broadcast to students:", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    sendableQuizzes.forEach { quiz ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    quizService.distributeQuiz(quiz)
                                    showSendDialog = false
                                    android.widget.Toast.makeText(
                                        context,
                                        "📡 Broadcasting '${quiz.title}' via mesh network...",
                                        android.widget.Toast.LENGTH_LONG
                                    ).show()
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (quiz.status == QuizStatus.Active) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(quiz.title, fontWeight = FontWeight.Bold)
                                    QuizStatusChip(status = quiz.status)
                                }
                                Text("${quiz.questions.size} questions • ${quiz.timeLimit} min", 
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSendDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun AllQuizzesTab(
    quizzes: List<Quiz>,
    onQuizClick: (Quiz) -> Unit,
    onDistributeQuiz: (Quiz) -> Unit,
    onEndQuiz: (Quiz) -> Unit
) {
    if (quizzes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Quiz,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No quizzes created",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap + to create your first quiz",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(quizzes) { quiz ->
                QuizCard(
                    quiz = quiz,
                    onClick = { onQuizClick(quiz) },
                    onDistribute = { onDistributeQuiz(quiz) },
                    onEnd = { onEndQuiz(quiz) }
                )
            }
        }
    }
}

@Composable
private fun ActiveQuizzesTab(
    activeQuizzes: List<Quiz>,
    submissions: List<QuizSubmission>,
    onQuizClick: (Quiz) -> Unit,
    onEndQuiz: (Quiz) -> Unit
) {
    if (activeQuizzes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.HourglassEmpty,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No active quizzes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Create and distribute quizzes to see them here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(activeQuizzes) { quiz ->
                val quizSubmissions = submissions.filter { it.quizId == quiz.id }
                ActiveQuizCard(
                    quiz = quiz,
                    submissionCount = quizSubmissions.size,
                    onClick = { onQuizClick(quiz) },
                    onEnd = { onEndQuiz(quiz) }
                )
            }
        }
    }
}

@Composable
private fun AnalyticsTab(
    quizzes: List<Quiz>,
    quizService: QuizDistributionService
) {
    var selectedQuizForDetails by remember { mutableStateOf<Quiz?>(null) }
    val quizSubmissions by quizService.quizSubmissions.collectAsState()
    
    if (quizzes.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Analytics,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No analytics available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Quiz analytics will appear here after creation",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                OverallStatsCard(quizzes = quizzes, quizService = quizService)
            }
            
            items(quizzes) { quiz ->
                val statistics = quizService.getQuizStatistics(quiz.id)
                QuizAnalyticsCard(
                    quiz = quiz,
                    statistics = statistics,
                    onClick = { selectedQuizForDetails = quiz }
                )
            }
        }
    }
    
    // Show detailed results dialog when quiz clicked
    if (selectedQuizForDetails != null) {
        QuizResultsDialog(
            quiz = selectedQuizForDetails!!,
            submissions = quizSubmissions.filter { it.quizId == selectedQuizForDetails!!.id },
            statistics = quizService.getQuizStatistics(selectedQuizForDetails!!.id),
            onDismiss = { selectedQuizForDetails = null }
        )
    }
}

@Composable
private fun QuizCard(
    quiz: Quiz,
    onClick: () -> Unit,
    onDistribute: () -> Unit,
    onEnd: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = quiz.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                QuizStatusChip(status = quiz.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Class: ${quiz.targetClass}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Created: ${dateFormat.format(Date(quiz.createdAt))}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${quiz.questions.size} questions • ${quiz.timeLimit} min",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row {
                    when (quiz.status) {
                        QuizStatus.Draft, QuizStatus.Scheduled -> {
                            FilledTonalButton(
                                onClick = onDistribute,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Distribute", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        QuizStatus.Active -> {
                            FilledTonalButton(
                                onClick = onEnd,
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = MaterialTheme.colorScheme.error
                                ),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("End Quiz", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        QuizStatus.Ended -> {
                            FilledTonalButton(
                                onClick = onClick,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("View Results", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActiveQuizCard(
    quiz: Quiz,
    submissionCount: Int,
    onClick: () -> Unit,
    onEnd: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = quiz.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = quiz.targetClass,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(Color.Green, CircleShape)
                )
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "$submissionCount submissions",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${quiz.timeLimit} minutes • ${quiz.questions.size} questions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                
                Row {
                    FilledTonalButton(
                        onClick = onClick,
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("View", style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onEnd,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("End", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizStatusChip(status: QuizStatus) {
    val (text, color) = when (status) {
        QuizStatus.Draft -> "Draft" to MaterialTheme.colorScheme.surfaceVariant
        QuizStatus.Scheduled -> "Scheduled" to MaterialTheme.colorScheme.secondaryContainer
        QuizStatus.Active -> "Active" to MaterialTheme.colorScheme.primaryContainer
        QuizStatus.Ended -> "Ended" to MaterialTheme.colorScheme.errorContainer
    }
    
    Text(
        text = text,
        modifier = Modifier
            .background(color, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun DistributionStatusCard(status: DistributionStatus) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (status) {
                is DistributionStatus.Error -> MaterialTheme.colorScheme.errorContainer
                is DistributionStatus.Success -> MaterialTheme.colorScheme.primaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                is DistributionStatus.Distributing -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Distributing \"${status.quizTitle}\" to students...")
                }
                is DistributionStatus.Success -> {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("\"${status.quizTitle}\" distributed successfully!")
                }
                is DistributionStatus.Error -> {
                    Icon(Icons.Default.Error, contentDescription = null)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(status.message)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun OverallStatsCard(
    quizzes: List<Quiz>,
    quizService: QuizDistributionService
) {
    val totalQuizzes = quizzes.size
    val activeQuizzes = quizzes.count { it.status == QuizStatus.Active }
    val endedQuizzes = quizzes.count { it.status == QuizStatus.Ended }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Overall Statistics",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    label = "Total Quizzes",
                    value = totalQuizzes.toString(),
                    icon = Icons.Default.Quiz
                )
                StatItem(
                    label = "Active",
                    value = activeQuizzes.toString(),
                    icon = Icons.Default.PlayArrow
                )
                StatItem(
                    label = "Completed",
                    value = endedQuizzes.toString(),
                    icon = Icons.Default.CheckCircle
                )
            }
        }
    }
}

@Composable
internal fun StatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun QuizAnalyticsCard(
    quiz: Quiz,
    statistics: QuizStatistics,
    onClick: () -> Unit
) {
    val totalQuestions = quiz.questions.size
    val averageMarks = (statistics.averageScore * totalQuestions / 100.0).toInt()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = quiz.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                QuizStatusChip(status = quiz.status)
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                AnalyticsItem(
                    label = "Students Attempted",
                    value = statistics.totalSubmissions.toString()
                )
                AnalyticsItem(
                    label = "Avg. Marks",
                    value = "$averageMarks/$totalQuestions"
                )
                AnalyticsItem(
                    label = "Avg. Score",
                    value = "${statistics.averageScore.toInt()}%"
                )
            }
            
            if (statistics.totalSubmissions > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Highest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${statistics.highestScore}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Green
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Lowest",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${statistics.lowestScore}%",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.Red
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsItem(
    label: String,
    value: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StudentsManagementTab(
    userManager: com.bitchat.android.data.UserManager,
    onlineStudents: List<com.bitchat.android.data.User>,
    userActivityEvents: com.bitchat.android.data.UserActivityEvent?
) {
    // Get all registered students - use remember with key to update when activity changes
    val allRegisteredStudents = remember(userActivityEvents) { 
        userManager.getAllRegisteredStudents() 
    }
    val onlineStudentIds = onlineStudents.map { it.id }
    
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Online Students Section
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Currently Online",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${onlineStudents.size} students online now",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        if (onlineStudents.isNotEmpty()) {
            items(onlineStudents) { student ->
                StudentCard(
                    student = student,
                    isOnline = true,
                    isRecentActivity = userActivityEvents?.user?.id == student.id
                )
            }
        } else {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(40.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No students online right now",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
        
        // All Registered Students Section
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "All Registered Students",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${allRegisteredStudents.size} total students registered",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        if (allRegisteredStudents.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonAddDisabled,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No students registered yet",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Students will appear here after they create an account",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(allRegisteredStudents) { student ->
                val isOnline = onlineStudentIds.contains(student.id)
                StudentCard(
                    student = student,
                    isOnline = isOnline,
                    isRecentActivity = false
                )
            }
        }
    }
}

@Composable
private fun StudentCard(
    student: com.bitchat.android.data.User,
    isOnline: Boolean,
    isRecentActivity: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecentActivity) {
                MaterialTheme.colorScheme.tertiaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status indicator
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) Color(0xFF4CAF50) else Color.Gray
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Student info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = student.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (isRecentActivity) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⚡ Just logged in",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // Status badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isOnline) {
                    Color(0xFF4CAF50).copy(alpha = 0.2f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ) {
                Text(
                    text = if (isOnline) "Online" else "Offline",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isOnline) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun OnlineStudentsTab(
    onlineStudents: List<com.bitchat.android.data.User>,
    userActivityEvents: com.bitchat.android.data.UserActivityEvent?
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
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
                        Icon(
                            Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Online Students",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "${onlineStudents.size} students currently online",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
        
        if (onlineStudents.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.PersonOff,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No students online",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Students will appear here when they log into the app",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(onlineStudents) { student ->
                OnlineStudentCard(
                    student = student,
                    isRecentActivity = userActivityEvents?.user?.id == student.id
                )
            }
        }
    }
}

@Composable
private fun OnlineStudentCard(
    student: com.bitchat.android.data.User,
    isRecentActivity: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isRecentActivity) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isRecentActivity) 4.dp else 2.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Student Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = student.name.firstOrNull()?.toString()?.uppercase() ?: "S",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Student Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = student.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = student.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Online Status Indicator
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.Green)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Online",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Green,
                    fontWeight = FontWeight.Medium
                )
            }
            
            if (isRecentActivity) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Default.FiberNew,
                    contentDescription = "Recent Activity",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}