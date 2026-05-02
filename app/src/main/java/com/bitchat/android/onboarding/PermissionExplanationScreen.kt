package com.bitchat.android.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Lumina Learn Colors
private val LuminaBackground = Color(0xFFF8F9FF)
private val LuminaSurface = Color(0xFFFFFFFF)
private val LuminaPrimary = Color(0xFF4648D4)
private val LuminaPrimaryLight = Color(0x1A4648D4) // 10% opacity
private val LuminaTextMain = Color(0xFF0D1C2D)
private val LuminaTextSecondary = Color(0xFF464554)
private val LuminaBorder = Color(0x1F6366F1) // rgba(99,102,241,0.12)
private val LuminaSectionTitle = Color(0xFF767586)

@Composable
fun PermissionExplanationScreen(
    modifier: Modifier = Modifier,
    permissionCategories: List<PermissionCategory>,
    onContinue: () -> Unit
) {
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LuminaBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(bottom = 100.dp) // Space for bottom button
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Header: EduLearn Title
            Text(
                text = "EduLearn",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = LuminaPrimary,
                letterSpacing = (-0.96).sp,
                fontFamily = FontFamily.SansSerif
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "collaborative learning platform with offline mesh\nnetworking",
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
                color = LuminaTextSecondary,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Privacy Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFF6366F1),
                        spotColor = Color(0xFF6366F1)
                    )
                    .background(LuminaSurface, RoundedCornerShape(24.dp))
                    .border(1.dp, LuminaBorder, RoundedCornerShape(24.dp))
                    .padding(24.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Shield + Title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(LuminaPrimaryLight, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Security,
                                contentDescription = "Privacy",
                                tint = LuminaPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Text(
                            text = "Your Privacy is Protected",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LuminaTextMain,
                            fontFamily = FontFamily.SansSerif
                        )
                    }

                    // Checklist
                    PrivacyCheckItem("Data stays local on your device.")
                    PrivacyCheckItem("Mesh connections are encrypted end-to-end.")
                    PrivacyCheckItem("No tracking or analytics sharing.")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Required Permissions Header
            Text(
                text = "REQUIRED PERMISSIONS",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LuminaSectionTitle,
                letterSpacing = 0.5.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Permissions List
            permissionCategories.forEach { category ->
                PermissionCategoryCard(category)
                Spacer(modifier = Modifier.height(12.dp))
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Fixed Button at Bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(LuminaBackground)
                .padding(horizontal = 24.dp, vertical = 24.dp)
        ) {
            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = LuminaPrimary
                )
            ) {
                Text(
                    text = "Grant Permissions",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    letterSpacing = 0.16.sp,
                    fontFamily = FontFamily.SansSerif
                )
            }
        }
    }
}

@Composable
private fun PrivacyCheckItem(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            tint = LuminaPrimary,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = text,
            fontSize = 15.sp,
            color = LuminaTextSecondary,
            fontFamily = FontFamily.SansSerif,
            lineHeight = 22.sp
        )
    }
}

@Composable
private fun PermissionCategoryCard(category: PermissionCategory) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LuminaSurface, RoundedCornerShape(50))
            .border(1.dp, LuminaBorder, RoundedCornerShape(50))
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            // Icon Background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(LuminaPrimaryLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getPermissionIcon(category.type),
                    contentDescription = null,
                    tint = LuminaPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = getPermissionName(category.type),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LuminaTextMain,
                        fontFamily = FontFamily.SansSerif
                    )

                    if (category.type == PermissionType.PRECISE_LOCATION) {
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFEF3C7), RoundedCornerShape(50))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "REQUIRED BY OS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB45309),
                                fontFamily = FontFamily.SansSerif,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(2.dp))
                
                Text(
                    text = getPermissionDescription(category.type, category.description),
                    fontSize = 14.sp,
                    color = LuminaTextSecondary,
                    fontFamily = FontFamily.SansSerif,
                    lineHeight = 20.sp
                )
            }
        }
    }
}

private fun getPermissionName(type: PermissionType): String {
    return when (type) {
        PermissionType.NEARBY_DEVICES -> "Bluetooth"
        PermissionType.PRECISE_LOCATION -> "Location"
        PermissionType.NOTIFICATIONS -> "Notifications"
        PermissionType.BATTERY_OPTIMIZATION -> "Battery Optimization"
        PermissionType.OTHER -> "Settings"
    }
}

private fun getPermissionDescription(type: PermissionType, defaultDesc: String): String {
    return when (type) {
        PermissionType.NEARBY_DEVICES -> "Required for nearby peer discovery."
        PermissionType.PRECISE_LOCATION -> "Needed for Bluetooth scanning on Android."
        PermissionType.NOTIFICATIONS -> "Alerts for new messages and file transfers."
        PermissionType.BATTERY_OPTIMIZATION -> "Allow background networking to stay connected."
        else -> defaultDesc
    }
}

private fun getPermissionIcon(permissionType: PermissionType): ImageVector {
    return when (permissionType) {
        PermissionType.NEARBY_DEVICES -> Icons.Filled.Bluetooth
        PermissionType.PRECISE_LOCATION -> Icons.Filled.LocationOn
        PermissionType.NOTIFICATIONS -> Icons.Filled.Notifications
        PermissionType.BATTERY_OPTIMIZATION -> Icons.Filled.Settings // Used Settings as BatteryStd might not be available
        PermissionType.OTHER -> Icons.Filled.Settings
    }
}

