package com.bitchat.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility for opening files using Android Intents
 * Handles file URIs with FileProvider for security
 */
object FileOpener {
    private const val TAG = "FileOpener"
    private const val FILE_PROVIDER_AUTHORITY = "com.bitchat.android.fileprovider"
    
    /**
     * Open a file with the appropriate app
     * @param context Android context
     * @param file The file to open
     * @return true if file was opened successfully, false otherwise
     */
    fun openFile(context: Context, file: File): Boolean {
        Log.d(TAG, "🔍 openFile called for: ${file.absolutePath}")
        Log.d(TAG, "📊 File exists: ${file.exists()}, size: ${file.length()}, readable: ${file.canRead()}")
        
        if (!file.exists()) {
            Log.e(TAG, "❌ File does not exist: ${file.absolutePath}")
            Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
            return false
        }
        
        try {
            Log.d(TAG, "🔐 Getting URI from FileProvider")
            
            // Get file URI using FileProvider for security
            val uri: Uri = FileProvider.getUriForFile(
                context,
                FILE_PROVIDER_AUTHORITY,
                file
            )
            
            Log.d(TAG, "✅ URI: $uri")
            
            // Determine MIME type
            val mimeType = getMimeType(file.name)
            Log.d(TAG, "📄 MIME type: $mimeType for file: ${file.name}")
            
            // Create intent to view file
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            Log.d(TAG, "🔍 Checking for app to open file...")
            
            // Check if there's an app that can handle this file type
            if (intent.resolveActivity(context.packageManager) != null) {
                Log.d(TAG, "✅ Found app, opening file...")
                context.startActivity(intent)
                Log.d(TAG, "✅ Opened file: ${file.name} with type: $mimeType")
                return true
            } else {
                Log.w(TAG, "⚠️ No app found to open file type: $mimeType")
                Toast.makeText(context, "No app found to open this file type", Toast.LENGTH_LONG).show()
                
                // Try with generic intent chooser
                Log.d(TAG, "🔄 Trying generic chooser...")
                val chooserIntent = Intent.createChooser(intent, "Open with")
                chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooserIntent)
                return true
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to open file: ${file.name}", e)
            e.printStackTrace()
            Toast.makeText(context, "Failed to open file: ${e.message}", Toast.LENGTH_LONG).show()
            return false
        }
    }
    
    /**
     * Get MIME type from filename extension
     */
    private fun getMimeType(filename: String): String {
        val extension = filename.substringAfterLast('.', "").lowercase()
        
        return when (extension) {
            // Documents
            "pdf" -> "application/pdf"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "txt" -> "text/plain"
            "csv" -> "text/csv"
            "rtf" -> "application/rtf"
            "odt" -> "application/vnd.oasis.opendocument.text"
            "ods" -> "application/vnd.oasis.opendocument.spreadsheet"
            "odp" -> "application/vnd.oasis.opendocument.presentation"
            
            // Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            
            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            
            // Video
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mkv" -> "video/x-matroska"
            "mov" -> "video/quicktime"
            "wmv" -> "video/x-ms-wmv"
            
            // Archives
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            
            // Code/Text
            "html", "htm" -> "text/html"
            "xml" -> "text/xml"
            "json" -> "application/json"
            "java" -> "text/x-java-source"
            "kt" -> "text/x-kotlin-source"
            "py" -> "text/x-python"
            "js" -> "text/javascript"
            "cpp", "cc" -> "text/x-c++src"
            "c" -> "text/x-csrc"
            "h" -> "text/x-chdr"
            
            // Android
            "apk" -> "application/vnd.android.package-archive"
            
            // Default
            else -> "application/octet-stream"
        }
    }
    
    /**
     * Get file extension from filename
     */
    fun getFileExtension(filename: String): String {
        return filename.substringAfterLast('.', "").lowercase()
    }
    
    /**
     * Check if file type is supported for viewing
     */
    fun isFileTypeSupported(filename: String): Boolean {
        val extension = getFileExtension(filename)
        val unsupportedExtensions = setOf("exe", "dll", "so", "bin")
        return extension !in unsupportedExtensions
    }
}
