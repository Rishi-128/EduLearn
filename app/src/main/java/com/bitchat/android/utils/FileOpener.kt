package com.bitchat.android.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

/**
 * Utility for opening files with appropriate apps using Android Intents
 */
object FileOpener {
    private const val TAG = "FileOpener"
    
    /**
     * Open a file with the default app for its type
     * @param context Android context
     * @param file File to open
     * @param mimeType MIME type of the file
     */
    fun openFile(context: Context, file: File, mimeType: String? = null) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🔍 ATTEMPTING TO OPEN FILE")
        Log.d(TAG, "File name: ${file.name}")
        Log.d(TAG, "File path: ${file.absolutePath}")
        Log.d(TAG, "File exists: ${file.exists()}")
        Log.d(TAG, "File size: ${if (file.exists()) file.length() else "N/A"} bytes")
        Log.d(TAG, "========================================")
        
        try {
            // Check 1: File exists?
            if (!file.exists()) {
                val errorMsg = """
                    ❌ FILE NOT FOUND!
                    
                    Expected location:
                    ${file.absolutePath}
                    
                    Directory exists: ${file.parentFile?.exists()}
                    
                    Files in directory:
                    ${file.parentFile?.listFiles()?.joinToString("\n") { it.name } ?: "Cannot list"}
                """.trimIndent()
                
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                Log.e(TAG, errorMsg)
                return
            }
            
            Toast.makeText(context, "✅ Step 1/4: File found!\n${file.name}\nSize: ${file.length()} bytes", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "✅ File exists: ${file.absolutePath}")
            
            // Check 2: Get URI
            val fileUri: Uri = try {
                // CRITICAL: Authority must match AndroidManifest.xml FileProvider configuration
                val authority = "${context.packageName}.fileprovider"
                Log.d(TAG, "🔑 Package: ${context.packageName}")
                Log.d(TAG, "🔑 FileProvider authority: $authority")
                Log.d(TAG, "🔑 File parent: ${file.parent}")
                Log.d(TAG, "🔑 Expected: com.bitchat.droid.fileprovider")
                
                val uri = FileProvider.getUriForFile(context, authority, file)
                
                Toast.makeText(context, "✅ Step 2/4: URI created!\n$uri", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ FileProvider URI created: $uri")
                uri
            } catch (e: Exception) {
                val errorMsg = """
                    ⚠️ FILEPROVIDER ERROR!
                    
                    ${e.javaClass.simpleName}: ${e.message}
                    
                    This usually means:
                    - File path not in file_paths.xml
                    - Wrong FileProvider authority
                    
                    Trying fallback...
                """.trimIndent()
                
                Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                Log.w(TAG, "⚠️ FileProvider failed: ${e.message}", e)
                Uri.fromFile(file)
            }
            
            // Check 3: MIME type
            val type = mimeType ?: getMimeType(file.name)
            Toast.makeText(context, "✅ Step 3/4: File type detected\n$type", Toast.LENGTH_SHORT).show()
            Log.d(TAG, "📊 MIME type: $type for extension: ${file.extension}")
            
            // Check 4: Try to open
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, type)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            val resolvedActivity = intent.resolveActivity(context.packageManager)
            Log.d(TAG, "🔍 Resolved activity: $resolvedActivity")
            
            // Check if any app can handle this file type
            if (resolvedActivity != null) {
                Toast.makeText(context, "✅ Step 4/4: Opening with app...\n${resolvedActivity.className}", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "✅ Opening with activity: ${resolvedActivity.className}")
                context.startActivity(intent)
            } else {
                Toast.makeText(context, "⚠️ No specific app for $type\nShowing app chooser...", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "⚠️ No app found for MIME type: $type, trying chooser")
                
                // No app found, try generic viewer
                val genericIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(fileUri, "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                
                try {
                    context.startActivity(Intent.createChooser(genericIntent, "Open with"))
                    Log.d(TAG, "✅ Chooser dialog shown")
                } catch (e: Exception) {
                    val errorMsg = """
                        ❌ NO APP AVAILABLE!
                        
                        File type: ${file.extension.uppercase()}
                        MIME type: $type
                        
                        SOLUTION:
                        Install an app from Play Store that can open ${file.extension.uppercase()} files
                        
                        Suggested apps:
                        - PDF: Adobe Reader, Google PDF Viewer
                        - Images: Google Photos
                        - Documents: Google Docs, Microsoft Office
                    """.trimIndent()
                    
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    Log.e(TAG, "❌ No app to open file: ${file.name}", e)
                }
            }
            
        } catch (e: Exception) {
            val errorMsg = """
                ❌ UNEXPECTED ERROR!
                
                ${e.javaClass.simpleName}
                ${e.message}
                
                File: ${file.name}
                Path: ${file.absolutePath}
            """.trimIndent()
            
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            Log.e(TAG, "❌ Error opening file: ${file.name}", e)
            e.printStackTrace()
        }
    }
    
    /**
     * Get MIME type based on file extension
     */
    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        
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
            
            // Images
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            
            // Videos
            "mp4" -> "video/mp4"
            "avi" -> "video/x-msvideo"
            "mov" -> "video/quicktime"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            
            // Audio
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            
            // Archives
            "zip" -> "application/zip"
            "rar" -> "application/x-rar-compressed"
            "7z" -> "application/x-7z-compressed"
            "tar" -> "application/x-tar"
            "gz" -> "application/gzip"
            
            // Code/Text
            "json" -> "application/json"
            "xml" -> "text/xml"
            "html" -> "text/html"
            "css" -> "text/css"
            "js" -> "text/javascript"
            "java" -> "text/x-java-source"
            "kt" -> "text/plain"
            "py" -> "text/x-python"
            
            // APK
            "apk" -> "application/vnd.android.package-archive"
            
            // Default
            else -> "*/*"
        }
    }
    
    /**
     * Share a file with other apps
     */
    fun shareFile(context: Context, file: File, mimeType: String? = null) {
        try {
            if (!file.exists()) {
                Toast.makeText(context, "File not found", Toast.LENGTH_SHORT).show()
                return
            }
            
            val fileUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            
            val type = mimeType ?: getMimeType(file.name)
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                setType(type)
                putExtra(Intent.EXTRA_STREAM, fileUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            context.startActivity(Intent.createChooser(shareIntent, "Share file"))
            
        } catch (e: Exception) {
            Toast.makeText(context, "Failed to share file: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e(TAG, "Error sharing file: ${file.name}", e)
        }
    }
}
