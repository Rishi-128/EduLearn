package com.bitchat.android.onboarding

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Coordinates the complete onboarding flow including permission explanation,
 * permission requests, and initialization of the mesh service
 */
class OnboardingCoordinator(
    private val activity: ComponentActivity,
    private val permissionManager: PermissionManager,
    private val onOnboardingComplete: () -> Unit,
    private val onOnboardingFailed: (String) -> Unit
) {

    companion object {
        private const val TAG = "OnboardingCoordinator"
    }

    private var permissionLauncher: ActivityResultLauncher<Array<String>>? = null

    init {
        setupPermissionLauncher()
    }

    /**
     * Setup the permission request launcher
     */
    private fun setupPermissionLauncher() {
        permissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            handlePermissionResults(permissions)
        }
    }

    /**
     * Start the onboarding process
     */
    fun startOnboarding() {
        Log.d(TAG, "Starting onboarding process")
        permissionManager.logPermissionStatus()

        if (permissionManager.areAllPermissionsGranted()) {
            Log.d(TAG, "All permissions already granted, completing onboarding")
            completeOnboarding()
        } else {
            Log.d(TAG, "Missing permissions, need to start explanation flow")
            // The explanation screen will be shown by the calling activity
        }
    }

    /**
     * Called when user accepts the permission explanation
     */
    fun requestPermissions() {
        Log.d(TAG, "User accepted permission explanation, requesting permissions")
        
        // Required permissions
        val missingRequired = permissionManager.getMissingPermissions()

        // Optional permissions (ask, but do not block if denied)
        val optionalToRequest = permissionManager
            .getOptionalPermissions()
            .filter { !permissionManager.isPermissionGranted(it) }

        val missingPermissions = (missingRequired + optionalToRequest).distinct()

        if (missingPermissions.isEmpty()) {
            completeOnboarding()
            return
        }

        Log.d(TAG, "Requesting ${missingPermissions.size} permissions")
        permissionLauncher?.launch(missingPermissions.toTypedArray())
    }

    /**
     * Handle permission request results
     */
    private fun handlePermissionResults(permissions: Map<String, Boolean>) {
        Log.d(TAG, "Received permission results:")
        permissions.forEach { (permission, granted) ->
            Log.d(TAG, "  $permission: ${if (granted) "GRANTED" else "DENIED"}")
        }

        // IMPORTANT: Check actual system permission status, not just the result map.
        // The result map only contains permissions that were requested this time.
        // Permissions already granted won't be in the map.
        val allRequiredGranted = permissionManager.areAllPermissionsGranted()
        val criticalPermissions = getCriticalPermissions()
        val criticalGranted = criticalPermissions.all { permissionManager.isPermissionGranted(it) }

        Log.d(TAG, "All required permissions granted: $allRequiredGranted")
        Log.d(TAG, "Critical permissions granted: $criticalGranted")

        when {
            allRequiredGranted -> {
                Log.d(TAG, "All permissions granted successfully")
                completeOnboarding()
            }
            criticalGranted -> {
                Log.d(TAG, "Critical permissions granted, can proceed with limited functionality")
                showPartialPermissionWarning(permissions)
            }
            else -> {
                Log.d(TAG, "Critical permissions denied")
                handlePermissionDenial(permissions)
            }
        }
    }

    /**
     * Get the list of critical permissions that are absolutely required
     */
    private fun getCriticalPermissions(): List<String> {
        // For bitchat, Bluetooth and location permissions are critical
        // Notifications are nice-to-have but not critical
        return permissionManager.getRequiredPermissions().filter { permission ->
            !permission.contains("POST_NOTIFICATIONS")
        }
    }

    /**
     * Show warning when some permissions are granted but others are denied
     */
    private fun showPartialPermissionWarning(permissions: Map<String, Boolean>) {
        val deniedPermissions = permissions.filter { !it.value }.keys
        val message = buildString {
            append("Some permissions were denied:\n")
            deniedPermissions.forEach { permission ->
                append("- ${getPermissionDisplayName(permission)}\n")
            }
            append("\n Edulearn may not work properly without all permissions.")
        }
        
        Log.w(TAG, "Partial permissions granted: $message")
        
        // For now, we'll proceed anyway and let the user experience the limitations
        // In a production app, you might want to show a dialog explaining the limitations
        completeOnboarding()
    }

    /**
     * Handle permission denial scenarios
     */
    private fun handlePermissionDenial(permissions: Map<String, Boolean>) {
        val deniedCritical = getCriticalPermissions().filter { !permissionManager.isPermissionGranted(it) }
        
        if (deniedCritical.isNotEmpty()) {
            Log.e(TAG, "Critical permissions denied: $deniedCritical")
            
            // Check if user can still proceed (maybe they granted permissions via settings)
            if (permissionManager.areAllPermissionsGranted()) {
                Log.d(TAG, "Actually all permissions are now granted, completing onboarding")
                completeOnboarding()
                return
            }
            
            // Build error message for the user
            val missingPermissionNames = deniedCritical.map { getPermissionDisplayName(it) }.distinct()
            val errorMessage = buildString {
                append("The following permissions are required:\n")
                missingPermissionNames.forEach { name ->
                    append("• $name\n")
                }
                append("\nPlease grant these permissions in app settings to continue.")
            }
            
            Log.d(TAG, "Reporting permission failure to user")
            onOnboardingFailed(errorMessage)
        } else {
            // All critical permissions granted, proceed with limited functionality
            completeOnboarding()
        }
    }

    /**
     * Complete the onboarding process and initialize the app
     */
    private fun completeOnboarding() {
        Log.d(TAG, "Completing onboarding process")
        
        // Mark onboarding as complete
        permissionManager.markOnboardingComplete()
        
        // Log final permission status
        permissionManager.logPermissionStatus()
        
        // Notify completion with a small delay to ensure everything is ready
        activity.lifecycleScope.launch {
            kotlinx.coroutines.delay(100) // Small delay for UI state to settle
            onOnboardingComplete()
        }
    }

    /**
     * Open app settings for manual permission management
     */
    fun openAppSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = android.net.Uri.fromParts("package", activity.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activity.startActivity(intent)
            Log.d(TAG, "Opened app settings for manual permission management")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app settings", e)
        }
    }

    /**
     * Convert permission string to user-friendly display name
     */
    private fun getPermissionDisplayName(permission: String): String {
        return when {
            permission.contains("BLUETOOTH") -> "Bluetooth/Nearby Devices"
            permission.contains("LOCATION") -> "Location (for Bluetooth scanning)"
            permission.contains("NOTIFICATION") -> "Notifications"
            else -> permission.substringAfterLast(".")
        }
    }

    /**
     * Get diagnostic information for troubleshooting
     */
    fun getDiagnostics(): String {
        return buildString {
            appendLine("Onboarding Coordinator Diagnostics:")
            appendLine("Activity: ${activity::class.simpleName}")
            appendLine("Permission launcher: ${permissionLauncher != null}")
            appendLine()
            append(permissionManager.getPermissionDiagnostics())
        }
    }
}
