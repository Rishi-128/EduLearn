package com.bitchat.android.ui.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.EnrollmentStatus
import com.bitchat.android.data.UserManager
import com.bitchat.android.data.UserRole

/**
 * Complete Authentication Screen with Class-Based Login
 * 
 * Teachers: Login with name + email, then create/manage classes
 * Students: Login with name + email + class code + class password
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticationScreen(
    userManager: UserManager,
    chatViewModel: com.bitchat.android.ui.ChatViewModel? = null,
    onAuthSuccess: () -> Unit
) {
    val context = LocalContext.current
    val classManager = remember { ClassManager.getInstance(context) }
    
    var isLogin by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf(UserRole.STUDENT) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    
    // Class-based login fields (for students) - COMMENTED OUT DUE TO MESH ISSUES
    // var classCode by remember { mutableStateOf("") }
    // var classPassword by remember { mutableStateOf("") }
    // var showPassword by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // App Logo/Title
        Icon(
            Icons.Default.School,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "EduLearn",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = if (isLogin) "Welcome Back!" else "Create Your Account",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Auth Form Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Name Field
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        errorMessage = ""
                    },
                    label = { Text("Full Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !isLoading
                )
                
                // Email Field
                OutlinedTextField(
                    value = email,
                    onValueChange = { 
                        email = it
                        errorMessage = ""
                    },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    enabled = !isLoading
                )
                
                // Role Selector (only for registration)
                if (!isLogin) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "I am a:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Teacher Option
                                FilterChip(
                                    onClick = { selectedRole = UserRole.TEACHER },
                                    label = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.School,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text("Teacher")
                                        }
                                    },
                                    selected = selectedRole == UserRole.TEACHER,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                // Student Option
                                FilterChip(
                                    onClick = { selectedRole = UserRole.STUDENT },
                                    label = { 
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.MenuBook,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text("Student")
                                        }
                                    },
                                    selected = selectedRole == UserRole.STUDENT,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                
                /* COMMENTED OUT - Class code functionality causing mesh issues
                // Class Login Fields (for Students during login)
                if (isLogin || selectedRole == UserRole.STUDENT) {
                    Divider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Text(
                        text = "🔐 Class Authentication",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    Text(
                        text = if (!isLogin) "Join a class (optional - can join later)" else "Enter your class details",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    // Discovered Classes from Mesh (show available classes from nearby teachers)
                    // Use remember to safely handle potential exceptions during collection
                    val discoveredClasses = remember {
                        try {
                            classManager.discoveredClasses
                        } catch (e: Exception) {
                            android.util.Log.w("AuthScreen", "Failed to access discovered classes: ${e.message}")
                            kotlinx.coroutines.flow.MutableStateFlow(emptyList())
                        }
                    }.collectAsState(initial = emptyList()).value
                    
                    if (discoveredClasses.isNotEmpty()) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Wifi,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = "Available Classes (via Mesh)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                discoveredClasses.forEach { discovered ->
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .clickable {
                                                // Auto-fill class code when clicked
                                                classCode = discovered.code
                                            },
                                        shape = MaterialTheme.shapes.small,
                                        color = if (classCode == discovered.code) 
                                            MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                        tonalElevation = 2.dp
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = discovered.name,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "Code: ${discovered.code} • Teacher: ${discovered.teacherName}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                discovered.subject?.let { subj ->
                                                    Text(
                                                        text = "Subject: $subj",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                            if (classCode == discovered.code) {
                                                Icon(
                                                    Icons.Default.CheckCircle,
                                                    contentDescription = "Selected",
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                Text(
                                    text = "Tap a class to auto-fill code. You still need the password from your teacher.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    
                    // Class Code Field
                    OutlinedTextField(
                        value = classCode,
                        onValueChange = { 
                            classCode = it.uppercase()
                            errorMessage = ""
                        },
                        label = { Text("Class Code") },
                        placeholder = { Text("e.g., 10AMATH") },
                        leadingIcon = { Icon(Icons.Default.Class, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                    
                    // Class Password Field
                    OutlinedTextField(
                        value = classPassword,
                        onValueChange = { 
                            classPassword = it
                            errorMessage = ""
                        },
                        label = { Text("Class Password") },
                        placeholder = { Text("Get from your teacher") },
                        leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = if (showPassword) "Hide password" else "Show password"
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        enabled = !isLoading
                    )
                }
                */ // END COMMENT - Class code section
                
                // Error Message
                if (errorMessage.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Error,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Auth Button
                Button(
                    onClick = {
                        // Simplified authentication without class code
                        handleAuthentication(
                            isLogin = isLogin,
                            name = name,
                            email = email,
                            role = selectedRole,
                            userManager = userManager,
                            chatViewModel = chatViewModel,
                            onLoading = { isLoading = it },
                            onError = { errorMessage = it },
                            onSuccess = onAuthSuccess
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading && name.isNotBlank() && email.isNotBlank()
                ) {
                    if (isLoading) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text("Processing...")
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (isLogin) Icons.Default.Login else Icons.Default.PersonAdd,
                                contentDescription = null
                            )
                            Text(if (isLogin) "Sign In" else "Create Account")
                        }
                    }
                }
                
                // Switch between login/register
                TextButton(
                    onClick = { 
                        isLogin = !isLogin
                        errorMessage = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                ) {
                    Text(
                        text = if (isLogin) {
                            "Don't have an account? Create one"
                        } else {
                            "Already have an account? Sign in"
                        },
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Features Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "🚀 Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                val features = listOf(
                    "🔐 Secure class-based authentication",
                    "📚 Share documents via Bluetooth",
                    "📝 Create and take interactive quizzes",
                    "👥 Teacher-controlled class access",
                    "📱 Fully offline & encrypted"
                )
                
                features.forEach { feature ->
                    Text(
                        text = feature,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }
        }
    }
}

// COMMENTED OUT - Class-based authentication causing mesh issues
/*
private fun handleAuthenticationWithClass(
    isLogin: Boolean,
    name: String,
    email: String,
    role: UserRole,
    classCode: String,
    classPassword: String,
    userManager: UserManager,
    classManager: ClassManager,
    chatViewModel: com.bitchat.android.ui.ChatViewModel?,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onLoading(true)
    
    // Step 1: Handle user authentication first
    val authResult = if (isLogin) {
        userManager.loginUser(email, name)
    } else {
        userManager.registerUser(name, email, role)
    }
    
    authResult.fold(
        onSuccess = { user ->
            // Step 2: Handle class enrollment for students
            if ((isLogin || role == UserRole.STUDENT) && classCode.isNotBlank() && classPassword.isNotBlank()) {
                // First check if this is a discovered class from the mesh
                val discoveredClass = try {
                    classManager.getDiscoveredClass(classCode)
                } catch (e: Exception) {
                    android.util.Log.w("AuthScreen", "Failed to get discovered class: ${e.message}")
                    null
                }
                
                val enrollmentResult = if (discoveredClass != null) {
                    // Join discovered class (validates password locally using mesh-synced hash)
                    classManager.joinDiscoveredClass(
                        code = classCode,
                        password = classPassword,
                        studentId = user.id,
                        studentName = user.name,
                        studentEmail = user.email
                    )
                } else {
                    // Try to join a locally stored class (for cases where teacher and student are on same device)
                    classManager.joinClass(
                        classCode = classCode,
                        password = classPassword,
                        studentId = user.id,
                        studentName = user.name,
                        studentEmail = user.email
                    )
                }
                
                enrollmentResult.fold(
                    onSuccess = { enrollment ->
                        // Set mesh nickname (safely)
                        try {
                            chatViewModel?.setNickname(user.name)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthScreen", "Failed to set nickname: ${e.message}")
                        }
                        
                        // Set active class
                        try {
                            classManager.setActiveClass(enrollment.classId)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthScreen", "Failed to set active class: ${e.message}")
                        }
                        
                        onLoading(false)
                        
                        if (enrollment.status == EnrollmentStatus.PENDING) {
                            // Still proceed but show that approval is pending
                            onSuccess()
                        } else {
                            onSuccess()
                        }
                    },
                    onFailure = { error ->
                        onLoading(false)
                        // Check if it's a "already enrolled" type error - if so, still proceed
                        val errorMsg = error.message ?: "Failed to join class"
                        if (errorMsg.contains("already enrolled", ignoreCase = true) ||
                            errorMsg.contains("pending", ignoreCase = true)) {
                            // Already in class, proceed anyway
                            chatViewModel?.setNickname(user.name)
                            onSuccess()
                        } else {
                            onError(errorMsg)
                        }
                    }
                )
            } else if (role == UserRole.TEACHER || classCode.isBlank()) {
                // Teachers don't need class code, or student didn't provide one
                try {
                    chatViewModel?.setNickname(user.name)
                } catch (e: Exception) {
                    android.util.Log.w("AuthScreen", "Failed to set nickname: ${e.message}")
                }
                onLoading(false)
                onSuccess()
            } else {
                // Student login requires class credentials
                if (isLogin && role == UserRole.STUDENT && classCode.isBlank()) {
                    // Check if student has any existing approved enrollments
                    val existingEnrollments = try {
                        classManager.getStudentClasses(user.id)
                    } catch (e: Exception) {
                        android.util.Log.w("AuthScreen", "Failed to get student classes: ${e.message}")
                        emptyList()
                    }
                    
                    if (existingEnrollments.isNotEmpty()) {
                        // Student has existing classes, allow login
                        try {
                            chatViewModel?.setNickname(user.name)
                            classManager.setActiveClass(existingEnrollments.first().id)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthScreen", "Failed to restore class: ${e.message}")
                        }
                        onLoading(false)
                        onSuccess()
                    } else {
                        // Allow login anyway - class code is optional for new students
                        try {
                            chatViewModel?.setNickname(user.name)
                        } catch (e: Exception) {
                            android.util.Log.w("AuthScreen", "Failed to set nickname: ${e.message}")
                        }
                        onLoading(false)
                        onSuccess()
                    }
                } else {
                    onLoading(false)
                    onSuccess()
                }
            }
        },
        onFailure = { error ->
            onLoading(false)
            onError(error.message ?: "Authentication failed")
        }
    )
}
*/

// Simplified authentication without class code complexity
private fun handleAuthentication(
    isLogin: Boolean,
    name: String,
    email: String,
    role: UserRole,
    userManager: UserManager,
    chatViewModel: com.bitchat.android.ui.ChatViewModel?,
    onLoading: (Boolean) -> Unit,
    onError: (String) -> Unit,
    onSuccess: () -> Unit
) {
    onLoading(true)
    
    val result = if (isLogin) {
        userManager.loginUser(email, name)
    } else {
        userManager.registerUser(name, email, role)
    }
    
    onLoading(false)
    
    result.fold(
        onSuccess = { user ->
            chatViewModel?.setNickname(user.name)
            onSuccess()
        },
        onFailure = { onError(it.message ?: "Authentication failed") }
    )
}