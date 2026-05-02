#!/bin/bash
# Educational APK Builder for Linux/Mac
echo "========================================"
echo "  BITCHAT EDUCATIONAL APK BUILDER"
echo "  Rural Education App - Offline Ready"
echo "========================================"
echo

# Check for Android SDK
echo "[1/5] Checking Android SDK..."
if [ -d "$ANDROID_HOME" ]; then
    echo "✓ Android SDK found at $ANDROID_HOME"
elif [ -d "$HOME/Android/Sdk" ]; then
    export ANDROID_HOME="$HOME/Android/Sdk"
    echo "✓ Android SDK found at $ANDROID_HOME"
elif [ -d "/opt/android-sdk" ]; then
    export ANDROID_HOME="/opt/android-sdk"
    echo "✓ Android SDK found at $ANDROID_HOME"
else
    echo "❌ Android SDK not found!"
    echo
    echo "Please install Android Command Line Tools:"
    echo "https://developer.android.com/studio#command-tools"
    exit 1
fi

# Update local.properties
echo "[2/5] Updating SDK configuration..."
echo "sdk.dir=$ANDROID_HOME" > local.properties
echo "✓ SDK path configured"

# Set PATH
echo "[3/5] Setting environment variables..."
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
echo "✓ Environment configured"

# Clean
echo "[4/5] Cleaning previous builds..."
./gradlew clean
if [ $? -ne 0 ]; then
    echo "❌ Clean failed. Check SDK installation."
    exit 1
fi
echo "✓ Clean completed"

# Build
echo "[5/5] Building Educational APK..."
echo "This may take 5-10 minutes for first build..."
./gradlew assembleDebug
if [ $? -ne 0 ]; then
    echo "❌ Build failed. Check errors above."
    exit 1
fi

echo
echo "========================================"
echo "  🎓 BUILD SUCCESSFUL! 🎓"
echo "========================================"
echo
echo "Your educational APK is ready at:"
echo "app/build/outputs/apk/debug/app-debug.apk"
echo
echo "Features included:"
echo "✓ Offline Hindi translation (500+ terms)"
echo "✓ Bluetooth classroom system"
echo "✓ File sharing for rural areas"
echo "✓ Educational content management"
echo "✓ Language settings"
echo
echo "Transfer the APK to your Android device and install!"