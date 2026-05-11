#!/usr/bin/env bash
# Test: Avatar sweeps left → right → left → clear override
# Issue #59
set -euo pipefail

ADB="${ADB:-adb}"
PKG="com.yogaflow"
ACTIVITY="$PKG/.MainActivity"
ARTIFACTS_DIR="test-artifacts"
WAIT="${WAIT:-2}"  # seconds between positions (override with WAIT=3 ./script.sh)

mkdir -p "$ARTIFACTS_DIR"

echo "=== Avatar movement test ==="

# Wake device and dismiss keyguard / USB dialog
echo "[0/6] Waking device..."
"$ADB" shell input keyevent KEYCODE_WAKEUP
sleep 1
"$ADB" shell wm dismiss-keyguard 2>/dev/null || true
sleep 1
"$ADB" shell input keyevent KEYCODE_BACK  # dismiss any modal dialog (USB mode picker, etc.)
sleep 1

# Ensure app is running with self-test so avatar is visible and bridge connects
echo "[1/6] Launching app (with avatarSelfTest to keep avatar visible)..."
"$ADB" shell am force-stop "$PKG"
sleep 1
"$ADB" shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
echo "  Waiting 8s for Godot to init and WebSocket bridge to connect..."
sleep 8

# Move to far left
echo "[2/5] Moving avatar to LEFT (-1.5)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX -1.5 --ef avatarTargetY 0.0
sleep "$WAIT"
"$ADB" shell screencap -p /sdcard/avatar-left.png
"$ADB" pull /sdcard/avatar-left.png "$ARTIFACTS_DIR/avatar-left.png" >/dev/null
"$ADB" shell rm /sdcard/avatar-left.png
echo "  → screenshot: $ARTIFACTS_DIR/avatar-left.png"

# Move to far right
echo "[3/5] Moving avatar to RIGHT (+1.5)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX 1.5 --ef avatarTargetY 0.0
sleep "$WAIT"
"$ADB" shell screencap -p /sdcard/avatar-right.png
"$ADB" pull /sdcard/avatar-right.png "$ARTIFACTS_DIR/avatar-right.png" >/dev/null
"$ADB" shell rm /sdcard/avatar-right.png
echo "  → screenshot: $ARTIFACTS_DIR/avatar-right.png"

# Move to center
echo "[4/5] Moving to CENTER (0.0)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX 0.0 --ef avatarTargetY 0.0
sleep "$WAIT"
"$ADB" shell screencap -p /sdcard/avatar-center.png
"$ADB" pull /sdcard/avatar-center.png "$ARTIFACTS_DIR/avatar-center.png" >/dev/null
"$ADB" shell rm /sdcard/avatar-center.png
echo "  → screenshot: $ARTIFACTS_DIR/avatar-center.png"

# Clear override — resume auto-positioning
echo "[5/5] Clearing override (resuming auto-positioning)..."
"$ADB" shell am start -n "$ACTIVITY" --ez avatarClearOverride true
sleep "$WAIT"
"$ADB" shell screencap -p /sdcard/avatar-auto.png
"$ADB" pull /sdcard/avatar-auto.png "$ARTIFACTS_DIR/avatar-auto.png" >/dev/null
"$ADB" shell rm /sdcard/avatar-auto.png
echo "  → screenshot: $ARTIFACTS_DIR/avatar-auto.png"

echo ""
echo "=== Test complete ==="
echo "Screenshots saved to $ARTIFACTS_DIR/:"
ls -lh "$ARTIFACTS_DIR"/avatar-*.png 2>/dev/null || true

echo ""
echo "Check logcat for any errors:"
"$ADB" logcat -d | grep -E "YogaFlow|AndroidRuntime|FATAL" | tail -10 || true
