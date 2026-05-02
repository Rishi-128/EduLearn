package com.bitchat.android.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.bitchat.android.R
import com.bitchat.android.utils.LanguageManager

/**
 * Language toggle button that switches between English and Hindi
 * Shows current language and allows switching with a single tap
 */
@Composable
fun LanguageToggleButton(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    
    OutlinedButton(
        onClick = {
            LanguageManager.toggleLanguage(context)
            // Force recomposition by recreating activity (best practice for language changes)
            (context as? android.app.Activity)?.recreate()
        },
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = stringResource(R.string.change_language),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = currentLanguage.nativeName,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * Language selector dialog that shows all available languages
 */
@Composable
fun LanguageSelectorDialog(
    onDismiss: () -> Unit,
    onLanguageSelected: (LanguageManager.Language) -> Unit
) {
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    val availableLanguages = LanguageManager.getAvailableLanguages()
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(R.string.change_language))
        },
        text = {
            Column {
                availableLanguages.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = language == currentLanguage,
                            onClick = {
                                onLanguageSelected(language)
                                onDismiss()
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = language.nativeName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = language.displayName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

/**
 * Compact language toggle icon button for toolbar
 */
@Composable
fun CompactLanguageToggle(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentLanguage by LanguageManager.currentLanguage.collectAsState()
    
    IconButton(
        onClick = {
            LanguageManager.toggleLanguage(context)
            (context as? android.app.Activity)?.recreate()
        },
        modifier = modifier
    ) {
        Icon(
            Icons.Default.Language,
            contentDescription = stringResource(R.string.change_language),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}
