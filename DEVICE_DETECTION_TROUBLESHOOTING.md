# Device Detection Troubleshooting Guide

## ✅ Fixed Issues (February 2026)

The device detection system has been improved with:

1. **Enhanced Permission Checking** - Now validates all required permissions for different Android versions
2. **Location Services Check** - Verifies location is enabled (required for BLE scanning)
3. **Better Error Messages** - Clear, actionable error messages with troubleshooting steps
4. **Automatic Retry Logic** - Automatically retries scanning after failures
5. **Improved Logging** - Detailed logs to help diagnose issues

---

## 🔍 Quick Checklist

Before using BitChat, ensure:

- [ ] **Bluetooth is ON** - Check your device's Bluetooth settings
- [ ] **Location Services are ON** - Required for BLE scanning on Android
- [ ] **All Permissions Granted** - BitChat needs Bluetooth and Location permissions
- [ ] **Another Device Nearby** - Need at least 2 devices running BitChat
- [ ] **Both Devices Advertising** - Make sure both have the app open and active
- [ ] **Within Range** - Devices should be within 10-30 meters

---

## 📱 Step-by-Step Setup

### 1. Enable Bluetooth
```
Settings → Connected devices → Bluetooth → ON
```

### 2. Enable Location Services
```
Settings → Location → ON
```
**Why?** Android requires location services for Bluetooth Low Energy scanning

### 3. Grant Permissions
When the app starts:
- Allow **Bluetooth** permission
- Allow **Nearby Devices** permission (Android 12+)
- Allow **Location** permission

### 4. Check App Logs
Open Android Logcat and filter by tag:
```
BluetoothGattClientManager
BluetoothPermissionManager
```

Look for these success messages:
```
✅ BLE scan started successfully!
✅ Found and connected to X device(s)
```

---

## 🐛 Common Issues & Solutions

### Issue 1: "Missing Bluetooth/Location permissions"
**Solution:** Go to Settings → Apps → BitChat → Permissions and enable all

### Issue 2: "Location services are disabled"
**Solution:** 
1. Open Settings → Location
2. Toggle Location ON
3. Restart BitChat app

### Issue 3: "No devices found after 10 seconds"
**Possible causes:**
1. Other device doesn't have BitChat running
2. Devices are too far apart (>30 meters)
3. Bluetooth interference from other devices
4. One device's Bluetooth is in bad state

**Solutions:**
1. Restart both devices
2. Move devices closer together
3. Toggle Bluetooth OFF then ON on both devices
4. Ensure both apps are in foreground (not background)

### Issue 4: "SCAN_FAILED_SCANNING_TOO_FREQUENTLY"
**Cause:** Android is rate-limiting Bluetooth scans
**Solution:** Wait 10 seconds - the app will automatically retry

### Issue 5: "SCAN_FAILED_OUT_OF_HARDWARE_RESOURCES"
**Cause:** Too many apps using Bluetooth simultaneously
**Solution:** 
1. Close other apps using Bluetooth
2. Restart your device
3. Try again

---

## 🔬 Advanced Diagnostics

### Check Bluetooth Adapter
```kotlin
// The app now logs this automatically:
Log.i(TAG, "BLE scanner available: ${bleScanner != null}")
Log.i(TAG, "Bluetooth enabled: ${bluetoothAdapter?.isEnabled}")
```

### Verify Service UUID
The app scans for UUID: `F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C`

Both devices must use the same UUID (this is automatic in BitChat)

### Debug Settings
If you have debug UI enabled:
- Ensure "GATT Client Enabled" is ON
- Ensure "GATT Server Enabled" is ON
- Check connection limits are reasonable (default: 8)

---

## 📊 Understanding Scan Modes

### Power Modes (Affects Scanning)

**BALANCED** (Default - recommended)
- Good device discovery
- Reasonable battery usage
- Scan mode: LOW_LATENCY

**PERFORMANCE**
- Best device discovery
- Higher battery usage
- Scan mode: LOW_LATENCY with aggressive matching

**POWER_SAVER**
- Slower device discovery
- Better battery life
- Scan mode: LOW_POWER

---

## 🚀 Best Practices

1. **Start Simple**: Test with 2 devices in the same room first
2. **Keep Apps in Foreground**: Android limits background BLE scanning
3. **Be Patient**: Initial discovery can take 5-10 seconds
4. **Check Logs**: Enable verbose logging to see what's happening
5. **Restart When Stuck**: If no devices found after 30 seconds, restart both apps

---

## 💡 Expected Behavior

**Good Scan Start:**
```
========================================
🔍 Starting BLE scan for nearby devices
   Service UUID: F47B5E2D-4A9E-4C5A-9B3F-8E1D2C3A4B5C
   Scan Mode: ScanSettings{...}
   Location Enabled: true
========================================
✅ BLE scan started successfully!
   Listening for BitChat devices nearby...
```

**Device Found:**
```
✅ Found and connected to 1 device(s)
```

**Problem Detected:**
```
⚠️ No devices found after 10 seconds of scanning
   Troubleshooting:
   1. Check if other devices have Bluetooth enabled
   2. Verify location services are ON
   3. Ensure devices are close (within 10-30 meters)
   4. Check if other devices are running BitChat
```

---

## 🛠️ Still Having Issues?

1. **Check Android Version**: Minimum API 26 (Android 8.0) required
2. **Verify BLE Support**: Some devices don't support BLE properly
3. **Test with Multiple Devices**: Try different device combinations
4. **Share Logs**: Check Logcat output for detailed error messages

---

## 📞 Getting Help

Include in your bug report:
- Android version
- Device model
- Logcat output (filter: BluetoothGatt*)
- Permissions status
- Location services status
- Number of nearby BitChat devices

---

*Last Updated: February 2026*
*Version: 1.3.0+*
