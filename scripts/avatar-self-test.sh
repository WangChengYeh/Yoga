#!/usr/bin/env bash
set -euo pipefail

PKG="com.yogaflow"
ACTIVITY="${PKG}/.MainActivity"
ADB="${ADB:-adb}"
ARTIFACTS="test-artifacts"

mkdir -p "$ARTIFACTS"

echo "=== YogaFlow Avatar Self-Test ==="

echo "[1/5] Installing debug APK..."
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

echo "[2/5] Launching app with camera bypass + avatar self-test..."
$ADB shell am start -n "$ACTIVITY" \
  --ez devDisableCameraSetup true \
  --ez avatarSelfTest true

echo "[3/5] Waiting for Godot to start and avatar to cycle poses (16s)..."
sleep 16

echo "[4/5] Capturing screenshot..."
$ADB shell screencap -p /sdcard/avatar-self-test.png
$ADB pull /sdcard/avatar-self-test.png "${ARTIFACTS}/avatar-self-test.png"
$ADB shell rm /sdcard/avatar-self-test.png

echo "[5/5] Dumping UI hierarchy..."
$ADB shell uiautomator dump /sdcard/ui-dump.xml
$ADB pull /sdcard/ui-dump.xml "${ARTIFACTS}/ui-dump.xml"
$ADB shell rm /sdcard/ui-dump.xml

echo "Screenshot saved: ${ARTIFACTS}/avatar-self-test.png"
echo "UI dump saved:    ${ARTIFACTS}/ui-dump.xml"
echo "PASS: Avatar self-test complete. Inspect screenshot to visually verify avatar poses."
