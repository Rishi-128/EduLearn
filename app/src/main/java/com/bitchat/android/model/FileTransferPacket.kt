package com.bitchat.android.model

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * File Transfer Packet for educational document sharing over mesh network
 * Uses TLV (Type-Length-Value) encoding for iOS compatibility
 * 
 * TLV Format:
 * - FileID: Type 0x01, Length 16 bytes (UUID)
 * - FileName: Type 0x02, Variable length string
 * - FileType: Type 0x03, Variable length string  
 * - FileSize: Type 0x04, 8 bytes (Long)
 * - ChunkIndex: Type 0x05, 4 bytes (Int)
 * - TotalChunks: Type 0x06, 4 bytes (Int)
 * - ChunkData: Type 0x07, Variable length bytes
 * - SenderName: Type 0x08, Variable length string
 * - Timestamp: Type 0x09, 8 bytes (Long)
 * - IsCompressed: Type 0x0A, 1 byte (Boolean)
 */
data class FileTransferPacket(
    val fileID: String,              // Unique identifier for this file
    val fileName: String,            // Original filename
    val fileType: String,            // MIME type or extension
    val fileSize: Long,              // Total file size in bytes (original size if compressed)
    val chunkIndex: Int,             // Current chunk index (0-based)
    val totalChunks: Int,            // Total number of chunks
    val chunkData: ByteArray,        // Actual chunk data (may be compressed)
    val senderName: String,          // Name of sender (teacher)
    val timestamp: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false // Whether the file data is GZIP compressed
) {
    /**
     * Encode to TLV format for transmission
     */
    fun encode(): ByteArray {
        // Calculate required size to prevent buffer overflow
        // Each field: 1 byte (type) + 2 bytes (length) + value bytes
        val requiredSize = 3 + fileID.toByteArray(Charsets.UTF_8).size +
                          3 + fileName.toByteArray(Charsets.UTF_8).size +
                          3 + fileType.toByteArray(Charsets.UTF_8).size +
                          3 + 8 +  // fileSize (Long)
                          3 + 4 +  // chunkIndex (Int)
                          3 + 4 +  // totalChunks (Int)
                          3 + chunkData.size +
                          3 + senderName.toByteArray(Charsets.UTF_8).size +
                          3 + 8 +  // timestamp (Long)
                          3 + 1    // isCompressed (Boolean)
        
        val buffer = ByteBuffer.allocate(requiredSize).order(ByteOrder.BIG_ENDIAN)
        
        // FileID (Type 0x01)
        encodeTLV(buffer, 0x01.toByte(), fileID.toByteArray(Charsets.UTF_8))
        
        // FileName (Type 0x02)
        encodeTLV(buffer, 0x02.toByte(), fileName.toByteArray(Charsets.UTF_8))
        
        // FileType (Type 0x03)
        encodeTLV(buffer, 0x03.toByte(), fileType.toByteArray(Charsets.UTF_8))
        
        // FileSize (Type 0x04)
        val fileSizeBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(fileSize).array()
        encodeTLV(buffer, 0x04.toByte(), fileSizeBytes)
        
        // ChunkIndex (Type 0x05)
        val chunkIndexBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(chunkIndex).array()
        encodeTLV(buffer, 0x05.toByte(), chunkIndexBytes)
        
        // TotalChunks (Type 0x06)
        val totalChunksBytes = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(totalChunks).array()
        encodeTLV(buffer, 0x06.toByte(), totalChunksBytes)
        
        // ChunkData (Type 0x07)
        encodeTLV(buffer, 0x07.toByte(), chunkData)
        
        // SenderName (Type 0x08)
        encodeTLV(buffer, 0x08.toByte(), senderName.toByteArray(Charsets.UTF_8))
        
        // Timestamp (Type 0x09)
        val timestampBytes = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timestamp).array()
        encodeTLV(buffer, 0x09.toByte(), timestampBytes)
        
        // IsCompressed (Type 0x0A)
        val isCompressedBytes = byteArrayOf(if (isCompressed) 1 else 0)
        encodeTLV(buffer, 0x0A.toByte(), isCompressedBytes)
        
        // Return only the used portion of the buffer
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }
    
    private fun encodeTLV(buffer: ByteBuffer, type: Byte, value: ByteArray) {
        buffer.put(type)
        buffer.putShort(value.size.toShort())
        buffer.put(value)
    }
    
    companion object {
        /**
         * Decode TLV format back to FileTransferPacket
         */
        fun decode(data: ByteArray): FileTransferPacket? {
            return try {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                
                var fileID = ""
                var fileName = ""
                var fileType = ""
                var fileSize = 0L
                var chunkIndex = 0
                var totalChunks = 0
                var chunkData = ByteArray(0)
                var senderName = ""
                var timestamp = System.currentTimeMillis()
                var isCompressed = false
                
                while (buffer.hasRemaining()) {
                    val type = buffer.get()
                    val length = buffer.short.toInt()
                    val value = ByteArray(length)
                    buffer.get(value)
                    
                    when (type.toInt()) {
                        0x01 -> fileID = String(value, Charsets.UTF_8)
                        0x02 -> fileName = String(value, Charsets.UTF_8)
                        0x03 -> fileType = String(value, Charsets.UTF_8)
                        0x04 -> fileSize = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long
                        0x05 -> chunkIndex = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).int
                        0x06 -> totalChunks = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).int
                        0x07 -> chunkData = value
                        0x08 -> senderName = String(value, Charsets.UTF_8)
                        0x09 -> timestamp = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long
                        0x0A -> isCompressed = value[0] == 1.toByte()
                    }
                }
                
                FileTransferPacket(
                    fileID = fileID,
                    fileName = fileName,
                    fileType = fileType,
                    fileSize = fileSize,
                    chunkIndex = chunkIndex,
                    totalChunks = totalChunks,
                    chunkData = chunkData,
                    senderName = senderName,
                    timestamp = timestamp,
                    isCompressed = isCompressed
                )
            } catch (e: Exception) {
                null
            }
        }
    }
    
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FileTransferPacket

        if (fileID != other.fileID) return false
        if (fileName != other.fileName) return false
        if (fileType != other.fileType) return false
        if (fileSize != other.fileSize) return false
        if (chunkIndex != other.chunkIndex) return false
        if (totalChunks != other.totalChunks) return false
        if (!chunkData.contentEquals(other.chunkData)) return false
        if (senderName != other.senderName) return false
        if (timestamp != other.timestamp) return false
        if (isCompressed != other.isCompressed) return false

        return true
    }

    override fun hashCode(): Int {
        var result = fileID.hashCode()
        result = 31 * result + fileName.hashCode()
        result = 31 * result + fileType.hashCode()
        result = 31 * result + fileSize.hashCode()
        result = 31 * result + chunkIndex
        result = 31 * result + totalChunks
        result = 31 * result + chunkData.contentHashCode()
        result = 31 * result + senderName.hashCode()
        result = 31 * result + timestamp.hashCode()
        return result
    }
}

/**
 * File Transfer ACK Packet
 */
data class FileTransferAckPacket(
    val fileID: String,
    val chunkIndex: Int,
    val receiverPeerID: String,  // Changed from receiverName to match usage
    val timestamp: Long = System.currentTimeMillis()
) {
    fun encode(): ByteArray {
        val buffer = ByteBuffer.allocate(512).order(ByteOrder.BIG_ENDIAN)
        
        // FileID
        val fileIDBytes = fileID.toByteArray(Charsets.UTF_8)
        buffer.put(0x01.toByte())
        buffer.putShort(fileIDBytes.size.toShort())
        buffer.put(fileIDBytes)
        
        // ChunkIndex
        buffer.put(0x02.toByte())
        buffer.putShort(4)
        buffer.putInt(chunkIndex)
        
        // ReceiverPeerID
        val receiverPeerIDBytes = receiverPeerID.toByteArray(Charsets.UTF_8)
        buffer.put(0x03.toByte())
        buffer.putShort(receiverPeerIDBytes.size.toShort())
        buffer.put(receiverPeerIDBytes)
        
        // Timestamp
        buffer.put(0x04.toByte())
        buffer.putShort(8)
        buffer.putLong(timestamp)
        
        val result = ByteArray(buffer.position())
        buffer.rewind()
        buffer.get(result)
        return result
    }
    
    companion object {
        fun decode(data: ByteArray): FileTransferAckPacket? {
            return try {
                val buffer = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
                
                var fileID = ""
                var chunkIndex = 0
                var receiverPeerID = ""
                var timestamp = System.currentTimeMillis()
                
                while (buffer.hasRemaining()) {
                    val type = buffer.get()
                    val length = buffer.short.toInt()
                    val value = ByteArray(length)
                    buffer.get(value)
                    
                    when (type.toInt()) {
                        0x01 -> fileID = String(value, Charsets.UTF_8)
                        0x02 -> chunkIndex = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).int
                        0x03 -> receiverPeerID = String(value, Charsets.UTF_8)
                        0x04 -> timestamp = ByteBuffer.wrap(value).order(ByteOrder.BIG_ENDIAN).long
                    }
                }
                
                FileTransferAckPacket(fileID, chunkIndex, receiverPeerID, timestamp)
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * Complete file transfer metadata for tracking
 */
data class FileTransferMetadata(
    val fileID: String,
    val fileName: String,
    val fileType: String,
    val fileSize: Long,
    val totalChunks: Int,
    val senderName: String,
    val senderPeerID: String,
    val receivedChunks: MutableSet<Int> = mutableSetOf(),
    val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
    val timestamp: Long = System.currentTimeMillis(),
    val isCompressed: Boolean = false,
    var lastActivityTime: Long = System.currentTimeMillis() // For timeout detection
) {
    fun addChunk(chunkIndex: Int, chunkData: ByteArray): Boolean {
        // Return true if this is a new chunk, false if duplicate
        val isNew = !receivedChunks.contains(chunkIndex)
        chunks[chunkIndex] = chunkData
        receivedChunks.add(chunkIndex)
        lastActivityTime = System.currentTimeMillis() // Update activity time on each chunk
        return isNew
    }
    
    fun isComplete(): Boolean = receivedChunks.size == totalChunks
    
    fun getProgress(): Float = if (totalChunks > 0) receivedChunks.size.toFloat() / totalChunks else 0f
    
    fun assembleFile(): ByteArray? {
        if (!isComplete()) return null
        
        // Calculate actual size from chunks instead of using fileSize
        // This handles both compressed and uncompressed data correctly
        val actualSize = chunks.values.sumOf { it.size }
        
        val result = ByteArray(actualSize)
        var offset = 0
        
        for (i in 0 until totalChunks) {
            val chunk = chunks[i] ?: return null
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        
        return result
    }
}
