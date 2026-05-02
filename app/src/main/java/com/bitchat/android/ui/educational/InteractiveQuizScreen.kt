package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.data.OfflineDataManager

/**
 * Interactive Quiz System - Completely Offline
 * Takes quizzes, shows immediate results, updates progress in real-time
 */

data class QuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: Int,
    val explanation: String = ""
)

data class Quiz(
    val id: String,
    val title: String,
    val subject: String,
    val questions: List<QuizQuestion>
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InteractiveQuizScreen(
    quiz: Quiz,
    dataManager: OfflineDataManager,
    onQuizComplete: (score: Int, total: Int) -> Unit,
    onBackClick: () -> Unit
) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedAnswers by remember { mutableStateOf(mapOf<String, Int>()) }
    var showResults by remember { mutableStateOf(false) }
    var quizScore by remember { mutableStateOf(0) }
    
    // Safety check for empty quiz
    if (quiz.questions.isEmpty()) {
        EmptyQuizErrorScreen(onBackClick = onBackClick)
        return
    }
    
    val currentQuestion = quiz.questions[currentQuestionIndex]
    val isLastQuestion = currentQuestionIndex == quiz.questions.size - 1
    
    // Calculate score when quiz is completed
    LaunchedEffect(showResults) {
        if (showResults) {
            quizScore = selectedAnswers.entries.sumOf { (questionId, selectedAnswer) ->
                val question = quiz.questions.find { it.id == questionId }
                if (question?.correctAnswer == selectedAnswer) 1 else 0
            }
            
            // IMMEDIATELY update progress in offline storage
            dataManager.completeQuiz(
                studentId = "student_001", // In real app, get from current user
                quizId = quiz.id,
                score = quizScore,
                totalQuestions = quiz.questions.size
            )
            
            onQuizComplete(quizScore, quiz.questions.size)
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        TopAppBar(
            title = { 
                Text("${quiz.title} - ${quiz.subject}") 
            },
            navigationIcon = {
                if (!showResults) {
                    TextButton(onClick = onBackClick) {
                        Text("← Back")
                    }
                }
            }
        )
        
        if (!showResults) {
            // Quiz Progress
            LinearProgressIndicator(
                progress = (currentQuestionIndex + 1).toFloat() / quiz.questions.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            )
            
            Text(
                text = "Question ${currentQuestionIndex + 1} of ${quiz.questions.size}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            // Current Question
            QuestionCard(
                question = currentQuestion,
                selectedAnswer = selectedAnswers[currentQuestion.id],
                onAnswerSelected = { answer ->
                    selectedAnswers = selectedAnswers + (currentQuestion.id to answer)
                }
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Navigation Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (currentQuestionIndex > 0) {
                    OutlinedButton(
                        onClick = { currentQuestionIndex-- }
                    ) {
                        Text("Previous")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                
                Button(
                    onClick = {
                        if (isLastQuestion) {
                            showResults = true
                        } else {
                            currentQuestionIndex++
                        }
                    },
                    enabled = selectedAnswers[currentQuestion.id] != null
                ) {
                    Text(if (isLastQuestion) "Finish Quiz" else "Next")
                }
            }
        } else {
            // Quiz Results - IMMEDIATE FEEDBACK
            QuizResultsScreen(
                quiz = quiz,
                selectedAnswers = selectedAnswers,
                score = quizScore,
                onRetakeQuiz = {
                    currentQuestionIndex = 0
                    selectedAnswers = mapOf()
                    showResults = false
                    quizScore = 0
                },
                onBackToResources = onBackClick
            )
        }
    }
}

@Composable
fun QuestionCard(
    question: QuizQuestion,
    selectedAnswer: Int?,
    onAnswerSelected: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = question.question,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            
            question.options.forEachIndexed { index, option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = selectedAnswer == index,
                            onClick = { onAnswerSelected(index) }
                        )
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = selectedAnswer == index,
                        onClick = { onAnswerSelected(index) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}

@Composable
fun QuizResultsScreen(
    quiz: Quiz,
    selectedAnswers: Map<String, Int>,
    score: Int,
    onRetakeQuiz: () -> Unit,
    onBackToResources: () -> Unit
) {
    val percentage = (score * 100) / quiz.questions.size
    val grade = when {
        percentage >= 90 -> "Excellent! 🌟"
        percentage >= 80 -> "Great Job! 👍"
        percentage >= 70 -> "Good Work! ✓"
        percentage >= 60 -> "Keep Practicing! 📚"
        else -> "Need More Study 💪"
    }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        
        // Score Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (percentage >= 70) 
                    MaterialTheme.colorScheme.primaryContainer 
                else 
                    MaterialTheme.colorScheme.errorContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Quiz Complete!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "$score / ${quiz.questions.size}",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "$percentage%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = grade,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Progress Update Confirmation
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "✅ Progress Updated!",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Your quiz score has been saved offline",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Action Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedButton(
                onClick = onRetakeQuiz,
                modifier = Modifier.weight(1f)
            ) {
                Text("Retake Quiz")
            }
            
            Button(
                onClick = onBackToResources,
                modifier = Modifier.weight(1f)
            ) {
                Text("Back to Resources")
            }
        }
    }
}

// Sample quizzes for testing - ALL STORED OFFLINE
object OfflineQuizData {
    // Quiz content will be loaded from teacher creations
    // TODO: Implement quiz loading from OfflineDataManager or Bluetooth mesh
    
    val mathQuiz = Quiz(id = "", title = "", subject = "", questions = emptyList())
    val scienceQuiz = Quiz(id = "", title = "", subject = "", questions = emptyList())
}

@Composable
fun EmptyQuizErrorScreen(onBackClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "⚠️ Quiz Error",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "This quiz has no questions available.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Button(onClick = onBackClick) {
            Text("Back to Resources")
        }
    }
}