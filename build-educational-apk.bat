@echo off
echo ========================================
echo   BITCHAT EDUCATIONAL APK BUILDER
echo   Rural Education App - Offline Ready
echo ========================================
echo.

REM Check if Android SDK exists
echo [1/5] Checking Android SDK...
if exist "C:\android\Sdk\cmdline-tools" (
    echo ✓ Android SDK found at C:\android\Sdk
    set SDK_PATH=C:\android\Sdk
) else if exist "C:\Android\Sdk\cmdline-tools" (
    echo ✓ Android SDK found at C:\Android\Sdk
    set SDK_PATH=C:\Android\Sdk
) else if exist "C:\Users\%USERNAME%\AppData\Local\Android\Sdk" (
    echo ✓ Android SDK found at %USERPROFILE%\AppData\Local\Android\Sdk
    set SDK_PATH=%USERPROFILE%\AppData\Local\Android\Sdk
) else if exist "C:\Program Files\Android\Android Studio\sdk" (
    echo ✓ Android SDK found at Program Files
    set SDK_PATH=C:\Program Files\Android\Android Studio\sdk
) else (
    echo ❌ Android SDK not found!
    echo.
    echo Please download Android Command Line Tools from:
    echo https://developer.android.com/studio#command-tools
    echo.
    echo Extract to: C:\Android\Sdk
    echo Then run this script again.
    pause
    exit /b 1
)

REM Update local.properties with forward slashes (more reliable)
echo [2/5] Updating SDK configuration...
set "SDK_PATH_FORWARD=%SDK_PATH:\=/%"
echo sdk.dir=%SDK_PATH_FORWARD% > local.properties
echo ✓ SDK path configured

REM Set environment variable
echo [3/5] Setting environment variables...
set ANDROID_HOME=%SDK_PATH%
set PATH=%PATH%;%SDK_PATH%\cmdline-tools\latest\bin;%SDK_PATH%\platform-tools
echo ✓ Environment configured

REM Clean previous builds
echo [4/5] Cleaning previous builds...
call gradlew clean
if %ERRORLEVEL% neq 0 (
    echo ❌ Clean failed. Check SDK installation.
    pause
    exit /b 1
)
echo ✓ Clean completed

REM Build debug APK
echo [5/5] Building Educational APK...
echo This may take 5-10 minutes for first build...
call gradlew assembleDebug
if %ERRORLEVEL% neq 0 (
    echo ❌ Build failed. Check errors above.
    pause
    exit /b 1
)

echo.
echo ========================================
echo   🎓 BUILD SUCCESSFUL! 🎓
echo ========================================
echo.
echo Your educational APK is ready at:
echo app\build\outputs\apk\debug\app-debug.apk
echo.
echo Features included:
echo ✓ Offline Hindi translation (500+ terms)
echo ✓ Bluetooth classroom system
echo ✓ File sharing for rural areas
echo ✓ Educational content management
echo ✓ Language settings
echo.
echo Next steps:
echo 1. Copy app-debug.apk to your Android phone
echo 2. Enable "Install from unknown sources"
echo 3. Install and test the educational features
echo.
pause