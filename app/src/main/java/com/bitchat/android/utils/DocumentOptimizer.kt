package com.bitchat.android.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Document optimization utility for reducing file sizes
 * Supports PDF, Office documents (Word, Excel, PowerPoint), and images
 */
object DocumentOptimizer {
    
    /**
     * Determines if a file should be optimized based on its MIME type or extension
     */
    fun shouldOptimize(file: File): Boolean {
        val extension = file.extension.lowercase()
        return when (extension) {
            // Documents
            "pdf" -> true
            "doc", "docx" -> true
            "xls", "xlsx" -> true
            "ppt", "pptx" -> true
            
            // Images
            "jpg", "jpeg", "png", "bmp", "webp" -> true
            
            // Skip already optimized or incompatible formats
            "zip", "rar", "7z" -> false
            "mp3", "mp4", "avi", "mov", "mkv" -> false
            "apk", "exe", "dmg" -> false
            
            else -> false
        }
    }
    
    /**
     * Optimizes a document file based on its type
     * Returns the optimized file path or null if optimization failed
     */
    suspend fun optimizeDocument(
        context: Context,
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality = CompressionQuality.BALANCED
    ): OptimizationResult = withContext(Dispatchers.IO) {
        try {
            val extension = inputFile.extension.lowercase()
            
            val result = when (extension) {
                "pdf" -> PDFOptimizer.optimizePDF(inputFile, outputFile, quality)
                "doc", "docx" -> OfficeDocumentOptimizer.optimizeWord(inputFile, outputFile, quality)
                "xls", "xlsx" -> OfficeDocumentOptimizer.optimizeExcel(inputFile, outputFile, quality)
                "ppt", "pptx" -> OfficeDocumentOptimizer.optimizePowerPoint(inputFile, outputFile, quality)
                "jpg", "jpeg", "png", "bmp", "webp" -> ImageOptimizer.optimizeImage(inputFile, outputFile, quality)
                else -> OptimizationResult.Unsupported("Unsupported file type: $extension")
            }
            
            result
        } catch (e: Exception) {
            OptimizationResult.Error(e.message ?: "Unknown optimization error")
        }
    }
    
    /**
     * Gets the estimated compression ratio for a file type
     */
    fun getEstimatedCompressionRatio(file: File): Float {
        return when (file.extension.lowercase()) {
            "pdf" -> 0.50f  // 50% reduction
            "doc", "docx" -> 0.60f  // 40% reduction
            "xls", "xlsx" -> 0.55f  // 45% reduction
            "ppt", "pptx" -> 0.45f  // 55% reduction
            "jpg", "jpeg" -> 0.70f  // 30% reduction
            "png", "bmp" -> 0.40f  // 60% reduction
            else -> 0.90f  // 10% reduction
        }
    }
}

/**
 * Compression quality levels
 */
enum class CompressionQuality(val imageQuality: Int, val pdfDPI: Int) {
    HIGH(90, 150),      // Best quality, less compression
    BALANCED(75, 100),  // Good balance
    AGGRESSIVE(60, 72)  // Maximum compression
}

/**
 * Result of optimization operation
 */
sealed class OptimizationResult {
    data class Success(
        val originalSize: Long,
        val optimizedSize: Long,
        val compressionRatio: Float,
        val timeTaken: Long
    ) : OptimizationResult() {
        val savedBytes: Long get() = originalSize - optimizedSize
        val savedPercentage: Float get() = (savedBytes.toFloat() / originalSize) * 100f
    }
    
    data class Error(val message: String) : OptimizationResult()
    data class Unsupported(val message: String) : OptimizationResult()
    object Skipped : OptimizationResult()
}
