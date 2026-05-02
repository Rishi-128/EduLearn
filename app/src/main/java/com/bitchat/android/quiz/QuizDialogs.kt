package com.bitchat.android.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateQuizDialog(
    onDismiss: () -> Unit,
    onQuizCreated: (String, String, List<QuizQuestion>, Int, String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var timeLimit by remember { mutableStateOf("15") }
    var targetClass by remember { mutableStateOf("Grade 8A") }
    var questions by remember { mutableStateOf(mutableListOf<QuizQuestion>()) }
    var showQuestionDialog by remember { mutableStateOf(false) }
    var editingQuestion by remember { mutableStateOf<QuizQuestion?>(null) }
    var editingIndex by remember { mutableStateOf(-1) }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
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
                        text = "Create New Quiz",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Form Content
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Basic Information
                        Card(
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
                                    text = "Quiz Information",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                OutlinedTextField(
                                    value = title,
                                    onValueChange = { title = it },
                                    label = { Text("Quiz Title") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                OutlinedTextField(
                                    value = description,
                                    onValueChange = { description = it },
                                    label = { Text("Description") },
                                    modifier = Modifier.fillMaxWidth(),
                                    maxLines = 3
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedTextField(
                                        value = timeLimit,
                                        onValueChange = { timeLimit = it },
                                        label = { Text("Time Limit (min)") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true
                                    )
                                    
                                    OutlinedTextField(
                                        value = targetClass,
                                        onValueChange = { targetClass = it },
                                        label = { Text("Target Class") },
                                        modifier = Modifier.weight(1f),
                                        singleLine = true
                                    )
                                }
                            }
                        }
                    }
                    
                    item {
                        // Questions Section
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
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
                                        text = "Questions (${questions.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    
                                    FilledTonalButton(
                                        onClick = { 
                                            editingQuestion = null
                                            editingIndex = -1
                                            showQuestionDialog = true 
                                        }
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Add Question")
                                    }
                                }
                                
                                if (questions.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    
                                    questions.forEachIndexed { index, question ->
                                        QuestionPreviewCard(
                                            question = question,
                                            index = index,
                                            onEdit = {
                                                editingQuestion = question
                                                editingIndex = index
                                                showQuestionDialog = true
                                            },
                                            onDelete = {
                                                questions = questions.toMutableList().apply { 
                                                    removeAt(index) 
                                                }
                                            }
                                        )
                                        
                                        if (index < questions.size - 1) {
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            if (title.isNotBlank() && description.isNotBlank() && 
                                questions.isNotEmpty() && timeLimit.isNotBlank()) {
                                onQuizCreated(
                                    title, 
                                    description, 
                                    questions.toList(), 
                                    timeLimit.toIntOrNull() ?: 15, 
                                    targetClass
                                )
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = title.isNotBlank() && description.isNotBlank() && 
                                questions.isNotEmpty() && timeLimit.isNotBlank()
                    ) {
                        Text("Create Quiz")
                    }
                }
            }
        }
    }
    
    // Question Creation/Edit Dialog
    if (showQuestionDialog) {
        QuestionDialog(
            question = editingQuestion,
            onDismiss = { showQuestionDialog = false },
            onSave = { question ->
                if (editingIndex >= 0) {
                    // Edit existing question
                    questions = questions.toMutableList().apply { 
                        set(editingIndex, question) 
                    }
                } else {
                    // Add new question
                    questions = questions.toMutableList().apply { 
                        add(question) 
                    }
                }
                showQuestionDialog = false
            }
        )
    }
}

@Composable
private fun QuestionPreviewCard(
    question: QuizQuestion,
    index: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Q${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Row {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Edit, 
                            contentDescription = "Edit",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete, 
                            contentDescription = "Delete",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            
            Text(
                text = question.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            question.options.forEachIndexed { optIndex, option ->
                val isCorrect = option == question.correctAnswer
                Text(
                    text = "${('A' + optIndex)} $option",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 8.dp, top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionDialog(
    question: QuizQuestion?,
    onDismiss: () -> Unit,
    onSave: (QuizQuestion) -> Unit
) {
    var questionText by remember { mutableStateOf(question?.text ?: "") }
    var options by remember { mutableStateOf(question?.options?.toMutableList() ?: mutableListOf("", "", "", "")) }
    var correctAnswerIndex by remember { 
        mutableStateOf(question?.options?.indexOf(question.correctAnswer) ?: 0) 
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
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
                        text = if (question == null) "Add Question" else "Edit Question",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = questionText,
                            onValueChange = { questionText = it },
                            label = { Text("Question Text") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                    
                    item {
                        Text(
                            text = "Answer Options",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    itemsIndexed(options) { index, option ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = correctAnswerIndex == index,
                                onClick = { correctAnswerIndex = index }
                            )
                            
                            OutlinedTextField(
                                value = option,
                                onValueChange = { newValue ->
                                    options = options.toMutableList().apply { 
                                        set(index, newValue) 
                                    }
                                },
                                label = { Text("Option ${('A' + index)}") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                        }
                    }
                    
                    item {
                        Text(
                            text = "Select the correct answer by clicking the radio button",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }
                    
                    FilledTonalButton(
                        onClick = {
                            if (questionText.isNotBlank() && 
                                options.all { it.isNotBlank() } &&
                                correctAnswerIndex in 0..3) {
                                val newQuestion = QuizQuestion(
                                    id = question?.id ?: UUID.randomUUID().toString(),
                                    text = questionText,
                                    options = options.toList(),
                                    correctAnswer = options[correctAnswerIndex],
                                    type = QuestionType.MultipleChoice
                                )
                                onSave(newQuestion)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = questionText.isNotBlank() && 
                                options.all { it.isNotBlank() } &&
                                correctAnswerIndex in 0..3
                    ) {
                        Text("Save Question")
                    }
                }
            }
        }
    }
}

@Composable
fun QuizDetailsDialog(
    quiz: Quiz,
    onDismiss: () -> Unit,
    onDistribute: (Quiz) -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.8f),
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
                        text = "Quiz Details",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Quiz Information
                        Card(
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
                                    text = quiz.title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                Text(
                                    text = quiz.description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    InfoItem(
                                        label = "Class",
                                        value = quiz.targetClass
                                    )
                                    InfoItem(
                                        label = "Time Limit",
                                        value = "${quiz.timeLimit} min"
                                    )
                                    InfoItem(
                                        label = "Questions",
                                        value = quiz.questions.size.toString()
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                InfoItem(
                                    label = "Created",
                                    value = dateFormat.format(Date(quiz.createdAt))
                                )
                            }
                        }
                    }
                    
                    item {
                        // Questions Preview
                        Card(
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
                                    text = "Questions Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                quiz.questions.forEachIndexed { index, question ->
                                    QuestionPreview(
                                        question = question,
                                        index = index
                                    )
                                    
                                    if (index < quiz.questions.size - 1) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Close")
                    }
                    
                    if (quiz.status == QuizStatus.Draft || quiz.status == QuizStatus.Scheduled) {
                        FilledTonalButton(
                            onClick = { onDistribute(quiz) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Distribute Now")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun QuestionPreview(
    question: QuizQuestion,
    index: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surface,
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp)
    ) {
        Text(
            text = "Q${index + 1}. ${question.text}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        question.options.forEachIndexed { optIndex, option ->
            val isCorrect = option == question.correctAnswer
            Text(
                text = "${('A' + optIndex)} $option",
                style = MaterialTheme.typography.bodySmall,
                color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isCorrect) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier.padding(start = 8.dp, top = 2.dp)
            )
        }
    }
}

@Composable
fun QuizResultsDialog(
    quiz: Quiz,
    submissions: List<QuizSubmission>,
    statistics: QuizStatistics,
    onDismiss: () -> Unit
) {
    val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
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
                    Column {
                        Text(
                            text = quiz.title,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Quiz Results",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        // Statistics Summary
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
                                Text(
                                    text = "Statistics",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Spacer(modifier = Modifier.height(12.dp))
                                
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    StatItem(
                                        label = "Submissions",
                                        value = statistics.totalSubmissions.toString(),
                                        icon = Icons.Default.Person
                                    )
                                    StatItem(
                                        label = "Avg Score",
                                        value = "${statistics.averageScore.toInt()}%",
                                        icon = Icons.Default.TrendingUp
                                    )
                                    StatItem(
                                        label = "Completion",
                                        value = "${statistics.completionRate.toInt()}%",
                                        icon = Icons.Default.CheckCircle
                                    )
                                }
                                
                                if (statistics.totalSubmissions > 0) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Score Range: ${statistics.lowestScore}% - ${statistics.highestScore}%",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                    
                    if (submissions.isNotEmpty()) {
                        item {
                            Text(
                                text = "Individual Results",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        
                        items(submissions.sortedByDescending { it.score }) { submission ->
                            SubmissionCard(
                                submission = submission,
                                quiz = quiz,
                                dateFormat = dateFormat
                            )
                        }
                    } else {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.HourglassEmpty,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "No submissions yet",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Results will appear here as students complete the quiz",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
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
private fun SubmissionCard(
    submission: QuizSubmission,
    quiz: Quiz,
    dateFormat: SimpleDateFormat
) {
    val totalQuestions = quiz.questions.size
    val correctAnswers = (submission.score * totalQuestions / 100.0).toInt()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                    text = submission.studentName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Submitted: ${dateFormat.format(Date(submission.submittedAt))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Marks: $correctAnswers/$totalQuestions",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = when {
                    submission.score >= 80 -> Color.Green.copy(alpha = 0.1f)
                    submission.score >= 60 -> Color(0xFFFF9800).copy(alpha = 0.1f)
                    else -> Color.Red.copy(alpha = 0.1f)
                }
            ) {
                Text(
                    text = "${submission.score}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        submission.score >= 80 -> Color.Green
                        submission.score >= 60 -> Color(0xFFFF9800)
                        else -> Color.Red
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}