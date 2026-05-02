# BITCHAT EDUCATIONAL APK - QUICK SETUP GUIDE

# =============================================

## 🚀 FASTEST WAY TO GET YOUR APK

### Option A: Download Minimal Android Tools (Recommended)

1. **Download Android Command Line Tools** (150MB only):

   - Link: https://dl.google.com/android/repository/commandlinetools-win-11076708_latest.zip
   - OR go to: https://developer.android.com/studio#command-tools

2. **Extract to**: `C:\Android\Sdk\cmdline-tools\latest\`

   - Create folders: `C:\Android\Sdk\cmdline-tools\latest\`
   - Extract all files there

3. **Run the build script**:
   ```
   cd C:\Users\Rishi\Desktop\bit-chat\bitchat-android-main
   build-educational-apk.bat
   ```

### Option B: If You Have Android Studio Installed

1. **Just run the build script** - it will auto-detect your SDK
2. **Or manually build**:
   ```
   gradlew assembleDebug
   ```

### Option C: Manual Gradle Build (If SDK exists)

```bash
cd bitchat-android-main
.\gradlew assembleDebug
```

## 📱 INSTALL ON YOUR PHONE

1. **Copy the APK**: `app\build\outputs\apk\debug\app-debug.apk`
2. **Transfer to phone** via USB/Bluetooth/WhatsApp
3. **Enable "Unknown Sources"** in Android Settings
4. **Install the APK**

## 🎓 TEST YOUR EDUCATIONAL FEATURES

### Test 1: Educational Mode

- Open app → Tap "Education" button in header
- Should show educational dashboard

### Test 2: Language Translation

- Go to "Language Settings"
- Change to Hindi → Test translation
- Try: "mathematics" → should show "गणित"

### Test 3: Bluetooth Classroom

- Go to "Bluetooth Classroom"
- Try "Create Classroom" (Teacher mode)
- Try "Join Classroom" (Student mode)

### Test 4: File Sharing

- Go to "File Sharing"
- Select a PDF/image file
- Share via Bluetooth

## 🔧 TROUBLESHOOTING

### If Build Fails:

1. **Check SDK path** in `local.properties`
2. **Download missing components**:
   ```
   sdkmanager "platform-tools" "platforms;android-34"
   ```
3. **Try different SDK location**

### If APK Install Fails:

1. **Enable Developer Options** on phone
2. **Allow "Install unknown apps"**
3. **Use different file transfer method**

## 📋 WHAT'S INCLUDED IN YOUR APK

✅ **Complete Educational System**:

- Bluetooth classroom management
- Offline Hindi translation (500+ terms)
- File sharing for rural areas
- Educational content viewer
- Language settings
- Progress tracking

✅ **Perfect for Rural Schools**:

- No internet required
- Bluetooth-only distribution
- Hindi language support
- Educational vocabulary pre-loaded

## 🆘 NEED HELP?

If the build script doesn't work:

1. Check if you can run: `java -version`
2. Check if you can run: `.\gradlew --version`
3. Try installing Android Studio (full version)
4. Or use online build services

Your educational transformation is ready! 🎓📱
