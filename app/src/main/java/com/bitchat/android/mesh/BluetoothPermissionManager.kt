package com.bitchat.android.mesh

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.util.Log
import androidx.core.app.ActivityCompat

/**
 * Handles all Bluetooth permission checking logic
 */
class BluetoothPermissionManager(private val context: Context) {
    
    companion object {
        private const val TAG = "BluetoothPermissionManager"
    }
    
    /**
     * Check if all required Bluetooth permissions are granted
     */
    fun hasBluetoothPermissions(): Boolean {
        val permissions = mutableListOf<String>()
        
        // Add version-specific Bluetooth permissions
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            ))
        } else {
            permissions.addAll(listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN
            ))
        }
        
        // Location permissions required for BLE scanning
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            // Android 10+ requires FINE location
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            // Pre-Android 10 can use COARSE location
            permissions.addAll(listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ))
        }
        
        val hasAllPermissions = permissions.all { 
            ActivityCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED 
        }
        
        if (!hasAllPermissions) {
            val missingPermissions = permissions.filter {
                ActivityCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            Log.w(TAG, "Missing permissions: ${missingPermissions.joinToString(", ")}")
        }
        
        return hasAllPermissions
    }
    
    /**
     * Check if location services are enabled (required for BLE scanning on Android)
     */
    fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) ?: false
        val isNetworkEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ?: false
        
        val locationEnabled = isGpsEnabled || isNetworkEnabled
        
        if (!locationEnabled) {
            Log.w(TAG, "[WARN] Location services are disabled - BLE scanning requires location to be enabled")
        }
        
        return locationEnabled
    }
    
    /**
     * Check if all prerequisites for BLE scanning are met
     */
    fun canScan(): Boolean {
        val hasPermissions = hasBluetoothPermissions()
        val hasLocation = isLocationEnabled()
        
        if (!hasPermissions) {
            Log.e(TAG, "[ERROR] Cannot scan: Missing Bluetooth/Location permissions")
        }
        
        if (!hasLocation) {
            Log.e(TAG, "[ERROR] Cannot scan: Location services disabled")
        }
        
        return hasPermissions && hasLocation
    }
} 
