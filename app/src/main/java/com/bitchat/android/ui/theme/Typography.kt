package com.bitchat.android.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Base font size for consistent scaling across the app
const val BASE_FONT_SIZE = 14 // sp - simplified for better performance

// Clean, minimal typography for EduLearn - using system fonts for lighter app size
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE + 2).sp,
        lineHeight = (BASE_FONT_SIZE + 6).sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = BASE_FONT_SIZE.sp,
        lineHeight = (BASE_FONT_SIZE + 4).sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE - 2).sp,
        lineHeight = BASE_FONT_SIZE.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE + 6).sp,
        lineHeight = (BASE_FONT_SIZE + 10).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE + 2).sp,
        lineHeight = (BASE_FONT_SIZE + 6).sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = (BASE_FONT_SIZE - 1).sp,
        lineHeight = (BASE_FONT_SIZE + 2).sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = (BASE_FONT_SIZE - 3).sp,
        lineHeight = BASE_FONT_SIZE.sp
    )
)
