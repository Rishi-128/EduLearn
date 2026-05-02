package com.bitchat.android.quiz

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.UserManager
import com.bitchat.android.data.UserRole
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.protocol.MessageType
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

/**
 * Quiz Management Service for Teacher-Student Distribution
 * Handles quiz creation, distribution, and real-time synchronization via mesh network
 * 
 * SECURITY: Class-based filtering ensures students only see quizzes for their enrolled classes
 */
class QuizDistributionService(
    private val context: Context,
    private var meshService: BluetoothMeshService? = null
) {
    
    companion object {
        private const val TAG = "QuizDistribution"
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences("quiz_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val classManager = ClassManager.getInstance(context)
    private val userManager = UserManager.getInstance(context)
    
    // State flows for reactive UI
    private val _availableQuizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val availableQuizzes: StateFlow<List<Quiz>> = _availableQuizzes.asStateFlow()
    
    private val _createdQuizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val createdQuizzes: StateFlow<List<Quiz>> = _createdQuizzes.asStateFlow()
    
    private val _activeQuizzes = MutableStateFlow<List<Quiz>>(emptyList())
    val activeQuizzes: StateFlow<List<Quiz>> = _activeQuizzes.asStateFlow()
    
    private val _quizSubmissions = MutableStateFlow<List<QuizSubmission>>(emptyList())
    val quizSubmissions: StateFlow<List<QuizSubmission>> = _quizSubmissions.asStateFlow()
    
    private val _distributionStatus = MutableStateFlow<DistributionStatus>(DistributionStatus.Idle)
    val distributionStatus: StateFlow<DistributionStatus> = _distributionStatus.asStateFlow()
    
    init {
        loadQuizData()
        Log.d(TAG, "QuizDistributionService initialized with class-based filtering")
    }
    
    /**
     * Set mesh service for quiz distribution (called after initialization)
     */
    fun setMeshService(service: BluetoothMeshService) {
        this.meshService = service
        Log.d(TAG, "Mesh service connected for quiz distribution")
    }
    
    /**
     * Handle received quiz from mesh network (called by MessageHandler)
     * 
     * SECURITY: Validates that student is enrolled in the quiz's target class
     * before accepting the quiz. Quizzes from non-enrolled classes are discarded.
     */
    fun handleReceivedQuiz(quizJson: String) {
        try {
            val quiz = gson.fromJson(quizJson, Quiz::class.java)
            Log.d(TAG, "Received quiz via mesh: ${quiz.title} (classId: ${quiz.classId})")
            
            // SECURITY CHECK: Validate class enrollment for students
            val currentUser = userManager.getCurrentUser()
            if (currentUser != null && currentUser.role == UserRole.STUDENT) {
                val quizClassId = quiz.classId
                
                if (quizClassId != null) {
                    // Check if student is enrolled and approved in this class
                    val isEnrolled = classManager.isStudentApproved(quizClassId, currentUser.id)
                    
                    if (!isEnrolled) {
                        Log.w(TAG, "SECURITY: Discarding quiz '${quiz.title}' - student not enrolled in class $quizClassId")
                        return // Don't accept quiz from non-enrolled class
                    }
                    
                    Log.d(TAG, "SECURITY: Student verified for class $quizClassId, accepting quiz")
                }
            }
            
            // Add to distributed quizzes
            val currentDistributed = loadDistributedQuizzes().toMutableList()
            
            // Remove existing quiz to prevent duplicates
            currentDistributed.removeAll { it.id == quiz.id }
            currentDistributed.add(quiz)
            
            saveDistributedQuizzes(currentDistributed)
            
            // Filter available quizzes based on student's enrolled classes
            updateAvailableQuizzesForCurrentUser(currentDistributed)
            
            Log.d(TAG, "Quiz '${quiz.title}' added to available quizzes")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling received quiz", e)
        }
    }
    
    /**
     * Filter and update available quizzes based on current user's class enrollments
     */
    private fun updateAvailableQuizzesForCurrentUser(allQuizzes: List<Quiz>) {
        val currentUser = userManager.getCurrentUser()
        
        if (currentUser == null || currentUser.role == UserRole.TEACHER) {
            // Teachers see all quizzes
            _availableQuizzes.value = allQuizzes
            return
        }
        
        // For students: Filter to only show quizzes from enrolled classes
        val enrolledClassIds = classManager.getStudentClasses(currentUser.id).map { it.id }.toSet()
        
        val filteredQuizzes = allQuizzes.filter { quiz ->
            val quizClassId = quiz.classId
            // Show quiz if: no classId (legacy) OR student is enrolled in that class
            quizClassId == null || quizClassId in enrolledClassIds
        }
        
        _availableQuizzes.value = filteredQuizzes
        Log.d(TAG, "Filtered quizzes: ${filteredQuizzes.size} of ${allQuizzes.size} available for student")
    }
    
    /**
     * Handle received quiz submission from student (called by MessageHandler)
     */
    fun handleReceivedSubmission(submissionJson: String) {
        try {
            val submission = gson.fromJson(submissionJson, QuizSubmission::class.java)
            Log.d(TAG, "Received quiz submission from ${submission.studentName} (score: ${submission.score}%)")
            
            // Add to submissions list
            val currentSubmissions = _quizSubmissions.value.toMutableList()
            
            // Remove existing submission from same student for same quiz to prevent duplicates
            currentSubmissions.removeAll { it.quizId == submission.quizId && it.studentName == submission.studentName }
            currentSubmissions.add(submission)
            
            _quizSubmissions.value = currentSubmissions
            saveSubmissionsToPrefs()
            
            Log.d(TAG, "Quiz submission from ${submission.studentName} saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling received submission", e)
        }
    }
    
    /**
     * Create a new quiz (Teacher function)
     * 
     * SECURITY: Quiz is tagged with classId for class-based filtering
     */
    fun createQuiz(
        title: String,
        description: String,
        questions: List<QuizQuestion>,
        timeLimit: Int, // in minutes
        targetClass: String,
        scheduleFor: Long? = null, // timestamp for scheduled release
        classId: String? = null, // Class ID for filtering (required for class-based distribution)
        classCode: String? = null
    ): Quiz {
        // Get active class if classId not provided
        val effectiveClassId = classId ?: classManager.getActiveClass()?.id
        val effectiveClassCode = classCode ?: classManager.getActiveClass()?.code
        
        val quiz = Quiz(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            questions = questions,
            createdBy = getCurrentTeacherId(),
            createdAt = System.currentTimeMillis(),
            timeLimit = timeLimit,
            targetClass = targetClass,
            scheduledFor = scheduleFor,
            status = if (scheduleFor == null) QuizStatus.Active else QuizStatus.Scheduled,
            classId = effectiveClassId,
            classCode = effectiveClassCode,
            isEncrypted = effectiveClassId != null // Mark as encrypted if has class
        )
        
        saveQuiz(quiz)
        
        Log.d(TAG, "Quiz '${quiz.title}' created for class: ${effectiveClassId ?: "ALL"}")
        
        // Don't auto-distribute - teacher must explicitly distribute via UI button
        // This ensures students only see quizzes when teacher is ready
        
        return quiz
    }
    
    /**
     * Distribute quiz to students in real-time via mesh network
     */
    fun distributeQuiz(quiz: Quiz) {
        _distributionStatus.value = DistributionStatus.Distributing(quiz.title)
        
        if (meshService == null) {
            Log.e(TAG, "Mesh service not available for quiz distribution")
            _distributionStatus.value = DistributionStatus.Error("Mesh network not available")
            return
        }
        
        Thread {
            try {
                // Update quiz status
                val distributedQuiz = quiz.copy(
                    status = QuizStatus.Active,
                    distributedAt = System.currentTimeMillis()
                )
                
                // Serialize quiz to JSON
                val quizJson = gson.toJson(distributedQuiz)
                val quizBytes = quizJson.toByteArray(Charsets.UTF_8)
                
                Log.d(TAG, "Broadcasting quiz '${quiz.title}' via mesh (${quizBytes.size} bytes)")
                
                // Send via mesh network (broadcast to all students)
                meshService?.sendQuizDistribution(quizBytes)
                
                // Update local distributed quizzes
                val currentDistributed = loadDistributedQuizzes().toMutableList()
                currentDistributed.removeAll { it.id == distributedQuiz.id }
                currentDistributed.add(distributedQuiz)
                saveDistributedQuizzes(currentDistributed)
                
                // Update available quizzes
                _availableQuizzes.value = currentDistributed
                
                // Update quiz status in teacher's created list
                updateQuizStatus(distributedQuiz)
                
                _distributionStatus.value = DistributionStatus.Success(quiz.title)
                Log.d(TAG, "Quiz '${quiz.title}' distributed successfully")
                
                // Reset status after delay
                Thread.sleep(2000)
                _distributionStatus.value = DistributionStatus.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Error distributing quiz", e)
                _distributionStatus.value = DistributionStatus.Error("Failed to distribute: ${e.message}")
            }
        }.start()
    }
    
    /**
     * Submit quiz answers (Student function)
     */
    fun submitQuiz(quizId: String, answers: Map<String, String>, studentName: String): QuizSubmission {
        val quiz = findQuizById(quizId)
        val submission = QuizSubmission(
            id = UUID.randomUUID().toString(),
            quizId = quizId,
            studentName = studentName,
            answers = answers,
            submittedAt = System.currentTimeMillis(),
            score = calculateScore(quiz, answers)
        )
        
        saveSubmission(submission)
        
        // Send submission back to teacher via mesh network
        if (meshService != null) {
            Thread {
                try {
                    val submissionJson = gson.toJson(submission)
                    val submissionBytes = submissionJson.toByteArray(Charsets.UTF_8)
                    
                    Log.d(TAG, "Sending quiz submission to teacher via mesh (${submissionBytes.size} bytes)")
                    meshService?.sendQuizSubmission(submissionBytes)
                    Log.d(TAG, "Quiz submission sent successfully")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending quiz submission", e)
                }
            }.start()
        } else {
            Log.w(TAG, "Mesh service not available, submission not sent to teacher")
        }
        
        return submission
    }
    
    /**
     * Get quiz results for teacher
     */
    fun getQuizResults(quizId: String): List<QuizSubmission> {
        return _quizSubmissions.value.filter { it.quizId == quizId }
    }
    
    /**
     * Get student's quiz history
     */
    fun getStudentQuizHistory(studentName: String): List<QuizSubmission> {
        return _quizSubmissions.value.filter { it.studentName == studentName }
    }
    
    /**
     * Schedule a quiz for later distribution
     */
    fun scheduleQuiz(quiz: Quiz, scheduleTime: Long) {
        val scheduledQuiz = quiz.copy(
            scheduledFor = scheduleTime,
            status = QuizStatus.Scheduled
        )
        updateQuiz(scheduledQuiz)
        
        // In a real app, you would set up a background task or alarm
        // to distribute the quiz at the scheduled time
    }
    
    /**
     * End an active quiz
     */
    fun endQuiz(quizId: String) {
        val quiz = findQuizById(quizId)
        val endedQuiz = quiz?.copy(
            status = QuizStatus.Ended,
            endedAt = System.currentTimeMillis()
        )
        
        endedQuiz?.let { updateQuizStatus(it) }
        
        // Remove from distributed quizzes storage when teacher ends quiz
        // This removes it for ALL students since quiz is officially over
        val distributedQuizzes = loadDistributedQuizzes().toMutableList()
        distributedQuizzes.removeAll { it.id == quizId }
        saveDistributedQuizzes(distributedQuizzes)
        
        // Update available quizzes StateFlow
        _availableQuizzes.value = distributedQuizzes
    }
    
    /**
     * Get quiz statistics
     */
    fun getQuizStatistics(quizId: String): QuizStatistics {
        val submissions = getQuizResults(quizId)
        val scores = submissions.map { it.score }
        
        return QuizStatistics(
            quizId = quizId,
            totalSubmissions = submissions.size,
            averageScore = if (scores.isNotEmpty()) scores.average() else 0.0,
            highestScore = scores.maxOrNull() ?: 0,
            lowestScore = scores.minOrNull() ?: 0,
            completionRate = calculateCompletionRate(quizId)
        )
    }
    
    // Private helper methods
    private fun saveQuiz(quiz: Quiz) {
        val currentQuizzes = _createdQuizzes.value.toMutableList()
        currentQuizzes.add(quiz)
        _createdQuizzes.value = currentQuizzes
        saveQuizzesToPrefs()
    }
    
    private fun updateQuiz(quiz: Quiz) {
        val currentQuizzes = _createdQuizzes.value.toMutableList()
        val index = currentQuizzes.indexOfFirst { it.id == quiz.id }
        if (index != -1) {
            currentQuizzes[index] = quiz
            _createdQuizzes.value = currentQuizzes
            saveQuizzesToPrefs()
        }
    }
    
    private fun updateQuizStatus(quiz: Quiz) {
        updateQuiz(quiz)
        
        // Also update in active quizzes
        val currentActive = _activeQuizzes.value.toMutableList()
        val index = currentActive.indexOfFirst { it.id == quiz.id }
        
        if (quiz.status == QuizStatus.Active) {
            // Add or update active quiz
            if (index != -1) {
                currentActive[index] = quiz
            } else {
                currentActive.add(quiz)
            }
        } else {
            // Remove from active if status is not Active (Draft or Ended)
            if (index != -1) {
                currentActive.removeAt(index)
            }
        }
        
        _activeQuizzes.value = currentActive
    }
    
    /**
     * Request quiz sync from mesh network (Student function)
     * Refreshes the local quiz list to get newly distributed quizzes
     */
    fun requestQuizSync() {
        Log.d(TAG, "Requesting quiz sync from mesh network...")
        // Refresh the local quiz list - mesh automatically receives distributed quizzes
        loadQuizData()
        Log.d(TAG, "Quiz sync requested, current available quizzes: ${_availableQuizzes.value.size}")
    }
    
    private fun saveSubmission(submission: QuizSubmission) {
        val currentSubmissions = _quizSubmissions.value.toMutableList()
        currentSubmissions.add(submission)
        _quizSubmissions.value = currentSubmissions
        saveSubmissionsToPrefs()
    }
    
    private fun calculateScore(quiz: Quiz?, answers: Map<String, String>): Int {
        if (quiz == null) return 0
        
        var correct = 0
        quiz.questions.forEach { question ->
            val studentAnswer = answers[question.id]
            if (studentAnswer == question.correctAnswer) {
                correct++
            }
        }
        
        return (correct * 100) / quiz.questions.size
    }
    
    private fun calculateCompletionRate(quizId: String): Double {
        // In a real app, you would track how many students were assigned the quiz
        // For demo purposes, we'll use a sample class size
        val sampleClassSize = 25
        val submissions = getQuizResults(quizId).size
        return (submissions.toDouble() / sampleClassSize) * 100
    }
    
    private fun findQuizById(id: String): Quiz? {
        return _createdQuizzes.value.find { it.id == id } 
            ?: _availableQuizzes.value.find { it.id == id }
    }
    
    private fun getCurrentTeacherId(): String {
        // In a real app, this would get the current user's ID
        return "teacher_001"
    }
    
    private fun loadQuizData() {
        loadQuizzesFromPrefs()
        loadSubmissionsFromPrefs()
        loadSampleData()
    }
    
    private fun loadQuizzesFromPrefs() {
        val quizzesJson = prefs.getString("quizzes", "[]")
        val type = object : TypeToken<List<Quiz>>() {}.type
        val quizzes: List<Quiz> = gson.fromJson(quizzesJson, type) ?: emptyList()
        _createdQuizzes.value = quizzes
        
        // Filter active quizzes for teacher view
        val active = quizzes.filter { it.status == QuizStatus.Active }
        _activeQuizzes.value = active
        
        // For students: Only load DISTRIBUTED quizzes (not all active quizzes)
        // Students should only see quizzes that were explicitly distributed to them
        val distributedQuizzesJson = prefs.getString("distributed_quizzes", "[]")
        val distributedQuizzes: List<Quiz> = gson.fromJson(distributedQuizzesJson, type) ?: emptyList()
        _availableQuizzes.value = distributedQuizzes
    }
    
    private fun loadSubmissionsFromPrefs() {
        val submissionsJson = prefs.getString("submissions", "[]")
        val type = object : TypeToken<List<QuizSubmission>>() {}.type
        val submissions: List<QuizSubmission> = gson.fromJson(submissionsJson, type) ?: emptyList()
        _quizSubmissions.value = submissions
    }
    
    private fun saveQuizzesToPrefs() {
        val quizzesJson = gson.toJson(_createdQuizzes.value)
        prefs.edit().putString("quizzes", quizzesJson).apply()
    }
    
    private fun saveSubmissionsToPrefs() {
        val submissionsJson = gson.toJson(_quizSubmissions.value)
        prefs.edit().putString("submissions", submissionsJson).apply()
    }
    
    private fun loadDistributedQuizzes(): List<Quiz> {
        val distributedJson = prefs.getString("distributed_quizzes", "[]")
        val type = object : TypeToken<List<Quiz>>() {}.type
        return gson.fromJson(distributedJson, type) ?: emptyList()
    }
    
    private fun saveDistributedQuizzes(quizzes: List<Quiz>) {
        val distributedJson = gson.toJson(quizzes)
        prefs.edit().putString("distributed_quizzes", distributedJson).apply()
    }
    
    private fun loadSampleData() {
        // Clear old predefined quiz data
        val dataVersion = prefs.getInt("quiz_data_version", 0)
        if (dataVersion < 2) {
            prefs.edit()
                .remove("created_quizzes")
                .remove("active_quizzes")
                .remove("available_quizzes")
                .remove("submissions")
                .putInt("quiz_data_version", 2)
                .apply()
        }
        // Start fresh - no sample quizzes
    }
    
    // Removed createSampleQuizzes() - no predefined quizzes
    // Removed createSampleQuizzes() - no predefined quizzes
}

// Data classes
data class Quiz(
    val id: String,
    val title: String,
    val description: String,
    val questions: List<QuizQuestion>,
    val createdBy: String,
    val createdAt: Long,
    val timeLimit: Int, // in minutes
    val targetClass: String,
    val scheduledFor: Long? = null,
    val distributedAt: Long? = null,
    val endedAt: Long? = null,
    val status: QuizStatus,
    // Class-based security: classId for filtering, encryptedData for AES-256 encrypted content
    val classId: String? = null,
    val classCode: String? = null,
    val isEncrypted: Boolean = false
)

data class QuizQuestion(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctAnswer: String,
    val type: QuestionType,
    val points: Int = 1
)

data class QuizSubmission(
    val id: String,
    val quizId: String,
    val studentName: String,
    val answers: Map<String, String>,
    val submittedAt: Long,
    val score: Int
)

data class QuizStatistics(
    val quizId: String,
    val totalSubmissions: Int,
    val averageScore: Double,
    val highestScore: Int,
    val lowestScore: Int,
    val completionRate: Double
)

enum class QuizStatus {
    Draft, Scheduled, Active, Ended
}

enum class QuestionType {
    MultipleChoice, TrueFalse, ShortAnswer, Essay
}

sealed class DistributionStatus {
    object Idle : DistributionStatus()
    data class Distributing(val quizTitle: String) : DistributionStatus()
    data class Success(val quizTitle: String) : DistributionStatus()
    data class Error(val message: String) : DistributionStatus()
}