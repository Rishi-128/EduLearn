package com.bitchat.android.mesh

import android.util.Log
import com.bitchat.android.mesh.FileCompressionUtil
import com.bitchat.android.model.BitchatMessage
import com.bitchat.android.model.IdentityAnnouncement
import com.bitchat.android.model.RoutedPacket
import com.bitchat.android.protocol.BitchatPacket
import com.bitchat.android.protocol.MessageType
import com.bitchat.android.util.toHexString
import kotlinx.coroutines.*
import java.util.*
import kotlin.random.Random

/**
 * Handles processing of different message types
 * Extracted from BluetoothMeshService for better separation of concerns
 */
class MessageHandler(private val myPeerID: String) {
    
    companion object {
        private const val TAG = "MessageHandler"
    }
    
    // Delegate for callbacks
    var delegate: MessageHandlerDelegate? = null
    
    // Reference to PacketProcessor for recursive packet handling
    var packetProcessor: PacketProcessor? = null
    
    // Coroutines
    private val handlerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    /**
     * Handle Noise encrypted transport message - SIMPLIFIED iOS-compatible version
     * Uses NoisePayloadType system exactly like iOS SimplifiedBluetoothService
     */
    suspend fun handleNoiseEncrypted(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing Noise encrypted message from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) return
        
        // Check if this message is for us
        val recipientID = packet.recipientID?.toHexString()
        if (recipientID != myPeerID) {
            Log.d(TAG, "🔐 Encrypted message not for me (for $recipientID, I am $myPeerID)")
            return
        }
        
        try {
            // Decrypt the message using the Noise service
            val decryptedData = delegate?.decryptFromPeer(packet.payload, peerID)
            if (decryptedData == null) {
                Log.w(TAG, "Failed to decrypt Noise message from $peerID - may need handshake")
                return
            }
            
            if (decryptedData.isEmpty()) {
                Log.w(TAG, "Decrypted data is empty from $peerID")
                return
            }
            
            // NEW: Use NoisePayload system exactly like iOS
            val noisePayload = com.bitchat.android.model.NoisePayload.decode(decryptedData)
            if (noisePayload == null) {
                Log.w(TAG, "Failed to parse NoisePayload from $peerID")
                return
            }
            
            Log.d(TAG, "🔓 Decrypted NoisePayload type ${noisePayload.type} from $peerID")
            
            when (noisePayload.type) {
                com.bitchat.android.model.NoisePayloadType.PRIVATE_MESSAGE -> {
                    // Decode TLV private message exactly like iOS
                    val privateMessage = com.bitchat.android.model.PrivateMessagePacket.decode(noisePayload.data)
                    if (privateMessage != null) {
                        Log.d(TAG, "🔓 Decrypted TLV PM from $peerID: ${privateMessage.content.take(30)}...")

                        // Handle favorite/unfavorite notifications embedded as PMs
                        val pmContent = privateMessage.content
                        if (pmContent.startsWith("[FAVORITED]") || pmContent.startsWith("[UNFAVORITED]")) {
                            handleFavoriteNotificationFromMesh(pmContent, peerID)
                            // Acknowledge delivery for UX parity
                            sendDeliveryAck(privateMessage.messageID, peerID)
                            return
                        }
                        
                        // Create BitchatMessage - preserve source packet timestamp
                        val message = BitchatMessage(
                            id = privateMessage.messageID,
                            sender = delegate?.getPeerNickname(peerID) ?: "Unknown",
                            content = privateMessage.content,
                            timestamp = java.util.Date(packet.timestamp.toLong()),
                            isRelay = false,
                            originalSender = null,
                            isPrivate = true,
                            recipientNickname = delegate?.getMyNickname(),
                            senderPeerID = peerID,
                            mentions = null // TODO: Parse mentions if needed
                        )
                        
                        // Notify delegate
                        delegate?.onMessageReceived(message)
                        
                        // Send delivery ACK exactly like iOS
                        sendDeliveryAck(privateMessage.messageID, peerID)
                    }
                }
                
                com.bitchat.android.model.NoisePayloadType.DELIVERED -> {
                    // Handle delivery ACK exactly like iOS
                    val messageID = String(noisePayload.data, Charsets.UTF_8)
                    Log.d(TAG, "📬 Delivery ACK received from $peerID for message $messageID")
                    
                    // Simplified: Call delegate with messageID and peerID directly
                    delegate?.onDeliveryAckReceived(messageID, peerID)
                }
                
                com.bitchat.android.model.NoisePayloadType.READ_RECEIPT -> {
                    // Handle read receipt exactly like iOS
                    val messageID = String(noisePayload.data, Charsets.UTF_8)
                    Log.d(TAG, "👁️ Read receipt received from $peerID for message $messageID")
                    
                    // Simplified: Call delegate with messageID and peerID directly
                    delegate?.onReadReceiptReceived(messageID, peerID)
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing Noise encrypted message from $peerID: ${e.message}")
        }
    }
    
    /**
     * Send delivery ACK for a received private message - exactly like iOS
     */
    private suspend fun sendDeliveryAck(messageID: String, senderPeerID: String) {
        try {
            // Create ACK payload: [type byte] + [message ID] - exactly like iOS
            val ackPayload = com.bitchat.android.model.NoisePayload(
                type = com.bitchat.android.model.NoisePayloadType.DELIVERED,
                data = messageID.toByteArray(Charsets.UTF_8)
            )
            
            // Encrypt the payload
            val encryptedPayload = delegate?.encryptForPeer(ackPayload.encode(), senderPeerID)
            if (encryptedPayload == null) {
                Log.w(TAG, "Failed to encrypt delivery ACK for $senderPeerID")
                return
            }
            
            // Create NOISE_ENCRYPTED packet exactly like iOS
            val packet = BitchatPacket(
                version = 1u,
                type = MessageType.NOISE_ENCRYPTED.value,
                senderID = hexStringToByteArray(myPeerID),
                recipientID = hexStringToByteArray(senderPeerID),
                timestamp = System.currentTimeMillis().toULong(),
                payload = encryptedPayload,
                signature = null,
                ttl = 7u // Same TTL as iOS messageTTL
            )
            
            delegate?.sendPacket(packet)
            Log.d(TAG, "📤 Sent delivery ACK to $senderPeerID for message $messageID")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send delivery ACK to $senderPeerID: ${e.message}")
        }
    }
    
    /**
     * Handle announce message with TLV decoding and signature verification - exactly like iOS
     */
    suspend fun handleAnnounce(routed: RoutedPacket): Boolean {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"

        if (peerID == myPeerID) return false
        
        // Try to decode as iOS-compatible IdentityAnnouncement with TLV format
        val announcement = IdentityAnnouncement.decode(packet.payload)
        if (announcement == null) {
            Log.w(TAG, "Failed to decode announce from $peerID as iOS-compatible TLV format")
            return false
        }
        
        // Verify packet signature using the announced signing public key
        var verified = false
        if (packet.signature != null) {
            // Verify that the packet was signed by the signing private key corresponding to the announced signing public key
            verified = delegate?.verifyEd25519Signature(packet.signature!!, packet.toBinaryDataForSigning()!!, announcement.signingPublicKey) ?: false
            if (!verified) {
                Log.w(TAG, "⚠️ Signature verification for announce failed ${peerID.take(8)}")
            }
        }

        // Check for existing peer with different noise public key
        // If existing peer has a different noise public key, do not consider this verified
        val existingPeer = delegate?.getPeerInfo(peerID)
        
        if (existingPeer != null && existingPeer.noisePublicKey != null && !existingPeer.noisePublicKey!!.contentEquals(announcement.noisePublicKey)) {
            Log.w(TAG, "⚠️ Announce key mismatch for ${peerID.take(8)}... — keeping unverified")
            verified = false
        }

        // Require verified announce; ignore otherwise (no backward compatibility)
        if (!verified) {
            Log.w(TAG, "❌ Ignoring unverified announce from ${peerID.take(8)}...")
            return false
        }
        
        // Successfully decoded TLV format exactly like iOS
        Log.d(TAG, "✅ Verified announce from $peerID: nickname=${announcement.nickname}, " +
                "noisePublicKey=${announcement.noisePublicKey.joinToString("") { "%02x".format(it) }.take(16)}..., " +
                "signingPublicKey=${announcement.signingPublicKey.joinToString("") { "%02x".format(it) }.take(16)}...")
        
        // Extract nickname and public keys from TLV data
        val nickname = announcement.nickname
        val noisePublicKey = announcement.noisePublicKey
        val signingPublicKey = announcement.signingPublicKey
        
        // Update peer info with verification status through new method
        val isFirstAnnounce = delegate?.updatePeerInfo(
            peerID = peerID,
            nickname = nickname,
            noisePublicKey = noisePublicKey,
            signingPublicKey = signingPublicKey,
            isVerified = true
        ) ?: false

        // Update peer ID binding with noise public key for identity management
        delegate?.updatePeerIDBinding(
            newPeerID = peerID,
            nickname = nickname,
            publicKey = noisePublicKey,
            previousPeerID = null
        )
        
        Log.d(TAG, "✅ Processed verified TLV announce: stored identity for $peerID")
        return isFirstAnnounce
    }
    
    /**
     * Handle Noise handshake - SIMPLIFIED iOS-compatible version
     * Single handshake type (0x10) with response determined by payload analysis
     */
    suspend fun handleNoiseHandshake(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing Noise handshake from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own handshake messages
        if (peerID == myPeerID) return
        
        // Check if handshake is addressed to us
        val recipientID = packet.recipientID?.toHexString()
        if (recipientID != myPeerID) {
            Log.d(TAG, "Handshake not for me (for $recipientID, I am $myPeerID)")
            return
        }
        
        try {
            // Process handshake message through delegate (simplified approach)
            val response = delegate?.processNoiseHandshakeMessage(packet.payload, peerID)
            
            if (response != null) {
                Log.d(TAG, "Generated handshake response for $peerID (${response.size} bytes)")
                
                // Send response using same packet type (simplified iOS approach)
                val responsePacket = BitchatPacket(
                    version = 1u,
                    type = MessageType.NOISE_HANDSHAKE.value,
                    senderID = hexStringToByteArray(myPeerID),
                    recipientID = hexStringToByteArray(peerID),
                    timestamp = System.currentTimeMillis().toULong(),
                    payload = response,
                    signature = null,
                    ttl = 7u // Same TTL as iOS
                )
                
                delegate?.sendPacket(responsePacket)
                Log.d(TAG, "📤 Sent handshake response to $peerID")
            }
            
            // Check if session is now established
            val hasSession = delegate?.hasNoiseSession(peerID) ?: false
            if (hasSession) {
                Log.d(TAG, "✅ Noise session established with $peerID")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process Noise handshake from $peerID: ${e.message}")
        }
    }
    
    /**
     * Handle broadcast or private message
     */
    suspend fun handleMessage(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        if (peerID == myPeerID) return
        val senderNickname = delegate?.getPeerNickname(peerID)
        if (senderNickname != null) {
            Log.d(TAG, "Received message from $senderNickname")
            delegate?.updatePeerNickname(peerID, senderNickname)
        }
        
        val recipientID = packet.recipientID?.takeIf { !it.contentEquals(delegate?.getBroadcastRecipient()) }
        
        if (recipientID == null) {
            // BROADCAST MESSAGE
            handleBroadcastMessage(routed)
        } else if (recipientID.toHexString() == myPeerID) {
            // PRIVATE MESSAGE FOR US
            handlePrivateMessage(packet, peerID)
        }
        // Message relay is now handled by centralized PacketRelayManager
    }
    
    /**
     * Handle broadcast message with verification enforcement
     */
    private suspend fun handleBroadcastMessage(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        // Enforce: only accept public messages from verified peers we know
        val peerInfo = delegate?.getPeerInfo(peerID)
        if (peerInfo == null || !peerInfo.isVerifiedNickname) {
            Log.w(TAG, "🚫 Dropping public message from unverified or unknown peer ${peerID.take(8)}...")
            return
        }
        
        try {
            // Parse message
            val message = BitchatMessage(
                sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                content = String(packet.payload, Charsets.UTF_8),
                senderPeerID = peerID,
                timestamp = Date(packet.timestamp.toLong())
            )

            delegate?.onMessageReceived(message)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to process broadcast message: ${e.message}")
        }
    }
    
    /**
     * Handle (decrypted) private message addressed to us
     */
    private suspend fun handlePrivateMessage(packet: BitchatPacket, peerID: String) {
        try {
            // Verify signature if present
            if (packet.signature != null && !delegate?.verifySignature(packet, peerID)!!) {
                Log.w(TAG, "Invalid signature for private message from $peerID")
                return
            }

            // Parse message
            val message = BitchatMessage(
                sender = delegate?.getPeerNickname(peerID) ?: "unknown",
                content = String(packet.payload, Charsets.UTF_8),
                senderPeerID = peerID,
                timestamp = Date(packet.timestamp.toLong())
            )
            delegate?.onMessageReceived(message)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to process private message from $peerID: ${e.message}")
        }
    }
    
    /**
     * Handle leave message
     */
    suspend fun handleLeave(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        val content = String(packet.payload, Charsets.UTF_8)
        
        if (content.startsWith("#")) {
            // Channel leave
            delegate?.onChannelLeave(content, peerID)
        } else {
            // Peer disconnect
            delegate?.removePeer(peerID)
        }
        
        // Leave message relay is now handled by centralized PacketRelayManager
    }
    
    /**
     * Get debug information
     */
    fun getDebugInfo(): String {
        return buildString {
            appendLine("=== Message Handler Debug Info ===")
            appendLine("Handler Scope Active: ${handlerScope.isActive}")
            appendLine("My Peer ID: $myPeerID")
        }
    }
    
    /**
     * Convert hex string peer ID to binary data (8 bytes) - same as iOS implementation
     */
    private fun hexStringToByteArray(hexString: String): ByteArray {
        val result = ByteArray(8) { 0 } // Initialize with zeros, exactly 8 bytes
        var tempID = hexString
        var index = 0
        
        while (tempID.length >= 2 && index < 8) {
            val hexByte = tempID.substring(0, 2)
            val byte = hexByte.toIntOrNull(16)?.toByte()
            if (byte != null) {
                result[index] = byte
            }
            tempID = tempID.substring(2)
            index++
        }
        
        return result
    }

    /**
     * Shutdown the handler
     */
    fun shutdown() {
        handlerScope.cancel()
    }

    /**
     * Handle favorite/unfavorite notification received over mesh as a private message.
     * Content format: "[FAVORITED]:npub..." or "[UNFAVORITED]:npub..."
     */
    private fun handleFavoriteNotificationFromMesh(content: String, fromPeerID: String) {
        try {
            val isFavorite = content.startsWith("[FAVORITED]")
            val npub = content.substringAfter(":", "").trim().takeIf { it.startsWith("npub1") }

            // Update mutual favorite status in persistence
            // Resolve full Noise key if available via delegate peer info
            val peerInfo = delegate?.getPeerInfo(fromPeerID)
            val noiseKey = peerInfo?.noisePublicKey
            if (noiseKey != null) {
                com.bitchat.android.favorites.FavoritesPersistenceService.shared.updatePeerFavoritedUs(noiseKey, isFavorite)
                if (npub != null) {
                    // Index by noise key and current mesh peerID for fast Nostr routing
                    com.bitchat.android.favorites.FavoritesPersistenceService.shared.updateNostrPublicKey(noiseKey, npub)
                    com.bitchat.android.favorites.FavoritesPersistenceService.shared.updateNostrPublicKeyForPeerID(fromPeerID, npub)
                }

                // Determine iOS-style guidance text
                val rel = com.bitchat.android.favorites.FavoritesPersistenceService.shared.getFavoriteStatus(noiseKey)
                val guidance = if (isFavorite) {
                    if (rel?.isFavorite == true) {
                        " — mutual! You can continue DMs via Nostr when out of mesh."
                    } else {
                        " — favorite back to continue DMs later."
                    }
                } else {
                    ". DMs over Nostr will pause unless you both favorite again."
                }

                // Emit system message via delegate callback
                val action = if (isFavorite) "favorited" else "unfavorited"
                val sys = com.bitchat.android.model.BitchatMessage(
                    sender = "system",
                    content = "${peerInfo.nickname} $action you$guidance",
                    timestamp = java.util.Date(),
                    isRelay = false
                )
                delegate?.onMessageReceived(sys)
            }
        } catch (_: Exception) {
            // Best-effort; ignore errors
        }
    }
    
    /**
     * Handle file transfer message - receives file chunks via mesh
     * Stores chunks in metadata map and assembles complete file when all chunks received
     */
    suspend fun handleFileTransfer(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing file transfer from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) return
        
        try {
            // Decode file transfer packet
            val filePacket = com.bitchat.android.model.FileTransferPacket.decode(packet.payload)
            if (filePacket == null) {
                Log.w(TAG, "Failed to decode file transfer packet from $peerID")
                return
            }
            
            Log.d(TAG, "📎 Received file chunk ${filePacket.chunkIndex + 1}/${filePacket.totalChunks} " +
                    "for ${filePacket.fileName} (${filePacket.fileSize} bytes total)${if (filePacket.isCompressed) " [COMPRESSED]" else ""}")
            
            // Get or create metadata for this file transfer
            val metadata = fileTransferMetadata.getOrPut(filePacket.fileID) {
                com.bitchat.android.model.FileTransferMetadata(
                    fileID = filePacket.fileID,
                    fileName = filePacket.fileName,
                    fileType = filePacket.fileType,
                    fileSize = filePacket.fileSize,
                    totalChunks = filePacket.totalChunks,
                    senderName = filePacket.senderName,
                    senderPeerID = peerID,
                    isCompressed = filePacket.isCompressed
                )
            }
            
            // Add chunk to metadata
            val isNewChunk = metadata.addChunk(filePacket.chunkIndex, filePacket.chunkData)
            
            // Update progress for every new chunk to ensure smooth progress bar
            if (isNewChunk) {
                val progress = metadata.getProgress()
                delegate?.onFileTransferProgress(filePacket.fileID, filePacket.fileName, progress)
            }
            
            // Log progress every 5 chunks for better visibility
            if ((filePacket.chunkIndex + 1) % 5 == 0 || filePacket.chunkIndex == filePacket.totalChunks - 1) {
                Log.d(TAG, "📥 Received chunk ${filePacket.chunkIndex + 1}/${filePacket.totalChunks} for ${filePacket.fileName} (${(metadata.receivedChunks.size * 100 / metadata.totalChunks)}%)")
            }
            
            // Only send acknowledgment for new chunks (avoid ACK spam for duplicates)
            if (isNewChunk) {
                sendFileTransferAck(filePacket.fileID, filePacket.chunkIndex, peerID)
            } else {
                Log.d(TAG, "Duplicate chunk ${filePacket.chunkIndex} for ${filePacket.fileName}, skipping ACK")
            }
            
            // Check if file is complete
            if (metadata.isComplete()) {
                Log.d(TAG, "✅ File transfer complete: ${filePacket.fileName}")
                Log.d(TAG, "📊 File stats - Chunks: ${metadata.receivedChunks.size}/${metadata.totalChunks}, Compressed: ${filePacket.isCompressed}")
                
                // Assemble file from chunks
                val completeFileData = metadata.assembleFile()
                if (completeFileData != null) {
                    Log.d(TAG, "📦 Assembled file data: ${completeFileData.size} bytes")
                    
                    // Decompress if needed
                    val finalFileData = if (filePacket.isCompressed) {
                        Log.d(TAG, "🗜️ Decompressing received file: ${filePacket.fileName} (${completeFileData.size} bytes compressed)")
                        try {
                            val decompressed = FileCompressionUtil.decompressBytes(completeFileData)
                            Log.d(TAG, "✅ Decompressed to ${decompressed.size} bytes")
                            decompressed
                        } catch (e: Exception) {
                            Log.e(TAG, "❌ Failed to decompress ${filePacket.fileName}: ${e.message}")
                            e.printStackTrace()
                            null
                        }
                    } else {
                        Log.d(TAG, "📄 File not compressed, using raw data")
                        completeFileData
                    }
                    
                    if (finalFileData != null) {
                        Log.d(TAG, "💾 File complete! Setting progress to 100% for ${filePacket.fileName}")
                        
                        // IMPORTANT: Update progress to 100% BEFORE notifying file received
                        // This ensures the UI shows 100% before the file disappears from active downloads
                        delegate?.onFileTransferProgress(filePacket.fileID, filePacket.fileName, 1.0f)
                        
                        Log.d(TAG, "💾 Calling delegate.onFileReceived for ${filePacket.fileName} (${finalFileData.size} bytes)")
                        Log.d(TAG, "📍 Delegate is null: ${delegate == null}")
                        
                        // Notify delegate about completed file transfer
                        delegate?.onFileReceived(
                            fileID = filePacket.fileID,
                            fileName = filePacket.fileName,
                            fileType = filePacket.fileType,
                            fileData = finalFileData,
                            senderPeerID = peerID,
                            senderName = filePacket.senderName
                        )
                        
                        Log.d(TAG, "✅ Delegate callback completed for ${filePacket.fileName}")
                        
                        // Clean up metadata
                        fileTransferMetadata.remove(filePacket.fileID)
                    } else {
                        Log.e(TAG, "Failed to process file ${filePacket.fileName}")
                    }
                } else {
                    Log.e(TAG, "Failed to assemble file ${filePacket.fileName}")
                }
            }
            // Progress already updated above for every new chunk
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file transfer from $peerID: ${e.message}")
        }
    }
    
    /**
     * Handle file transfer acknowledgment - tracks delivery confirmations for chunks
     */
    suspend fun handleFileTransferAck(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "Processing file transfer ACK from $peerID")
        
        // Skip our own messages
        if (peerID == myPeerID) return
        
        try {
            // Decode ACK packet
            val ackPacket = com.bitchat.android.model.FileTransferAckPacket.decode(packet.payload)
            if (ackPacket == null) {
                Log.w(TAG, "Failed to decode file transfer ACK from $peerID")
                return
            }
            
            Log.d(TAG, "📬 File transfer ACK from $peerID for chunk ${ackPacket.chunkIndex} of ${ackPacket.fileID}")
            
            // Notify delegate about chunk delivery confirmation
            delegate?.onFileTransferAckReceived(
                fileID = ackPacket.fileID,
                chunkIndex = ackPacket.chunkIndex,
                receiverPeerID = peerID
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing file transfer ACK from $peerID: ${e.message}")
        }
    }
    
    /**
     * Send file transfer acknowledgment for received chunk
     */
    private suspend fun sendFileTransferAck(fileID: String, chunkIndex: Int, senderPeerID: String) {
        try {
            // Create ACK packet
            val ackPacket = com.bitchat.android.model.FileTransferAckPacket(
                fileID = fileID,
                chunkIndex = chunkIndex,
                receiverPeerID = myPeerID,
                timestamp = System.currentTimeMillis()
            )
            
            // Encode ACK packet
            val ackPayload = ackPacket.encode()
            
            // Create packet
            val packet = BitchatPacket(
                version = 1u,
                type = MessageType.FILE_TRANSFER_ACK.value,
                senderID = hexStringToByteArray(myPeerID),
                recipientID = hexStringToByteArray(senderPeerID),
                timestamp = System.currentTimeMillis().toULong(),
                payload = ackPayload,
                signature = null,
                ttl = 7u
            )
            
            delegate?.sendPacket(packet)
            Log.d(TAG, "📤 Sent file transfer ACK to $senderPeerID for chunk $chunkIndex")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send file transfer ACK to $senderPeerID: ${e.message}")
        }
    }
    
    /**
     * Handle quiz distribution message - receives quiz data via mesh network
     */
    suspend fun handleQuizDistribution(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "📚 Processing quiz distribution from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) {
            Log.d(TAG, "Skipping quiz from self")
            return
        }
        
        try {
            // Convert payload to JSON string
            val quizJson = String(packet.payload, Charsets.UTF_8)
            Log.d(TAG, "📚 Received quiz JSON: ${quizJson.take(100)}...")
            
            // Notify delegate about received quiz
            delegate?.onQuizReceived(quizJson, peerID)
            Log.d(TAG, "✅ Quiz distribution processed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing quiz distribution from $peerID: ${e.message}", e)
        }
    }
    
    /**
     * Handle quiz submission message - receives student's quiz answers via mesh network
     */
    suspend fun handleQuizSubmission(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "📝 Processing quiz submission from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) {
            Log.d(TAG, "Skipping submission from self")
            return
        }
        
        try {
            // Convert payload to JSON string
            val submissionJson = String(packet.payload, Charsets.UTF_8)
            Log.d(TAG, "📝 Received submission JSON: ${submissionJson.take(100)}...")
            
            // Notify delegate about received submission
            delegate?.onQuizSubmissionReceived(submissionJson, peerID)
            Log.d(TAG, "✅ Quiz submission processed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing quiz submission from $peerID: ${e.message}", e)
        }
    }
    
    // Map to track file transfer metadata for incomplete transfers
    private val fileTransferMetadata = mutableMapOf<String, com.bitchat.android.model.FileTransferMetadata>()
    
    /**
     * Handle class announcement from teacher - broadcasts class info to students
     */
    suspend fun handleClassAnnounce(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "📚 Processing class announcement from $peerID (${packet.payload.size} bytes)")
        
        // Skip our own messages
        if (peerID == myPeerID) {
            Log.d(TAG, "Skipping class announcement from self")
            return
        }
        
        try {
            // Convert payload to JSON string
            val classJson = String(packet.payload, Charsets.UTF_8)
            Log.d(TAG, "📚 Received class announcement: ${classJson.take(100)}...")
            
            // Notify delegate about received class announcement
            delegate?.onClassAnnouncementReceived(classJson, peerID)
            Log.d(TAG, "✅ Class announcement processed successfully")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing class announcement from $peerID: ${e.message}", e)
        }
    }
    
    /**
     * Handle class discovery request from student
     */
    suspend fun handleClassDiscovery(routed: RoutedPacket) {
        val packet = routed.packet
        val peerID = routed.peerID ?: "unknown"
        
        Log.d(TAG, "🔍 Processing class discovery request from $peerID")
        
        // Skip our own messages
        if (peerID == myPeerID) {
            Log.d(TAG, "Skipping class discovery from self")
            return
        }
        
        try {
            // Get requested class code from payload
            val classCode = String(packet.payload, Charsets.UTF_8)
            Log.d(TAG, "🔍 Student requesting class info for: $classCode")
            
            // Notify delegate to potentially respond with class info
            delegate?.onClassDiscoveryRequested(classCode, peerID)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error processing class discovery request from $peerID: ${e.message}", e)
        }
    }
}

/**
 * Delegate interface for message handler callbacks
 */
interface MessageHandlerDelegate {
    // Peer management
    fun addOrUpdatePeer(peerID: String, nickname: String): Boolean
    fun removePeer(peerID: String)
    fun updatePeerNickname(peerID: String, nickname: String)
    fun getPeerNickname(peerID: String): String?
    fun getNetworkSize(): Int
    fun getMyNickname(): String?
    fun getPeerInfo(peerID: String): PeerInfo?
    fun updatePeerInfo(peerID: String, nickname: String, noisePublicKey: ByteArray, signingPublicKey: ByteArray, isVerified: Boolean): Boolean
    
    // Packet operations
    fun sendPacket(packet: BitchatPacket)
    fun relayPacket(routed: RoutedPacket)
    fun getBroadcastRecipient(): ByteArray
    
    // Cryptographic operations
    fun verifySignature(packet: BitchatPacket, peerID: String): Boolean
    fun encryptForPeer(data: ByteArray, recipientPeerID: String): ByteArray?
    fun decryptFromPeer(encryptedData: ByteArray, senderPeerID: String): ByteArray?
    fun verifyEd25519Signature(signature: ByteArray, data: ByteArray, publicKey: ByteArray): Boolean
    
    // Noise protocol operations
    fun hasNoiseSession(peerID: String): Boolean
    fun initiateNoiseHandshake(peerID: String)
    fun processNoiseHandshakeMessage(payload: ByteArray, peerID: String): ByteArray?
    fun updatePeerIDBinding(newPeerID: String, nickname: String,
                           publicKey: ByteArray, previousPeerID: String?)
    
    // Message operations
    fun decryptChannelMessage(encryptedContent: ByteArray, channel: String): String?

    // Callbacks
    fun onMessageReceived(message: BitchatMessage)
    fun onChannelLeave(channel: String, fromPeer: String)
    fun onDeliveryAckReceived(messageID: String, peerID: String)
    fun onReadReceiptReceived(messageID: String, peerID: String)
    
    // File transfer callbacks
    fun onFileReceived(fileID: String, fileName: String, fileType: String, fileData: ByteArray, senderPeerID: String, senderName: String)
    fun onFileTransferProgress(fileID: String, fileName: String, progress: Float)
    fun onFileTransferAckReceived(fileID: String, chunkIndex: Int, receiverPeerID: String)
    
    // Quiz distribution callbacks
    fun onQuizReceived(quizJson: String, senderPeerID: String)
    fun onQuizSubmissionReceived(submissionJson: String, senderPeerID: String)
    
    // Class announcement callbacks
    fun onClassAnnouncementReceived(classJson: String, senderPeerID: String)
    fun onClassDiscoveryRequested(classCode: String, requesterPeerID: String)
}