package com.bitchat.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Shared quiz data model
 */
data class SharedQuizQuestion(
    val id: String,
    val question: String,
    val options: List<String>,
    val correctAnswer: Int,
    val explanation: String = ""
)

data class SharedQuiz(
    val id: String,
    val title: String,
    val questions: List<SharedQuizQuestion>,
    val description: String,
    val subject: String,
    val difficulty: String,
    val timeLimit: Int, // minutes
    val createdDate: Long,
    val teacherId: String,
    // Class-based filtering: only students in this class can see the quiz
    val classId: String? = null,
    val classCode: String? = null
)

/**
 * Shared content manager for teacher-student communication
 * Handles sharing of files, quizzes, and educational content
 * All data is stored locally and synchronized between modes
 */
class SharedContentManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("shared_educational_content", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Shared uploaded files (teacher uploads, students access)
    private val _uploadedFiles = MutableStateFlow<Map<String, SharedFile>>(loadUploadedFiles())
    val uploadedFiles: StateFlow<Map<String, SharedFile>> = _uploadedFiles.asStateFlow()
    
    // Shared quizzes (teacher creates, students take)
    private val _sharedQuizzes = MutableStateFlow<Map<String, SharedQuiz>>(loadSharedQuizzes())
    val sharedQuizzes: StateFlow<Map<String, SharedQuiz>> = _sharedQuizzes.asStateFlow()
    
    // Student names for display
    private val _studentNames = MutableStateFlow<Map<String, String>>(loadStudentNames())
    val studentNames: StateFlow<Map<String, String>> = _studentNames.asStateFlow()
    
    // Real-time student activities for teacher dashboard
    private val _studentActivities = MutableStateFlow<Map<String, StudentActivity>>(loadStudentActivities())
    val studentActivities: StateFlow<Map<String, StudentActivity>> = _studentActivities.asStateFlow()
    
    /**
     * Teacher uploads a file - immediately available to students
     */
    fun addUploadedFile(fileName: String, fileType: String, fileId: String, description: String = "") {
        val sharedFile = SharedFile(
            id = UUID.randomUUID().toString(),
            fileName = fileName,
            fileType = fileType,
            fileId = fileId,
            description = description,
            uploadDate = System.currentTimeMillis(),
            teacherId = "teacher_001"
        )
        
        val currentFiles = _uploadedFiles.value.toMutableMap()
        currentFiles[sharedFile.id] = sharedFile
        _uploadedFiles.value = currentFiles
        
        saveUploadedFiles()
    }
    
    /**
     * Teacher creates a quiz - immediately available to students
     */
    fun addSharedQuiz(quiz: SharedQuiz) {
        val currentQuizzes = _sharedQuizzes.value.toMutableMap()
        currentQuizzes[quiz.id] = quiz
        _sharedQuizzes.value = currentQuizzes
        
        saveSharedQuizzes()
    }
    
    /**
     * Get all uploaded files for students
     */
    fun getAvailableFiles(): List<SharedFile> {
        return _uploadedFiles.value.values.toList().sortedByDescending { it.uploadDate }
    }
    
    /**
     * Get all available quizzes for students
     */
    fun getAvailableQuizzes(): List<SharedQuiz> {
        return _sharedQuizzes.value.values.toList().sortedByDescending { it.createdDate }
    }
    
    /**
     * Update student name
     */
    fun updateStudentName(studentId: String, name: String) {
        val currentNames = _studentNames.value.toMutableMap()
        currentNames[studentId] = name
        _studentNames.value = currentNames
        saveStudentNames()
    }
    
    /**
     * Get student name by ID
     */
    fun getStudentName(studentId: String): String {
        return _studentNames.value[studentId] ?: "Student ${studentId.takeLast(3)}"
    }
    
    // Private save/load methods
    private fun saveUploadedFiles() {
        val json = gson.toJson(_uploadedFiles.value)
        prefs.edit().putString("uploaded_files", json).apply()
    }
    
    private fun loadUploadedFiles(): Map<String, SharedFile> {
        val json = prefs.getString("uploaded_files", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, SharedFile>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveSharedQuizzes() {
        val json = gson.toJson(_sharedQuizzes.value)
        prefs.edit().putString("shared_quizzes", json).apply()
    }
    
    private fun loadSharedQuizzes(): Map<String, SharedQuiz> {
        val json = prefs.getString("shared_quizzes", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, SharedQuiz>>() {}.type
        return try {
            gson.fromJson(json, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }
    }
    
    private fun saveStudentNames() {
        val json = gson.toJson(_studentNames.value)
        prefs.edit().putString("student_names", json).apply()
    }
    
    private fun loadStudentNames(): Map<String, String> {
        val json = prefs.getString("student_names", null) ?: return getDefaultStudentNames()
        val type = object : TypeToken<Map<String, String>>() {}.type
        return try {
            gson.fromJson(json, type) ?: getDefaultStudentNames()
        } catch (e: Exception) {
            getDefaultStudentNames()
        }
    }
    
    private fun getDefaultStudentNames(): Map<String, String> {
        return mapOf(
            "student_001" to "Priya Singh",
            "student_002" to "Ravi Kumar",
            "student_003" to "Anita Sharma",
            "student_004" to "Vikram Patel",
            "student_005" to "Meera Gupta"
        )
    }
    /**
     * Update student activity when they complete quizzes or watch videos
     */
    fun updateStudentActivity(studentId: String, studentName: String, activityType: String, details: String = "") {
        val currentActivities = _studentActivities.value.toMutableMap()
        val currentActivity = currentActivities[studentId] ?: StudentActivity(
            studentId = studentId,
            studentName = studentName,
            quizzesCompleted = 0,
            videosWatched = 0,
            totalScore = 0,
            lastActivity = System.currentTimeMillis()
        )
        
        val updatedActivity = when (activityType) {
            "quiz_completed" -> currentActivity.copy(
                quizzesCompleted = currentActivity.quizzesCompleted + 1,
                totalScore = currentActivity.totalScore + (details.toIntOrNull() ?: 0),
                lastActivity = System.currentTimeMillis()
            )
            "video_watched" -> currentActivity.copy(
                videosWatched = currentActivity.videosWatched + 1,
                lastActivity = System.currentTimeMillis()
            )
            else -> currentActivity.copy(lastActivity = System.currentTimeMillis())
        }
        
        currentActivities[studentId] = updatedActivity
        _studentActivities.value = currentActivities
        saveStudentActivities()
        
        // Also update student names mapping
        val currentNames = _studentNames.value.toMutableMap()
        currentNames[studentId] = studentName
        _studentNames.value = currentNames
        saveStudentNames()
    }
    
    private fun loadStudentActivities(): Map<String, StudentActivity> {
        val json = prefs.getString("student_activities", null) ?: return getDefaultStudentActivities()
        val type = object : TypeToken<Map<String, StudentActivity>>() {}.type
        return gson.fromJson(json, type) ?: getDefaultStudentActivities()
    }
    
    private fun saveStudentActivities() {
        val json = gson.toJson(_studentActivities.value)
        prefs.edit().putString("student_activities", json).apply()
    }
    
    private fun getDefaultStudentActivities(): Map<String, StudentActivity> {
        return mapOf(
            "student_001" to StudentActivity("student_001", "Priya Singh", 2, 3, 85, System.currentTimeMillis() - 3600000),
            "student_002" to StudentActivity("student_002", "Ravi Kumar", 1, 2, 75, System.currentTimeMillis() - 7200000),
            "student_003" to StudentActivity("student_003", "Anita Sharma", 3, 4, 92, System.currentTimeMillis() - 1800000)
        )
    }
}

/**
 * Student activity model for real-time teacher dashboard updates
 */
data class StudentActivity(
    val studentId: String,
    val studentName: String,
    val quizzesCompleted: Int,
    val videosWatched: Int,
    val totalScore: Int,
    val lastActivity: Long
) {
    val averageScore: Int
        get() = if (quizzesCompleted > 0) totalScore / quizzesCompleted else 0
}

/**
 * Shared file model for teacher-student file sharing
 */
data class SharedFile(
    val id: String,
    val fileName: String,
    val fileType: String, // "document", "video"
    val fileId: String, // ID from EduLearnFileStorage
    val description: String,
    val uploadDate: Long,
    val teacherId: String
)