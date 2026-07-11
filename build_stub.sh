#!/bin/bash
echo "Building Xposed API stub jar..."
mkdir -p app/libs app/stub-out

# Find android.jar
ANDROID_JAR="$ANDROID_HOME/platforms/android-34/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    ANDROID_JAR="$HOME/Android/Sdk/platforms/android-34/android.jar"
fi
if [ ! -f "$ANDROID_JAR" ]; then
    echo "ERROR: android.jar not found"
    exit 1
fi

# Compile stub sources
javac -cp "$ANDROID_JAR" \
    -d app/stub-out \
    $(find app/stub-src -name "*.java")

# Package into jar
cd app/stub-out && jar cf ../libs/xposed-api-stub.jar . && cd ../..
rm -rf app/stub-out
echo "Done! Stub jar created at app/libs/xposed-api-stub.jar"
