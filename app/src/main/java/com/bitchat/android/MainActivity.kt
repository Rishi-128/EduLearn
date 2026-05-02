package com.bitchat.android

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.Lifecycle
import com.bitchat.android.mesh.BluetoothMeshService
import com.bitchat.android.onboarding.BluetoothCheckScreen
import com.bitchat.android.onboarding.BluetoothStatus
import com.bitchat.android.onboarding.BluetoothStatusManager
import com.bitchat.android.onboarding.BatteryOptimizationManager
import com.bitchat.android.onboarding.BatteryOptimizationPreferenceManager
import com.bitchat.android.onboarding.BatteryOptimizationScreen
import com.bitchat.android.onboarding.BatteryOptimizationStatus
import com.bitchat.android.onboarding.InitializationErrorScreen
import com.bitchat.android.onboarding.InitializingScreen
import com.bitchat.android.onboarding.LocationCheckScreen
import com.bitchat.android.onboarding.LocationStatus
import com.bitchat.android.onboarding.LocationStatusManager
import com.bitchat.android.onboarding.OnboardingCoordinator
import com.bitchat.android.onboarding.OnboardingState
import com.bitchat.android.onboarding.PermissionExplanationScreen
import com.bitchat.android.onboarding.PermissionManager
import com.bitchat.android.ui.ChatScreen
import com.bitchat.android.ui.SimpleEducationalScreen
import com.bitchat.android.ui.ChatViewModel
import com.bitchat.android.ui.theme.EduLearnTheme
import com.bitchat.android.nostr.PoWPreferenceManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

// Added imports for AppModeSelectionScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

// App mode selection
enum class AppMode {
    CHAT, EDUCATIONAL
}

@Composable
fun AppModeSelectionScreen(
    onModeSelected: (AppMode) -> Unit
) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF8F9FF))
            .drawBehind {
                val dotRadius = 1.5f
                val spacing = 48f
                val color = Color(0xFFD4E4FA)
                var x = spacing / 2
                while (x < size.width) {
                    var y = spacing / 2
                    while (y < size.height) {
                        drawCircle(color = color, radius = dotRadius, center = Offset(x, y))
                        y += spacing
                    }
                    x += spacing
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Choose App\nMode",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0D1C2D),
                textAlign = TextAlign.Center,
                lineHeight = 44.sp,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-0.8).sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            
            Text(
                text = "Select your preferred learning experience",
                fontSize = 16.sp,
                color = Color(0xFF464554),
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            Button(
                onClick = { onModeSelected(AppMode.CHAT) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4648D4)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Chat Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
            
            Button(
                onClick = { onModeSelected(AppMode.EDUCATIONAL) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(vertical = 8.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF4648D4)
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Educational Mode",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        fontFamily = FontFamily.SansSerif
                    )
                }
            }
        }
    }
}

class MainActivity : ComponentActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var onboardingCoordinator: OnboardingCoordinator
    private lateinit var bluetoothStatusManager: BluetoothStatusManager
    private lateinit var locationStatusManager: LocationStatusManager
    private lateinit var batteryOptimizationManager: BatteryOptimizationManager
    
    // Core mesh service - managed at app level
    private lateinit var meshService: BluetoothMeshService
    private val mainViewModel: MainViewModel by viewModels()
    private val chatViewModel: ChatViewModel by viewModels { 
        object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(application, meshService) as T
            }
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enable edge-to-edge display for modern Android look
        enableEdgeToEdge()

        // Initialize permission management
        permissionManager = PermissionManager(this)
        // Initialize core mesh service first
        meshService = BluetoothMeshService(this)
        bluetoothStatusManager = BluetoothStatusManager(
            activity = this,
            context = this,
            onBluetoothEnabled = ::handleBluetoothEnabled,
            onBluetoothDisabled = ::handleBluetoothDisabled
        )
        locationStatusManager = LocationStatusManager(
            activity = this,
            context = this,
            onLocationEnabled = ::handleLocationEnabled,
            onLocationDisabled = ::handleLocationDisabled
        )
        batteryOptimizationManager = BatteryOptimizationManager(
            activity = this,
            context = this,
            onBatteryOptimizationDisabled = ::handleBatteryOptimizationDisabled,
            onBatteryOptimizationFailed = ::handleBatteryOptimizationFailed
        )
        onboardingCoordinator = OnboardingCoordinator(
            activity = this,
            permissionManager = permissionManager,
            onOnboardingComplete = ::handleOnboardingComplete,
            onOnboardingFailed = ::handleOnboardingFailed
        )
        
        setContent {
            EduLearnTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    OnboardingFlowScreen(modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                    )
                }
            }
        }
        
        // Collect state changes in a lifecycle-aware manner
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.onboardingState.collect { state ->
                    handleOnboardingStateChange(state)
                }
            }
        }
        
        // Only start onboarding process if we're in the initial CHECKING state
        // This prevents restarting onboarding on configuration changes
        if (mainViewModel.onboardingState.value == OnboardingState.CHECKING) {
            checkOnboardingStatus()
        }
    }
    
    @Composable
    private fun OnboardingFlowScreen(modifier: Modifier = Modifier) {
        val context = LocalContext.current
        val onboardingState by mainViewModel.onboardingState.collectAsState()
        val bluetoothStatus by mainViewModel.bluetoothStatus.collectAsState()
        val locationStatus by mainViewModel.locationStatus.collectAsState()
        val batteryOptimizationStatus by mainViewModel.batteryOptimizationStatus.collectAsState()
        val errorMessage by mainViewModel.errorMessage.collectAsState()
        val isBluetoothLoading by mainViewModel.isBluetoothLoading.collectAsState()
        val isLocationLoading by mainViewModel.isLocationLoading.collectAsState()
        val isBatteryOptimizationLoading by mainViewModel.isBatteryOptimizationLoading.collectAsState()

        DisposableEffect(context, bluetoothStatusManager) {

            val receiver = bluetoothStatusManager.monitorBluetoothState(
                context = context,
                bluetoothStatusManager = bluetoothStatusManager,
                onBluetoothStateChanged = { status ->
                    if (status == BluetoothStatus.ENABLED && onboardingState == OnboardingState.BLUETOOTH_CHECK) {
                        checkBluetoothAndProceed()
                    }
                }
            )

            onDispose {
                try {
                    context.unregisterReceiver(receiver)
                    Log.d("BluetoothStatusUI", "BroadcastReceiver unregistered")
                } catch (e: IllegalStateException) {
                    Log.w("BluetoothStatusUI", "Receiver was not registered")
                }
            }
        }

        when (onboardingState) {
            OnboardingState.PERMISSION_REQUESTING -> {
                InitializingScreen(modifier)
            }
            
            OnboardingState.BLUETOOTH_CHECK -> {
                BluetoothCheckScreen(
                    modifier = modifier,
                    status = bluetoothStatus,
                    onEnableBluetooth = {
                        mainViewModel.updateBluetoothLoading(true)
                        bluetoothStatusManager.requestEnableBluetooth()
                    },
                    onRetry = {
                        checkBluetoothAndProceed()
                    },
                    isLoading = isBluetoothLoading
                )
            }
            
            OnboardingState.LOCATION_CHECK -> {
                LocationCheckScreen(
                    modifier = modifier,
                    status = locationStatus,
                    onEnableLocation = {
                        mainViewModel.updateLocationLoading(true)
                        locationStatusManager.requestEnableLocation()
                    },
                    onRetry = {
                        checkLocationAndProceed()
                    },
                    isLoading = isLocationLoading
                )
            }
            
            OnboardingState.BATTERY_OPTIMIZATION_CHECK -> {
                BatteryOptimizationScreen(
                    modifier = modifier,
                    status = batteryOptimizationStatus,
                    onDisableBatteryOptimization = {
                        mainViewModel.updateBatteryOptimizationLoading(true)
                        batteryOptimizationManager.requestDisableBatteryOptimization()
                    },
                    onRetry = {
                        checkBatteryOptimizationAndProceed()
                    },
                    onSkip = {
                        // Skip battery optimization and proceed
                        proceedWithPermissionCheck()
                    },
                    isLoading = isBatteryOptimizationLoading
                )
            }
            
            OnboardingState.PERMISSION_EXPLANATION -> {
                PermissionExplanationScreen(
                    modifier = modifier,
                    permissionCategories = permissionManager.getCategorizedPermissions(),
                    onContinue = {
                        mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_REQUESTING)
                        onboardingCoordinator.requestPermissions()
                    }
                )
            }

            OnboardingState.CHECKING, OnboardingState.INITIALIZING, OnboardingState.COMPLETE -> {
                // Set up back navigation handling for the chat screen
                val backCallback = object : OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        // Let ChatViewModel handle navigation state
                        val handled = chatViewModel.handleBackPressed()
                        if (!handled) {
                            // If ChatViewModel doesn't handle it, disable this callback
                            // and let the system handle it (which will exit the app)
                            this.isEnabled = false
                            onBackPressedDispatcher.onBackPressed()
                            this.isEnabled = true
                        }
                    }
                }

                // Add the callback - this will be automatically removed when the activity is destroyed
                onBackPressedDispatcher.addCallback(this, backCallback)
                
                // Main app interface with mode selection
                MainAppInterface(chatViewModel = chatViewModel)
            }
            
            OnboardingState.ERROR -> {
                InitializationErrorScreen(
                    modifier = modifier,
                    errorMessage = errorMessage,
                    onRetry = {
                        mainViewModel.updateOnboardingState(OnboardingState.CHECKING)
                        checkOnboardingStatus()
                    },
                    onOpenSettings = {
                        onboardingCoordinator.openAppSettings()
                    }
                )
            }
        }
    }
    
    @Composable
    private fun MainAppInterface(chatViewModel: ChatViewModel) {
        var selectedMode by remember { mutableStateOf<AppMode?>(null) }
        
        when (selectedMode) {
            null -> {
                // Show mode selection screen
                AppModeSelectionScreen(
                    onModeSelected = { mode -> selectedMode = mode }
                )
            }
            AppMode.CHAT -> {
                // Show original BitChat interface with back navigation
                ChatScreen(
                    viewModel = chatViewModel,
                    onBackToModeSelection = { selectedMode = null }
                )
            }
            AppMode.EDUCATIONAL -> {
                // Show educational interface with back navigation
                SimpleEducationalScreen(
                    quizDistributionService = chatViewModel.quizDistributionService,
                    meshService = meshService,
                    fileTransferService = chatViewModel.fileTransferService,
                    chatViewModel = chatViewModel,
                    onBackToModeSelection = { selectedMode = null }
                )
            }
        }
    }
    
    private fun handleOnboardingStateChange(state: OnboardingState) {

        when (state) {
            OnboardingState.COMPLETE -> {
                // App is fully initialized, mesh service is running
                android.util.Log.d("MainActivity", "Onboarding completed - app ready")
            }
            OnboardingState.ERROR -> {
                android.util.Log.e("MainActivity", "Onboarding error state reached")
            }
            else -> {}
        }
    }
    
    private fun checkOnboardingStatus() {
        Log.d("MainActivity", "Checking onboarding status")
        
        lifecycleScope.launch {
            delay(500)
            
            // ALWAYS check permissions first, no matter what
            if (!permissionManager.areAllPermissionsGranted()) {
                Log.d("MainActivity", "Permissions missing - showing permission screen")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
                return@launch
            }
            
            // Only check Bluetooth if permissions are granted
            checkBluetoothAndProceed()
        }
    }
    
    /**
     * Check Bluetooth status and proceed with onboarding flow
     */
    private fun checkBluetoothAndProceed() {
        // Always check permissions first - if missing, go to permission screen
        if (!permissionManager.areAllPermissionsGranted()) {
            Log.d("MainActivity", "Missing permissions, redirecting to permission explanation")
            proceedWithPermissionCheck()
            return
        }
        
        // Check Bluetooth status
        bluetoothStatusManager.logBluetoothStatus()
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())
        
        when (mainViewModel.bluetoothStatus.value) {
            BluetoothStatus.ENABLED -> {
                // Bluetooth is enabled, check location services next
                checkLocationAndProceed()
            }
            BluetoothStatus.DISABLED -> {
                // Show Bluetooth enable screen (should have permissions as existing user)
                Log.d("MainActivity", "Bluetooth disabled, showing enable screen")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            BluetoothStatus.NOT_SUPPORTED -> {
                // Device doesn't support Bluetooth
                android.util.Log.e("MainActivity", "Bluetooth not supported")
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
        }
    }
    
    /**
     * Proceed with permission checking 
     */
    private fun proceedWithPermissionCheck() {
        Log.d("MainActivity", "Proceeding with permission check")
        
        lifecycleScope.launch {
            delay(200) // Small delay for smooth transition
            
            if (permissionManager.areAllPermissionsGranted()) {
                Log.d("MainActivity", "All permissions granted, initializing app")
                mainViewModel.updateOnboardingState(OnboardingState.INITIALIZING)
                initializeApp()
            } else {
                Log.d("MainActivity", "Missing permissions, showing explanation screen")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
        }
    }
    
    /**
     * Handle Bluetooth enabled callback
     */
    private fun handleBluetoothEnabled() {
        Log.d("MainActivity", "Bluetooth enabled by user")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(BluetoothStatus.ENABLED)
        checkLocationAndProceed()
    }

    /**
     * Check Location services status and proceed with onboarding flow
     */
    private fun checkLocationAndProceed() {
        Log.d("MainActivity", "Checking location services status")
        
        // Always check permissions first - if missing, go to permission screen
        if (!permissionManager.areAllPermissionsGranted()) {
            Log.d("MainActivity", "Missing permissions, redirecting to permission explanation")
            proceedWithPermissionCheck()
            return
        }
        
        // Check location status
        locationStatusManager.logLocationStatus()
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())
        
        when (mainViewModel.locationStatus.value) {
            LocationStatus.ENABLED -> {
                // Location services enabled, check battery optimization next
                checkBatteryOptimizationAndProceed()
            }
            LocationStatus.DISABLED -> {
                // Show location enable screen (should have permissions as existing user)
                Log.d("MainActivity", "Location services disabled, showing enable screen")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            LocationStatus.NOT_AVAILABLE -> {
                // Device doesn't support location services (very unusual)
                Log.e("MainActivity", "Location services not available")
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }

    /**
     * Handle Location enabled callback
     */
    private fun handleLocationEnabled() {
        Log.d("MainActivity", "Location services enabled by user")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(LocationStatus.ENABLED)
        checkBatteryOptimizationAndProceed()
    }

    /**
     * Handle Location disabled callback
     */
    private fun handleLocationDisabled(message: String) {
        Log.w("MainActivity", "Location services disabled or failed: $message")
        mainViewModel.updateLocationLoading(false)
        mainViewModel.updateLocationStatus(locationStatusManager.checkLocationStatus())

        when {
            mainViewModel.locationStatus.value == LocationStatus.NOT_AVAILABLE -> {
                // Show permanent error for devices without location services
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            else -> {
                // Stay on location check screen for retry
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
            }
        }
    }
    
    /**
     * Handle Bluetooth disabled callback
     */
    private fun handleBluetoothDisabled(message: String) {
        Log.w("MainActivity", "Bluetooth disabled or failed: $message")
        mainViewModel.updateBluetoothLoading(false)
        mainViewModel.updateBluetoothStatus(bluetoothStatusManager.checkBluetoothStatus())
        
        when {
            mainViewModel.bluetoothStatus.value == BluetoothStatus.NOT_SUPPORTED -> {
                // Show permanent error for unsupported devices
                mainViewModel.updateErrorMessage(message)
                mainViewModel.updateOnboardingState(OnboardingState.ERROR)
            }
            message.contains("Permission") && permissionManager.isFirstTimeLaunch() -> {
                // During first-time onboarding, if Bluetooth enable fails due to permissions,
                // proceed to permission explanation screen where user will grant permissions first
                Log.d("MainActivity", "Bluetooth enable requires permissions, proceeding to permission explanation")
                proceedWithPermissionCheck()
            }
            message.contains("Permission") -> {
                // For existing users, redirect to permission explanation to grant missing permissions
                Log.d("MainActivity", "Bluetooth enable requires permissions, showing permission explanation")
                mainViewModel.updateOnboardingState(OnboardingState.PERMISSION_EXPLANATION)
            }
            else -> {
                // Stay on Bluetooth check screen for retry
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
            }
        }
    }
    
    private fun handleOnboardingComplete() {
        Log.d("MainActivity", "Onboarding completed, checking Bluetooth and Location before initializing app")
        
        // After permissions are granted, re-check Bluetooth, Location, and Battery Optimization status
        val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
        val currentLocationStatus = locationStatusManager.checkLocationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        
        // If Bluetooth or Location is disabled, show enable screens
        // Otherwise proceed to initialization (battery optimization is optional and can be skipped)
        when {
            currentBluetoothStatus != BluetoothStatus.ENABLED -> {
                // Bluetooth still disabled, but now we have permissions to enable it
                Log.d("MainActivity", "Permissions granted, but Bluetooth still disabled. Showing Bluetooth enable screen.")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
            }
            currentLocationStatus != LocationStatus.ENABLED -> {
                // Location services still disabled, but now we have permissions to enable it
                Log.d("MainActivity", "Permissions granted, but Location services still disabled. Showing Location enable screen.")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
            else -> {
                // Bluetooth and Location are enabled, skip to complete
                Log.d("MainActivity", "Permissions granted, Bluetooth and Location enabled, going directly to app")
                initializeApp()
            }
        }
    }
    
    private fun handleOnboardingFailed(message: String) {
        Log.e("MainActivity", "Onboarding failed: $message")
        mainViewModel.updateErrorMessage(message)
        mainViewModel.updateOnboardingState(OnboardingState.ERROR)
    }
    
    /**
     * Check Battery Optimization status and proceed with onboarding flow
     */
    private fun checkBatteryOptimizationAndProceed() {
        android.util.Log.d("MainActivity", "Checking battery optimization status")
        
        // For first-time users, skip battery optimization check and go straight to permissions
        // We'll check battery optimization after permissions are granted
        if (permissionManager.isFirstTimeLaunch()) {
            android.util.Log.d("MainActivity", "First-time launch, skipping battery optimization check - will check after permissions")
            proceedWithPermissionCheck()
            return
        }
        
        // Check if user has previously skipped battery optimization
        if (BatteryOptimizationPreferenceManager.isSkipped(this)) {
            android.util.Log.d("MainActivity", "User previously skipped battery optimization, proceeding to permissions")
            proceedWithPermissionCheck()
            return
        }
        
        // For existing users, check battery optimization status
        batteryOptimizationManager.logBatteryOptimizationStatus()
        val currentBatteryOptimizationStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentBatteryOptimizationStatus)
        
        when (currentBatteryOptimizationStatus) {
            BatteryOptimizationStatus.DISABLED, BatteryOptimizationStatus.NOT_SUPPORTED -> {
                // Battery optimization is disabled or not supported, proceed with permission check
                proceedWithPermissionCheck()
            }
            BatteryOptimizationStatus.ENABLED -> {
                // Show battery optimization disable screen
                android.util.Log.d("MainActivity", "Battery optimization enabled, showing disable screen")
                mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
                mainViewModel.updateBatteryOptimizationLoading(false)
            }
        }
    }
    
    /**
     * Handle Battery Optimization disabled callback
     */
    private fun handleBatteryOptimizationDisabled() {
        android.util.Log.d("MainActivity", "Battery optimization disabled by user")
        mainViewModel.updateBatteryOptimizationLoading(false)
        mainViewModel.updateBatteryOptimizationStatus(BatteryOptimizationStatus.DISABLED)
        proceedWithPermissionCheck()
    }
    
    /**
     * Handle Battery Optimization failed callback
     */
    private fun handleBatteryOptimizationFailed(message: String) {
        android.util.Log.w("MainActivity", "Battery optimization disable failed: $message")
        mainViewModel.updateBatteryOptimizationLoading(false)
        val currentStatus = when {
            !batteryOptimizationManager.isBatteryOptimizationSupported() -> BatteryOptimizationStatus.NOT_SUPPORTED
            batteryOptimizationManager.isBatteryOptimizationDisabled() -> BatteryOptimizationStatus.DISABLED
            else -> BatteryOptimizationStatus.ENABLED
        }
        mainViewModel.updateBatteryOptimizationStatus(currentStatus)
        
        // Stay on battery optimization check screen for retry
        mainViewModel.updateOnboardingState(OnboardingState.BATTERY_OPTIMIZATION_CHECK)
    }
    
    private fun initializeApp() {
        // Initialize PoW preferences synchronously
        PoWPreferenceManager.init(this@MainActivity)
        
        // Start mesh service asynchronously - don't wait
        lifecycleScope.launch {
            try {
                meshService.delegate = chatViewModel
                meshService.startServices()
            } catch (e: Exception) {
                Log.w("MainActivity", "Mesh init error: ${e.message}")
            }
        }
        
        // Immediately mark as complete - don't wait for mesh
        mainViewModel.updateOnboardingState(OnboardingState.COMPLETE)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle notification intents when app is already running
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            handleNotificationIntent(intent)
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check Bluetooth and Location status on resume and handle accordingly
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            // Set app foreground state
            meshService.connectionManager.setAppBackgroundState(false)
            chatViewModel.setAppBackgroundState(false)

            // Check if Bluetooth was disabled while app was backgrounded
            val currentBluetoothStatus = bluetoothStatusManager.checkBluetoothStatus()
            if (currentBluetoothStatus != BluetoothStatus.ENABLED) {
                Log.w("MainActivity", "Bluetooth disabled while app was backgrounded")
                mainViewModel.updateBluetoothStatus(currentBluetoothStatus)
                mainViewModel.updateOnboardingState(OnboardingState.BLUETOOTH_CHECK)
                mainViewModel.updateBluetoothLoading(false)
                return
            }
            
            // Check if location services were disabled while app was backgrounded
            val currentLocationStatus = locationStatusManager.checkLocationStatus()
            if (currentLocationStatus != LocationStatus.ENABLED) {
                Log.w("MainActivity", "Location services disabled while app was backgrounded")
                mainViewModel.updateLocationStatus(currentLocationStatus)
                mainViewModel.updateOnboardingState(OnboardingState.LOCATION_CHECK)
                mainViewModel.updateLocationLoading(false)
            }
        }
    }
    
     override fun onPause() {
        super.onPause()
        // Only set background state if app is fully initialized
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            // Set app background state
            meshService.connectionManager.setAppBackgroundState(true)
            chatViewModel.setAppBackgroundState(true)
        }
    }
    
    /**
     * Handle intents from notification clicks - open specific private chat or geohash chat
     */
    private fun handleNotificationIntent(intent: Intent) {
        val shouldOpenPrivateChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_PRIVATE_CHAT, 
            false
        )
        
        val shouldOpenGeohashChat = intent.getBooleanExtra(
            com.bitchat.android.ui.NotificationManager.EXTRA_OPEN_GEOHASH_CHAT,
            false
        )
        
        when {
            shouldOpenPrivateChat -> {
                val peerID = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_PEER_ID)
                val senderNickname = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_SENDER_NICKNAME)
                
                if (peerID != null) {
                    Log.d("MainActivity", "Opening private chat with $senderNickname (peerID: $peerID) from notification")
                    
                    // Open the private chat with this peer
                    chatViewModel.startPrivateChat(peerID)
                    
                    // Clear notifications for this sender since user is now viewing the chat
                    chatViewModel.clearNotificationsForSender(peerID)
                }
            }
            
            shouldOpenGeohashChat -> {
                val geohash = intent.getStringExtra(com.bitchat.android.ui.NotificationManager.EXTRA_GEOHASH)
                
                if (geohash != null) {
                    Log.d("MainActivity", "Opening geohash chat #$geohash from notification")
                    
                    // Switch to the geohash channel - create appropriate geohash channel level
                    val level = when (geohash.length) {
                        7 -> com.bitchat.android.geohash.GeohashChannelLevel.BLOCK
                        6 -> com.bitchat.android.geohash.GeohashChannelLevel.NEIGHBORHOOD
                        5 -> com.bitchat.android.geohash.GeohashChannelLevel.CITY
                        4 -> com.bitchat.android.geohash.GeohashChannelLevel.PROVINCE
                        2 -> com.bitchat.android.geohash.GeohashChannelLevel.REGION
                        else -> com.bitchat.android.geohash.GeohashChannelLevel.CITY // Default fallback
                    }
                    val geohashChannel = com.bitchat.android.geohash.GeohashChannel(level, geohash)
                    val channelId = com.bitchat.android.geohash.ChannelID.Location(geohashChannel)
                    chatViewModel.selectLocationChannel(channelId)
                    
                    // Update current geohash state for notifications
                    chatViewModel.setCurrentGeohash(geohash)
                    
                    // Clear notifications for this geohash since user is now viewing it
                    chatViewModel.clearNotificationsForGeohash(geohash)
                }
            }
        }
    }

    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cleanup location status manager
        try {
            locationStatusManager.cleanup()
            Log.d("MainActivity", "Location status manager cleaned up successfully")
        } catch (e: Exception) {
            Log.w("MainActivity", "Error cleaning up location status manager: ${e.message}")
        }
        
        // Stop mesh services if app was fully initialized
        if (mainViewModel.onboardingState.value == OnboardingState.COMPLETE) {
            try {
                meshService.stopServices()
                Log.d("MainActivity", "Mesh services stopped successfully")
            } catch (e: Exception) {
                Log.w("MainActivity", "Error stopping mesh services in onDestroy: ${e.message}")
            }
        }
    }
}

