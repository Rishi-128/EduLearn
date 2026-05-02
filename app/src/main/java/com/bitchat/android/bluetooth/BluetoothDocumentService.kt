package com.bitchat.android.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.bitchat.android.mesh.FileCompressionUtil
import com.bitchat.android.data.ClassManager
import com.bitchat.android.data.UserManager
import com.bitchat.android.data.UserRole
import android.util.Log
import java.io.*
import java.util.*

/**
 * Real Bluetooth Document Sharing Service
 * Handles actual Bluetooth discovery and file transfer
 * 
 * SECURITY: Class-based filtering ensures documents are only shared within enrolled classes.
 * Documents are tagged with classId and filtered on receive.
 */
class BluetoothDocumentService(private val context: Context) {
    
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val classManager = ClassManager.getInstance(context)
    private val userManager = UserManager.getInstance(context)
    
    // Service UUID for educational document sharing
    private val SERVICE_UUID = UUID.fromString("12345678-1234-5678-9abc-123456789abc")
    private val SERVICE_NAME = "EduShare"
    
    private var serverSocket: BluetoothServerSocket? = null
    private var clientSocket: BluetoothSocket? = null
    private var isDiscovering = false
    
    // State flows
    private val _discoveredDevices = MutableStateFlow<List<BluetoothDeviceInfo>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDeviceInfo>> = _discoveredDevices.asStateFlow()
    
    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()
    
    private val _sharingStatus = MutableStateFlow<SharingStatus>(SharingStatus.Idle)
    val sharingStatus: StateFlow<SharingStatus> = _sharingStatus.asStateFlow()
    
    private val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFile>> = _receivedFiles.asStateFlow()
    
    // Bluetooth discovery receiver
    private val discoveryReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let { addDiscoveredDevice(it) }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    _isScanning.value = false
                    isDiscovering = false
                }
            }
        }
    }
    
    init {
        // Register for broadcasts
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        context.registerReceiver(discoveryReceiver, filter)
    }
    
    /**
     * Check if Bluetooth is available and enabled
     */
    fun isBluetoothAvailable(): Boolean {
        return bluetoothAdapter != null && bluetoothAdapter.isEnabled
    }
    
    /**
     * Check if necessary permissions are granted
     */
    fun hasRequiredPermissions(): Boolean {
        val hasLocationPermission = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            // Android 12+ (API 31+) - use new Bluetooth permissions
            val hasBluetoothConnect = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val hasBluetoothScan = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            hasBluetoothConnect && hasBluetoothScan && hasLocationPermission
        } else {
            // Android 11 and below - use legacy permissions
            val hasBluetooth = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED
            val hasBluetoothAdmin = ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED
            hasBluetooth && hasBluetoothAdmin && hasLocationPermission
        }
    }
    
    /**
     * Start scanning for nearby devices
     */
    @SuppressLint("MissingPermission")
    fun startDiscovery(): Boolean {
        if (!isBluetoothAvailable() || !hasRequiredPermissions()) {
            return false
        }
        
        if (isDiscovering) {
            bluetoothAdapter?.cancelDiscovery()
        }
        
        _discoveredDevices.value = emptyList()
        _isScanning.value = true
        isDiscovering = true
        
        return bluetoothAdapter?.startDiscovery() ?: false
    }
    
    /**
     * Stop scanning
     */
    @SuppressLint("MissingPermission")
    fun stopDiscovery() {
        if (isDiscovering) {
            bluetoothAdapter?.cancelDiscovery()
            _isScanning.value = false
            isDiscovering = false
        }
    }
    
    /**
     * Start server to listen for incoming connections
     */
    @SuppressLint("MissingPermission")
    fun startServer() {
        if (!isBluetoothAvailable() || !hasRequiredPermissions()) {
            return
        }
        
        Thread {
            try {
                serverSocket = bluetoothAdapter?.listenUsingRfcommWithServiceRecord(SERVICE_NAME, SERVICE_UUID)
                
                while (true) {
                    val socket = serverSocket?.accept()
                    socket?.let {
                        handleIncomingConnection(it)
                    }
                }
            } catch (e: IOException) {
                // Server stopped or error occurred
            }
        }.start()
    }
    
    /**
     * Share a document with a specific device
     * 
     * SECURITY: Document is tagged with classId for class-based filtering on receive
     */
    @SuppressLint("MissingPermission")
    fun shareDocument(device: BluetoothDeviceInfo, document: DocumentToShare) {
        if (!isBluetoothAvailable() || !hasRequiredPermissions()) {
            _sharingStatus.value = SharingStatus.Error("Bluetooth not available")
            return
        }
        
        _sharingStatus.value = SharingStatus.Connecting(device.name)
        
        // Get active class for document tagging
        val activeClass = classManager.getActiveClass()
        val classId = document.classId ?: activeClass?.id ?: ""
        val classCode = document.classCode ?: activeClass?.code ?: ""
        
        Thread {
            try {
                val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
                clientSocket = bluetoothDevice?.createRfcommSocketToServiceRecord(SERVICE_UUID)
                
                clientSocket?.connect()
                
                _sharingStatus.value = SharingStatus.Sending(device.name, 0f)
                
                val outputStream = clientSocket?.outputStream ?: throw IOException("Output stream is null")
                val dataOutputStream = DataOutputStream(outputStream)
                
                // Encrypt content if class is set
                val contentToProcess: ByteArray = if (classId.isNotBlank()) {
                    try {
                        val encrypted = classManager.encryptForClass(classId, document.content)
                        if (encrypted != null) {
                            Log.d("BluetoothDoc", "Encrypted document for class $classId")
                            encrypted
                        } else {
                            Log.e("BluetoothDoc", "Encryption returned null, sending unencrypted")
                            document.content
                        }
                    } catch (e: Exception) {
                        Log.e("BluetoothDoc", "Encryption failed, sending unencrypted", e)
                        document.content
                    }
                } else {
                    document.content
                }
                
                // Compress file content if beneficial
                val shouldCompress = FileCompressionUtil.shouldCompress(document.name)
                val contentToSend: ByteArray = if (shouldCompress) {
                    Log.d("BluetoothDoc", "Compressing ${document.name} (${contentToProcess.size} bytes)")
                    try {
                        val compressed = FileCompressionUtil.compressBytes(contentToProcess)
                        Log.d("BluetoothDoc", "Compressed to ${compressed.size} bytes")
                        compressed
                    } catch (e: Exception) {
                        Log.e("BluetoothDoc", "Compression failed, sending uncompressed", e)
                        contentToProcess
                    }
                } else {
                    Log.d("BluetoothDoc", "Skipping compression for ${document.name}")
                    contentToProcess
                }
                
                // Send metadata including class info for filtering
                dataOutputStream.writeUTF(document.name)
                dataOutputStream.writeUTF(document.type)
                dataOutputStream.writeUTF(document.senderName)
                dataOutputStream.writeBoolean(shouldCompress && contentToSend !== contentToProcess)
                dataOutputStream.writeInt(contentToSend.size)
                // SECURITY: Send class info for filtering on receive
                dataOutputStream.writeUTF(classId)
                dataOutputStream.writeUTF(classCode)
                dataOutputStream.writeBoolean(classId.isNotBlank()) // isEncrypted flag
                dataOutputStream.flush()
                
                // Send file content in chunks
                val buffer = ByteArray(8192)
                val inputStream = ByteArrayInputStream(contentToSend)
                var totalBytes = contentToSend.size
                var sentBytes = 0
                var bytesRead: Int
                
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    dataOutputStream.write(buffer, 0, bytesRead)
                    sentBytes += bytesRead
                    val progress = sentBytes.toFloat() / totalBytes
                    _sharingStatus.value = SharingStatus.Sending(device.name, progress)
                }
                
                dataOutputStream.flush()
                Log.d("BluetoothDoc", "Successfully sent ${document.name} ($sentBytes bytes, class: $classId)")
                _sharingStatus.value = SharingStatus.Success(device.name)
                
            } catch (e: IOException) {
                Log.e("BluetoothDoc", "Failed to share document", e)
                _sharingStatus.value = SharingStatus.Error("Failed to share: ${e.message}")
            } finally {
                try {
                    clientSocket?.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }.start()
    }
    
    /**
     * Handle incoming connection and receive files
     * 
     * SECURITY: Validates class enrollment before accepting documents.
     * Documents from non-enrolled classes are discarded.
     */
    private fun handleIncomingConnection(socket: BluetoothSocket) {
        Thread {
            try {
                val inputStream = socket.inputStream
                val dataInputStream = DataInputStream(inputStream)
                
                // Receive metadata
                val fileName = dataInputStream.readUTF()
                val fileType = dataInputStream.readUTF()
                val senderName = dataInputStream.readUTF()
                val isCompressed = dataInputStream.readBoolean()
                val fileSize = dataInputStream.readInt()
                
                // SECURITY: Receive class info for filtering
                val classId = try { dataInputStream.readUTF() } catch (e: Exception) { "" }
                val classCode = try { dataInputStream.readUTF() } catch (e: Exception) { "" }
                val isEncrypted = try { dataInputStream.readBoolean() } catch (e: Exception) { false }
                
                Log.d("BluetoothDoc", "Receiving $fileName ($fileSize bytes, compressed: $isCompressed, class: $classId)")
                
                // SECURITY CHECK: Validate class enrollment before accepting
                val currentUser = userManager.getCurrentUser()
                if (currentUser != null && currentUser.role == UserRole.STUDENT && classId.isNotBlank()) {
                    val isEnrolled = classManager.isStudentApproved(classId, currentUser.id)
                    
                    if (!isEnrolled) {
                        Log.w("BluetoothDoc", "SECURITY: Discarding document '$fileName' - student not enrolled in class $classId")
                        // Still read the data to clear the stream, but don't save
                        val discardData = ByteArray(fileSize)
                        dataInputStream.readFully(discardData)
                        return@Thread
                    }
                    
                    Log.d("BluetoothDoc", "SECURITY: Student verified for class $classId, accepting document")
                }
                
                // Receive file content
                val receivedData = ByteArray(fileSize)
                var totalRead = 0
                
                while (totalRead < fileSize) {
                    val remaining = fileSize - totalRead
                    val read = dataInputStream.read(receivedData, totalRead, remaining)
                    if (read == -1) break
                    totalRead += read
                }
                
                Log.d("BluetoothDoc", "Received $totalRead bytes")
                
                // Decompress if needed
                var processedContent = if (isCompressed) {
                    try {
                        Log.d("BluetoothDoc", "Decompressing file...")
                        val decompressed = FileCompressionUtil.decompressBytes(receivedData)
                        Log.d("BluetoothDoc", "Decompressed to ${decompressed.size} bytes")
                        decompressed
                    } catch (e: Exception) {
                        Log.e("BluetoothDoc", "Decompression failed, using raw data", e)
                        receivedData
                    }
                } else {
                    receivedData
                }
                
                // Decrypt if encrypted
                val finalContent: ByteArray = if (isEncrypted && classId.isNotBlank()) {
                    try {
                        Log.d("BluetoothDoc", "Decrypting document for class $classId...")
                        val decrypted = classManager.decryptForClass(classId, processedContent)
                        if (decrypted != null) {
                            Log.d("BluetoothDoc", "Decrypted to ${decrypted.size} bytes")
                            decrypted
                        } else {
                            Log.e("BluetoothDoc", "Decryption returned null - document may be corrupted or from wrong class")
                            return@Thread
                        }
                    } catch (e: Exception) {
                        Log.e("BluetoothDoc", "Decryption failed - document may be corrupted or from wrong class", e)
                        // Cannot decrypt - discard document
                        return@Thread
                    }
                } else {
                    processedContent
                }
                
                val receivedFile = ReceivedFile(
                    id = UUID.randomUUID().toString(),
                    name = fileName,
                    type = fileType,
                    content = finalContent,
                    senderName = senderName,
                    receivedAt = System.currentTimeMillis(),
                    classId = classId,
                    classCode = classCode
                )
                
                // Add to received files
                val currentFiles = _receivedFiles.value.toMutableList()
                currentFiles.add(receivedFile)
                _receivedFiles.value = currentFiles
                
                // Save to local storage
                saveReceivedFile(receivedFile)
                Log.d("BluetoothDoc", "Successfully received and saved ${receivedFile.name}")
                
            } catch (e: Exception) {
                Log.e("BluetoothDoc", "Error receiving file", e)
            } finally {
                try {
                    socket.close()
                } catch (e: IOException) {
                    // Ignore
                }
            }
        }.start()
    }
    
    /**
     * Add discovered device to list
     */
    @SuppressLint("MissingPermission")
    private fun addDiscoveredDevice(device: BluetoothDevice) {
        if (!hasRequiredPermissions()) return
        
        val deviceInfo = BluetoothDeviceInfo(
            name = device.name ?: "Unknown Device",
            address = device.address,
            isConnected = device.bondState == BluetoothDevice.BOND_BONDED
        )
        
        val currentDevices = _discoveredDevices.value.toMutableList()
        if (!currentDevices.any { it.address == deviceInfo.address }) {
            currentDevices.add(deviceInfo)
            _discoveredDevices.value = currentDevices
        }
    }
    
    /**
     * Save received file to local storage
     */
    private fun saveReceivedFile(file: ReceivedFile) {
        try {
            val directory = File(context.filesDir, "received_documents")
            if (!directory.exists()) {
                directory.mkdirs()
            }
            
            val localFile = File(directory, "${file.id}_${file.name}")
            localFile.writeBytes(file.content)
            
        } catch (e: IOException) {
            // Handle error
        }
    }
    
    /**
     * Get list of paired devices
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDeviceInfo> {
        if (!isBluetoothAvailable() || !hasRequiredPermissions()) {
            return emptyList()
        }
        
        return bluetoothAdapter?.bondedDevices?.map { device ->
            BluetoothDeviceInfo(
                name = device.name ?: "Unknown Device",
                address = device.address,
                isConnected = true
            )
        } ?: emptyList()
    }
    
    /**
     * Clean up resources
     */
    fun cleanup() {
        try {
            stopDiscovery()
            serverSocket?.close()
            clientSocket?.close()
            context.unregisterReceiver(discoveryReceiver)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }
}

// Data classes
data class BluetoothDeviceInfo(
    val name: String,
    val address: String,
    val isConnected: Boolean,
    val lastSeen: Long = System.currentTimeMillis()
)

data class DocumentToShare(
    val name: String,
    val type: String,
    val content: ByteArray,
    val senderName: String,
    val metadata: Map<String, String> = emptyMap(),
    // Class-based security: classId for filtering
    val classId: String? = null,
    val classCode: String? = null
) : Serializable

data class ReceivedFile(
    val id: String,
    val name: String,
    val type: String,
    val content: ByteArray,
    val senderName: String,
    val receivedAt: Long,
    // Class-based security: classId for filtering
    val classId: String = "",
    val classCode: String = ""
)

sealed class SharingStatus {
    object Idle : SharingStatus()
    data class Connecting(val deviceName: String) : SharingStatus()
    data class Sending(val deviceName: String, val progress: Float) : SharingStatus()
    data class Success(val deviceName: String) : SharingStatus()
    data class Error(val message: String) : SharingStatus()
}