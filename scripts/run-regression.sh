#!/usr/bin/env bash
set -euo pipefail
ADB="${ADB:-/Users/wangchengye/Library/Android/sdk/platform-tools/adb}"
JAVA_HOME_PATH="/opt/homebrew/Cellar/openjdk@17/17.0.16/libexec/openjdk.jdk/Contents/Home"
PKG="com.yogaflow"
ACTIVITY="$PKG/.MainActivity"
ARTIFACTS="test-artifacts"

mkdir -p "$ARTIFACTS"

echo "=== Step 1: Gradle unit tests ==="
JAVA_HOME="$JAVA_HOME_PATH" ./gradlew test 2>&1 | tail -10

echo "=== Step 2: Build APK ==="
JAVA_HOME="$JAVA_HOME_PATH" ./gradlew assembleDebug 2>&1 | tail -5

echo "=== Step 3: Install ==="
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk

echo "=== Step 4: Godot init check ==="
"$ADB" shell am force-stop "$PKG"; sleep 1
"$ADB" shell input keyevent KEYCODE_WAKEUP; sleep 1
"$ADB" shell wm dismiss-keyguard 2>/dev/null || true; sleep 1
"$ADB" shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
echo "  Waiting 10s for Godot init..."
sleep 10
BRIDGE_UP=$({ "$ADB" shell cat /proc/net/tcp6 2>/dev/null; "$ADB" shell cat /proc/net/tcp 2>/dev/null; } | awk '{print $2}' | grep -ci "2382" || true)
if [ "$BRIDGE_UP" -gt 0 ]; then echo "  PASS: port 9090 listening"; else echo "  FAIL: port 9090 not found"; fi

echo "=== Step 5: Avatar position sweep ==="
bash scripts/test-avatar-movement.sh

echo "=== Step 6: Screenshot size check ==="
for f in "$ARTIFACTS"/avatar-left.png "$ARTIFACTS"/avatar-right.png; do
  SIZE=$(wc -c < "$f" 2>/dev/null || echo 0)
  if [ "$SIZE" -gt 500000 ]; then echo "  PASS: $f ($SIZE bytes)"; else echo "  FAIL: $f too small ($SIZE bytes)"; fi
done

echo "=== Step 7: Logcat crash check ==="
CRASHES=$("$ADB" logcat -d 2>/dev/null | grep -cE "FATAL EXCEPTION|AndroidRuntime" || true)
if [ "$CRASHES" -eq 0 ]; then echo "  PASS: no crashes"; else echo "  FAIL: $CRASHES crash lines found"; fi

echo ""
echo "=== Regression complete ==="
