package com.bitchat.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * User Management System
 * Handles user registration, login, and profile management
 */
data class User(
    val id: String,
    val name: String,
    val email: String,
    val role: UserRole,
    val avatar: String = "",
    val isLoggedIn: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val currentClassId: String? = null,     // Currently active class (for context)
    val currentClassCode: String? = null    // Currently active class code
)

enum class UserRole {
    TEACHER, STUDENT
}

// User activity events for real-time updates
data class UserActivityEvent(
    val user: User,
    val action: UserAction,
    val timestamp: Long = System.currentTimeMillis()
)

enum class UserAction {
    LOGIN, LOGOUT, PROFILE_UPDATE
}

class UserManager private constructor(context: Context) {
    
    companion object {
        @Volatile
        private var instance: UserManager? = null
        
        fun getInstance(context: Context): UserManager {
            return instance ?: synchronized(this) {
                instance ?: UserManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("user_prefs", Context.MODE_PRIVATE)
    
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    
    /**
     * Get current user directly (convenience method)
     */
    fun getCurrentUser(): User? = _currentUser.value
    
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()
    
    private val _allUsers = MutableStateFlow<List<User>>(emptyList())
    val allUsers: StateFlow<List<User>> = _allUsers.asStateFlow()
    
    // Real-time user activity tracking for teacher dashboard
    private val _onlineUsers = MutableStateFlow<List<User>>(emptyList())
    val onlineUsers: StateFlow<List<User>> = _onlineUsers.asStateFlow()
    
    private val _userActivityEvents = MutableStateFlow<UserActivityEvent?>(null)
    val userActivityEvents: StateFlow<UserActivityEvent?> = _userActivityEvents.asStateFlow()

    init {
        loadCurrentUser()
        loadAllUsers()
        startUserActivityTracking()
    }

    /**
     * Register a new user
     */
    fun registerUser(name: String, email: String, role: UserRole): Result<User> {
        if (name.isBlank()) {
            return Result.failure(Exception("Name cannot be empty"))
        }
        
        if (email.isBlank() || !isValidEmail(email)) {
            return Result.failure(Exception("Please enter a valid email"))
        }
        
        // Check if email already exists
        val existingUsers = getAllStoredUsers()
        if (existingUsers.any { it.email.lowercase() == email.lowercase() }) {
            return Result.failure(Exception("Email already registered"))
        }
        
        val user = User(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            email = email.lowercase().trim(),
            role = role
        )
        
        // Save user to storage
        saveUserToStorage(user)
        
        // Set as current user
        loginUser(user)
        
        return Result.success(user)
    }

    /**
     * Login with existing credentials
     * Both email AND name must match for successful login
     */
    fun loginUser(email: String, name: String): Result<User> {
        val existingUsers = getAllStoredUsers()
        
        // Find user by email
        val user = existingUsers.find { it.email.lowercase() == email.lowercase() }
        
        if (user != null) {
            // Verify that the name also matches (case-insensitive comparison)
            if (user.name.lowercase().trim() != name.lowercase().trim()) {
                return Result.failure(Exception("Invalid credentials. Name does not match."))
            }
            
            loginUser(user)
            return Result.success(user)
        } else {
            return Result.failure(Exception("User not found. Please register first."))
        }
    }

    /**
     * Login user directly
     */
    private fun loginUser(user: User) {
        val loggedInUser = user.copy(isLoggedIn = true)
        _currentUser.value = loggedInUser
        _isLoggedIn.value = true
        
        // Save to preferences
        with(sharedPrefs.edit()) {
            putString("current_user_id", user.id)
            putString("current_user_name", user.name)
            putString("current_user_email", user.email)
            putString("current_user_role", user.role.name)
            putBoolean("is_logged_in", true)
            apply()
        }
        
        // Update user in storage
        saveUserToStorage(loggedInUser)
        loadAllUsers()
        
        // Broadcast login event for real-time dashboard updates
        _userActivityEvents.value = UserActivityEvent(
            user = loggedInUser,
            action = UserAction.LOGIN
        )
        
        // Update online users
        updateOnlineUsers(loggedInUser, true)
    }

    /**
     * Logout current user
     */
    fun logout() {
        val currentUser = _currentUser.value
        
        _currentUser.value = null
        _isLoggedIn.value = false
        
        with(sharedPrefs.edit()) {
            remove("current_user_id")
            remove("current_user_name")
            remove("current_user_email")
            remove("current_user_role")
            putBoolean("is_logged_in", false)
            apply()
        }
        
        // Broadcast logout event for real-time dashboard updates
        currentUser?.let { user ->
            _userActivityEvents.value = UserActivityEvent(
                user = user,
                action = UserAction.LOGOUT
            )
            
            // Update online users
            updateOnlineUsers(user, false)
        }
    }

    /**
     * Update user profile
     */
    fun updateUserProfile(name: String, email: String): Result<User> {
        val currentUser = _currentUser.value ?: return Result.failure(Exception("No user logged in"))
        
        if (name.isBlank()) {
            return Result.failure(Exception("Name cannot be empty"))
        }
        
        if (email.isBlank() || !isValidEmail(email)) {
            return Result.failure(Exception("Please enter a valid email"))
        }
        
        // Check if email is taken by another user
        val existingUsers = getAllStoredUsers()
        val emailTaken = existingUsers.any { 
            it.email.lowercase() == email.lowercase() && it.id != currentUser.id 
        }
        
        if (emailTaken) {
            return Result.failure(Exception("Email already taken by another user"))
        }
        
        val updatedUser = currentUser.copy(
            name = name.trim(),
            email = email.lowercase().trim()
        )
        
        loginUser(updatedUser)
        return Result.success(updatedUser)
    }

    /**
     * Get all teachers for student view
     */
    fun getAllTeachers(): List<User> {
        return getAllStoredUsers().filter { it.role == UserRole.TEACHER }
    }

    /**
     * Get all students for teacher view
     */
    fun getAllStudents(): List<User> {
        return getAllStoredUsers().filter { it.role == UserRole.STUDENT }
    }

    /**
     * Check if user is teacher
     */
    fun isTeacher(): Boolean {
        return _currentUser.value?.role == UserRole.TEACHER
    }

    /**
     * Check if user is student
     */
    fun isStudent(): Boolean {
        return _currentUser.value?.role == UserRole.STUDENT
    }

    // Private helper methods

    private fun loadCurrentUser() {
        val isLoggedIn = sharedPrefs.getBoolean("is_logged_in", false)
        if (isLoggedIn) {
            val userId = sharedPrefs.getString("current_user_id", "") ?: ""
            val name = sharedPrefs.getString("current_user_name", "") ?: ""
            val email = sharedPrefs.getString("current_user_email", "") ?: ""
            val roleString = sharedPrefs.getString("current_user_role", "") ?: ""
            
            if (userId.isNotEmpty() && name.isNotEmpty() && email.isNotEmpty()) {
                val role = try {
                    UserRole.valueOf(roleString)
                } catch (e: Exception) {
                    UserRole.STUDENT
                }
                
                val user = User(
                    id = userId,
                    name = name,
                    email = email,
                    role = role,
                    isLoggedIn = true
                )
                
                _currentUser.value = user
                _isLoggedIn.value = true
            }
        }
    }

    private fun saveUserToStorage(user: User) {
        val existingUsers = getAllStoredUsers().toMutableList()
        val existingIndex = existingUsers.indexOfFirst { it.id == user.id }
        
        if (existingIndex >= 0) {
            existingUsers[existingIndex] = user
        } else {
            existingUsers.add(user)
        }
        
        // Save to preferences as JSON-like string
        val usersString = existingUsers.joinToString("|") { user ->
            "${user.id},${user.name},${user.email},${user.role.name},${user.createdAt}"
        }
        
        with(sharedPrefs.edit()) {
            putString("all_users", usersString)
            apply()
        }
    }

    private fun getAllStoredUsers(): List<User> {
        val usersString = sharedPrefs.getString("all_users", "") ?: ""
        if (usersString.isEmpty()) return emptyList()
        
        return usersString.split("|").mapNotNull { userString ->
            try {
                val parts = userString.split(",")
                if (parts.size >= 5) {
                    User(
                        id = parts[0],
                        name = parts[1],
                        email = parts[2],
                        role = UserRole.valueOf(parts[3]),
                        createdAt = parts[4].toLong()
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun loadAllUsers() {
        _allUsers.value = getAllStoredUsers()
    }

    private fun isValidEmail(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
    
    /**
     * Start tracking user activity for real-time updates
     */
    private fun startUserActivityTracking() {
        // Load currently online users from preferences
        val onlineUsersString = sharedPrefs.getString("online_users", "") ?: ""
        if (onlineUsersString.isNotEmpty()) {
            val onlineUserIds = onlineUsersString.split(",")
            val allUsers = getAllStoredUsers()
            val onlineUsers = allUsers.filter { it.id in onlineUserIds }
            _onlineUsers.value = onlineUsers
        }
    }
    
    /**
     * Update online users list
     */
    private fun updateOnlineUsers(user: User, isOnline: Boolean) {
        val currentOnlineUsers = _onlineUsers.value.toMutableList()
        
        if (isOnline) {
            if (!currentOnlineUsers.any { it.id == user.id }) {
                currentOnlineUsers.add(user)
            }
        } else {
            currentOnlineUsers.removeAll { it.id == user.id }
        }
        
        _onlineUsers.value = currentOnlineUsers
        
        // Save online users to preferences for persistence
        val onlineUserIds = currentOnlineUsers.map { it.id }.joinToString(",")
        with(sharedPrefs.edit()) {
            putString("online_users", onlineUserIds)
            apply()
        }
    }
    
    /**
     * Get list of currently online students (for teacher dashboard)
     */
    fun getOnlineStudents(): List<User> {
        return _onlineUsers.value.filter { it.role == UserRole.STUDENT }
    }
    
    /**
     * Get list of currently online teachers
     */
    fun getOnlineTeachers(): List<User> {
        return _onlineUsers.value.filter { it.role == UserRole.TEACHER }
    }
    
    /**
     * Initialize demo data to ensure teachers and students can see each other
     */
    private fun initializeDemoData() {
        val existingUsers = getAllStoredUsers()
        
        // Add demo teacher if none exists
        if (existingUsers.none { it.role == UserRole.TEACHER }) {
            val demoTeacher = User(
                id = "teacher_demo_001",
                name = "Demo Teacher",
                email = "teacher@demo.com",
                role = UserRole.TEACHER,
                createdAt = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000) // 7 days ago
            )
            saveUserToStorage(demoTeacher)
        }
        
        // Add demo students if none exist
        if (existingUsers.none { it.role == UserRole.STUDENT }) {
            val demoStudents = listOf(
                User(
                    id = "student_demo_001",
                    name = "Demo Student 1",
                    email = "student1@demo.com",
                    role = UserRole.STUDENT,
                    createdAt = System.currentTimeMillis() - (5 * 24 * 60 * 60 * 1000)
                ),
                User(
                    id = "student_demo_002", 
                    name = "Demo Student 2",
                    email = "student2@demo.com",
                    role = UserRole.STUDENT,
                    createdAt = System.currentTimeMillis() - (3 * 24 * 60 * 60 * 1000)
                )
            )
            
            demoStudents.forEach { saveUserToStorage(it) }
        }
        
        // Reload all users after adding demo data
        loadAllUsers()
    }
    
    /**
     * Call this during initialization to ensure demo data exists
     */
    fun ensureDemoDataExists() {
        initializeDemoData()
    }
    
    /**
     * Get all registered students (for teacher dashboard and presence tracking)
     */
    fun getAllRegisteredStudents(): List<User> {
        return getAllStudents()
    }
    
    /**
     * Check if a user is a registered student by name
     */
    fun isRegisteredStudent(name: String): Boolean {
        return getAllStoredUsers().any { 
            it.role == UserRole.STUDENT && it.name.equals(name, ignoreCase = true)
        }
    }
    
    /**
     * Find a user by name (case insensitive)
     */
    fun findUserByName(name: String): User? {
        return getAllStoredUsers().find { 
            it.name.equals(name, ignoreCase = true)
        }
    }
    
    /**
     * Update user online status (for presence tracking)
     */
    fun updateUserOnlineStatus(user: User, isOnline: Boolean) {
        updateOnlineUsers(user, isOnline)
    }
}