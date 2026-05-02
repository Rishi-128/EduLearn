package com.bitchat.android.data

import android.content.Context
import com.bitchat.android.mesh.BluetoothMeshService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.CoroutineContext

/**
 * Service to detect and track student presence across different devices using Bluetooth mesh
 * 
 * SIMPLIFIED APPROACH:
 * - Uses nickname format: "StudentName (Student)" or "TeacherName (Teacher)"
 * - Detects students by parsing nicknames from mesh peers
 * - Updates UserManager with online/offline status
 */
class StudentPresenceService(
    private val context: Context,
    private val userManager: UserManager
) : CoroutineScope {

    private val job = Job()
    override val coroutineContext: CoroutineContext = Dispatchers.Default + job

    private val _nearbyStudents = MutableStateFlow<List<StudentPresence>>(emptyList())
    val nearbyStudents: StateFlow<List<StudentPresence>> = _nearbyStudents.asStateFlow()

    private var meshService: BluetoothMeshService? = null
    private var isScanning = false

    /**
     * Initialize with mesh service
     */
    fun initialize(meshService: BluetoothMeshService) {
        this.meshService = meshService
        startPresenceMonitoring()
    }

    /**
     * Start monitoring for student presence via Bluetooth mesh
     */
    private fun startPresenceMonitoring() {
        if (isScanning) return
        isScanning = true

        launch {
            while (isScanning) {
                try {
                    // Get all verified peers from mesh (these are active/online)
                    val verifiedPeers = meshService?.getVerifiedPeersForEducation() ?: emptyMap()
                    
                    val students = mutableListOf<StudentPresence>()
                    
                    verifiedPeers.forEach { (peerId, peerInfo) ->
                        val nickname = peerInfo.nickname
                        
                        // Check if this peer is a student by nickname format
                        // Expected format: "Name (Student)" or just check if registered
                        val isStudent = nickname.contains("(Student)", ignoreCase = true) ||
                                       userManager.isRegisteredStudent(nickname)
                        
                        if (isStudent) {
                            // Extract actual name (remove role suffix if present)
                            val name = nickname.replace(Regex("\\s*\\(Student\\)", RegexOption.IGNORE_CASE), "").trim()
                            
                            // Try to find or create matching user in UserManager
                            var matchingUser = userManager.findUserByName(name)
                            
                            // If student not registered locally (cross-device scenario), register them
                            if (matchingUser == null) {
                                val email = "${name.lowercase().replace(" ", ".")}@detected.student"
                                val registerResult = userManager.registerUser(name, email, UserRole.STUDENT)
                                matchingUser = registerResult.getOrNull()
                            }
                            
                            students.add(
                                StudentPresence(
                                    userId = matchingUser?.id ?: peerId,
                                    name = name,
                                    email = matchingUser?.email ?: "",
                                    peerId = peerId,
                                    lastSeen = peerInfo.lastSeen,
                                    isOnline = true,
                                    deviceName = "Bluetooth Device"
                                )
                            )
                            
                            // Update UserManager about this student
                            if (matchingUser != null) {
                                userManager.updateUserOnlineStatus(matchingUser, true)
                            }
                        }
                    }
                    
                    // Update nearby students list
                    val previousStudents = _nearbyStudents.value
                    _nearbyStudents.value = students
                    
                    // Mark students who disappeared as offline
                    previousStudents.forEach { prevStudent ->
                        if (!students.any { it.peerId == prevStudent.peerId }) {
                            val user = userManager.findUserByName(prevStudent.name)
                            if (user != null) {
                                userManager.updateUserOnlineStatus(user, false)
                            }
                        }
                    }
                    
                } catch (e: Exception) {
                    // Continue monitoring even if error occurs
                }
                
                // Check every 3 seconds for responsive updates
                delay(3000)
            }
        }
    }

    /**
     * Stop monitoring
     */
    fun stop() {
        isScanning = false
        job.cancel()
    }
}

/**
 * Represents a student detected nearby via Bluetooth
 */
data class StudentPresence(
    val userId: String,
    val name: String,
    val email: String,
    val peerId: String,
    val lastSeen: Long,
    val isOnline: Boolean,
    val deviceName: String
)
