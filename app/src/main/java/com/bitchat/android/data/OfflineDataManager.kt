package com.bitchat.android.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Offline data manager for EduLearn
 * Handles all local storage of student progress, quiz scores, video completion
 * Everything stored locally using SharedPreferences - NO INTERNET REQUIRED
 */
class OfflineDataManager(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences("edulearn_offline_data", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    // Real-time state flows for UI updates
    private val _studentProgress = MutableStateFlow<Map<String, StudentProgress>>(loadStudentProgress())
    val studentProgress: StateFlow<Map<String, StudentProgress>> = _studentProgress.asStateFlow()
    
    private val _quizResults = MutableStateFlow<Map<String, List<QuizResult>>>(loadQuizResults())
    val quizResults: StateFlow<Map<String, List<QuizResult>>> = _quizResults.asStateFlow()
    
    private val _videoProgress = MutableStateFlow<Map<String, VideoProgress>>(loadVideoProgress())
    val videoProgress: StateFlow<Map<String, VideoProgress>> = _videoProgress.asStateFlow()
    
    // Current student ID (simulating login - in real app this would be from authentication)
    private var currentStudentId: String = prefs.getString("current_student_id", "student_001") ?: "student_001"
    
    init {
        // Initialize with default data if first time
        if (_studentProgress.value.isEmpty()) {
            initializeDefaultData()
        }
    }
    
    /**
     * Update quiz completion - IMMEDIATE LOCAL STORAGE
     */
    fun completeQuiz(studentId: String, quizId: String, score: Int, totalQuestions: Int) {
        val quizResult = QuizResult(
            quizId = quizId,
            score = score,
            totalQuestions = totalQuestions,
            completedAt = System.currentTimeMillis(),
            percentage = (score * 100) / totalQuestions
        )
        
        // Update quiz results
        val currentResults = _quizResults.value.toMutableMap()
        val studentResults = currentResults[studentId]?.toMutableList() ?: mutableListOf()
        studentResults.add(quizResult)
        currentResults[studentId] = studentResults
        _quizResults.value = currentResults
        
        // Update student progress
        val currentProgress = _studentProgress.value.toMutableMap()
        val student = currentProgress[studentId] ?: createDefaultStudentProgress(studentId)
        val updatedStudent = student.copy(
            quizzesCompleted = student.quizzesCompleted + 1,
            totalQuizScore = student.totalQuizScore + score,
            averageScore = calculateAverageScore(studentId),
            lastActivity = System.currentTimeMillis()
        )
        currentProgress[studentId] = updatedStudent
        _studentProgress.value = currentProgress
        
        // Save to local storage immediately
        saveAllData()
    }
    
    /**
     * Update video watching progress - REAL-TIME LOCAL STORAGE
     */
    fun updateVideoProgress(studentId: String, videoId: String, progressPercent: Int, watchTimeSeconds: Int) {
        val videoProgress = VideoProgress(
            videoId = videoId,
            progressPercent = progressPercent,
            watchTimeSeconds = watchTimeSeconds,
            completed = progressPercent >= 90,
            lastWatched = System.currentTimeMillis()
        )
        
        // Update video progress
        val currentProgress = _videoProgress.value.toMutableMap()
        val studentKey = "${studentId}_${videoId}"
        currentProgress[studentKey] = videoProgress
        _videoProgress.value = currentProgress
        
        // Update student overall progress
        updateStudentVideoProgress(studentId)
        
        // Save immediately to local storage
        saveAllData()
    }
    
    /**
     * Get current student progress
     */
    fun getCurrentStudentProgress(): StudentProgress? {
        return _studentProgress.value[currentStudentId]
    }
    
    /**
     * Get all students for teacher dashboard
     */
    fun getAllStudentsProgress(): List<StudentProgress> {
        return _studentProgress.value.values.toList().sortedByDescending { it.averageScore }
    }
    
    /**
     * Switch current student (for testing multiple students)
     */
    fun switchStudent(studentId: String) {
        currentStudentId = studentId
        prefs.edit().putString("current_student_id", studentId).apply()
    }
    
    // Private helper methods
    private fun updateStudentVideoProgress(studentId: String) {
        val studentVideos = _videoProgress.value.filter { it.key.startsWith("${studentId}_") }
        val completedVideos = studentVideos.values.count { it.completed }
        val totalWatchTime = studentVideos.values.sumOf { it.watchTimeSeconds }
        
        val currentProgress = _studentProgress.value.toMutableMap()
        val student = currentProgress[studentId] ?: createDefaultStudentProgress(studentId)
        val updatedStudent = student.copy(
            videosWatched = completedVideos,
            totalWatchTimeMinutes = totalWatchTime / 60,
            lastActivity = System.currentTimeMillis()
        )
        currentProgress[studentId] = updatedStudent
        _studentProgress.value = currentProgress
    }
    
    private fun calculateAverageScore(studentId: String): Int {
        val quizResults = _quizResults.value[studentId] ?: return 0
        return if (quizResults.isNotEmpty()) {
            quizResults.sumOf { it.percentage } / quizResults.size
        } else 0
    }
    
    private fun createDefaultStudentProgress(studentId: String): StudentProgress {
        val studentNames = mapOf(
            "student_001" to "Priya Singh",
            "student_002" to "Ravi Kumar", 
            "student_003" to "Amit Sharma",
            "student_004" to "Sunita Devi",
            "student_005" to "Vikash Singh"
        )
        
        return StudentProgress(
            studentId = studentId,
            studentName = studentNames[studentId] ?: "Student ${studentId.takeLast(3)}",
            quizzesCompleted = 0,
            totalQuizScore = 0,
            videosWatched = 0,
            totalWatchTimeMinutes = 0,
            averageScore = 0,
            lastActivity = System.currentTimeMillis(),
            needsAttention = false
        )
    }
    
    private fun initializeDefaultData() {
        val defaultStudents = listOf("student_001", "student_002", "student_003", "student_004", "student_005")
        val progressMap = mutableMapOf<String, StudentProgress>()
        
        defaultStudents.forEach { studentId ->
            progressMap[studentId] = createDefaultStudentProgress(studentId)
        }
        
        _studentProgress.value = progressMap
        saveAllData()
    }
    
    // Local storage methods
    private fun saveAllData() {
        prefs.edit().apply {
            putString("student_progress", gson.toJson(_studentProgress.value))
            putString("quiz_results", gson.toJson(_quizResults.value))
            putString("video_progress", gson.toJson(_videoProgress.value))
            apply()
        }
    }
    
    private fun loadStudentProgress(): Map<String, StudentProgress> {
        val json = prefs.getString("student_progress", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, StudentProgress>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    private fun loadQuizResults(): Map<String, List<QuizResult>> {
        val json = prefs.getString("quiz_results", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, List<QuizResult>>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    private fun loadVideoProgress(): Map<String, VideoProgress> {
        val json = prefs.getString("video_progress", null) ?: return emptyMap()
        val type = object : TypeToken<Map<String, VideoProgress>>() {}.type
        return gson.fromJson(json, type) ?: emptyMap()
    }
    
    /**
     * Add a sample video for demonstration purposes
     */
    fun addSampleVideo() {
        // This is a placeholder implementation
        // In a real app, this would open a file picker and handle video upload
        val currentTime = System.currentTimeMillis()
        val sampleStudentId = "student_001"
        
        // Simulate adding a new video by updating a student's video count
        val currentProgress = _studentProgress.value.toMutableMap()
        val student = currentProgress[sampleStudentId] ?: createDefaultStudentProgress(sampleStudentId)
        val updatedStudent = student.copy(
            videosWatched = student.videosWatched + 1,
            lastActivity = currentTime
        )
        currentProgress[sampleStudentId] = updatedStudent
        _studentProgress.value = currentProgress
        
        // Save the updated data
        saveAllData()
    }
}

// Data classes for offline storage
data class StudentProgress(
    val studentId: String,
    val studentName: String,
    val quizzesCompleted: Int,
    val totalQuizScore: Int,
    val videosWatched: Int,
    val totalWatchTimeMinutes: Int,
    val averageScore: Int,
    val lastActivity: Long,
    val needsAttention: Boolean = false
)

data class QuizResult(
    val quizId: String,
    val score: Int,
    val totalQuestions: Int,
    val completedAt: Long,
    val percentage: Int
)

data class VideoProgress(
    val videoId: String,
    val progressPercent: Int,
    val watchTimeSeconds: Int,
    val completed: Boolean,
    val lastWatched: Long
)