package com.bitchat.android.utils

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Image optimization utility for reducing image file sizes
 * Supports JPEG, PNG, BMP, WebP formats
 */
object ImageOptimizer {
    
    private const val MAX_IMAGE_DIMENSION = 1920  // Max width/height for images
    
    /**
     * Optimizes an image file by downscaling and recompressing
     */
    suspend fun optimizeImage(
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val originalSize = inputFile.length()
            
            // Skip if file is already small
            if (originalSize < 100 * 1024) { // Less than 100KB
                inputFile.copyTo(outputFile, overwrite = true)
                return@withContext OptimizationResult.Skipped
            }
            
            // Decode image
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(inputFile.absolutePath, options)
            
            // Calculate sample size for downscaling
            val sampleSize = calculateSampleSize(
                options.outWidth,
                options.outHeight,
                MAX_IMAGE_DIMENSION,
                MAX_IMAGE_DIMENSION
            )
            
            // Decode with sample size
            options.inJustDecodeBounds = false
            options.inSampleSize = sampleSize
            options.inPreferredConfig = Bitmap.Config.RGB_565  // Use less memory
            
            val bitmap = BitmapFactory.decodeFile(inputFile.absolutePath, options)
                ?: return@withContext OptimizationResult.Error("Failed to decode image")
            
            // Further resize if still too large
            val scaledBitmap = if (bitmap.width > MAX_IMAGE_DIMENSION || bitmap.height > MAX_IMAGE_DIMENSION) {
                val scale = minOf(
                    MAX_IMAGE_DIMENSION.toFloat() / bitmap.width,
                    MAX_IMAGE_DIMENSION.toFloat() / bitmap.height
                )
                val matrix = Matrix().apply { postScale(scale, scale) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true).also {
                    if (it != bitmap) bitmap.recycle()
                }
            } else {
                bitmap
            }
            
            // Save optimized image
            FileOutputStream(outputFile).use { out ->
                val format = when (inputFile.extension.lowercase()) {
                    "png" -> Bitmap.CompressFormat.PNG
                    "webp" -> Bitmap.CompressFormat.WEBP
                    else -> Bitmap.CompressFormat.JPEG
                }
                scaledBitmap.compress(format, quality.imageQuality, out)
            }
            
            scaledBitmap.recycle()
            
            val optimizedSize = outputFile.length()
            val compressionRatio = optimizedSize.toFloat() / originalSize
            val timeTaken = System.currentTimeMillis() - startTime
            
            OptimizationResult.Success(
                originalSize = originalSize,
                optimizedSize = optimizedSize,
                compressionRatio = compressionRatio,
                timeTaken = timeTaken
            )
            
        } catch (e: Exception) {
            OptimizationResult.Error("Image optimization failed: ${e.message}")
        }
    }
    
    /**
     * Calculates the optimal sample size for image downscaling
     */
    private fun calculateSampleSize(
        width: Int,
        height: Int,
        reqWidth: Int,
        reqHeight: Int
    ): Int {
        var inSampleSize = 1
        
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        
        return inSampleSize
    }
    
    /**
     * Estimates the compressed size without actually compressing
     */
    fun estimateCompressedSize(file: File, quality: CompressionQuality): Long {
        val originalSize = file.length()
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> (originalSize * 0.70).toLong()  // 30% reduction
            "png", "bmp" -> (originalSize * 0.40).toLong()   // 60% reduction
            "webp" -> (originalSize * 0.60).toLong()         // 40% reduction
            else -> originalSize
        }
    }
}
