package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Built-in Document Viewer - COMPLETELY OFFLINE
 * No external apps needed - view PDFs, text documents directly in app
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltInDocumentViewer(
    documentTitle: String,
    documentContent: DocumentContent,
    onBackClick: () -> Unit
) {
    var zoomLevel by remember { mutableStateOf(1f) }
    val scrollState = rememberScrollState()
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Document Viewer Header
        TopAppBar(
            title = { 
                Text(
                    text = documentTitle,
                    maxLines = 1
                ) 
            },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                // Zoom Controls
                IconButton(
                    onClick = { if (zoomLevel > 0.5f) zoomLevel -= 0.2f }
                ) {
                    Icon(Icons.Default.ZoomOut, contentDescription = "Zoom Out")
                }
                
                Text(
                    text = "${(zoomLevel * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
                
                IconButton(
                    onClick = { if (zoomLevel < 2.0f) zoomLevel += 0.2f }
                ) {
                    Icon(Icons.Default.ZoomIn, contentDescription = "Zoom In")
                }
            }
        )
        
        // Document Content
        when (documentContent) {
            is DocumentContent.TextDocument -> {
                TextDocumentViewer(
                    content = documentContent,
                    zoomLevel = zoomLevel,
                    scrollState = scrollState
                )
            }
            is DocumentContent.PDFDocument -> {
                PDFDocumentViewer(
                    content = documentContent,
                    zoomLevel = zoomLevel
                )
            }
            is DocumentContent.ImageDocument -> {
                ImageDocumentViewer(
                    content = documentContent,
                    zoomLevel = zoomLevel,
                    scrollState = scrollState
                )
            }
        }
    }
}

@Composable
fun TextDocumentViewer(
    content: DocumentContent.TextDocument,
    zoomLevel: Float,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        Text(
            text = content.text,
            fontSize = (16 * zoomLevel).sp,
            lineHeight = (24 * zoomLevel).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Justify,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun PDFDocumentViewer(
    content: DocumentContent.PDFDocument,
    zoomLevel: Float
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        items(content.pages) { page ->
            PDFPageViewer(
                page = page,
                zoomLevel = zoomLevel
            )
        }
    }
}

@Composable
fun PDFPageViewer(
    page: PDFPage,
    zoomLevel: Float
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Page header
            Text(
                text = "Page ${page.pageNumber}",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.Black,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            
            // Page content (simulated text extraction)
            Text(
                text = page.textContent,
                fontSize = (14 * zoomLevel).sp,
                lineHeight = (20 * zoomLevel).sp,
                color = Color.Black,
                textAlign = TextAlign.Justify
            )
        }
    }
}

@Composable
fun ImageDocumentViewer(
    content: DocumentContent.ImageDocument,
    zoomLevel: Float,
    scrollState: androidx.compose.foundation.ScrollState
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // In a real app, this would display the actual image
        // For offline demo, showing placeholder
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height((300 * zoomLevel).dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "📄 ${content.fileName}",
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        
        if (content.description.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = content.description,
                fontSize = (14 * zoomLevel).sp,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

// Document content models for offline viewing
sealed class DocumentContent {
    data class TextDocument(
        val text: String
    ) : DocumentContent()
    
    data class PDFDocument(
        val pages: List<PDFPage>
    ) : DocumentContent()
    
    data class ImageDocument(
        val fileName: String,
        val description: String = ""
    ) : DocumentContent()
}

data class PDFPage(
    val pageNumber: Int,
    val textContent: String
)

// Offline sample documents for testing
object OfflineDocuments {
    // Document content will be loaded from teacher shares or storage
    // TODO: Implement document loading from Bluetooth mesh or local storage
    
    val mathChapter1 = DocumentContent.PDFDocument(pages = emptyList())
    val englishWorksheet = DocumentContent.TextDocument(text = "")
    val historyTimeline = DocumentContent.PDFDocument(pages = emptyList())
    val scienceVideo = DocumentContent.TextDocument(text = "")
}