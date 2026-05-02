# Permission Mismatch Issue - Location Permissions

## Problem Description

**Symptom**: App shows "fine location is not given" error even though all permissions appear to be granted in system settings. Device detection via Bluetooth does not work.

**Affected Devices**: Fresh installs on Android 12-13 (API 31-33)

**User Experience**: 
- Onboarding completes successfully
- System shows all permissions as granted
- App logs show "missing permissions" or "cannot start scanning"
- BLE device detection fails to start
- No nearby devices detected

## Root Cause

**Permission Request/Check Mismatch** between two components:

### 1. PermissionManager.kt (Onboarding - What We Request)
Previously requested on Android 12+:
- ✅ `ACCESS_COARSE_LOCATION` only

### 2. BluetoothPermissionManager.kt (Runtime - What We Check)
Always checked on Android 10+:
- ❌ `ACCESS_FINE_LOCATION` required

### Result
On Android 12-13 devices:
1. Onboarding only requests COARSE location
2. User grants COARSE location ✅
3. App proceeds thinking all permissions granted
4. BLE scanning checks for FINE location
5. FINE location was never requested ❌
6. Permission check fails
7. Scanning never starts
8. Device detection doesn't work

## Technical Details

### Affected Files
- `app/src/main/java/com/bitchat/android/onboarding/PermissionManager.kt` - Lines 68-88, 184-193
- `app/src/main/java/com/bitchat/android/mesh/BluetoothPermissionManager.kt` - Lines 40-48
- `app/src/main/java/com/bitchat/android/mesh/BluetoothGattClientManager.kt` - Line 229

### Why It Wasn't Caught Earlier
- Previous installs had FINE location granted from older app versions
- Testing on Android 11 or below (where both permissions were always requested)
- The bug only manifests on **fresh installs on Android 12-13**

### Incorrect Assumption
The original code assumed `BLUETOOTH_SCAN` had `android:usesPermissionFlags="neverForLocation"` in AndroidManifest.xml, which would exempt it from fine location requirements. However:

**AndroidManifest.xml** (Line 14):
```xml
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
```

**Missing**: `android:usesPermissionFlags="neverForLocation"`

Without this flag, BLUETOOTH_SCAN still requires fine location on Android 12+.

## Solution

### Fix Applied
Simplified permission logic to request both COARSE and FINE location on all Android versions.

**File**: `app/src/main/java/com/bitchat/android/onboarding/PermissionManager.kt`

#### Change 1: getRequiredPermissions() method
**Before** (Lines 68-88):
```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
} else {
    permissions.addAll(listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    ))
}
```

**After**:
```kotlin
// Location permissions - required for Bluetooth LE scanning
permissions.addAll(listOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
))
```

#### Change 2: getCategorizedPermissions() method
**Before** (Lines 184-193):
```kotlin
val locationPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
} else {
    listOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )
}
```

**After**:
```kotlin
// Location category - match the logic in getRequiredPermissions()
val locationPermissions = listOf(
    Manifest.permission.ACCESS_COARSE_LOCATION,
    Manifest.permission.ACCESS_FINE_LOCATION
)
```

## Verification Steps

After applying the fix:

1. **Uninstall the app completely** (to clear all previous permissions)
2. Reinstall from the new build
3. Complete onboarding
4. Verify system settings show both COARSE and FINE location granted
5. Check logs for successful BLE scanning initialization
6. Verify device detection works

### Log Indicators

**Before Fix** (Error):
```
[ERROR] Cannot start scanning - missing permissions or location services disabled
Missing permissions: ACCESS_FINE_LOCATION
```

**After Fix** (Success):
```
[INFO] Starting BLE scanning...
[INFO] Bluetooth permissions granted, location enabled
```

## Prevention

To avoid similar issues in the future:

1. **Always match permission requests with permission checks**
   - If you request permissions in PermissionManager, ensure runtime checks require the same
   - If you add new permission checks, update PermissionManager accordingly

2. **Test on fresh installs** across all supported Android versions
   - Android 11 and below (API ≤ 30)
   - Android 12-13 (API 31-33)
   - Android 14+ (API 34+)

3. **Check both methods in PermissionManager.kt**:
   - `getRequiredPermissions()` - what we request
   - `getCategorizedPermissions()` - what we display to users

4. **Version-specific logic requires careful validation**
   - If permissions differ by Android version, document why
   - Ensure manifest flags match code assumptions

## Related Files

- `app/src/main/java/com/bitchat/android/onboarding/PermissionManager.kt`
- `app/src/main/java/com/bitchat/android/mesh/BluetoothPermissionManager.kt`
- `app/src/main/java/com/bitchat/android/mesh/BluetoothGattClientManager.kt`
- `app/src/main/AndroidManifest.xml`

## Date Fixed
February 11, 2026

## Notes

- This issue is **not related** to recent class/class ID or internet/teleport feature changes
- The bug existed in the codebase but was exposed during fresh install testing
- Both permission methods now use simplified, version-independent logic for location permissions
