package com.bitchat.android.ui.theme

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsetsController
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

// Simple, minimal colors for EduLearn
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),        // Material Green
    onPrimary = Color.White,
    secondary = Color(0xFF81C784),      // Light Green
    onSecondary = Color.Black,
    background = Color(0xFF121212),     // Material Dark
    onBackground = Color(0xFFE0E0E0),   // Light gray text
    surface = Color(0xFF1E1E1E),        // Dark surface
    onSurface = Color(0xFFE0E0E0),      // Light text
    error = Color(0xFFFF5252),          // Material Red
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF4CAF50),        // Material Green
    onPrimary = Color.White,
    secondary = Color(0xFF81C784),      // Light Green
    onSecondary = Color.Black,
    background = Color(0xFFFAFAFA),     // Very light gray
    onBackground = Color(0xFF212121),   // Dark text
    surface = Color.White,              // White surface
    onSurface = Color(0xFF212121),      // Dark text
    error = Color(0xFFE53935),          // Material Red
    onError = Color.White
)

@Composable
fun EduLearnTheme(
    darkTheme: Boolean? = null,
    content: @Composable () -> Unit
) {
    // App-level override from ThemePreferenceManager
    val themePref by ThemePreferenceManager.themeFlow.collectAsState(initial = ThemePreference.System)
    val shouldUseDark = when (darkTheme) {
        true -> true
        false -> false
        null -> when (themePref) {
            ThemePreference.Dark -> true
            ThemePreference.Light -> false
            ThemePreference.System -> isSystemInDarkTheme()
        }
    }

    val colorScheme = if (shouldUseDark) DarkColorScheme else LightColorScheme

    val view = LocalView.current
    SideEffect {
        (view.context as? Activity)?.window?.let { window ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (!shouldUseDark) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!shouldUseDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                } else 0
            }
            window.navigationBarColor = colorScheme.background.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
