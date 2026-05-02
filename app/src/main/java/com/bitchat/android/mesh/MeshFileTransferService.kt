package com.bitchat.android.mesh

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

/**
 * Service for managing file transfers over the mesh network
 * Handles chunking large files and tracking transfer progress
 */
class MeshFileTransferService(
    private val context: Context,
    private val meshService: BluetoothMeshService
) {
    companion object {
        private const val TAG = "MeshFileTransferService"
        // BLE MTU max is 517-520 bytes, Fragment threshold is 512 bytes
        // With TLV encoding overhead (~125 bytes for metadata), we need chunk < 387 bytes
        // Using 250 bytes ensures total packet ~375 bytes, avoiding fragmentation entirely
        private const val CHUNK_SIZE = 250 // Optimized to prevent double fragmentation
        private const val RECEIVED_FILES_DIR = "received_documents"
        private const val RECEIVED_FILES_INDEX = "received_files_index.txt"
        private const val CHUNK_DELAY_MS = 10L // Matches BLE write time, prevents buffer overflow
        private const val MAX_RETRIES = 3 // Maximum retry attempts for failed chunks
        private const val RETRY_DELAY_MS = 2000L // Wait 2 seconds before retrying
        private const val TRANSFER_TIMEOUT_MS = 30000L // 30 seconds timeout for inactive transfers
    }
    
    // Coroutine scope for async operations
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    // Track ongoing file transfers
    private val outgoingTransfers = mutableMapOf<String, OutgoingFileTransfer>()
    private val incomingTransfers = mutableMapOf<String, IncomingFileTransfer>()
    
    // Transfer progress state
    private val _transferProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val transferProgress: StateFlow<Map<String, Float>> = _transferProgress
    
    // Received files state - internal mutable for ChatViewModel access
    internal val _receivedFiles = MutableStateFlow<List<ReceivedFile>>(emptyList())
    val receivedFiles: StateFlow<List<ReceivedFile>> = _receivedFiles
    
    init {
        // Load previously received files on initialization
        loadReceivedFiles()
        
        // Start periodic cleanup of stale transfers
        serviceScope.launch {
            while (true) {
                delay(10000L) // Check every 10 seconds
                cleanupStaleTransfers()
            }
        }
    }
    
    /**
     * Send file to specific peer or broadcast to all
     * Automatically compresses compressible files before sending
     */
    fun sendFile(
        filePath: String,
        fileName: String,
        fileType: String,
        recipientPeerID: String? = null // null = broadcast to all
    ) {
        serviceScope.launch {
            try {
                val file = File(filePath)
                if (!file.exists()) {
                    Log.e(TAG, "❌ File not found: $filePath")
                    return@launch
                }
                
                Log.d(TAG, "📤 Starting file transfer: $fileName")
                Log.d(TAG, "📊 File size: ${file.length()} bytes (${file.length() / 1024} KB)")
                
                // Check if compression should be applied
                val shouldCompress = FileCompressionUtil.shouldCompress(fileName)
                Log.d(TAG, "🗜️ Should compress: $shouldCompress")
                
                val fileData: ByteArray
                val originalFileSize = file.length()
                val isCompressed: Boolean
                
                if (shouldCompress) {
                    // Compress to temp file
                    val tempCompressedFile = File(context.cacheDir, "${UUID.randomUUID()}.gz")
                    try {
                        val compressionSuccess = FileCompressionUtil.compressFile(file, tempCompressedFile)
                        
                        if (compressionSuccess && tempCompressedFile.exists()) {
                            val compressedSize = tempCompressedFile.length()
                            
                            // Check if compression actually reduced size (edge case: random/encrypted data may expand)
                            if (compressedSize < originalFileSize) {
                                fileData = tempCompressedFile.readBytes()
                                isCompressed = true
                                
                                val compressionRatio = ((originalFileSize - compressedSize) * 100.0 / originalFileSize)
                                Log.d(TAG, "Compressed $fileName: ${originalFileSize}B → ${compressedSize}B (${String.format("%.1f", compressionRatio)}% reduction)")
                            } else {
                                // Compression expanded the file, send uncompressed
                                fileData = file.readBytes()
                                isCompressed = false
                                Log.d(TAG, "Compression expanded $fileName (${originalFileSize}B → ${compressedSize}B), sending uncompressed")
                            }
                        } else {
                            // Compression failed, send uncompressed
                            fileData = file.readBytes()
                            isCompressed = false
                            Log.w(TAG, "Compression failed for $fileName, sending uncompressed")
                        }
                    } finally {
                        // Ensure temp file is always cleaned up
                        if (tempCompressedFile.exists()) {
                            tempCompressedFile.delete()
                        }
                    }
                } else {
                    // File type not compressible (already compressed format)
                    fileData = file.readBytes()
                    isCompressed = false
                    Log.d(TAG, "Skipping compression for $fileName (already compressed format)")
                }
                
                // IMPORTANT: fileSize should be the size of data being sent (compressed or original)
                val fileSize = fileData.size.toLong()
                val fileID = UUID.randomUUID().toString()
                
                // Calculate number of chunks based on actual data size
                val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
                
                Log.d(TAG, "Sending file $fileName ($fileSize bytes${if (isCompressed) " compressed from $originalFileSize bytes" else ""}) in $totalChunks chunks of ${CHUNK_SIZE} bytes each")
                Log.d(TAG, "Estimated transfer time: ~${(totalChunks * CHUNK_DELAY_MS) / 1000}s (delays only)")
                
                // Create outgoing transfer tracker
                val transfer = OutgoingFileTransfer(
                    fileID = fileID,
                    fileName = fileName,
                    fileType = fileType,
                    fileSize = fileSize, // Use actual data size, not original
                    totalChunks = totalChunks,
                    recipientPeerID = recipientPeerID,
                    chunksAcked = mutableSetOf()
                )
                outgoingTransfers[fileID] = transfer
                
                // Initialize progress to 0% before sending
                updateTransferProgress(fileID, 0f)
                
                // Send all chunks with delay to avoid overwhelming BLE stack
                for (chunkIndex in 0 until totalChunks) {
                    val start = chunkIndex * CHUNK_SIZE
                    val end = minOf(start + CHUNK_SIZE, fileData.size)
                    val chunkData = fileData.copyOfRange(start, end)
                    
                    meshService.sendFileTransfer(
                        fileID = fileID,
                        fileName = fileName,
                        fileType = fileType,
                        fileSize = fileSize, // Send actual data size (compressed or original)
                        chunkIndex = chunkIndex,
                        totalChunks = totalChunks,
                        chunkData = chunkData,
                        recipientPeerID = recipientPeerID,
                        isCompressed = isCompressed // Add compression flag
                    )
                    
                    // Reduced delay for faster transfers with larger chunks
                    delay(CHUNK_DELAY_MS)
                    
                    // For broadcast mode (null recipient), update progress immediately since ACKs may not come
                    if (recipientPeerID == null) {
                        val sendProgress = (chunkIndex + 1).toFloat() / totalChunks
                        updateTransferProgress(fileID, sendProgress)
                    }
                    
                    // Log progress every 5 chunks for better progress visibility
                    if ((chunkIndex + 1) % 5 == 0 || chunkIndex == totalChunks - 1) {
                        Log.d(TAG, "📤 Sent chunk ${chunkIndex + 1}/$totalChunks for $fileName (${((chunkIndex + 1) * 100 / totalChunks)}%)")
                    }
                }
                
                Log.d(TAG, "✅ All $totalChunks chunks sent for $fileName${if (isCompressed) " (compressed)" else ""}")
                
                // For broadcast mode, always do one retransmission for reliability
                // Students will deduplicate automatically if they already have the file
                if (recipientPeerID == null) {
                    delay(RETRY_DELAY_MS) // Wait 2 seconds before retransmission
                    
                    Log.d(TAG, "🔄 Retransmitting $fileName for broadcast reliability")
                    
                    // Retransmit ALL chunks once
                    for (chunkIndex in 0 until totalChunks) {
                        val start = chunkIndex * CHUNK_SIZE
                        val end = minOf(start + CHUNK_SIZE, fileData.size)
                        val chunkData = fileData.copyOfRange(start, end)
                        
                        meshService.sendFileTransfer(
                            fileID = fileID,
                            fileName = fileName,
                            fileType = fileType,
                            fileSize = fileSize,
                            chunkIndex = chunkIndex,
                            totalChunks = totalChunks,
                            chunkData = chunkData,
                            recipientPeerID = null,
                            isCompressed = isCompressed
                        )
                        
                        delay(CHUNK_DELAY_MS)
                    }
                    
                    Log.d(TAG, "✅ Broadcast complete with 1 retransmission: $fileName")
                    updateTransferProgress(fileID, 1f)
                    outgoingTransfers.remove(fileID)
                }
                // For targeted sends, progress updates happen via ACKs in handleChunkAck()
                
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send file: ${e.message}")
            }
        }
    }
    
    /**
     * Handle acknowledgment for sent chunk
     */
    fun handleChunkAck(fileID: String, chunkIndex: Int, receiverPeerID: String) {
        val transfer = outgoingTransfers[fileID] ?: return
        
        transfer.chunksAcked.add(chunkIndex)
        transfer.studentsAcked.add(receiverPeerID) // Track unique student who ACKed
        transfer.lastActivityTime = System.currentTimeMillis() // Update activity time
        
        val progress = transfer.chunksAcked.size.toFloat() / transfer.totalChunks
        updateTransferProgress(fileID, progress)
        
        Log.d(TAG, "Chunk $chunkIndex acked by $receiverPeerID for ${transfer.fileName} - Progress: ${(progress * 100).toInt()}%")
        
        // Check if all chunks are acked
        if (transfer.chunksAcked.size >= transfer.totalChunks) {
            Log.d(TAG, "File transfer complete: ${transfer.fileName}")
            outgoingTransfers.remove(fileID)
            updateTransferProgress(fileID, 1f)
        }
    }
    
    /**
     * Handle received file chunk
     * NOTE: This function is currently NOT USED - MessageHandler handles chunk assembly directly
     * Kept commented for potential future refactoring
     */
    /*
    fun handleReceivedChunk(
        fileID: String,
        fileName: String,
        fileType: String,
        fileSize: Long,
        chunkIndex: Int,
        totalChunks: Int,
        chunkData: ByteArray,
        senderPeerID: String,
        senderName: String
    ) {
        // Get or create incoming transfer tracker
        val transfer = incomingTransfers.getOrPut(fileID) {
            IncomingFileTransfer(
                fileID = fileID,
                fileName = fileName,
                fileType = fileType,
                fileSize = fileSize,
                totalChunks = totalChunks,
                senderPeerID = senderPeerID,
                senderName = senderName,
                chunks = mutableMapOf()
            )
        }
        
        // Store chunk
        transfer.chunks[chunkIndex] = chunkData
        
        val progress = transfer.chunks.size.toFloat() / transfer.totalChunks
        updateTransferProgress(fileID, progress)
        
        Log.d(TAG, "Received chunk ${chunkIndex + 1}/$totalChunks for $fileName - Progress: ${(progress * 100).toInt()}%")
        
        // Check if all chunks received
        if (transfer.chunks.size >= transfer.totalChunks) {
            assembleAndSaveFile(transfer)
        }
    }
    */
    
    /**
     * Assemble complete file from chunks and save
     */
    private fun assembleAndSaveFile(transfer: IncomingFileTransfer) {
        try {
            // Create received files directory
            val receivedDir = File(context.filesDir, RECEIVED_FILES_DIR)
            if (!receivedDir.exists()) {
                receivedDir.mkdirs()
            }
            
            // Assemble file data from chunks in order
            val completeData = ByteArray(transfer.fileSize.toInt())
            var offset = 0
            
            for (chunkIndex in 0 until transfer.totalChunks) {
                val chunkData = transfer.chunks[chunkIndex]
                if (chunkData != null) {
                    chunkData.copyInto(completeData, offset)
                    offset += chunkData.size
                } else {
                    Log.e(TAG, "Missing chunk $chunkIndex for ${transfer.fileName}")
                    return
                }
            }
            
            // Save file
            val savedFile = File(receivedDir, transfer.fileName)
            savedFile.writeBytes(completeData)
            
            Log.d(TAG, "✅ File saved: ${savedFile.absolutePath}")
            
            // Add to received files list
            val receivedFile = ReceivedFile(
                fileID = transfer.fileID,
                fileName = transfer.fileName,
                fileType = transfer.fileType,
                fileSize = transfer.fileSize,
                filePath = savedFile.absolutePath,
                senderPeerID = transfer.senderPeerID,
                senderName = transfer.senderName,
                timestamp = System.currentTimeMillis()
            )
            
            val currentFiles = _receivedFiles.value.toMutableList()
            currentFiles.add(receivedFile)
            _receivedFiles.value = currentFiles
            
            // Clean up
            incomingTransfers.remove(transfer.fileID)
            updateTransferProgress(transfer.fileID, 1f)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to assemble and save file: ${e.message}")
        }
    }
    
    /**
     * Update transfer progress state
     */
    private fun updateTransferProgress(fileID: String, progress: Float) {
        val currentProgress = _transferProgress.value.toMutableMap()
        currentProgress[fileID] = progress
        _transferProgress.value = currentProgress
    }
    
    /**
     * Find missing chunks that haven't been acknowledged
     */
    private fun findMissingChunks(transfer: OutgoingFileTransfer): List<Int> {
        val missing = mutableListOf<Int>()
        for (i in 0 until transfer.totalChunks) {
            if (!transfer.chunksAcked.contains(i)) {
                missing.add(i)
            }
        }
        return missing
    }
    
    /**
     * Clean up stale file transfers that have timed out
     */
    private fun cleanupStaleTransfers() {
        val now = System.currentTimeMillis()
        
        // Clean up outgoing transfers
        val staleOutgoing = outgoingTransfers.filter { (_, transfer) ->
            now - transfer.lastActivityTime > TRANSFER_TIMEOUT_MS
        }
        
        for ((fileID, transfer) in staleOutgoing) {
            Log.w(TAG, "⏱️ Transfer timeout: ${transfer.fileName} (inactive for ${TRANSFER_TIMEOUT_MS/1000}s)")
            outgoingTransfers.remove(fileID)
            updateTransferProgress(fileID, -1f) // Negative progress indicates failure
        }
        
        // Clean up incoming transfers  
        val staleIncoming = incomingTransfers.filter { (_, transfer) ->
            now - transfer.lastActivityTime > TRANSFER_TIMEOUT_MS
        }
        
        for ((fileID, transfer) in staleIncoming) {
            Log.w(TAG, "⏱️ Receive timeout: ${transfer.fileName} (inactive for ${TRANSFER_TIMEOUT_MS/1000}s)")
            incomingTransfers.remove(fileID)
        }
        
        if (staleOutgoing.isNotEmpty() || staleIncoming.isNotEmpty()) {
            Log.d(TAG, "🧹 Cleaned up ${staleOutgoing.size} outgoing + ${staleIncoming.size} incoming stale transfers")
        }
    }
    
    /**
     * Update incoming transfer progress (called by delegate for receiving files)
     */
    fun updateIncomingProgress(fileID: String, progress: Float) {
        updateTransferProgress(fileID, progress)
    }
    
    /**
     * Get list of received files
     */
    fun getReceivedFiles(): List<ReceivedFile> {
        return _receivedFiles.value
    }
    
    /**
     * Add a received file to the list (called by delegate)
     */
    fun addReceivedFile(file: ReceivedFile) {
        Log.d(TAG, "📋 addReceivedFile called for ${file.fileName}")
        val currentFiles = _receivedFiles.value.toMutableList()
        currentFiles.add(file)
        _receivedFiles.value = currentFiles
        Log.d(TAG, "✅ File added. Total received files: ${_receivedFiles.value.size}")
        Log.d(TAG, "📄 Files: ${_receivedFiles.value.map { it.fileName }}")
        
        // Persist to disk
        saveReceivedFiles()
    }
    
    /**
     * Clear received files history
     */
    fun clearReceivedFiles() {
        _receivedFiles.value = emptyList()
        saveReceivedFiles()
    }
    
    /**
     * Load received files from persistent storage
     */
    private fun loadReceivedFiles() {
        try {
            val indexFile = File(context.filesDir, RECEIVED_FILES_INDEX)
            if (!indexFile.exists()) {
                Log.d(TAG, "No saved files index found")
                return
            }
            
            val files = mutableListOf<ReceivedFile>()
            indexFile.readLines().forEach { line ->
                if (line.isNotBlank()) {
                    val parts = line.split("|")
                    if (parts.size >= 7) {
                        val file = ReceivedFile(
                            fileID = parts[0],
                            fileName = parts[1],
                            fileType = parts[2],
                            fileSize = parts[3].toLongOrNull() ?: 0L,
                            filePath = parts[4],
                            senderPeerID = parts[5],
                            senderName = parts[6],
                            timestamp = parts.getOrNull(7)?.toLongOrNull() ?: System.currentTimeMillis()
                        )
                        
                        // Verify file still exists
                        if (File(file.filePath).exists()) {
                            files.add(file)
                        } else {
                            Log.w(TAG, "Saved file no longer exists: ${file.filePath}")
                        }
                    }
                }
            }
            
            _receivedFiles.value = files
            Log.d(TAG, "📂 Loaded ${files.size} received files from storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading received files: ${e.message}", e)
        }
    }
    
    /**
     * Save received files to persistent storage
     */
    private fun saveReceivedFiles() {
        serviceScope.launch {
            try {
                val indexFile = File(context.filesDir, RECEIVED_FILES_INDEX)
                indexFile.writeText(
                    _receivedFiles.value.joinToString("\n") { file ->
                        "${file.fileID}|${file.fileName}|${file.fileType}|${file.fileSize}|${file.filePath}|${file.senderPeerID}|${file.senderName}|${file.timestamp}"
                    }
                )
                Log.d(TAG, "💾 Saved ${_receivedFiles.value.size} files to persistent storage")
            } catch (e: Exception) {
                Log.e(TAG, "Error saving received files: ${e.message}", e)
            }
        }
    }
}

/**
 * Track outgoing file transfer state
 */
data class OutgoingFileTransfer(
    val fileID: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val totalChunks: Int,
    val recipientPeerID: String?,
    val chunksAcked: MutableSet<Int>,
    val studentsAcked: MutableSet<String> = mutableSetOf(), // Track unique students who ACKed
    var retryCount: Int = 0, // Track retry attempts
    var lastActivityTime: Long = System.currentTimeMillis() // For timeout detection
)

/**
 * Track incoming file transfer state
 */
data class IncomingFileTransfer(
    val fileID: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val totalChunks: Int,
    val senderPeerID: String,
    val senderName: String,
    val chunks: MutableMap<Int, ByteArray>,
    var lastActivityTime: Long = System.currentTimeMillis() // For timeout detection
)

/**
 * Represents a received file
 */
data class ReceivedFile(
    val fileID: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val filePath: String,
    val senderPeerID: String,
    val senderName: String,
    val timestamp: Long
)
