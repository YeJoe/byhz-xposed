@echo off
echo Building Xposed API stub jar...
if not exist "app\libs" mkdir app\libs
if not exist "app\stub-out" mkdir app\stub-out

rem Find android.jar (adjust path if needed)
set ANDROID_JAR=%LOCALAPPDATA%\Android\Sdk\platforms\android-34\android.jar
if not exist "%ANDROID_JAR%" (
    echo ERROR: android.jar not found at %ANDROID_JAR%
    echo Please update ANDROID_JAR path in this script.
    pause
    exit /b 1
)

rem Compile stub sources
dir /s /b app\stub-src\*.java > sources.txt
javac -cp "%ANDROID_JAR%" -d app\stub-out @sources.txt
del sources.txt

rem Package into jar
cd app\stub-out
jar cf ..\libs\xposed-api-stub.jar .
cd ..\..

rmdir /s /q app\stub-out
echo Done! Stub jar created at app\libs\xposed-api-stub.jar
pause
