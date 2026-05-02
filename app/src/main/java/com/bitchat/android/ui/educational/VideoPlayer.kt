/*
 * UNUSED FILE - COMMENTED OUT
 * This file is potentially replaced by OfflineVideoPlayer.kt
 * Kept for reference in case needed in future
 *
package com.bitchat.android.ui.educational

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color

@Composable
fun VideoPlayerScreen(
    videoFileName: String,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Simple header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("← Back", color = Color.White)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = videoFileName.replace(".mp4", "").replace("_", " "),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
        }

        // Video player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (hasError) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Unable to play video",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onBackClick) {
                        Text("Go Back", color = Color.White)
                    }
                }
            } else {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            // Create URI for asset video
                            val uri = Uri.parse("android.resource://${context.packageName}/raw/${videoFileName.replace(".mp4", "")}")
                            
                            // Set up media controller
                            val mediaController = MediaController(context)
                            mediaController.setAnchorView(this)
                            setMediaController(mediaController)
                            
                            // Set video URI
                            setVideoURI(uri)
                            
                            // Set listeners
                            setOnPreparedListener { 
                                isLoading = false
                                start() // Auto-play
                            }
                            
                            setOnErrorListener { _, _, _ ->
                                hasError = true
                                isLoading = false
                                true
                            }
                            
                            setOnCompletionListener {
                                // Video completed - could track progress here
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
                
                // Loading indicator
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White
                    )
                }
            }
        }
    }
}
*/
