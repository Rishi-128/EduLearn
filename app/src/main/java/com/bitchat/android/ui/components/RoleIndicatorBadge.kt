package com.bitchat.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.R

/**
 * Display a small badge indicating user role (Teacher/Student)
 */
@Composable
fun RoleIndicatorBadge(
    role: String,
    modifier: Modifier = Modifier
) {
    val (roleText, color) = when (role.lowercase()) {
        "teacher" -> Pair(
            stringResource(R.string.role_teacher),
            MaterialTheme.colorScheme.primaryContainer
        )
        "student" -> Pair(
            stringResource(R.string.role_student),
            MaterialTheme.colorScheme.secondaryContainer
        )
        else -> return // Don't show badge for unknown roles
    }
    
    Text(
        text = roleText,
        modifier = modifier
            .background(
                color = color,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium
    )
}
