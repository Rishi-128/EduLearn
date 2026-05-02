package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.Classroom
import com.bitchat.android.data.SharedContentManager
import com.bitchat.android.data.SharedQuizQuestion

/**
 * Quiz Creation Dialog for Teachers
 * Creates quizzes and shares them with students via SharedContentManager
 * Now includes class selection for targeted distribution
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizCreationDialog(
    sharedContentManager: SharedContentManager,
    classManager: ClassManager? = null,
    classes: List<Classroom> = emptyList(),
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val effectiveClassManager = classManager ?: remember { ClassManager.getInstance(context) }
    
    var quizTitle by remember { mutableStateOf("") }
    var quizDescription by remember { mutableStateOf("") }
    var selectedSubject by remember { mutableStateOf("Math") }
    var selectedDifficulty by remember { mutableStateOf("Medium") }
    var timeLimit by remember { mutableStateOf("30") }
    var questions by remember { mutableStateOf(listOf<SharedQuizQuestion>()) }
    var showAddQuestion by remember { mutableStateOf(false) }
    
    // Class selection for targeted distribution
    var selectedClass by remember { mutableStateOf<Classroom?>(null) }
    var expandedClassDropdown by remember { mutableStateOf(false) }
    
    val subjects = listOf("Math", "Science", "English", "Hindi", "History", "Geography")
    val difficulties = listOf("Easy", "Medium", "Hard")
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
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
                        text = "Create Quiz",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Quiz Details Form
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        // Quiz Title
                        OutlinedTextField(
                            value = quizTitle,
                            onValueChange = { quizTitle = it },
                            label = { Text("Quiz Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    
                    item {
                        // Quiz Description
                        OutlinedTextField(
                            value = quizDescription,
                            onValueChange = { quizDescription = it },
                            label = { Text("Description (Optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 2
                        )
                    }
                    
                    // CLASS SELECTION - Share quiz to specific class
                    if (classes.isNotEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "🎯 Share to Class",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    ExposedDropdownMenuBox(
                                        expanded = expandedClassDropdown,
                                        onExpandedChange = { expandedClassDropdown = !expandedClassDropdown }
                                    ) {
                                        OutlinedTextField(
                                            value = selectedClass?.name ?: "All Students (No class filter)",
                                            onValueChange = {},
                                            readOnly = true,
                                            label = { Text("Target Class") },
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedClassDropdown) },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .menuAnchor(),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                                            )
                                        )
                                        ExposedDropdownMenu(
                                            expanded = expandedClassDropdown,
                                            onDismissRequest = { expandedClassDropdown = false }
                                        ) {
                                            // Option for all students
                                            DropdownMenuItem(
                                                text = { 
                                                    Column {
                                                        Text("All Students", fontWeight = FontWeight.Medium)
                                                        Text(
                                                            "No class filter - visible to everyone",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    selectedClass = null
                                                    expandedClassDropdown = false
                                                }
                                            )
                                            
                                            Divider()
                                            
                                            // Class options
                                            classes.forEach { classroom ->
                                                DropdownMenuItem(
                                                    text = { 
                                                        Column {
                                                            Text(classroom.name, fontWeight = FontWeight.Medium)
                                                            Text(
                                                                "${classroom.code} • ${classroom.subject}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        selectedClass = classroom
                                                        expandedClassDropdown = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                    
                                    if (selectedClass != null) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "🔐 Quiz will only be visible to students enrolled in ${selectedClass!!.name}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        // Subject and Difficulty Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Subject Dropdown
                            var expandedSubject by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedSubject,
                                onExpandedChange = { expandedSubject = !expandedSubject },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedSubject,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Subject") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSubject) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedSubject,
                                    onDismissRequest = { expandedSubject = false }
                                ) {
                                    subjects.forEach { subject ->
                                        DropdownMenuItem(
                                            text = { Text(subject) },
                                            onClick = {
                                                selectedSubject = subject
                                                expandedSubject = false
                                            }
                                        )
                                    }
                                }
                            }
                            
                            // Difficulty Dropdown
                            var expandedDifficulty by remember { mutableStateOf(false) }
                            ExposedDropdownMenuBox(
                                expanded = expandedDifficulty,
                                onExpandedChange = { expandedDifficulty = !expandedDifficulty },
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selectedDifficulty,
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("Difficulty") },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedDifficulty) },
                                    modifier = Modifier.menuAnchor()
                                )
                                ExposedDropdownMenu(
                                    expanded = expandedDifficulty,
                                    onDismissRequest = { expandedDifficulty = false }
                                ) {
                                    difficulties.forEach { difficulty ->
                                        DropdownMenuItem(
                                            text = { Text(difficulty) },
                                            onClick = {
                                                selectedDifficulty = difficulty
                                                expandedDifficulty = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    item {
                        // Time Limit
                        OutlinedTextField(
                            value = timeLimit,
                            onValueChange = { timeLimit = it.filter { char -> char.isDigit() } },
                            label = { Text("Time Limit (minutes)") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                    
                    item {
                        // Questions Section Header
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
                            
                            Button(
                                onClick = { showAddQuestion = true },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Question")
                            }
                        }
                    }
                    
                    // Questions List
                    items(questions.withIndex().toList()) { (index, question) ->
                        QuestionCard(
                            questionNumber = index + 1,
                            question = question,
                            onDeleteQuestion = {
                                questions = questions.filterIndexed { i, _ -> i != index }
                            }
                        )
                    }
                    
                    item {
                        if (questions.isEmpty()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "No questions added yet",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = "Click 'Add Question' to get started",
                                        style = MaterialTheme.typography.bodySmall
                                    )
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
                    
                    Button(
                        onClick = {
                            if (quizTitle.isNotBlank() && questions.isNotEmpty()) {
                                val sharedQuiz = com.bitchat.android.data.SharedQuiz(
                                    id = "quiz_${System.currentTimeMillis()}",
                                    title = quizTitle,
                                    questions = questions.map { q ->
                                        SharedQuizQuestion(
                                            id = q.id,
                                            question = q.question,
                                            options = q.options,
                                            correctAnswer = q.correctAnswer,
                                            explanation = q.explanation
                                        )
                                    },
                                    description = quizDescription,
                                    subject = selectedSubject,
                                    difficulty = selectedDifficulty,
                                    timeLimit = timeLimit.toIntOrNull() ?: 30,
                                    createdDate = System.currentTimeMillis(),
                                    teacherId = "teacher_001",
                                    // Class-based filtering
                                    classId = selectedClass?.id,
                                    classCode = selectedClass?.code
                                )
                                sharedContentManager.addSharedQuiz(sharedQuiz)
                                onDismiss()
                            }
                        },
                        enabled = quizTitle.isNotBlank() && questions.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (selectedClass != null) "Create & Share to ${selectedClass!!.name}" else "Create & Share Quiz")
                    }
                }
            }
        }
        
        // Add Question Dialog
        if (showAddQuestion) {
            AddQuestionDialog(
                onDismiss = { showAddQuestion = false },
                onAddQuestion = { question ->
                    questions = questions + question
                    showAddQuestion = false
                }
            )
        }
    }
}

@Composable
private fun QuestionCard(
    questionNumber: Int,
    question: SharedQuizQuestion,
    onDeleteQuestion: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
                    text = "Question $questionNumber",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = onDeleteQuestion,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete Question",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            
            Text(
                text = question.question,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(vertical = 4.dp)
            )
            
            question.options.forEachIndexed { index, option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 2.dp)
                ) {
                    RadioButton(
                        selected = index == question.correctAnswer,
                        onClick = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (index == question.correctAnswer) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun AddQuestionDialog(
    onDismiss: () -> Unit,
    onAddQuestion: (SharedQuizQuestion) -> Unit
) {
    var questionText by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "", "", "")) }
    var correctAnswer by remember { mutableStateOf(0) }
    var explanation by remember { mutableStateOf("") }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Add Question",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Question Text
                OutlinedTextField(
                    value = questionText,
                    onValueChange = { questionText = it },
                    label = { Text("Question") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Options
                Text(
                    text = "Answer Options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                options.forEachIndexed { index, option ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = correctAnswer == index,
                            onClick = { correctAnswer = index }
                        )
                        OutlinedTextField(
                            value = option,
                            onValueChange = { newValue ->
                                options = options.mapIndexed { i, opt ->
                                    if (i == index) newValue else opt
                                }
                            },
                            label = { Text("Option ${('A' + index)}") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }
                
                // Explanation (Optional)
                OutlinedTextField(
                    value = explanation,
                    onValueChange = { explanation = it },
                    label = { Text("Explanation (Optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Buttons
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
                    
                    Button(
                        onClick = {
                            if (questionText.isNotBlank() && options.all { it.isNotBlank() }) {
                                val question = SharedQuizQuestion(
                                    id = "q_${System.currentTimeMillis()}",
                                    question = questionText,
                                    options = options,
                                    correctAnswer = correctAnswer,
                                    explanation = explanation
                                )
                                onAddQuestion(question)
                            }
                        },
                        enabled = questionText.isNotBlank() && options.all { it.isNotBlank() },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Add Question")
                    }
                }
            }
        }
    }
}