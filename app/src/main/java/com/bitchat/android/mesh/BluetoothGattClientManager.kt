package com.bitchat.android.mesh

import android.bluetooth.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.bitchat.android.protocol.BitchatPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*
import kotlinx.coroutines.Job
import com.bitchat.android.ui.debug.DebugSettingsManager
import com.bitchat.android.ui.debug.DebugScanResult

/**
 * Manages GATT client operations, scanning, and client-side connections
 */
class BluetoothGattClientManager(
    private val context: Context,
    private val connectionScope: CoroutineScope,
    private val connectionTracker: BluetoothConnectionTracker,
    private val permissionManager: BluetoothPermissionManager,
    private val powerManager: PowerManager,
    private val delegate: BluetoothConnectionManagerDelegate?
) {
    
    companion object {
        private const val TAG = "BluetoothGattClientManager"
        // Use exact same UUIDs as iOS version
        private val SERVICE_UUID = UUID.fromString("F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C")
        private val CHARACTERISTIC_UUID = UUID.fromString("A1B2C3D4-E5F6-4A5B-8C9D-0E1F2A3B4C5D")
        private val DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
        
        // RSSI monitoring constants
        private const val RSSI_UPDATE_INTERVAL = 5000L // 5 seconds
    }
    
    // Core Bluetooth components
    private val bluetoothManager: BluetoothManager = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter
    private val bleScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    
    /**
     * Public: Connect to a device by MAC address (for debug UI)
     */
    fun connectToAddress(deviceAddress: String): Boolean {
        val device = bluetoothAdapter?.getRemoteDevice(deviceAddress)
        return if (device != null) {
            val rssi = connectionTracker.getBestRSSI(deviceAddress) ?: -50
            connectToDevice(device, rssi)
            true
        } else {
            Log.w(TAG, "connectToAddress: No device for $deviceAddress")
            false
        }
    }

    // Scan management
    private var scanCallback: ScanCallback? = null
    
    // Scan rate limiting to prevent "scanning too frequently" errors
    private var lastScanStartTime = 0L
    private var lastScanStopTime = 0L
    private var isCurrentlyScanning = false
    private val scanRateLimit = 5000L // Minimum 5 seconds between scan start attempts
    
    // RSSI monitoring state
    private var rssiMonitoringJob: Job? = null
    
    // State management
    private var isActive = false
    
    /**
     * Start client manager
     */
    fun start(): Boolean {
        // Respect debug setting
        try {
            if (!com.bitchat.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value) {
                Log.i(TAG, "Client start skipped: GATT Client disabled in debug settings")
                return false
            }
        } catch (_: Exception) { }

        if (isActive) {
            Log.d(TAG, "GATT client already active; start is a no-op")
            return true
        }
        if (!permissionManager.hasBluetoothPermissions()) {
            Log.e(TAG, "Missing Bluetooth permissions")
            return false
        }
        
        if (bluetoothAdapter?.isEnabled != true) {
            Log.e(TAG, "Bluetooth is not enabled")
            return false
        }
        
        if (bleScanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return false
        }
        
        isActive = true
        
        connectionScope.launch {
            if (powerManager.shouldUseDutyCycle()) {
                Log.i(TAG, "Using power-aware duty cycling")
            } else {
                startScanning()
            }
            
            // Start RSSI monitoring
            startRSSIMonitoring()
        }
        
        return true
    }
    
    /**
     * Stop client manager
     */
    fun stop() {
        if (!isActive) {
            // Idempotent stop
            stopScanning()
            stopRSSIMonitoring()
            Log.i(TAG, "GATT client manager stopped (already inactive)")
            return
        }

        isActive = false
        
        connectionScope.launch {
            // Disconnect all client connections decisively
            try {
                val conns = connectionTracker.getConnectedDevices().values.filter { it.isClient && it.gatt != null }
                conns.forEach { dc ->
                    try { dc.gatt?.disconnect() } catch (_: Exception) { }
                }
            } catch (_: Exception) { }
            
            stopScanning()
            stopRSSIMonitoring()
            Log.i(TAG, "GATT client manager stopped")
        }
    }
    
    /**
     * Handle scan state changes from power manager
     */
    fun onScanStateChanged(shouldScan: Boolean) {
        val enabled = try { com.bitchat.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        if (shouldScan && enabled) {
            startScanning()
        } else {
            stopScanning()
        }
    }
    
    /**
     * Start periodic RSSI monitoring for all client connections
     */
    private fun startRSSIMonitoring() {
        rssiMonitoringJob?.cancel()
        rssiMonitoringJob = connectionScope.launch {
            while (isActive) {
                try {
                    // Request RSSI from all client connections
                    val connectedDevices = connectionTracker.getConnectedDevices()
                    connectedDevices.values.filter { it.isClient && it.gatt != null }.forEach { deviceConn ->
                        try {
                            Log.d(TAG, "Requesting RSSI from ${deviceConn.device.address}")
                            deviceConn.gatt?.readRemoteRssi()
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to request RSSI from ${deviceConn.device.address}: ${e.message}")
                        }
                    }
                    delay(RSSI_UPDATE_INTERVAL)
                } catch (e: Exception) {
                    Log.w(TAG, "Error in RSSI monitoring: ${e.message}")
                    delay(RSSI_UPDATE_INTERVAL)
                }
            }
        }
    }
    
    /**
     * Stop RSSI monitoring
     */
    private fun stopRSSIMonitoring() {
        rssiMonitoringJob?.cancel()
        rssiMonitoringJob = null
    }
    
    /**
     * Start scanning with rate limiting
     */
    @Suppress("DEPRECATION")
    private fun startScanning() {
        // Respect debug setting
        val enabled = try { com.bitchat.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        
        if (!enabled) {
            Log.d(TAG, "Scanning disabled in debug settings")
            return
        }
        
        if (!isActive) {
            Log.d(TAG, "Client manager not active")
            return
        }
        
        // Comprehensive permission and prerequisite check
        Log.d(TAG, "========================================")
        Log.d(TAG, "DEVICE DETECTION - Checking prerequisites...")
        Log.d(TAG, "   Bluetooth Adapter: ${bluetoothAdapter != null}")
        Log.d(TAG, "   BLE Scanner: ${bleScanner != null}")
        Log.d(TAG, "   Has Permissions: ${permissionManager.hasBluetoothPermissions()}")
        Log.d(TAG, "   Location Enabled: ${permissionManager.isLocationEnabled()}")
        Log.d(TAG, "========================================")
        
        if (!permissionManager.canScan()) {
            Log.e(TAG, "[ERROR] Cannot start scanning - missing permissions or location services disabled")
            Log.e(TAG, "   Please enable location services and grant all required permissions")
            return
        }
        
        if (bleScanner == null) {
            Log.e(TAG, "[ERROR] BLE scanner is null - Bluetooth may not be supported or enabled")
            return
        }
        
        // Rate limit scan starts to prevent "scanning too frequently" errors
        val currentTime = System.currentTimeMillis()
        if (isCurrentlyScanning) {
            return
        }
        
        val timeSinceLastStart = currentTime - lastScanStartTime
        if (timeSinceLastStart < scanRateLimit) {
            val remainingWait = scanRateLimit - timeSinceLastStart
            Log.w(TAG, "Scan rate limited: need to wait ${remainingWait}ms before starting scan")
            
            // Schedule delayed scan start
            connectionScope.launch {
                delay(remainingWait)
                if (isActive && !isCurrentlyScanning) {
                    startScanning()
                }
            }
            return
        }
        
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        
        val scanFilters = listOf(scanFilter) 
        
        Log.d(TAG, "Starting BLE scan with target service UUID: $SERVICE_UUID")
        
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                Log.d(TAG, ">>> DEVICE FOUND: ${result.device.address} | Name: ${result.device.name} | RSSI: ${result.rssi}")
                handleScanResult(result)
            }
            
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                Log.d(TAG, ">>> Batch scan results: ${results.size} devices")
                results.forEach { result ->
                    Log.d(TAG, "   - Device: ${result.device.address} | ${result.device.name}")
                    handleScanResult(result)
                }
            }
            
            override fun onScanFailed(errorCode: Int) {
                isCurrentlyScanning = false
                lastScanStopTime = System.currentTimeMillis()
                
                Log.e(TAG, "========================================")
                Log.e(TAG, "[ERROR] BLE Scan Failed with error code: $errorCode")
                
                when (errorCode) {
                    1 -> {
                        Log.e(TAG, "   SCAN_FAILED_ALREADY_STARTED")
                        Log.e(TAG, "   Scan is already running")
                    }
                    2 -> {
                        Log.e(TAG, "   SCAN_FAILED_APPLICATION_REGISTRATION_FAILED")
                        Log.e(TAG, "   Try restarting the app")
                    }
                    3 -> {
                        Log.e(TAG, "   SCAN_FAILED_INTERNAL_ERROR")
                        Log.e(TAG, "   Bluetooth may be in bad state - try toggling Bluetooth")
                    }
                    4 -> {
                        Log.e(TAG, "   SCAN_FAILED_FEATURE_UNSUPPORTED")
                        Log.e(TAG, "   This device may not support BLE scanning")
                    }
                    5 -> {
                        Log.e(TAG, "   SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES")
                        Log.e(TAG, "   Too many apps using Bluetooth - close other apps")
                    }
                    6 -> {
                        Log.e(TAG, "   SCAN_FAILED_SCANNING_TOO_FREQUENTLY")
                        Log.e(TAG, "   Android is rate limiting - will retry in 10 seconds")
                        connectionScope.launch {
                            delay(10000)
                            if (isActive && !isCurrentlyScanning) {
                                Log.i(TAG, "[RETRY] Retrying scan after rate limit...")
                                startScanning()
                            }
                        }
                    }
                    else -> {
                        Log.e(TAG, "   Unknown scan failure code: $errorCode")
                        // Retry for unknown errors
                        connectionScope.launch {
                            delay(5000)
                            if (isActive && !isCurrentlyScanning) {
                                Log.i(TAG, "[RETRY] Retrying scan after unknown error...")
                                startScanning()
                            }
                        }
                    }
                }
                Log.e(TAG, "========================================")
            }
        }
        
        try {
            lastScanStartTime = currentTime
            isCurrentlyScanning = true
            
            Log.i(TAG, "========================================")
            Log.i(TAG, "[SCAN] Starting BLE scan for nearby devices")
            Log.i(TAG, "   Service UUID: $SERVICE_UUID")
            Log.i(TAG, "   Scan Mode: ${powerManager.getScanSettings()}")
            Log.i(TAG, "   Location Enabled: ${permissionManager.isLocationEnabled()}")
            Log.i(TAG, "========================================")
            
            bleScanner.startScan(scanFilters, powerManager.getScanSettings(), scanCallback)
            
            Log.i(TAG, "[OK] BLE scan started successfully!")
            Log.i(TAG, "   Listening for BitChat devices nearby...")
            Log.i(TAG, "   Make sure other devices are advertising")
            
            // Schedule a check to see if we found any devices after 10 seconds
            connectionScope.launch {
                delay(10000)
                if (isActive && isCurrentlyScanning) {
                    val connectedCount = connectionTracker.getConnectedDevices().size
                    if (connectedCount == 0) {
                        Log.w(TAG, "[WARN] No devices found after 10 seconds of scanning")
                        Log.w(TAG, "   Troubleshooting:")
                        Log.w(TAG, "   1. Check if other devices have Bluetooth enabled")
                        Log.w(TAG, "   2. Verify location services are ON")
                        Log.w(TAG, "   3. Ensure devices are close (within 10-30 meters)")
                        Log.w(TAG, "   4. Check if other devices are running BitChat")
                    } else {
                        Log.i(TAG, "[OK] Found and connected to $connectedCount device(s)")
                    }
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "[ERROR] SecurityException starting scan: Missing permissions")
            Log.e(TAG, "   ${e.message}")
            isCurrentlyScanning = false
        } catch (e: Exception) {
            Log.e(TAG, "[ERROR] Exception starting scan: ${e.message}")
            Log.e(TAG, "   ${e.javaClass.simpleName}")
            isCurrentlyScanning = false
            
            // Retry after delay
            connectionScope.launch {
                delay(5000)
                if (isActive && !isCurrentlyScanning) {
                    Log.i(TAG, "[RETRY] Retrying scan after error...")
                    startScanning()
                }
            }
        }
    }
    
    /**
     * Stop scanning
     */
    @Suppress("DEPRECATION")
    private fun stopScanning() {
        if (!permissionManager.hasBluetoothPermissions() || bleScanner == null) return
        
        if (isCurrentlyScanning) {
            try {
                scanCallback?.let { 
                    bleScanner.stopScan(it)
                    Log.d(TAG, "BLE scan stopped successfully")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping scan: ${e.message}")
            }
            
            isCurrentlyScanning = false
            lastScanStopTime = System.currentTimeMillis()
        }
    }
    
    /**
     * Handle scan result and initiate connection if appropriate
     */
    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val deviceAddress = device.address
        val scanRecord = result.scanRecord
        
        // CRITICAL: Only process devices that have our service UUID
        val hasOurService = scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true
        if (!hasOurService) {
            return
        }

        // Log.d(TAG, "Received scan result from $deviceAddress - already connected: ${connectionTracker.isDeviceConnected(deviceAddress)}")
        
        // Store RSSI from scan results for later use (especially for server connections)
        connectionTracker.updateScanRSSI(deviceAddress, rssi)

        // Publish scan result to debug UI buffer
        try {
            DebugSettingsManager.getInstance().addScanResult(
                DebugScanResult(
                    deviceName = device.name,
                    deviceAddress = deviceAddress,
                    rssi = rssi,
                    peerID = null // peerID unknown at scan time
                )
            )
        } catch (_: Exception) { }
        
        // Power-aware RSSI filtering
        if (rssi < powerManager.getRSSIThreshold()) {
            Log.d(TAG, "Skipping device $deviceAddress due to weak signal: $rssi < ${powerManager.getRSSIThreshold()}")
            // Even if we skip connecting, still publish scan result to debug UI
            try {
                val pid: String? = null // We don't know peerID until packet exchange
                DebugSettingsManager.getInstance().addScanResult(
                    DebugScanResult(
                        deviceName = device.name,
                        deviceAddress = deviceAddress,
                        rssi = rssi,
                        peerID = pid
                    )
                )
            } catch (_: Exception) { }
            return
        }
        
        // Check if already connected OR already attempting to connect
        if (connectionTracker.isDeviceConnected(deviceAddress)) {
            return
        }
        
        // Check if connection attempt is allowed
        if (!connectionTracker.isConnectionAttemptAllowed(deviceAddress)) {
            Log.d(TAG, "Connection to $deviceAddress not allowed due to recent attempts")
            return
        }
        
        if (connectionTracker.isConnectionLimitReached()) {
            Log.d(TAG, "Connection limit reached (${powerManager.getMaxConnections()})")
            return
        }
        
        // Add pending connection and start connection
        if (connectionTracker.addPendingConnection(deviceAddress)) {
            connectToDevice(device, rssi)
        }
    }
    
    /**
     * Connect to a device as GATT client
     */
    @Suppress("DEPRECATION")
    private fun connectToDevice(device: BluetoothDevice, rssi: Int) {
        if (!permissionManager.hasBluetoothPermissions()) return

        val deviceAddress = device.address
        Log.i(TAG, "Connecting to bitchat device: $deviceAddress")
        
        val gattCallback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
                Log.d(TAG, "Client: Connection state change - Device: $deviceAddress, Status: $status, NewState: $newState")

                if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "Client: Successfully connected to $deviceAddress. Requesting MTU...")
                    // Request a larger MTU. Must be done before any data transfer.
                    connectionScope.launch {
                        delay(200) // A small delay can improve reliability of MTU request.
                        gatt.requestMtu(517)
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS) {
                        Log.w(TAG, "Client: Disconnected from $deviceAddress with error status $status")
                        if (status == 147) {
                            Log.e(TAG, "Client: Connection establishment failed (status 147) for $deviceAddress")
                        }
                    } else {
                        Log.d(TAG, "Client: Cleanly disconnected from $deviceAddress")
                        connectionTracker.cleanupDeviceConnection(deviceAddress)
                    }

                    // Notify higher layers about device disconnection to update direct flags
                    delegate?.onDeviceDisconnected(gatt.device)

                    connectionScope.launch {
                        delay(500) // CLEANUP_DELAY
                        try {
                            gatt.close()
                        } catch (e: Exception) {
                            Log.w(TAG, "Error closing GATT: ${e.message}")
                        }
                    }
                }
            }
            
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
                val deviceAddress = gatt.device.address
                Log.i(TAG, "Client: MTU changed for $deviceAddress to $mtu with status $status")

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.i(TAG, "MTU successfully negotiated for $deviceAddress. Discovering services.")
                    
                    // Now that MTU is set, connection is fully ready.
                    val deviceConn = BluetoothConnectionTracker.DeviceConnection(
                        device = gatt.device,
                        gatt = gatt,
                        rssi = rssi,
                        isClient = true
                    )
                    connectionTracker.addDeviceConnection(deviceAddress, deviceConn)
                    
                    // Start service discovery only AFTER MTU is set.
                    gatt.discoverServices()
                } else {
                    Log.w(TAG, "MTU negotiation failed for $deviceAddress with status: $status. Disconnecting.")
                    //connectionTracker.removePendingConnection(deviceAddress)
                    gatt.disconnect()
                }
            }

            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {                
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    val service = gatt.getService(SERVICE_UUID)
                    if (service != null) {
                        val characteristic = service.getCharacteristic(CHARACTERISTIC_UUID)
                        if (characteristic != null) {
                            connectionTracker.getDeviceConnection(deviceAddress)?.let { deviceConn ->
                                val updatedConn = deviceConn.copy(characteristic = characteristic)
                                connectionTracker.updateDeviceConnection(deviceAddress, updatedConn)
                                Log.d(TAG, "Client: Updated device connection with characteristic for $deviceAddress")
                            }
                            
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(DESCRIPTOR_UUID)
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                                
                                connectionScope.launch {
                                    delay(200)
                                    Log.i(TAG, "Client: Connection setup complete for $deviceAddress")
                                    delegate?.onDeviceConnected(device)
                                }
                            } else {
                                Log.e(TAG, "Client: CCCD descriptor not found for $deviceAddress")
                                gatt.disconnect()
                            }
                        } else {
                            Log.e(TAG, "Client: Required characteristic not found for $deviceAddress")
                            gatt.disconnect()
                        }
                    } else {
                        Log.e(TAG, "Client: Required service not found for $deviceAddress")
                        gatt.disconnect()
                    }
                } else {
                    Log.e(TAG, "Client: Service discovery failed with status $status for $deviceAddress")
                    gatt.disconnect()
                }
            }
            
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                val value = characteristic.value
                Log.i(TAG, "Client: Received packet from ${gatt.device.address}, size: ${value.size} bytes")
                val packet = BitchatPacket.fromBinaryData(value)
                if (packet != null) {
                    val peerID = packet.senderID.take(8).toByteArray().joinToString("") { "%02x".format(it) }
                    Log.d(TAG, "Client: Parsed packet type ${packet.type} from $peerID")
                    delegate?.onPacketReceived(packet, peerID, gatt.device)
                } else {
                    Log.w(TAG, "Client: Failed to parse packet from ${gatt.device.address}, size: ${value.size} bytes")
                    Log.w(TAG, "Client: Packet data: ${value.joinToString(" ") { "%02x".format(it) }}")
                }
            }
            
            override fun onReadRemoteRssi(gatt: BluetoothGatt, rssi: Int, status: Int) {
                val deviceAddress = gatt.device.address
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d(TAG, "Client: RSSI updated for $deviceAddress: $rssi dBm")
                    
                    // Update the connection tracker with new RSSI value
                    connectionTracker.getDeviceConnection(deviceAddress)?.let { deviceConn ->
                        val updatedConn = deviceConn.copy(rssi = rssi)
                        connectionTracker.updateDeviceConnection(deviceAddress, updatedConn)
                    }
                } else {
                    Log.w(TAG, "Client: Failed to read RSSI for $deviceAddress, status: $status")
                }
            }
        }
        
        try {
            Log.d(TAG, "Client: Attempting GATT connection to $deviceAddress with autoConnect=false")
            val gatt = device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            if (gatt == null) {
                Log.e(TAG, "connectGatt returned null for $deviceAddress")
                // keep the pending connection so we can avoid too many reconnections attempts, TODO: needs testing
                // connectionTracker.removePendingConnection(deviceAddress)
            } else {
                Log.d(TAG, "Client: GATT connection initiated successfully for $deviceAddress")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Client: Exception connecting to $deviceAddress: ${e.message}")
            // keep the pending connection so we can avoid too many reconnections attempts, TODO: needs testing
            // connectionTracker.removePendingConnection(deviceAddress)
        }
    }
    
    /**
     * Restart scanning for power mode changes
     */
    fun restartScanning() {
        // Respect debug setting
        val enabled = try { com.bitchat.android.ui.debug.DebugSettingsManager.getInstance().gattClientEnabled.value } catch (_: Exception) { true }
        if (!isActive || !enabled) return
        
        connectionScope.launch {
            stopScanning()
            delay(1000) // Extra delay to avoid rate limiting
            
            if (powerManager.shouldUseDutyCycle()) {
                Log.i(TAG, "Switching to duty cycle scanning mode")
                // Duty cycle will handle scanning
            } else {
                Log.i(TAG, "Switching to continuous scanning mode")
                startScanning()
            }
        }
    }
} 
