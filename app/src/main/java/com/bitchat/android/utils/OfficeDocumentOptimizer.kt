package com.bitchat.android.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Office document optimization utility using Apache POI
 * Supports Word (.docx), Excel (.xlsx), PowerPoint (.pptx)
 */
object OfficeDocumentOptimizer {
    
    /**
     * Optimizes a Word document (.doc, .docx)
     */
    suspend fun optimizeWord(
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val originalSize = inputFile.length()
            
            // For .docx (Open XML format)
            if (inputFile.extension.lowercase() == "docx") {
                optimizeOpenXMLDocument(inputFile, outputFile, quality)
            } else {
                // For legacy .doc format, just copy
                inputFile.copyTo(outputFile, overwrite = true)
            }
            
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
            if (outputFile.exists()) outputFile.delete()
            OptimizationResult.Error("Word optimization failed: ${e.message}")
        }
    }
    
    /**
     * Optimizes an Excel spreadsheet (.xls, .xlsx)
     */
    suspend fun optimizeExcel(
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val originalSize = inputFile.length()
            
            if (inputFile.extension.lowercase() == "xlsx") {
                optimizeOpenXMLDocument(inputFile, outputFile, quality)
            } else {
                inputFile.copyTo(outputFile, overwrite = true)
            }
            
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
            if (outputFile.exists()) outputFile.delete()
            OptimizationResult.Error("Excel optimization failed: ${e.message}")
        }
    }
    
    /**
     * Optimizes a PowerPoint presentation (.ppt, .pptx)
     */
    suspend fun optimizePowerPoint(
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality
    ): OptimizationResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        
        try {
            val originalSize = inputFile.length()
            
            if (inputFile.extension.lowercase() == "pptx") {
                optimizeOpenXMLDocument(inputFile, outputFile, quality)
            } else {
                inputFile.copyTo(outputFile, overwrite = true)
            }
            
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
            if (outputFile.exists()) outputFile.delete()
            OptimizationResult.Error("PowerPoint optimization failed: ${e.message}")
        }
    }
    
    /**
     * Optimizes Open XML documents (docx, xlsx, pptx) by recompressing their ZIP container
     * and optimizing embedded images
     */
    private fun optimizeOpenXMLDocument(
        inputFile: File,
        outputFile: File,
        quality: CompressionQuality
    ) {
        val tempDir = File(inputFile.parent, "temp_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        
        try {
            // Extract ZIP contents
            ZipInputStream(FileInputStream(inputFile)).use { zis ->
                var entry: ZipEntry? = zis.nextEntry
                while (entry != null) {
                    val entryFile = File(tempDir, entry.name)
                    
                    if (entry.isDirectory) {
                        entryFile.mkdirs()
                    } else {
                        entryFile.parentFile?.mkdirs()
                        FileOutputStream(entryFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        
                        // Optimize images in media folder
                        if (entry.name.startsWith("word/media/") || 
                            entry.name.startsWith("xl/media/") ||
                            entry.name.startsWith("ppt/media/")) {
                            if (isImageFile(entryFile)) {
                                optimizeEmbeddedImage(entryFile, quality)
                            }
                        }
                    }
                    
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            
            // Recompress to output file with maximum compression
            ZipOutputStream(FileOutputStream(outputFile)).use { zos ->
                zos.setLevel(9)  // Maximum compression
                addDirectoryToZip(tempDir, tempDir, zos)
            }
            
        } finally {
            // Clean up temp directory
            tempDir.deleteRecursively()
        }
    }
    
    /**
     * Adds a directory to a ZIP output stream
     */
    private fun addDirectoryToZip(rootDir: File, sourceDir: File, zos: ZipOutputStream) {
        sourceDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addDirectoryToZip(rootDir, file, zos)
            } else {
                val zipPath = file.relativeTo(rootDir).path.replace(File.separator, "/")
                val entry = ZipEntry(zipPath)
                zos.putNextEntry(entry)
                FileInputStream(file).use { fis ->
                    fis.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
    
    /**
     * Optimizes an embedded image file
     */
    private fun optimizeEmbeddedImage(imageFile: File, quality: CompressionQuality) {
        try {
            val tempOutput = File(imageFile.parent, "${imageFile.name}.temp")
            
            // Use ImageOptimizer for consistent compression
            val result = kotlinx.coroutines.runBlocking {
                ImageOptimizer.optimizeImage(imageFile, tempOutput, quality)
            }
            
            if (result is OptimizationResult.Success && tempOutput.exists()) {
                // Replace original with optimized
                tempOutput.copyTo(imageFile, overwrite = true)
                tempOutput.delete()
            } else {
                tempOutput.delete()
            }
        } catch (e: Exception) {
            // Skip optimization on error, keep original
        }
    }
    
    /**
     * Checks if a file is an image based on extension
     */
    private fun isImageFile(file: File): Boolean {
        val extension = file.extension.lowercase()
        return extension in setOf("jpg", "jpeg", "png", "bmp", "gif", "webp")
    }
}
