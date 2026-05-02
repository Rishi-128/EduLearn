package com.bitchat.android.storage

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.util.UUID

/**
 * Local file storage manager for EduLearn
 * Handles offline storage of documents and videos uploaded by teachers
 */
class EduLearnFileStorage(private val context: Context) {
    
    private val storageDir = File(context.filesDir, "edulearn_content")
    private val documentsDir = File(storageDir, "documents")
    private val videosDir = File(storageDir, "videos")
    
    init {
        // Create storage directories if they don't exist
        documentsDir.mkdirs()
        videosDir.mkdirs()
    }
    
    /**
     * Save a document file to local storage
     */
    fun saveDocument(inputStream: InputStream, originalName: String): String? {
        return try {
            val fileId = UUID.randomUUID().toString()
            val extension = originalName.substringAfterLast(".", "")
            val fileName = "${fileId}.${extension}"
            val file = File(documentsDir, fileName)
            
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            fileId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Save a video file to local storage
     */
    fun saveVideo(inputStream: InputStream, originalName: String): String? {
        return try {
            val fileId = UUID.randomUUID().toString()
            val extension = originalName.substringAfterLast(".", "")
            val fileName = "${fileId}.${extension}"
            val file = File(videosDir, fileName)
            
            file.outputStream().use { output ->
                inputStream.copyTo(output)
            }
            
            fileId
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Get file by ID
     */
    fun getFile(fileId: String, isVideo: Boolean = false): File? {
        val searchDir = if (isVideo) videosDir else documentsDir
        return searchDir.listFiles()?.find { it.nameWithoutExtension == fileId }
    }
    
    /**
     * Delete file by ID
     */
    fun deleteFile(fileId: String, isVideo: Boolean = false): Boolean {
        val file = getFile(fileId, isVideo)
        return file?.delete() ?: false
    }
    
    /**
     * Get all stored files
     */
    fun getAllFiles(): List<StoredFile> {
        val files = mutableListOf<StoredFile>()
        
        // Add documents
        documentsDir.listFiles()?.forEach { file ->
            files.add(
                StoredFile(
                    id = file.nameWithoutExtension,
                    name = file.name,
                    size = file.length(),
                    isVideo = false,
                    lastModified = file.lastModified()
                )
            )
        }
        
        // Add videos
        videosDir.listFiles()?.forEach { file ->
            files.add(
                StoredFile(
                    id = file.nameWithoutExtension,
                    name = file.name,
                    size = file.length(),
                    isVideo = true,
                    lastModified = file.lastModified()
                )
            )
        }
        
        return files.sortedByDescending { it.lastModified }
    }
    
    /**
     * Get storage usage info
     */
    fun getStorageInfo(): StorageInfo {
        val documentSize = documentsDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
            
        val videoSize = videosDir.walkTopDown()
            .filter { it.isFile }
            .map { it.length() }
            .sum()
            
        return StorageInfo(
            totalSize = documentSize + videoSize,
            documentCount = documentsDir.listFiles()?.size ?: 0,
            videoCount = videosDir.listFiles()?.size ?: 0
        )
    }
}

data class StoredFile(
    val id: String,
    val name: String,
    val size: Long,
    val isVideo: Boolean,
    val lastModified: Long
)

data class StorageInfo(
    val totalSize: Long,
    val documentCount: Int,
    val videoCount: Int
)
