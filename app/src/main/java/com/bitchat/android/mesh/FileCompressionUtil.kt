package com.bitchat.android.mesh

import android.content.Context
import android.util.Log
import com.bitchat.android.utils.DocumentOptimizer
import com.bitchat.android.utils.CompressionQuality
import com.bitchat.android.utils.OptimizationResult
import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Utility for compressing and decompressing files for mesh network transfer.
 * Now supports two compression modes:
 * 1. Document Optimization (PDF, Office, Images) - Uses specialized optimizers for 40-60% reduction
 * 2. GZIP compression (text, other files) - Generic compression for 70-90% text reduction
 */
object FileCompressionUtil {
    private const val TAG = "FileCompression"
    private const val BUFFER_SIZE = 8192
    
    /**
     * Compress a file using appropriate method.
     * Uses DocumentOptimizer for supported file types, falls back to GZIP for others.
     * @param inputFile The file to compress
     * @param outputFile The compressed output file
     * @param context Android context (optional, for document optimization)
     * @return true if compression succeeded, false otherwise
     */
    fun compressFile(inputFile: File, outputFile: File, context: Context? = null): Boolean {
        return try {
            // Try document optimization first if supported
            if (context != null && DocumentOptimizer.shouldOptimize(inputFile)) {
                val result = runBlocking {
                    DocumentOptimizer.optimizeDocument(
                        context,
                        inputFile,
                        outputFile,
                        CompressionQuality.BALANCED
                    )
                }
                
                when (result) {
                    is OptimizationResult.Success -> {
                        Log.d(TAG, "Optimized ${inputFile.name}: ${result.originalSize}B → ${result.optimizedSize}B (${String.format("%.1f", result.savedPercentage)}% reduction)")
                        return true
                    }
                    is OptimizationResult.Error -> {
                        Log.w(TAG, "Document optimization failed, falling back to GZIP: ${result.message}")
                        // Fall through to GZIP
                    }
                    is OptimizationResult.Unsupported, OptimizationResult.Skipped -> {
                        // Fall through to GZIP
                    }
                }
            }
            
            // Use GZIP compression as fallback or for unsupported types
            compressFileWithGZIP(inputFile, outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress file: ${inputFile.name}", e)
            false
        }
    }
    
    /**
     * Compress a file using GZIP (original method, now private).
     */
    private fun compressFileWithGZIP(inputFile: File, outputFile: File): Boolean {
        return try {
            FileInputStream(inputFile).use { fis ->
                FileOutputStream(outputFile).use { fos ->
                    GZIPOutputStream(fos).use { gzos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (fis.read(buffer).also { len = it } > 0) {
                            gzos.write(buffer, 0, len)
                        }
                    }
                }
            }
            
            val originalSize = inputFile.length()
            val compressedSize = outputFile.length()
            val ratio = ((originalSize - compressedSize) * 100.0 / originalSize)
            
            Log.d(TAG, "Compressed ${inputFile.name}: ${originalSize}B → ${compressedSize}B (${String.format("%.1f", ratio)}% reduction)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to compress file: ${inputFile.name}", e)
            false
        }
    }
    
    /**
     * Decompress a GZIP file.
     * @param compressedFile The compressed file
     * @param outputFile The decompressed output file
     * @return true if decompression succeeded, false otherwise
     */
    fun decompressFile(compressedFile: File, outputFile: File): Boolean {
        return try {
            FileInputStream(compressedFile).use { fis ->
                GZIPInputStream(fis).use { gzis ->
                    FileOutputStream(outputFile).use { fos ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        var len: Int
                        while (gzis.read(buffer).also { len = it } > 0) {
                            fos.write(buffer, 0, len)
                        }
                    }
                }
            }
            
            val compressedSize = compressedFile.length()
            val decompressedSize = outputFile.length()
            
            Log.d(TAG, "Decompressed ${compressedFile.name}: ${compressedSize}B → ${decompressedSize}B")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decompress file: ${compressedFile.name}", e)
            false
        }
    }
    
    /**
     * Compress data in memory (for smaller files).
     */
    fun compressBytes(data: ByteArray): ByteArray {
        val bos = ByteArrayOutputStream(data.size)
        GZIPOutputStream(bos).use { gzos ->
            gzos.write(data)
        }
        return bos.toByteArray()
    }
    
    /**
     * Decompress data in memory.
     */
    fun decompressBytes(compressedData: ByteArray): ByteArray {
        val bis = ByteArrayInputStream(compressedData)
        val bos = ByteArrayOutputStream()
        GZIPInputStream(bis).use { gzis ->
            val buffer = ByteArray(BUFFER_SIZE)
            var len: Int
            while (gzis.read(buffer).also { len = it } > 0) {
                bos.write(buffer, 0, len)
            }
        }
        return bos.toByteArray()
    }
    
    /**
     * Check if compression is worth it based on file type.
     * Some files are already compressed and won't benefit.
     */
    fun shouldCompress(filename: String): Boolean {
        val extension = filename.substringAfterLast('.', "").lowercase()
        
        // Already compressed formats - skip compression
        val compressedFormats = setOf(
            "jpg", "jpeg", "png", "gif", "webp",  // Images
            "mp3", "mp4", "m4a", "aac", "ogg",    // Audio/Video
            "zip", "rar", "7z", "gz", "bz2",      // Archives
            "apk", "jar"                           // Android packages
        )
        
        return extension !in compressedFormats
    }
}
