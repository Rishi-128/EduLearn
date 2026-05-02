package com.bitchat.android.test

import com.bitchat.android.ui.MessageSpecialParser

fun main() {
    // Test cases for the PDF link fix
    val testMessages = listOf(
        "Check out this document.pdf",
        "Here's the report: annual_report.pdf for review",
        "Visit https://example.com for more info",
        "Download from www.example.com/file.pdf",
        "The file math_homework.docx is ready",
        "See presentation.pptx tomorrow"
    )
    
    println("Testing URL detection (should NOT detect local files as URLs):")
    testMessages.forEach { message ->
        val urls = MessageSpecialParser.findUrls(message)
        println("Message: '$message'")
        if (urls.isEmpty()) {
            println("  ✅ No URLs detected (correct for local files)")
        } else {
            urls.forEach { url ->
                println("  ❌ URL detected: '${url.url}' at position ${url.start}-${url.endExclusive}")
            }
        }
        println()
    }
}