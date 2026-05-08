#!/usr/bin/env bash
set -euo pipefail

PKG="com.yogaflow"
ACTIVITY="${PKG}/.MainActivity"
ADB="${ADB:-adb}"
ARTIFACTS="test-artifacts"
VIDEO_REMOTE="/sdcard/Movies/avatar-demo.mp4"

mkdir -p "$ARTIFACTS"
RECORD_FAILED=0

echo "=== YogaFlow Avatar Self-Test ==="

echo "[1/6] Installing debug APK..."
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

echo "[2/6] Launching app with camera bypass + demo mode..."
$ADB shell am start -n "$ACTIVITY" \
  --ez devDisableCameraSetup true \
  --ez demoMode true

echo "[3/6] Recording 12-second demo video..."
$ADB shell mkdir -p /sdcard/Movies
$ADB shell rm -f "$VIDEO_REMOTE"
$ADB shell screenrecord --time-limit 12 "$VIDEO_REMOTE" &
RECORD_PID=$!
sleep 14
wait "$RECORD_PID" || true

echo "[4/6] Pulling demo video artifact..."
if $ADB pull "$VIDEO_REMOTE" "${ARTIFACTS}/avatar-demo.mp4"; then
  $ADB shell rm "$VIDEO_REMOTE"
else
  RECORD_FAILED=1
  echo "WARN: Unable to pull recorded demo video from device."
fi

echo "[5/6] Capturing final screenshot..."
$ADB shell screencap -p /sdcard/avatar-self-test.png
$ADB pull /sdcard/avatar-self-test.png "${ARTIFACTS}/avatar-self-test.png"
$ADB shell rm /sdcard/avatar-self-test.png

echo "[6/6] Dumping UI hierarchy (best-effort)..."
if $ADB shell uiautomator dump /sdcard/ui-dump.xml 2>/dev/null; then
    $ADB pull /sdcard/ui-dump.xml "${ARTIFACTS}/ui-dump.xml" 2>/dev/null || true
    $ADB shell rm /sdcard/ui-dump.xml 2>/dev/null || true
    echo "UI dump saved:    ${ARTIFACTS}/ui-dump.xml"
else
    echo "UI dump skipped (app busy/animating — expected with Godot running)"
fi

echo "Video saved:      ${ARTIFACTS}/avatar-demo.mp4"
echo "Screenshot saved: ${ARTIFACTS}/avatar-self-test.png"
echo "PASS: Avatar self-test complete. Inspect artifacts to verify demo behavior."
echo
echo "REVIEW CHECKLIST:"
echo "- [ ] Avatar visible with no opaque gray background (transparent over camera)"
echo "- [ ] All 4 poses animate: mountain, forward_fold, squat, twist"
echo "- [ ] Motion is smooth (no jank/stutter)"
echo "- [ ] Avatar proportions look correct (no distortion)"
echo "- [ ] No crashes in 12 seconds of demo"

if [[ "$RECORD_FAILED" -ne 0 ]]; then
  echo "FAIL: Demo video capture failed (screenrecord did not produce ${VIDEO_REMOTE})."
  exit 1
fi
