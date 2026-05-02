@echo off
echo Cleaning Gradle cache and rebuilding...
call gradlew.bat clean
call gradlew.bat assembleDebug
echo Build complete!
pause
