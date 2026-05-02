 package com.bitchat.android.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Classroom data class
 * Represents a class created by a teacher
 */
data class Classroom(
    val id: String,
    val name: String,                    // e.g., "Class 10-A Math"
    val code: String,                    // Unique class code for joining (e.g., "10AMATH")
    val passwordHash: String,            // Hashed password for joining
    val passwordSalt: String,            // Salt for password hashing
    val encryptionKey: String,           // AES key for encrypting class content (base64)
    val teacherId: String,               // Teacher who created this class
    val teacherName: String,             // Teacher's display name
    val createdAt: Long = System.currentTimeMillis(),
    val subject: String = "",            // Optional subject
    val description: String = ""         // Optional description
)

/**
 * Student enrollment in a class
 */
data class ClassEnrollment(
    val id: String,
    val classId: String,
    val classCode: String,
    val studentId: String,
    val studentName: String,
    val studentEmail: String,
    val enrolledAt: Long = System.currentTimeMillis(),
    val status: EnrollmentStatus = EnrollmentStatus.PENDING,
    val approvedBy: String? = null,      // Teacher who approved
    val approvedAt: Long? = null
)

enum class EnrollmentStatus {
    PENDING,    // Waiting for teacher approval
    APPROVED,   // Teacher approved
    REJECTED,   // Teacher rejected
    REVOKED     // Access revoked by teacher
}

/**
 * Class activity events for real-time updates
 */
data class ClassActivityEvent(
    val classId: String,
    val action: ClassAction,
    val userId: String,
    val userName: String,
    val timestamp: Long = System.currentTimeMillis()
)

enum class ClassAction {
    STUDENT_JOINED,
    STUDENT_LEFT,
    STUDENT_APPROVED,
    STUDENT_REJECTED,
    CONTENT_SHARED,
    QUIZ_DISTRIBUTED
}

/**
 * Discovered class from mesh broadcast (temporary storage)
 * Auto-deleted after 10 minutes if not joined
 */
data class DiscoveredClass(
    val id: String,
    val name: String,
    val code: String,
    val passwordHash: String,
    val passwordSalt: String,
    val encryptionKey: String,
    val teacherId: String,
    val teacherName: String,
    val subject: String,
    val discoveredAt: Long = System.currentTimeMillis()
)

/**
 * ClassManager - Handles class creation, enrollment, and content encryption
 * 
 * Security Features:
 * - Password hashing with PBKDF2 + random salt
 * - AES-256 encryption for class content
 * - Class-specific encryption keys
 */
class ClassManager(private val context: Context) {
    
    companion object {
        private const val TAG = "ClassManager"
        private const val PREFS_NAME = "class_manager_prefs"
        private const val PBKDF2_ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
        private const val AES_KEY_LENGTH = 32  // 256 bits
        
        @Volatile
        private var instance: ClassManager? = null
        
        fun getInstance(context: Context): ClassManager {
            return instance ?: synchronized(this) {
                instance ?: ClassManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val secureRandom = SecureRandom()
    
    // State flows for reactive UI
    private val _classes = MutableStateFlow<List<Classroom>>(emptyList())
    val classes: StateFlow<List<Classroom>> = _classes.asStateFlow()
    
    private val _enrollments = MutableStateFlow<List<ClassEnrollment>>(emptyList())
    val enrollments: StateFlow<List<ClassEnrollment>> = _enrollments.asStateFlow()
    
    private val _currentClassId = MutableStateFlow<String?>(null)
    val currentClassId: StateFlow<String?> = _currentClassId.asStateFlow()
    
    private val _classActivityEvents = MutableStateFlow<ClassActivityEvent?>(null)
    val classActivityEvents: StateFlow<ClassActivityEvent?> = _classActivityEvents.asStateFlow()
    
    // Discovered classes (temporary storage - auto-cleanup after 10 mins)
    private val _discoveredClasses = MutableStateFlow<List<DiscoveredClass>>(emptyList())
    val discoveredClasses: StateFlow<List<DiscoveredClass>> = _discoveredClasses.asStateFlow()
    
    // Cleanup interval
    private val DISCOVERY_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes
    
    init {
        loadClasses()
        loadEnrollments()
        loadCurrentClass()
        startDiscoveryCleanup()
    }
    
    // ==================== TEACHER FUNCTIONS ====================
    
    /**
     * Create a new class (Teacher only)
     * @param name Display name for the class
     * @param code Unique class code for students to join
     * @param password Password for joining the class
     * @param teacherId Teacher's user ID
     * @param teacherName Teacher's display name
     * @param subject Optional subject
     * @param description Optional description
     */
    fun createClass(
        name: String,
        code: String,
        password: String,
        teacherId: String,
        teacherName: String,
        subject: String = "",
        description: String = ""
    ): Result<Classroom> {
        // Validate inputs
        if (name.isBlank()) {
            return Result.failure(Exception("Class name cannot be empty"))
        }
        if (code.isBlank() || code.length < 3) {
            return Result.failure(Exception("Class code must be at least 3 characters"))
        }
        if (password.isBlank() || password.length < 4) {
            return Result.failure(Exception("Password must be at least 4 characters"))
        }
        
        // Check if class code already exists
        val existingClasses = _classes.value
        if (existingClasses.any { it.code.equals(code, ignoreCase = true) }) {
            return Result.failure(Exception("Class code already exists"))
        }
        
        // Generate salt and hash password
        val salt = generateSalt()
        val passwordHash = hashPassword(password, salt)
        
        // Generate AES encryption key for this class
        val encryptionKey = generateEncryptionKey()
        
        val classroom = Classroom(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            code = code.uppercase().trim(),
            passwordHash = passwordHash,
            passwordSalt = salt,
            encryptionKey = encryptionKey,
            teacherId = teacherId,
            teacherName = teacherName,
            subject = subject.trim(),
            description = description.trim()
        )
        
        // Save class
        val updatedClasses = existingClasses.toMutableList()
        updatedClasses.add(classroom)
        _classes.value = updatedClasses
        saveClasses()
        
        Log.d(TAG, "Created class: ${classroom.name} (${classroom.code})")
        return Result.success(classroom)
    }
    
    /**
     * Get classes created by a specific teacher
     */
    fun getTeacherClasses(teacherId: String): List<Classroom> {
        return _classes.value.filter { it.teacherId == teacherId }
    }
    
    /**
     * Set active class for teacher (for sharing context)
     */
    fun setActiveClass(classId: String?) {
        _currentClassId.value = classId
        saveCurrentClass()
        Log.d(TAG, "Active class set to: $classId")
    }
    
    /**
     * Get active class
     */
    fun getActiveClass(): Classroom? {
        val classId = _currentClassId.value ?: return null
        return _classes.value.find { it.id == classId }
    }
    
    /**
     * Get all enrollments for a class (all statuses)
     */
    fun getClassEnrollments(classId: String): List<ClassEnrollment> {
        return _enrollments.value.filter { it.classId == classId }
    }
    
    /**
     * Get pending enrollment requests for a class
     */
    fun getPendingEnrollments(classId: String): List<ClassEnrollment> {
        return _enrollments.value.filter { 
            it.classId == classId && it.status == EnrollmentStatus.PENDING 
        }
    }
    
    /**
     * Get approved students for a class
     */
    fun getApprovedStudents(classId: String): List<ClassEnrollment> {
        return _enrollments.value.filter { 
            it.classId == classId && it.status == EnrollmentStatus.APPROVED 
        }
    }
    
    /**
     * Approve a student's enrollment request
     */
    fun approveEnrollment(enrollmentId: String, teacherId: String): Result<ClassEnrollment> {
        val enrollment = _enrollments.value.find { it.id == enrollmentId }
            ?: return Result.failure(Exception("Enrollment not found"))
        
        val updatedEnrollment = enrollment.copy(
            status = EnrollmentStatus.APPROVED,
            approvedBy = teacherId,
            approvedAt = System.currentTimeMillis()
        )
        
        updateEnrollment(updatedEnrollment)
        
        // Broadcast event
        _classActivityEvents.value = ClassActivityEvent(
            classId = enrollment.classId,
            action = ClassAction.STUDENT_APPROVED,
            userId = enrollment.studentId,
            userName = enrollment.studentName
        )
        
        Log.d(TAG, "Approved enrollment for ${enrollment.studentName}")
        return Result.success(updatedEnrollment)
    }
    
    /**
     * Reject a student's enrollment request
     */
    fun rejectEnrollment(enrollmentId: String): Result<ClassEnrollment> {
        val enrollment = _enrollments.value.find { it.id == enrollmentId }
            ?: return Result.failure(Exception("Enrollment not found"))
        
        val updatedEnrollment = enrollment.copy(status = EnrollmentStatus.REJECTED)
        updateEnrollment(updatedEnrollment)
        
        // Broadcast event
        _classActivityEvents.value = ClassActivityEvent(
            classId = enrollment.classId,
            action = ClassAction.STUDENT_REJECTED,
            userId = enrollment.studentId,
            userName = enrollment.studentName
        )
        
        Log.d(TAG, "Rejected enrollment for ${enrollment.studentName}")
        return Result.success(updatedEnrollment)
    }
    
    /**
     * Revoke a student's access
     */
    fun revokeAccess(enrollmentId: String): Result<ClassEnrollment> {
        val enrollment = _enrollments.value.find { it.id == enrollmentId }
            ?: return Result.failure(Exception("Enrollment not found"))
        
        val updatedEnrollment = enrollment.copy(status = EnrollmentStatus.REVOKED)
        updateEnrollment(updatedEnrollment)
        
        Log.d(TAG, "Revoked access for ${enrollment.studentName}")
        return Result.success(updatedEnrollment)
    }
    
    /**
     * Delete a class (Teacher only)
     */
    fun deleteClass(classId: String, teacherId: String): Result<Unit> {
        val classroom = _classes.value.find { it.id == classId }
            ?: return Result.failure(Exception("Class not found"))
        
        if (classroom.teacherId != teacherId) {
            return Result.failure(Exception("Only the class creator can delete the class"))
        }
        
        // Remove class
        val updatedClasses = _classes.value.filter { it.id != classId }
        _classes.value = updatedClasses
        saveClasses()
        
        // Remove all enrollments for this class
        val updatedEnrollments = _enrollments.value.filter { it.classId != classId }
        _enrollments.value = updatedEnrollments
        saveEnrollments()
        
        // Clear current class if it was the active one
        if (_currentClassId.value == classId) {
            _currentClassId.value = null
            saveCurrentClass()
        }
        
        Log.d(TAG, "Deleted class: ${classroom.name}")
        return Result.success(Unit)
    }
    
    /**
     * Update class password
     */
    fun updateClassPassword(classId: String, newPassword: String, teacherId: String): Result<Classroom> {
        val classroom = _classes.value.find { it.id == classId }
            ?: return Result.failure(Exception("Class not found"))
        
        if (classroom.teacherId != teacherId) {
            return Result.failure(Exception("Only the class creator can change the password"))
        }
        
        if (newPassword.isBlank() || newPassword.length < 4) {
            return Result.failure(Exception("Password must be at least 4 characters"))
        }
        
        val newSalt = generateSalt()
        val newPasswordHash = hashPassword(newPassword, newSalt)
        
        val updatedClassroom = classroom.copy(
            passwordHash = newPasswordHash,
            passwordSalt = newSalt
        )
        
        updateClass(updatedClassroom)
        
        Log.d(TAG, "Updated password for class: ${classroom.name}")
        return Result.success(updatedClassroom)
    }
    
    // ==================== STUDENT FUNCTIONS ====================
    
    /**
     * Join a class (Student only)
     * @param classCode The class code to join
     * @param password The class password
     * @param studentId Student's user ID
     * @param studentName Student's display name
     * @param studentEmail Student's email
     */
    fun joinClass(
        classCode: String,
        password: String,
        studentId: String,
        studentName: String,
        studentEmail: String
    ): Result<ClassEnrollment> {
        // Find class by code
        val classroom = _classes.value.find { it.code.equals(classCode, ignoreCase = true) }
            ?: return Result.failure(Exception("Class not found. Check the class code."))
        
        // Verify password
        val passwordHash = hashPassword(password, classroom.passwordSalt)
        if (passwordHash != classroom.passwordHash) {
            return Result.failure(Exception("Incorrect password"))
        }
        
        // Check if already enrolled
        val existingEnrollment = _enrollments.value.find { 
            it.classId == classroom.id && it.studentId == studentId 
        }
        
        if (existingEnrollment != null) {
            return when (existingEnrollment.status) {
                EnrollmentStatus.PENDING -> Result.failure(Exception("Your request is pending approval"))
                EnrollmentStatus.APPROVED -> Result.failure(Exception("You are already enrolled in this class"))
                EnrollmentStatus.REJECTED -> Result.failure(Exception("Your request was rejected. Contact teacher."))
                EnrollmentStatus.REVOKED -> Result.failure(Exception("Your access was revoked. Contact teacher."))
            }
        }
        
        // Create enrollment request
        val enrollment = ClassEnrollment(
            id = UUID.randomUUID().toString(),
            classId = classroom.id,
            classCode = classroom.code,
            studentId = studentId,
            studentName = studentName,
            studentEmail = studentEmail,
            status = EnrollmentStatus.PENDING
        )
        
        val updatedEnrollments = _enrollments.value.toMutableList()
        updatedEnrollments.add(enrollment)
        _enrollments.value = updatedEnrollments
        saveEnrollments()
        
        // Broadcast event
        _classActivityEvents.value = ClassActivityEvent(
            classId = classroom.id,
            action = ClassAction.STUDENT_JOINED,
            userId = studentId,
            userName = studentName
        )
        
        Log.d(TAG, "Student $studentName requested to join class ${classroom.name}")
        return Result.success(enrollment)
    }
    
    /**
     * Get student's enrolled classes (approved only)
     */
    fun getStudentClasses(studentId: String): List<Classroom> {
        val approvedEnrollments = _enrollments.value.filter { 
            it.studentId == studentId && it.status == EnrollmentStatus.APPROVED 
        }
        
        return approvedEnrollments.mapNotNull { enrollment ->
            _classes.value.find { it.id == enrollment.classId }
        }
    }
    
    /**
     * Get student's enrollment status for a class
     */
    fun getStudentEnrollment(studentId: String, classId: String): ClassEnrollment? {
        return _enrollments.value.find { 
            it.studentId == studentId && it.classId == classId 
        }
    }
    
    /**
     * Check if student is approved for a specific class
     */
    fun isStudentApproved(studentId: String, classId: String): Boolean {
        return _enrollments.value.any { 
            it.studentId == studentId && 
            it.classId == classId && 
            it.status == EnrollmentStatus.APPROVED 
        }
    }
    
    /**
     * Leave a class (Student)
     */
    fun leaveClass(studentId: String, classId: String): Result<Unit> {
        val enrollment = _enrollments.value.find { 
            it.studentId == studentId && it.classId == classId 
        } ?: return Result.failure(Exception("Enrollment not found"))
        
        val updatedEnrollments = _enrollments.value.filter { it.id != enrollment.id }
        _enrollments.value = updatedEnrollments
        saveEnrollments()
        
        // Clear current class if it was the active one
        if (_currentClassId.value == classId) {
            _currentClassId.value = null
            saveCurrentClass()
        }
        
        Log.d(TAG, "Student left class: $classId")
        return Result.success(Unit)
    }
    
    // ==================== ENCRYPTION FUNCTIONS ====================
    
    /**
     * Encrypt content for a specific class
     * Only devices with the class encryption key can decrypt
     */
    fun encryptForClass(classId: String, content: ByteArray): ByteArray? {
        val classroom = _classes.value.find { it.id == classId } ?: return null
        
        return try {
            val keyBytes = Base64.decode(classroom.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            
            // Generate random IV
            val iv = ByteArray(16)
            secureRandom.nextBytes(iv)
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, ivSpec)
            
            val encrypted = cipher.doFinal(content)
            
            // Prepend IV to encrypted data
            iv + encrypted
        } catch (e: Exception) {
            Log.e(TAG, "Encryption failed", e)
            null
        }
    }
    
    /**
     * Decrypt content for a specific class
     */
    fun decryptForClass(classId: String, encryptedContent: ByteArray): ByteArray? {
        val classroom = _classes.value.find { it.id == classId } 
            ?: findClassByIdFromEnrollment(classId)
            ?: return null
        
        return try {
            val keyBytes = Base64.decode(classroom.encryptionKey, Base64.NO_WRAP)
            val secretKey = SecretKeySpec(keyBytes, "AES")
            
            // Extract IV from beginning
            val iv = encryptedContent.copyOfRange(0, 16)
            val encrypted = encryptedContent.copyOfRange(16, encryptedContent.size)
            val ivSpec = IvParameterSpec(iv)
            
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey, ivSpec)
            
            cipher.doFinal(encrypted)
        } catch (e: Exception) {
            Log.e(TAG, "Decryption failed", e)
            null
        }
    }
    
    /**
     * Check if content should be accepted based on class ID
     */
    fun shouldAcceptContent(contentClassId: String, studentId: String): Boolean {
        // Check if student is approved for this class
        return isStudentApproved(studentId, contentClassId)
    }
    
    /**
     * Get encryption key for a class (for teachers to include in broadcasts)
     */
    fun getClassEncryptionKey(classId: String): String? {
        return _classes.value.find { it.id == classId }?.encryptionKey
    }
    
    // ==================== HELPER FUNCTIONS ====================
    
    private fun generateSalt(): String {
        val salt = ByteArray(SALT_LENGTH)
        secureRandom.nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }
    
    private fun hashPassword(password: String, saltBase64: String): String {
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = factory.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }
    
    private fun generateEncryptionKey(): String {
        val key = ByteArray(AES_KEY_LENGTH)
        secureRandom.nextBytes(key)
        return Base64.encodeToString(key, Base64.NO_WRAP)
    }
    
    private fun findClassByIdFromEnrollment(classId: String): Classroom? {
        // For students, we need to store the encryption key when they get approved
        // This is handled by the enrollment approval process
        return _classes.value.find { it.id == classId }
    }
    
    private fun updateClass(classroom: Classroom) {
        val updatedClasses = _classes.value.toMutableList()
        val index = updatedClasses.indexOfFirst { it.id == classroom.id }
        if (index != -1) {
            updatedClasses[index] = classroom
            _classes.value = updatedClasses
            saveClasses()
        }
    }
    
    private fun updateEnrollment(enrollment: ClassEnrollment) {
        val updatedEnrollments = _enrollments.value.toMutableList()
        val index = updatedEnrollments.indexOfFirst { it.id == enrollment.id }
        if (index != -1) {
            updatedEnrollments[index] = enrollment
            _enrollments.value = updatedEnrollments
            saveEnrollments()
        }
    }
    
    // ==================== PERSISTENCE ====================
    
    private fun saveClasses() {
        val classesJson = _classes.value.joinToString("|||") { c ->
            "${c.id}::${c.name}::${c.code}::${c.passwordHash}::${c.passwordSalt}::${c.encryptionKey}::${c.teacherId}::${c.teacherName}::${c.createdAt}::${c.subject}::${c.description}"
        }
        prefs.edit().putString("classes", classesJson).apply()
    }
    
    private fun loadClasses() {
        val classesJson = prefs.getString("classes", "") ?: ""
        if (classesJson.isEmpty()) {
            _classes.value = emptyList()
            return
        }
        
        _classes.value = classesJson.split("|||").mapNotNull { classStr ->
            try {
                val parts = classStr.split("::")
                if (parts.size >= 11) {
                    Classroom(
                        id = parts[0],
                        name = parts[1],
                        code = parts[2],
                        passwordHash = parts[3],
                        passwordSalt = parts[4],
                        encryptionKey = parts[5],
                        teacherId = parts[6],
                        teacherName = parts[7],
                        createdAt = parts[8].toLongOrNull() ?: System.currentTimeMillis(),
                        subject = parts[9],
                        description = parts[10]
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing class", e)
                null
            }
        }
    }
    
    private fun saveEnrollments() {
        val enrollmentsJson = _enrollments.value.joinToString("|||") { e ->
            "${e.id}::${e.classId}::${e.classCode}::${e.studentId}::${e.studentName}::${e.studentEmail}::${e.enrolledAt}::${e.status.name}::${e.approvedBy ?: ""}::${e.approvedAt ?: 0}"
        }
        prefs.edit().putString("enrollments", enrollmentsJson).apply()
    }
    
    private fun loadEnrollments() {
        val enrollmentsJson = prefs.getString("enrollments", "") ?: ""
        if (enrollmentsJson.isEmpty()) {
            _enrollments.value = emptyList()
            return
        }
        
        _enrollments.value = enrollmentsJson.split("|||").mapNotNull { enrollStr ->
            try {
                val parts = enrollStr.split("::")
                if (parts.size >= 10) {
                    ClassEnrollment(
                        id = parts[0],
                        classId = parts[1],
                        classCode = parts[2],
                        studentId = parts[3],
                        studentName = parts[4],
                        studentEmail = parts[5],
                        enrolledAt = parts[6].toLongOrNull() ?: System.currentTimeMillis(),
                        status = try { EnrollmentStatus.valueOf(parts[7]) } catch (e: Exception) { EnrollmentStatus.PENDING },
                        approvedBy = parts[8].takeIf { it.isNotEmpty() },
                        approvedAt = parts[9].toLongOrNull()?.takeIf { it > 0 }
                    )
                } else null
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing enrollment", e)
                null
            }
        }
    }
    
    private fun saveCurrentClass() {
        prefs.edit().putString("current_class_id", _currentClassId.value ?: "").apply()
    }
    
    private fun loadCurrentClass() {
        val classId = prefs.getString("current_class_id", "") ?: ""
        _currentClassId.value = classId.takeIf { it.isNotEmpty() }
    }
    
    /**
     * Find class by code (for UI lookups)
     */
    fun findClassByCode(code: String): Classroom? {
        return _classes.value.find { it.code.equals(code, ignoreCase = true) }
    }
    
    /**
     * Find class by ID
     */
    fun findClassById(classId: String): Classroom? {
        return _classes.value.find { it.id == classId }
    }
    
    /**
     * Get all enrollments for a student
     */
    fun getStudentEnrollments(studentId: String): List<ClassEnrollment> {
        return _enrollments.value.filter { it.studentId == studentId }
    }
    
    // ==================== DISCOVERY FUNCTIONS (MESH) ====================
    
    /**
     * Start periodic cleanup of expired discovered classes (10 min timeout)
     */
    private fun startDiscoveryCleanup() {
        Thread {
            while (true) {
                try {
                    Thread.sleep(60000) // Check every minute
                    cleanupExpiredDiscoveries()
                } catch (e: InterruptedException) {
                    break
                }
            }
        }.apply { isDaemon = true }.start()
    }
    
    /**
     * Remove discovered classes older than 10 minutes
     */
    private fun cleanupExpiredDiscoveries() {
        val now = System.currentTimeMillis()
        val expired = _discoveredClasses.value.filter { 
            now - it.discoveredAt > DISCOVERY_TIMEOUT_MS 
        }
        
        if (expired.isNotEmpty()) {
            Log.d(TAG, "Cleaning up ${expired.size} expired discovered classes")
            _discoveredClasses.value = _discoveredClasses.value.filter { 
                now - it.discoveredAt <= DISCOVERY_TIMEOUT_MS 
            }
        }
    }
    
    /**
     * Add a discovered class from mesh broadcast
     * Called when CLASS_ANNOUNCE message is received
     */
    fun addDiscoveredClass(
        id: String,
        name: String,
        code: String,
        passwordHash: String,
        passwordSalt: String,
        encryptionKey: String,
        teacherId: String,
        teacherName: String,
        subject: String
    ) {
        // Don't add if already exists (permanent or discovered)
        if (_classes.value.any { it.code.equals(code, ignoreCase = true) }) {
            Log.d(TAG, "Class $code already exists in permanent storage, skipping")
            return
        }
        
        // Remove existing discovery with same code
        _discoveredClasses.value = _discoveredClasses.value.filter { 
            !it.code.equals(code, ignoreCase = true) 
        }
        
        val discovered = DiscoveredClass(
            id = id,
            name = name,
            code = code,
            passwordHash = passwordHash,
            passwordSalt = passwordSalt,
            encryptionKey = encryptionKey,
            teacherId = teacherId,
            teacherName = teacherName,
            subject = subject
        )
        
        _discoveredClasses.value = _discoveredClasses.value + discovered
        Log.d(TAG, "Added discovered class: $name ($code) - expires in 10 mins")
    }
    
    /**
     * Get a discovered class by code
     */
    fun getDiscoveredClass(code: String): DiscoveredClass? {
        return _discoveredClasses.value.find { it.code.equals(code, ignoreCase = true) }
    }
    
    /**
     * Join a discovered class with password verification
     * On success, moves class to permanent storage and creates enrollment
     */
    fun joinDiscoveredClass(
        code: String,
        password: String,
        studentId: String,
        studentName: String,
        studentEmail: String
    ): Result<ClassEnrollment> {
        val discovered = getDiscoveredClass(code)
            ?: return Result.failure(Exception("Class not found. Make sure you're in range of the teacher."))
        
        // Verify password
        val passwordHash = hashPassword(password, discovered.passwordSalt)
        if (passwordHash != discovered.passwordHash) {
            return Result.failure(Exception("Incorrect password"))
        }
        
        // Move to permanent storage
        val classroom = Classroom(
            id = discovered.id,
            name = discovered.name,
            code = discovered.code,
            passwordHash = discovered.passwordHash,
            passwordSalt = discovered.passwordSalt,
            encryptionKey = discovered.encryptionKey,
            teacherId = discovered.teacherId,
            teacherName = discovered.teacherName,
            subject = discovered.subject
        )
        
        // Add to permanent classes
        val updatedClasses = _classes.value.toMutableList()
        if (!updatedClasses.any { it.id == classroom.id }) {
            updatedClasses.add(classroom)
            _classes.value = updatedClasses
            saveClasses()
        }
        
        // Remove from discovered
        _discoveredClasses.value = _discoveredClasses.value.filter { it.id != discovered.id }
        
        // Create enrollment (auto-approved since password was correct)
        val enrollment = ClassEnrollment(
            id = UUID.randomUUID().toString(),
            classId = classroom.id,
            classCode = classroom.code,
            studentId = studentId,
            studentName = studentName,
            studentEmail = studentEmail,
            status = EnrollmentStatus.APPROVED, // Auto-approved with correct password
            approvedAt = System.currentTimeMillis()
        )
        
        val updatedEnrollments = _enrollments.value.toMutableList()
        updatedEnrollments.add(enrollment)
        _enrollments.value = updatedEnrollments
        saveEnrollments()
        
        // Set as active class
        _currentClassId.value = classroom.id
        saveCurrentClass()
        
        Log.d(TAG, "Student $studentName joined class ${classroom.name} (auto-approved)")
        return Result.success(enrollment)
    }
    
    /**
     * Get class info for mesh broadcast
     * Returns serializable data for CLASS_ANNOUNCE message
     */
    fun getClassBroadcastData(classId: String): Map<String, String>? {
        val classroom = findClassById(classId) ?: return null
        return mapOf(
            "id" to classroom.id,
            "name" to classroom.name,
            "code" to classroom.code,
            "passwordHash" to classroom.passwordHash,
            "passwordSalt" to classroom.passwordSalt,
            "encryptionKey" to classroom.encryptionKey,
            "teacherId" to classroom.teacherId,
            "teacherName" to classroom.teacherName,
            "subject" to classroom.subject
        )
    }
}