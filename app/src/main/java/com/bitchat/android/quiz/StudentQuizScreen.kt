package com.bitchat.android.quiz

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudentQuizScreen(
    quizService: QuizDistributionService,
    studentName: String,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val allDistributedQuizzes by quizService.availableQuizzes.collectAsState()
    val studentHistory = quizService.getStudentQuizHistory(studentName)
    
    // Sync state for quiz discovery
    var isSyncing by remember { mutableStateOf(false) }
    var syncMessage by remember { mutableStateOf("") }
    
    // Filter out quizzes this student has already submitted
    val availableQuizzes = remember(allDistributedQuizzes, studentHistory) {
        val submittedQuizIds = studentHistory.map { it.quizId }.toSet()
        allDistributedQuizzes.filter { it.id !in submittedQuizIds }
    }
    
    var selectedTab by remember { mutableStateOf(0) }
    var selectedQuiz by remember { mutableStateOf<Quiz?>(null) }
    var showQuizTaking by remember { mutableStateOf(false) }
    var showQuizResult by remember { mutableStateOf(false) }
    var quizResult by remember { mutableStateOf<QuizSubmission?>(null) }
    
    // Auto-refresh for new quizzes
    LaunchedEffect(Unit) {
        while (true) {
            delay(5000) // Refresh every 5 seconds for new quizzes
            // In a real app, this would be handled by real-time updates
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        CenterAlignedTopAppBar(
            title = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "My Quizzes",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Welcome, $studentName",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Sync Button to discover quizzes from mesh network
                IconButton(
                    onClick = {
                        isSyncing = true
                        syncMessage = "Discovering quizzes from mesh network..."
                        // Request quiz sync from mesh (this will trigger mesh to rebroadcast available quizzes)
                        quizService.requestQuizSync()
                        scope.launch {
                            delay(2000)
                            isSyncing = false
                            syncMessage = if (availableQuizzes.isNotEmpty()) {
                                "Found ${availableQuizzes.size} quiz(zes)!"
                            } else {
                                "No new quizzes found. Make sure you're connected to teacher."
                            }
                            delay(3000)
                            syncMessage = ""
                        }
                    },
                    enabled = !isSyncing
                ) {
                    if (isSyncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync Quizzes")
                    }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )
        
        // Sync Message
        AnimatedVisibility(
            visible = syncMessage.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = syncMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
        
        // New Quiz Notification
        AnimatedVisibility(
            visible = availableQuizzes.isNotEmpty(),
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut()
        ) {
            NewQuizNotification(availableQuizzes.size)
        }
        
        // Tab Row
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Available")
                        if (availableQuizzes.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Badge {
                                Text(availableQuizzes.size.toString())
                            }
                        }
                    }
                }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Completed") }
            )
        }
        
        // Content based on selected tab
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                0 -> AvailableQuizzesTab(
                    quizzes = availableQuizzes,
                    onQuizSelected = { quiz ->
                        selectedQuiz = quiz
                        showQuizTaking = true
                    }
                )
                1 -> CompletedQuizzesTab(
                    history = studentHistory,
                    onViewResult = { submission ->
                        quizResult = submission
                        showQuizResult = true
                    }
                )
            }
        }
    }
    
    // Quiz Taking Dialog
    if (showQuizTaking && selectedQuiz != null) {
        QuizTakingDialog(
            quiz = selectedQuiz!!,
            studentName = studentName,
            onDismiss = {
                showQuizTaking = false
                selectedQuiz = null
            },
            onSubmit = { submission ->
                quizService.submitQuiz(
                    selectedQuiz!!.id,
                    submission,
                    studentName
                )
                showQuizTaking = false
                selectedQuiz = null
            }
        )
    }
    
    // Quiz Result Dialog
    if (showQuizResult && quizResult != null) {
        QuizResultDialog(
            submission = quizResult!!,
            onDismiss = {
                showQuizResult = false
                quizResult = null
            }
        )
    }
}

@Composable
private fun NewQuizNotification(quizCount: Int) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "New Quiz${if (quizCount > 1) "zes" else ""} Available!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You have $quizCount quiz${if (quizCount > 1) "zes" else ""} waiting to be completed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun AvailableQuizzesTab(
    quizzes: List<Quiz>,
    onQuizSelected: (Quiz) -> Unit
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
                    Icons.Default.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No quizzes available",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "New quizzes from your teacher will appear here",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(quizzes) { quiz ->
                AvailableQuizCard(
                    quiz = quiz,
                    onClick = { onQuizSelected(quiz) }
                )
            }
        }
    }
}

@Composable
private fun CompletedQuizzesTab(
    history: List<QuizSubmission>,
    onViewResult: (QuizSubmission) -> Unit
) {
    if (history.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "No completed quizzes",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Your completed quiz history will appear here",
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
            items(history.sortedByDescending { it.submittedAt }) { submission ->
                CompletedQuizCard(
                    submission = submission,
                    onClick = { onViewResult(submission) }
                )
            }
        }
    }
}

@Composable
private fun AvailableQuizCard(
    quiz: Quiz,
    onClick: () -> Unit
) {
    val timeUntilExpiry = remember {
        if (quiz.distributedAt != null) {
            val expiryTime = quiz.distributedAt + (quiz.timeLimit * 60 * 1000)
            expiryTime - System.currentTimeMillis()
        } else null
    }
    
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = quiz.description,
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
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                QuizInfoChip(
                    icon = Icons.Default.Quiz,
                    text = "${quiz.questions.size} questions"
                )
                QuizInfoChip(
                    icon = Icons.Default.Timer,
                    text = "${quiz.timeLimit} min"
                )
                QuizInfoChip(
                    icon = Icons.Default.Class,
                    text = quiz.targetClass
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            FilledTonalButton(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Quiz")
            }
            
            if (timeUntilExpiry != null && timeUntilExpiry > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Time remaining: ${formatTimeRemaining(timeUntilExpiry)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun CompletedQuizCard(
    submission: QuizSubmission,
    onClick: () -> Unit
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiz ID: ${submission.quizId.take(8)}...",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Completed: ${dateFormat.format(Date(submission.submittedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            ScoreChip(score = submission.score)
        }
    }
}

@Composable
private fun QuizInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun ScoreChip(score: Int) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = when {
            score >= 80 -> Color.Green.copy(alpha = 0.1f)
            score >= 60 -> Color(0xFFFF9800).copy(alpha = 0.1f)
            else -> Color.Red.copy(alpha = 0.1f)
        }
    ) {
        Text(
            text = "$score%",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                score >= 80 -> Color.Green
                score >= 60 -> Color(0xFFFF9800)
                else -> Color.Red
            },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun QuizTakingDialog(
    quiz: Quiz,
    studentName: String,
    onDismiss: () -> Unit,
    onSubmit: (Map<String, String>) -> Unit
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var answers by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var timeRemaining by remember { mutableStateOf(quiz.timeLimit * 60) } // in seconds
    var showSubmitConfirmation by remember { mutableStateOf(false) }
    
    // Timer
    LaunchedEffect(timeRemaining) {
        if (timeRemaining > 0) {
            delay(1000)
            timeRemaining--
        } else {
            // Time's up, auto-submit
            onSubmit(answers)
        }
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Header with timer
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = quiz.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Question ${currentQuestionIndex + 1} of ${quiz.questions.size}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            
                            TimerChip(timeRemaining = timeRemaining)
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        LinearProgressIndicator(
                            progress = (currentQuestionIndex + 1).toFloat() / quiz.questions.size,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                // Question Content
                val currentQuestion = quiz.questions[currentQuestionIndex]
                
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(16.dp)
                ) {
                    Text(
                        text = currentQuestion.text,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    currentQuestion.options.forEach { option ->
                        val isSelected = answers[currentQuestion.id] == option
                        
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    answers[currentQuestion.id] = option
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else MaterialTheme.colorScheme.surfaceVariant
                            ),
                            border = if (isSelected) 
                                BorderStroke(2.dp, MaterialTheme.colorScheme.primary) 
                            else null
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = null
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                // Navigation Buttons
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        if (currentQuestionIndex > 0) {
                            OutlinedButton(
                                onClick = { currentQuestionIndex-- }
                            ) {
                                Icon(Icons.Default.ArrowBack, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Previous")
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        
                        if (currentQuestionIndex < quiz.questions.size - 1) {
                            FilledTonalButton(
                                onClick = { currentQuestionIndex++ },
                                enabled = answers.containsKey(currentQuestion.id)
                            ) {
                                Text("Next")
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.ArrowForward, contentDescription = null)
                            }
                        } else {
                            FilledTonalButton(
                                onClick = { showSubmitConfirmation = true },
                                enabled = answers.size == quiz.questions.size
                            ) {
                                Text("Submit Quiz")
                            }
                        }
                    }
                }
            }
        }
    }
    
    // Submit Confirmation
    if (showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            title = { Text("Submit Quiz?") },
            text = { 
                Text("Are you sure you want to submit your quiz? You answered ${answers.size} out of ${quiz.questions.size} questions.")
            },
            confirmButton = {
                FilledTonalButton(
                    onClick = {
                        onSubmit(answers)
                        showSubmitConfirmation = false
                    }
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSubmitConfirmation = false }
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun TimerChip(timeRemaining: Int) {
    val minutes = timeRemaining / 60
    val seconds = timeRemaining % 60
    val isLowTime = timeRemaining < 300 // 5 minutes
    
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isLowTime) Color.Red.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isLowTime) Color.Red else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (isLowTime) Color.Red else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun QuizResultDialog(
    submission: QuizSubmission,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.7f),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quiz Result",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Score Display
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "${submission.score}%",
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                submission.score >= 80 -> Color.Green
                                submission.score >= 60 -> Color(0xFFFF9800)
                                else -> Color.Red
                            }
                        )
                        
                        Text(
                            text = when {
                                submission.score >= 80 -> "Excellent!"
                                submission.score >= 60 -> "Good job!"
                                else -> "Keep practicing!"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Details
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
                            text = "Quiz Details",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        DetailItem(
                            label = "Student",
                            value = submission.studentName
                        )
                        DetailItem(
                            label = "Submitted",
                            value = dateFormat.format(Date(submission.submittedAt))
                        )
                        DetailItem(
                            label = "Questions Answered",
                            value = "${submission.answers.size}"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                // Close Button
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
private fun DetailItem(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatTimeRemaining(milliseconds: Long): String {
    val minutes = (milliseconds / (1000 * 60)).toInt()
    val seconds = ((milliseconds % (1000 * 60)) / 1000).toInt()
    return "${minutes}m ${seconds}s"
}