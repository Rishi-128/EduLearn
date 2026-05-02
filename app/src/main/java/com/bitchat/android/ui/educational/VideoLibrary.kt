package com.bitchat.android.ui.educational

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class EducationalVideo(
    val fileName: String,
    val title: String,
    val subject: String,
    val duration: String = "2-3 min"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoLibraryScreen(
    onVideoClick: (String) -> Unit,
    onBackClick: () -> Unit
) {
    // Video data - will be loaded from teacher shares or assets
    val videos = remember {
        listOf<EducationalVideo>()
        // TODO: Load videos from storage, assets, or Bluetooth mesh
    }

    // Group videos by subject
    val videosBySubject = videos.groupBy { it.subject }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBackClick) {
                Text("← Back")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Educational Videos",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video count summary
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Total Videos: ${videos.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                videosBySubject.forEach { (subject, subjectVideos) ->
                    Text(
                        text = "$subject: ${subjectVideos.size} videos",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Video list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            videosBySubject.forEach { (subject, subjectVideos) ->
                item {
                    Text(
                        text = subject,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(subjectVideos) { video ->
                    VideoItemCard(
                        video = video,
                        onClick = { onVideoClick(video.fileName) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoItemCard(
    video: EducationalVideo,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Simple video icon (using text)
            Text(
                text = "▶",
                style = MaterialTheme.typography.titleLarge
            )
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${video.subject} • ${video.duration}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
