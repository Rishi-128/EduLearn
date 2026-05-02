package com.bitchat.android.ui.components

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bitchat.android.utils.LanguageManager

/**
 * A reusable language selector dropdown component
 * Displays current language and allows switching between English, Hindi, and Punjabi
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSelector(
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    
    Box(modifier = modifier) {
        // Language button
        IconButton(
            onClick = { expanded = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Change Language"
                )
                Text(
                    text = currentLanguage.nativeName,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        
        // Dropdown menu
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            LanguageManager.Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = language.nativeName)
                            if (language == currentLanguage) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = "Selected",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    onClick = {
                        if (language != currentLanguage) {
                            LanguageManager.setLanguage(language, context)
                            expanded = false
                            // Recreate activity to apply language change
                            (context as? Activity)?.recreate()
                        }
                    }
                )
            }
        }
    }
}
