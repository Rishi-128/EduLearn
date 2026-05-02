package com.bitchat.android.wifidirect

import android.util.Log
import com.bitchat.android.mesh.BluetoothMeshService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * WiFi Direct Group Owner Permission Manager
 * 
 * Handles permission requests for students who want to become WiFi Direct Group Owners.
 * Uses Bluetooth Mesh network for communication between students and teachers.
 * 
 * Flow:
 * 1. Teachers are Group Owners by DEFAULT (no permission needed)
 * 2. Students who want to share files as GO must REQUEST permission
 * 3. Request goes to nearest teacher via Bluetooth mesh
 * 4. Teacher APPROVES or DENIES the request
 * 5. Student receives response and can proceed if approved
 */
class WifiDirectPermissionManager(
    private val meshService: BluetoothMeshService
) {
    companion object {
        private const val TAG = "WifiDirectPermission"
        
        // Message prefixes for mesh communication
        const val PREFIX_GO_REQUEST = "[WIFI_GO_REQUEST]"
        const val PREFIX_GO_APPROVE = "[WIFI_GO_APPROVE]"
        const val PREFIX_GO_DENY = "[WIFI_GO_DENY]"
        const val PREFIX_GO_REVOKE = "[WIFI_GO_REVOKE]"
        
        // Permission timeout
        const val PERMISSION_TIMEOUT_MS = 60000L // 1 minute
        const val PERMISSION_VALID_DURATION_MS = 30 * 60 * 1000L // 30 minutes
    }
    
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Pending requests (for teachers to see)
    private val _pendingRequests = MutableStateFlow<List<GOPermissionRequest>>(emptyList())
    val pendingRequests: StateFlow<List<GOPermissionRequest>> = _pendingRequests.asStateFlow()
    
    // Approved students (students with GO permission)
    private val _approvedStudents = MutableStateFlow<Set<String>>(emptySet())
    val approvedStudents: StateFlow<Set<String>> = _approvedStudents.asStateFlow()
    
    // Current request status (for students)
    private val _myRequestStatus = MutableStateFlow<GORequestStatus>(GORequestStatus.None)
    val myRequestStatus: StateFlow<GORequestStatus> = _myRequestStatus.asStateFlow()
    
    // Callback for when permission is granted/denied
    var onPermissionResult: ((Boolean, String?) -> Unit)? = null
    
    // Callback for teachers when new request arrives
    var onNewRequest: ((GOPermissionRequest) -> Unit)? = null
    
    /**
     * Initialize the permission manager
     * Call this after mesh service is ready
     */
    fun initialize() {
        Log.d(TAG, "WiFi Direct Permission Manager initialized")
        // Listen for mesh messages related to GO permissions
        startListeningForMessages()
    }
    
    /**
     * Cleanup resources
     */
    fun cleanup() {
        scope.cancel()
        _pendingRequests.value = emptyList()
        _approvedStudents.value = emptySet()
        _myRequestStatus.value = GORequestStatus.None
    }
    
    // ==================== STUDENT FUNCTIONS ====================
    
    /**
     * Request permission to become Group Owner (for students)
     * Finds nearest teacher via mesh and sends request
     */
    fun requestGOPermission(reason: String = "Share files with classmates"): Boolean {
        val nearestTeacher = findNearestTeacher()
        
        if (nearestTeacher == null) {
            Log.w(TAG, "No teacher found in mesh network")
            _myRequestStatus.value = GORequestStatus.Failed("No teacher nearby")
            return false
        }
        
        val requestId = UUID.randomUUID().toString().take(8)
        val myPeerId = meshService.myPeerID
        val myName = meshService.myPeerID.take(8) // Use peer ID as fallback name
        
        // Format: [WIFI_GO_REQUEST]requestId|studentPeerId|studentName|reason
        val message = "$PREFIX_GO_REQUEST$requestId|$myPeerId|$myName|$reason"
        
        // Send via mesh to the teacher
        meshService.sendPrivateMessage(
            content = message,
            recipientPeerID = nearestTeacher.peerId,
            recipientNickname = nearestTeacher.name
        )
        
        _myRequestStatus.value = GORequestStatus.Pending(
            requestId = requestId,
            teacherName = nearestTeacher.name,
            timestamp = System.currentTimeMillis()
        )
        
        Log.d(TAG, "Sent GO permission request to ${nearestTeacher.name}")
        
        // Start timeout timer
        scope.launch {
            delay(PERMISSION_TIMEOUT_MS)
            if (_myRequestStatus.value is GORequestStatus.Pending) {
                _myRequestStatus.value = GORequestStatus.Failed("Request timed out")
                onPermissionResult?.invoke(false, "Request timed out")
            }
        }
        
        return true
    }
    
    /**
     * Check if student has valid GO permission
     */
    fun hasGOPermission(): Boolean {
        val status = _myRequestStatus.value
        return status is GORequestStatus.Approved && 
               System.currentTimeMillis() - status.timestamp < PERMISSION_VALID_DURATION_MS
    }
    
    /**
     * Find a teacher in the mesh network
     * Note: Since PeerInfo doesn't track roles, we return the first connected peer
     * Teachers will respond to permission requests; students will ignore them
     * In a real implementation, nickname patterns or a role broadcast could be used
     */
    private fun findNearestTeacher(): TeacherInfo? {
        val verifiedPeers = meshService.getVerifiedPeersForEducation()
        
        if (verifiedPeers.isEmpty()) return null
        
        // Return the first peer found - teachers will respond, students will ignore
        // In practice, you might filter by nickname pattern like "Teacher_*" or "Prof_*"
        val (peerId, peerInfo) = verifiedPeers.entries.first()
        return TeacherInfo(peerId, peerInfo.nickname)
    }
    
    // ==================== TEACHER FUNCTIONS ====================
    
    /**
     * Approve a student's GO permission request (for teachers)
     */
    fun approveRequest(request: GOPermissionRequest) {
        // Format: [WIFI_GO_APPROVE]requestId|studentPeerId|teacherName
        val myName = meshService.myPeerID.take(8) // Use peer ID as fallback
        val message = "$PREFIX_GO_APPROVE${request.requestId}|${request.studentPeerId}|$myName"
        
        meshService.sendPrivateMessage(
            content = message,
            recipientPeerID = request.studentPeerId,
            recipientNickname = request.studentName
        )
        
        // Add to approved list
        _approvedStudents.value = _approvedStudents.value + request.studentPeerId
        
        // Remove from pending
        _pendingRequests.value = _pendingRequests.value.filter { it.requestId != request.requestId }
        
        Log.d(TAG, "Approved GO request from ${request.studentName}")
    }
    
    /**
     * Deny a student's GO permission request (for teachers)
     */
    fun denyRequest(request: GOPermissionRequest, reason: String = "Request denied") {
        // Format: [WIFI_GO_DENY]requestId|studentPeerId|reason
        val message = "$PREFIX_GO_DENY${request.requestId}|${request.studentPeerId}|$reason"
        
        meshService.sendPrivateMessage(
            content = message,
            recipientPeerID = request.studentPeerId,
            recipientNickname = request.studentName
        )
        
        // Remove from pending
        _pendingRequests.value = _pendingRequests.value.filter { it.requestId != request.requestId }
        
        Log.d(TAG, "Denied GO request from ${request.studentName}: $reason")
    }
    
    /**
     * Revoke a previously granted permission (for teachers)
     */
    fun revokePermission(studentPeerId: String, studentName: String) {
        val message = "$PREFIX_GO_REVOKE$studentPeerId"
        
        meshService.sendPrivateMessage(
            content = message,
            recipientPeerID = studentPeerId,
            recipientNickname = studentName
        )
        
        _approvedStudents.value = _approvedStudents.value - studentPeerId
        
        Log.d(TAG, "Revoked GO permission for $studentName")
    }
    
    // ==================== MESSAGE HANDLING ====================
    
    /**
     * Start listening for mesh messages related to GO permissions
     */
    private fun startListeningForMessages() {
        // This would hook into the mesh service's message handling
        // For now, we provide a method to be called when messages arrive
    }
    
    /**
     * Process incoming mesh message (call this from mesh message handler)
     */
    fun processIncomingMessage(senderId: String, senderName: String, content: String) {
        when {
            content.startsWith(PREFIX_GO_REQUEST) -> {
                handleGORequest(senderId, senderName, content)
            }
            content.startsWith(PREFIX_GO_APPROVE) -> {
                handleGOApproval(content)
            }
            content.startsWith(PREFIX_GO_DENY) -> {
                handleGODenial(content)
            }
            content.startsWith(PREFIX_GO_REVOKE) -> {
                handleGORevoke(content)
            }
        }
    }
    
    private fun handleGORequest(senderId: String, senderName: String, content: String) {
        // Parse: [WIFI_GO_REQUEST]requestId|studentPeerId|studentName|reason
        val data = content.removePrefix(PREFIX_GO_REQUEST)
        val parts = data.split("|")
        
        if (parts.size >= 4) {
            val request = GOPermissionRequest(
                requestId = parts[0],
                studentPeerId = parts[1],
                studentName = parts[2],
                reason = parts[3],
                timestamp = System.currentTimeMillis()
            )
            
            _pendingRequests.value = _pendingRequests.value + request
            onNewRequest?.invoke(request)
            
            Log.d(TAG, "Received GO request from ${request.studentName}: ${request.reason}")
        }
    }
    
    private fun handleGOApproval(content: String) {
        // Parse: [WIFI_GO_APPROVE]requestId|studentPeerId|teacherName
        val data = content.removePrefix(PREFIX_GO_APPROVE)
        val parts = data.split("|")
        
        if (parts.size >= 3) {
            val requestId = parts[0]
            val teacherName = parts[2]
            
            _myRequestStatus.value = GORequestStatus.Approved(
                requestId = requestId,
                teacherName = teacherName,
                timestamp = System.currentTimeMillis()
            )
            
            onPermissionResult?.invoke(true, "Approved by $teacherName")
            Log.d(TAG, "GO permission approved by $teacherName")
        }
    }
    
    private fun handleGODenial(content: String) {
        // Parse: [WIFI_GO_DENY]requestId|studentPeerId|reason
        val data = content.removePrefix(PREFIX_GO_DENY)
        val parts = data.split("|")
        
        if (parts.size >= 3) {
            val reason = parts[2]
            
            _myRequestStatus.value = GORequestStatus.Denied(reason)
            onPermissionResult?.invoke(false, reason)
            Log.d(TAG, "GO permission denied: $reason")
        }
    }
    
    private fun handleGORevoke(content: String) {
        // Parse: [WIFI_GO_REVOKE]studentPeerId
        val studentPeerId = content.removePrefix(PREFIX_GO_REVOKE)
        
        if (studentPeerId == meshService.myPeerID) {
            _myRequestStatus.value = GORequestStatus.Revoked
            onPermissionResult?.invoke(false, "Permission revoked by teacher")
            Log.d(TAG, "GO permission revoked")
        }
    }
}

// ==================== DATA CLASSES ====================

data class GOPermissionRequest(
    val requestId: String,
    val studentPeerId: String,
    val studentName: String,
    val reason: String,
    val timestamp: Long
)

data class TeacherInfo(
    val peerId: String,
    val name: String
)

sealed class GORequestStatus {
    object None : GORequestStatus()
    
    data class Pending(
        val requestId: String,
        val teacherName: String,
        val timestamp: Long
    ) : GORequestStatus()
    
    data class Approved(
        val requestId: String,
        val teacherName: String,
        val timestamp: Long
    ) : GORequestStatus()
    
    data class Denied(val reason: String) : GORequestStatus()
    
    object Revoked : GORequestStatus()
    
    data class Failed(val error: String) : GORequestStatus()
}
