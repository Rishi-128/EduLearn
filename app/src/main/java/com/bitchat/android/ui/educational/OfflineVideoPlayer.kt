package com.bitchat.android.ui.educational

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitchat.android.data.OfflineDataManager
import kotlinx.coroutines.delay

/**
 * Offline Video Player with Real-time Progress Tracking
 * Simulates video playback and tracks progress locally
 */

data class OfflineVideo(
    val id: String,
    val title: String,
    val subject: String,
    val description: String,
    val durationSeconds: Int,
    val transcript: String = "" // For accessibility and offline text
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineVideoPlayer(
    video: OfflineVideo,
    dataManager: OfflineDataManager,
    onBackClick: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var volume by remember { mutableStateOf(0.7f) }
    var showTranscript by remember { mutableStateOf(false) }
    
    // Simulate video playback and track progress
    LaunchedEffect(isPlaying) {
        while (isPlaying && currentPosition < video.durationSeconds) {
            delay(1000) // 1 second intervals
            currentPosition += 1
            
            // Update progress every 10 seconds
            if (currentPosition % 10 == 0) {
                val progressPercent = (currentPosition * 100) / video.durationSeconds
                dataManager.updateVideoProgress(
                    studentId = "student_001", // In real app, get from current user
                    videoId = video.id,
                    progressPercent = progressPercent,
                    watchTimeSeconds = currentPosition
                )
            }
        }
        
        // Auto-pause when video ends
        if (currentPosition >= video.durationSeconds) {
            isPlaying = false
            // Mark as completed
            dataManager.updateVideoProgress(
                studentId = "student_001",
                videoId = video.id,
                progressPercent = 100,
                watchTimeSeconds = video.durationSeconds
            )
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Header
        TopAppBar(
            title = { Text(video.title) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = { showTranscript = !showTranscript }
                ) {
                    Text(if (showTranscript) "Hide Script" else "Show Script")
                }
            }
        )
        
        if (!showTranscript) {
            // Video Player UI
            VideoPlayerScreen(
                video = video,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                volume = volume,
                onPlayPause = { isPlaying = !isPlaying },
                onSeek = { position -> 
                    currentPosition = position
                    // Update progress when seeking
                    val progressPercent = (currentPosition * 100) / video.durationSeconds
                    dataManager.updateVideoProgress(
                        studentId = "student_001",
                        videoId = video.id,
                        progressPercent = progressPercent,
                        watchTimeSeconds = currentPosition
                    )
                },
                onVolumeChange = { volume = it }
            )
        } else {
            // Video Transcript for accessibility
            VideoTranscriptScreen(
                video = video,
                currentPosition = currentPosition
            )
        }
    }
}

@Composable
fun VideoPlayerScreen(
    video: OfflineVideo,
    isPlaying: Boolean,
    currentPosition: Int,
    volume: Float,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Video Display Area (simulated)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(250.dp)
                .background(Color.Black)
                .clip(RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Video simulation
                Text(
                    text = "🎥 ${video.title}",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Progress indicator
                Text(
                    text = formatTime(currentPosition) + " / " + formatTime(video.durationSeconds),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyLarge
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Visual progress bar
                LinearProgressIndicator(
                    progress = currentPosition.toFloat() / video.durationSeconds.toFloat(),
                    modifier = Modifier
                        .width(200.dp)
                        .height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Video Controls
        VideoControls(
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = video.durationSeconds,
            volume = volume,
            onPlayPause = onPlayPause,
            onSeek = onSeek,
            onVolumeChange = onVolumeChange
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Video Info
        VideoInfoCard(video = video, currentPosition = currentPosition)
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun VideoControls(
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    volume: Float,
    onPlayPause: () -> Unit,
    onSeek: (Int) -> Unit,
    onVolumeChange: (Float) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Play/Pause and Time
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onPlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = formatTime(currentPosition),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.weight(1f))
                
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Seek Bar
            Slider(
                value = currentPosition.toFloat(),
                onValueChange = { onSeek(it.toInt()) },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Volume Control
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Default.VolumeUp,
                    contentDescription = "Volume"
                )
                
                Slider(
                    value = volume,
                    onValueChange = onVolumeChange,
                    modifier = Modifier.weight(1f)
                )
                
                Text(
                    text = "${(volume * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun VideoInfoCard(
    video: OfflineVideo,
    currentPosition: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "About this video",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = video.description,
                style = MaterialTheme.typography.bodyMedium
            )
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Progress Update Indicator
            val progressPercent = (currentPosition * 100) / video.durationSeconds
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📊 Progress: $progressPercent%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                if (progressPercent >= 90) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "✅ Completed!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun VideoTranscriptScreen(
    video: OfflineVideo,
    currentPosition: Int
) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Video Transcript",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = "Current time: ${formatTime(currentPosition)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = video.transcript.ifEmpty { "Transcript not available for this video." },
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
            )
        }
    }
}

// Helper function to format time
private fun formatTime(seconds: Int): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%d:%02d", minutes, remainingSeconds)
}

// Sample videos for testing - ALL OFFLINE
object OfflineVideoData {
    // Video content will be loaded from teacher shares or storage
    // TODO: Implement video loading from Bluetooth mesh or local storage
    
    val plantsVideo = OfflineVideo(
        id = "", title = "", subject = "", description = "", durationSeconds = 0, transcript = ""
    )
    
    val mathVideo = OfflineVideo(
        id = "", title = "", subject = "", description = "", durationSeconds = 0, transcript = ""
    )
}