package com.bitchat.android.wifidirect

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.p2p.*
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import java.io.*
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Reusable WiFi Direct (P2P) Manager for high-speed offline file transfers
 * Supports video and audio file sharing between teacher and students
 * 
 * Requirements:
 * - Android 8.0+ (API 26+)
 * - WiFi Direct hardware support
 * - Runtime permissions: ACCESS_FINE_LOCATION (API ≤32), NEARBY_WIFI_DEVICES (API 33+)
 */
class WifiDirectManager(private val context: Context) {
    
    companion object {
        private const val TAG = "WifiDirectManager"
        private const val FILE_TRANSFER_PORT = 8988
        private const val SOCKET_TIMEOUT = 30000 // 30 seconds
        private const val BUFFER_SIZE = 8192 // 8KB buffer for file transfer
    }
    
    // WiFi P2P components
    private val wifiP2pManager: WifiP2pManager? by lazy {
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager
    }
    private var channel: WifiP2pManager.Channel? = null
    
    // State tracking
    private var isP2pEnabled = false
    private var isDiscovering = false
    private var currentConnectionInfo: WifiP2pInfo? = null
    private var fileServerJob: Job? = null
    
    // Callback interface
    var callback: WifiDirectCallback? = null
    
    // Broadcast receiver for WiFi P2P events
    private val p2pReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                    // WiFi P2P enabled/disabled
                    val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                    isP2pEnabled = state == WifiP2pManager.WIFI_P2P_STATE_ENABLED
                    Log.d(TAG, "WiFi P2P state changed: enabled=$isP2pEnabled")
                    callback?.onP2pSupported(isP2pEnabled)
                }
                
                WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                    // Peer list has changed - request updated list
                    Log.d(TAG, "Peers changed, requesting peer list")
                    requestPeers()
                }
                
                WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                    // Connection state has changed
                    Log.d(TAG, "Connection changed")
                    requestConnectionInfo()
                }
                
                WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                    // This device's details have changed
                    val device = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(
                            WifiP2pManager.EXTRA_WIFI_P2P_DEVICE,
                            WifiP2pDevice::class.java
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE)
                    }
                    Log.d(TAG, "This device changed: ${device?.deviceName}")
                }
            }
        }
    }
    
    private val intentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }
    
    /**
     * Initialize WiFi Direct manager
     * Must be called before using any other methods
     */
    fun initialize() {
        if (wifiP2pManager == null) {
            Log.e(TAG, "WiFi P2P not supported on this device")
            callback?.onP2pSupported(false)
            return
        }
        
        channel = wifiP2pManager?.initialize(context, context.mainLooper, null)
        context.registerReceiver(p2pReceiver, intentFilter)
        Log.d(TAG, "WiFi Direct manager initialized")
        callback?.onP2pSupported(true)
    }
    
    /**
     * Clean up resources
     * Call this when done using WiFi Direct
     */
    fun cleanup() {
        stopDiscovery()
        disconnect()
        fileServerJob?.cancel()
        
        try {
            context.unregisterReceiver(p2pReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
        
        channel?.close()
        channel = null
        Log.d(TAG, "WiFi Direct manager cleaned up")
    }
    
    /**
     * Check if WiFi P2P is supported on this device
     */
    fun isP2pSupported(): Boolean {
        return wifiP2pManager != null
    }
    
    /**
     * Start discovering nearby WiFi Direct peers
     * Requires location permission
     */
    fun startDiscovery() {
        if (channel == null) {
            Log.e(TAG, "Channel not initialized")
            callback?.onError("WiFi Direct not initialized")
            return
        }
        
        if (isDiscovering) {
            Log.d(TAG, "Already discovering")
            return
        }
        
        wifiP2pManager?.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                isDiscovering = true
                Log.d(TAG, "Peer discovery started")
            }
            
            override fun onFailure(reasonCode: Int) {
                isDiscovering = false
                val reason = getFailureReason(reasonCode)
                Log.e(TAG, "Peer discovery failed: $reason")
                callback?.onError("Discovery failed: $reason")
            }
        })
    }
    
    /**
     * Stop peer discovery
     */
    fun stopDiscovery() {
        if (!isDiscovering) return
        
        wifiP2pManager?.stopPeerDiscovery(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                isDiscovering = false
                Log.d(TAG, "Peer discovery stopped")
            }
            
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Failed to stop discovery: ${getFailureReason(reasonCode)}")
            }
        })
    }
    
    /**
     * Create WiFi Direct group as Group Owner (TEACHER ONLY)
     * Teachers are always Group Owners by default - guaranteed GO status
     * Students must request permission via Bluetooth mesh before calling this
     * 
     * @param onGroupCreated Callback when group is successfully created
     */
    fun createGroupAsOwner(onGroupCreated: (() -> Unit)? = null) {
        if (channel == null) {
            callback?.onError("WiFi Direct not initialized")
            return
        }
        
        wifiP2pManager?.createGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "✅ Group created - this device is now Group Owner")
                onGroupCreated?.invoke()
            }
            
            override fun onFailure(reasonCode: Int) {
                val reason = getFailureReason(reasonCode)
                Log.e(TAG, "Failed to create group: $reason")
                callback?.onError("Failed to create group: $reason")
            }
        })
    }
    
    /**
     * Connect to a specific WiFi Direct peer as CLIENT (non-GO)
     * Use groupOwnerIntent = 0 to avoid becoming GO
     * Students should use this to connect to teacher's group
     */
    fun connectAsClient(device: WifiP2pDevice) {
        if (channel == null) {
            callback?.onError("WiFi Direct not initialized")
            return
        }
        
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0 // LOW value = don't want to be group owner
        }
        
        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated as CLIENT to ${device.deviceName}")
            }
            
            override fun onFailure(reasonCode: Int) {
                val reason = getFailureReason(reasonCode)
                Log.e(TAG, "Connection failed: $reason")
                callback?.onError("Connection failed: $reason")
            }
        })
    }
    
    /**
     * Connect to a specific WiFi Direct peer (legacy - Android decides GO)
     */
    fun connectTo(device: WifiP2pDevice) {
        if (channel == null) {
            callback?.onError("WiFi Direct not initialized")
            return
        }
        
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 15 // High value = prefer to be group owner
        }
        
        wifiP2pManager?.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Connection initiated to ${device.deviceName}")
            }
            
            override fun onFailure(reasonCode: Int) {
                val reason = getFailureReason(reasonCode)
                Log.e(TAG, "Connection failed: $reason")
                callback?.onError("Connection failed: $reason")
            }
        })
    }
    
    /**
     * Disconnect from current peer/group
     */
    fun disconnect() {
        wifiP2pManager?.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Disconnected from group")
                currentConnectionInfo = null
            }
            
            override fun onFailure(reasonCode: Int) {
                Log.e(TAG, "Failed to disconnect: ${getFailureReason(reasonCode)}")
            }
        })
    }
    
    /**
     * Stop the file server if running
     */
    fun stopFileServer() {
        fileServerJob?.cancel()
        fileServerJob = null
        Log.d(TAG, "File server stopped")
    }
    
    /**
     * Start file server to receive files
     * Should be called by non-group-owner (student/client)
     * 
     * @param port Port to listen on (default: 8988)
     * @param onFileReceived Callback when file is received
     */
    fun startFileServer(port: Int = FILE_TRANSFER_PORT, onFileReceived: (File) -> Unit) {
        fileServerJob?.cancel()
        
        fileServerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverSocket = ServerSocket(port)
                serverSocket.soTimeout = SOCKET_TIMEOUT
                Log.d(TAG, "File server started on port $port")
                
                while (isActive) {
                    try {
                        val clientSocket = serverSocket.accept()
                        Log.d(TAG, "Client connected: ${clientSocket.inetAddress.hostAddress}")
                        
                        // Receive file
                        receiveFile(clientSocket, onFileReceived)
                        
                        clientSocket.close()
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
                
                serverSocket.close()
            } catch (e: Exception) {
                Log.e(TAG, "File server error", e)
                withContext(Dispatchers.Main) {
                    callback?.onError("File server error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Start a file distribution server (Group Owner / Teacher mode)
     * Serves a file to multiple connecting clients
     * 
     * @param file File to serve to clients
     * @param port Port to listen on (default: 8988)
     * @param onClientServed Callback when a client receives the file
     */
    fun startFileDistributionServer(file: File, port: Int = FILE_TRANSFER_PORT, onClientServed: (String) -> Unit) {
        if (!file.exists()) {
            callback?.onError("File not found: ${file.name}")
            return
        }
        
        fileServerJob?.cancel()
        
        fileServerJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val serverSocket = ServerSocket(port)
                serverSocket.reuseAddress = true
                Log.d(TAG, "File distribution server started on port $port, serving: ${file.name}")
                
                while (isActive) {
                    try {
                        val clientSocket = serverSocket.accept()
                        val clientAddress = clientSocket.inetAddress.hostAddress ?: "unknown"
                        Log.d(TAG, "Client connected for file: $clientAddress")
                        
                        // Send file to this client
                        launch {
                            try {
                                sendFileToSocket(file, clientSocket)
                                withContext(Dispatchers.Main) {
                                    onClientServed(clientAddress)
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error sending file to $clientAddress", e)
                            } finally {
                                clientSocket.close()
                            }
                        }
                    } catch (e: java.net.SocketTimeoutException) {
                        // Timeout is normal, continue waiting
                    } catch (e: Exception) {
                        if (isActive) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
                
                serverSocket.close()
                Log.d(TAG, "File distribution server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "File distribution server error", e)
                withContext(Dispatchers.Main) {
                    callback?.onError("File server error: ${e.message}")
                }
            }
        }
    }
    
    /**
     * Send file data through an already-connected socket
     */
    private suspend fun sendFileToSocket(file: File, socket: Socket) {
        val outputStream = socket.getOutputStream()
        val dataOutputStream = DataOutputStream(outputStream)
        val fileInputStream = FileInputStream(file)
        
        try {
            // Send file metadata
            dataOutputStream.writeLong(file.length())
            dataOutputStream.writeUTF(file.name)
            dataOutputStream.flush()
            
            Log.d(TAG, "Sending file: ${file.name} (${file.length()} bytes)")
            
            // Send file data
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesSent = 0L
            val fileSize = file.length()
            
            while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                totalBytesSent += bytesRead
                
                // Report progress
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferProgress(totalBytesSent, fileSize)
                }
            }
            
            outputStream.flush()
            Log.d(TAG, "File sent successfully: ${file.name}")
            withContext(Dispatchers.Main) {
                callback?.onFileTransferCompleted(true)
            }
        } finally {
            fileInputStream.close()
        }
    }
    
    /**
     * Request a file from the Group Owner (Client / Student mode)
     * Connects to the Group Owner's file distribution server
     * 
     * @param groupOwnerAddress IP address of the Group Owner
     * @param port Port to connect to (default: 8988)
     * @param onFileReceived Callback when file is received
     */
    fun requestFileFromGroupOwner(
        groupOwnerAddress: InetAddress,
        port: Int = FILE_TRANSFER_PORT,
        onFileReceived: (File) -> Unit
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            
            try {
                Log.d(TAG, "Connecting to Group Owner at ${groupOwnerAddress.hostAddress}:$port")
                socket = Socket(groupOwnerAddress, port)
                socket.soTimeout = SOCKET_TIMEOUT
                
                // Receive the file
                receiveFile(socket, onFileReceived)
                
            } catch (e: Exception) {
                Log.e(TAG, "Error receiving file from Group Owner", e)
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferCompleted(false)
                    callback?.onError("Failed to receive file: ${e.message}")
                }
            } finally {
                socket?.close()
            }
        }
    }
    
    /**
     * Send a file to the group owner
     * Should be called by group owner (teacher/server)
     * 
     * @param file File to send
     * @param hostAddress IP address of the receiver
     * @param port Port to connect to (default: 8988)
     */
    fun sendFile(file: File, hostAddress: InetAddress, port: Int = FILE_TRANSFER_PORT) {
        if (!file.exists()) {
            callback?.onError("File not found: ${file.name}")
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            var socket: Socket? = null
            
            try {
                Log.d(TAG, "Connecting to ${hostAddress.hostAddress}:$port")
                socket = Socket(hostAddress, port)
                socket.soTimeout = SOCKET_TIMEOUT
                
                val outputStream = socket.getOutputStream()
                val dataOutputStream = DataOutputStream(outputStream)
                val fileInputStream = FileInputStream(file)
                
                // Send file metadata
                dataOutputStream.writeLong(file.length())
                dataOutputStream.writeUTF(file.name)
                dataOutputStream.flush()
                
                Log.d(TAG, "Sending file: ${file.name} (${file.length()} bytes)")
                
                // Send file data
                val buffer = ByteArray(BUFFER_SIZE)
                var bytesRead: Int
                var totalBytesSent = 0L
                val fileSize = file.length()
                
                while (fileInputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesSent += bytesRead
                    
                    // Report progress
                    withContext(Dispatchers.Main) {
                        callback?.onFileTransferProgress(totalBytesSent, fileSize)
                    }
                }
                
                outputStream.flush()
                fileInputStream.close()
                
                Log.d(TAG, "File sent successfully: ${file.name}")
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferCompleted(true)
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error sending file", e)
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferCompleted(false)
                    callback?.onError("File transfer failed: ${e.message}")
                }
            } finally {
                socket?.close()
            }
        }
    }
    
    /**
     * Receive a file from the sender
     */
    private suspend fun receiveFile(socket: Socket, onFileReceived: (File) -> Unit) {
        try {
            val inputStream = socket.getInputStream()
            val dataInputStream = DataInputStream(inputStream)
            
            // Read file metadata
            val fileSize = dataInputStream.readLong()
            val fileName = dataInputStream.readUTF()
            
            Log.d(TAG, "Receiving file: $fileName ($fileSize bytes)")
            
            // Create output file
            val outputDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES)
                ?: context.filesDir
            val outputFile = File(outputDir, "received_$fileName")
            val fileOutputStream = FileOutputStream(outputFile)
            
            // Receive file data
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesRead: Int
            var totalBytesReceived = 0L
            
            while (totalBytesReceived < fileSize) {
                bytesRead = inputStream.read(buffer, 0, minOf(BUFFER_SIZE, (fileSize - totalBytesReceived).toInt()))
                if (bytesRead == -1) break
                
                fileOutputStream.write(buffer, 0, bytesRead)
                totalBytesReceived += bytesRead
                
                // Report progress
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferProgress(totalBytesReceived, fileSize)
                }
            }
            
            fileOutputStream.close()
            
            if (totalBytesReceived == fileSize) {
                Log.d(TAG, "File received successfully: $fileName")
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferCompleted(true)
                    onFileReceived(outputFile)
                }
            } else {
                Log.e(TAG, "File transfer incomplete: $totalBytesReceived/$fileSize bytes")
                outputFile.delete()
                withContext(Dispatchers.Main) {
                    callback?.onFileTransferCompleted(false)
                    callback?.onError("File transfer incomplete")
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving file", e)
            withContext(Dispatchers.Main) {
                callback?.onFileTransferCompleted(false)
                callback?.onError("File receive failed: ${e.message}")
            }
        }
    }
    
    /**
     * Request updated peer list
     */
    private fun requestPeers() {
        wifiP2pManager?.requestPeers(channel) { peerList ->
            val peers = peerList.deviceList
            Log.d(TAG, "Discovered ${peers.size} peers")
            callback?.onPeersUpdated(peers)
        }
    }
    
    /**
     * Request connection info
     */
    private fun requestConnectionInfo() {
        wifiP2pManager?.requestConnectionInfo(channel) { info ->
            currentConnectionInfo = info
            
            if (info.groupFormed) {
                Log.d(TAG, "Group formed - Group owner: ${info.isGroupOwner}, Owner IP: ${info.groupOwnerAddress?.hostAddress}")
                callback?.onConnectionInfoAvailable(info)
                
                // If not group owner (client/student), connect to group owner's server to receive files
                if (!info.isGroupOwner && info.groupOwnerAddress != null) {
                    Log.d(TAG, "Client mode: Ready to receive files from group owner at ${info.groupOwnerAddress?.hostAddress}")
                }
            } else {
                Log.d(TAG, "No group formed")
            }
        }
    }
    
    /**
     * Convert failure reason code to readable string
     */
    private fun getFailureReason(reasonCode: Int): String {
        return when (reasonCode) {
            WifiP2pManager.ERROR -> "Internal error"
            WifiP2pManager.P2P_UNSUPPORTED -> "WiFi P2P not supported"
            WifiP2pManager.BUSY -> "Framework busy, try again"
            else -> "Unknown error ($reasonCode)"
        }
    }
}

/**
 * Callback interface for WiFi Direct events
 */
interface WifiDirectCallback {
    /**
     * Called when WiFi P2P support status is determined
     */
    fun onP2pSupported(supported: Boolean)
    
    /**
     * Called when peer list is updated
     */
    fun onPeersUpdated(peers: Collection<WifiP2pDevice>)
    
    /**
     * Called when connection info is available (group formed)
     */
    fun onConnectionInfoAvailable(info: WifiP2pInfo)
    
    /**
     * Called during file transfer to report progress
     */
    fun onFileTransferProgress(bytesSent: Long, totalBytes: Long)
    
    /**
     * Called when file transfer is completed
     */
    fun onFileTransferCompleted(success: Boolean)
    
    /**
     * Called when an error occurs
     */
    fun onError(message: String)
}
