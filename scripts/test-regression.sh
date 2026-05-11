#!/usr/bin/env bash
set -euo pipefail
ADB="${ADB:-adb}"
# Resolve JAVA_HOME from environment, then Homebrew, then give up with a clear error.
if [ -z "${JAVA_HOME:-}" ]; then
  BREW_JDK="$(brew --prefix openjdk@17 2>/dev/null)/libexec/openjdk.jdk/Contents/Home"
  if [ -d "$BREW_JDK" ]; then
    export JAVA_HOME="$BREW_JDK"
  else
    echo "ERROR: JAVA_HOME is not set and openjdk@17 not found via brew. Set JAVA_HOME and retry." >&2
    exit 1
  fi
fi
PKG="com.yogaflow"
ACTIVITY="$PKG/.MainActivity"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS="test-artifacts"

mkdir -p "$ARTIFACTS"

echo "=== Step 1: Gradle unit tests ==="
./gradlew test 2>&1 | tail -10

echo "=== Step 2: Build APK ==="
./gradlew assembleDebug 2>&1 | tail -5

echo "=== Step 3: Install ==="
"$ADB" install -r app/build/outputs/apk/debug/app-debug.apk

echo "=== Step 4: Godot init check ==="
"$ADB" shell am force-stop "$PKG"; sleep 1
"$ADB" shell input keyevent KEYCODE_WAKEUP; sleep 1
"$ADB" shell wm dismiss-keyguard 2>/dev/null || true; sleep 1
"$ADB" shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
echo "  Waiting 10s for Godot init..."
sleep 10
# 9090 decimal = 0x2382; /proc/net/tcp* encodes ports as uppercase hex in field 2 (local_address:port)
BRIDGE_UP=$({ "$ADB" shell cat /proc/net/tcp6 2>/dev/null; "$ADB" shell cat /proc/net/tcp 2>/dev/null; } | awk '{print $2}' | grep -ci "2382" || true)
if [ "$BRIDGE_UP" -gt 0 ]; then echo "  PASS: port 9090 (0x2382) listening"; else echo "  FAIL: port 9090 not found"; fi

echo "=== Step 5: Avatar position sweep ==="
bash "$SCRIPT_DIR/test-avatar-movement.sh"

echo "=== Step 6: Screenshot size check ==="
# 10 KB minimum — a truly blank/black PNG is ~3–5 KB; any real UI frame with content exceeds 10 KB.
# (PNG compresses dark solid-color screens heavily; 88 KB is a valid home-screen capture.)
for f in "$ARTIFACTS"/avatar-left.png "$ARTIFACTS"/avatar-right.png; do
  SIZE=$(wc -c < "$f" 2>/dev/null || echo 0)
  if [ "$SIZE" -gt 10000 ]; then echo "  PASS: $f ($SIZE bytes)"; else echo "  FAIL: $f too small ($SIZE bytes) — blank screen?"; fi
done

echo "=== Step 7: Logcat crash check ==="
# Match only actual crash indicators:
#   "FATAL EXCEPTION" — JVM/Android crash header
#   "E AndroidRuntime" — error-level AndroidRuntime (stack traces after a crash)
# Excludes "I GodotPluginRegistry: ... AndroidRuntime" (INFO plugin registration, not a crash).
CRASHES=$("$ADB" logcat -d 2>/dev/null | grep -cE "FATAL EXCEPTION|E AndroidRuntime" || true)
if [ "$CRASHES" -eq 0 ]; then echo "  PASS: no crashes"; else echo "  FAIL: $CRASHES crash lines found"; fi

echo ""
echo "=== Regression complete ==="
