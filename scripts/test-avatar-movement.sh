#!/usr/bin/env bash
# Test: Avatar sweeps left → right → center → clear override, then verifies positions.
# Issue #59
set -euo pipefail

ADB="${ADB:-adb}"
PKG="com.yogaflow"
ACTIVITY="$PKG/.MainActivity"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ARTIFACTS_DIR="test-artifacts"
WAIT="${WAIT:-2}"  # seconds between positions (override with WAIT=3 ./script.sh)
POSITION_PY="$SCRIPT_DIR/test-avatar-position.py"

mkdir -p "$ARTIFACTS_DIR"

find_resource_center() {
  local resource_name="$1"
  local tmp_xml
  tmp_xml="$(mktemp)"
  "$ADB" shell uiautomator dump /sdcard/_yogaflow_ui.xml >/dev/null 2>&1 || {
    rm -f "$tmp_xml"
    return 1
  }
  "$ADB" pull /sdcard/_yogaflow_ui.xml "$tmp_xml" >/dev/null 2>&1 || {
    rm -f "$tmp_xml"
    return 1
  }
  python3 - "$tmp_xml" "$resource_name" <<'PY'
import re
import sys
import xml.etree.ElementTree as ET

xml_path, resource_name = sys.argv[1], sys.argv[2]
root = ET.parse(xml_path).getroot()
for node in root.iter("node"):
    resource_id = node.attrib.get("resource-id", "")
    if not (resource_id == resource_name or resource_id.endswith("/" + resource_name)):
        continue
    bounds = [int(v) for v in re.findall(r"\d+", node.attrib.get("bounds", ""))]
    if len(bounds) == 4:
        print(f"{(bounds[0] + bounds[2]) // 2} {(bounds[1] + bounds[3]) // 2}")
        sys.exit(0)
sys.exit(1)
PY
  local status=$?
  rm -f "$tmp_xml"
  return "$status"
}

tap_resource() {
  local resource_name="$1"
  local label="$2"
  local center
  center="$(find_resource_center "$resource_name")" || {
    echo "  FAIL: $label ($resource_name) not found in UI hierarchy"
    return 1
  }
  echo "  Tapping $label at $center"
  "$ADB" shell input tap $center
}

resource_exists() {
  find_resource_center "$1" >/dev/null 2>&1
}

echo "=== Avatar movement test ==="

# Wake device and dismiss keyguard / USB dialog
echo "[0/5] Waking device..."
"$ADB" shell input keyevent KEYCODE_WAKEUP
sleep 1
"$ADB" shell wm dismiss-keyguard 2>/dev/null || true
sleep 1
"$ADB" shell input keyevent KEYCODE_BACK  # dismiss any modal dialog (USB mode picker, etc.)
sleep 1

# Ensure app is running with self-test so avatar is visible and bridge connects
echo "[1/5] Launching app (with avatarSelfTest to keep avatar visible)..."
"$ADB" shell am force-stop "$PKG"
sleep 1
"$ADB" shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true --ez demoMode true
echo "  Waiting 8s for Godot to init and WebSocket bridge to connect..."
sleep 8

# Enter class/session UI so avatar captures cannot pass while still on the home course card.
echo "  Entering class screen (tapping startClassButton by resource id if present)..."
if resource_exists "startClassButton"; then
  tap_resource "startClassButton" "startClassButton"
  sleep 3
fi

if resource_exists "startClassButton"; then
  echo "  FAIL: still on home/course screen after tapping startClassButton"
  "$ADB" shell screencap -p /sdcard/_yogaflow_avatar_home_failure.png >/dev/null 2>&1 || true
  "$ADB" pull /sdcard/_yogaflow_avatar_home_failure.png "$ARTIFACTS_DIR/avatar-home-failure.png" >/dev/null 2>&1 || true
  exit 1
fi

if resource_exists "startButton"; then
  echo "  Entering running session (tapping bottom startButton when available)..."
  tap_resource "startButton" "startButton" || true
  sleep 3
fi

echo "  Waiting 2s for class/session + avatar to render..."
sleep 2

# Clear logcat so --logcat and --verify only see commands from this run
"$ADB" logcat -c

SWEEP_ERRORS=0

# Move to far left
echo "[2/5] Moving avatar to LEFT (-1.5)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX -1.5 --ef avatarTargetY 0.0
sleep "$WAIT"
uv run "$POSITION_PY" --capture "$ARTIFACTS_DIR/avatar-left.png" \
  --annotate "$ARTIFACTS_DIR/avatar-left-annotated.png" \
  --expected-side LEFT || SWEEP_ERRORS=$((SWEEP_ERRORS + 1))

# Move to far right
echo "[3/5] Moving avatar to RIGHT (+1.5)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX 1.5 --ef avatarTargetY 0.0
sleep "$WAIT"
uv run "$POSITION_PY" --capture "$ARTIFACTS_DIR/avatar-right.png" \
  --annotate "$ARTIFACTS_DIR/avatar-right-annotated.png" \
  --expected-side RIGHT || SWEEP_ERRORS=$((SWEEP_ERRORS + 1))

# Move to center
echo "[4/5] Moving to CENTER (0.0)..."
"$ADB" shell am start -n "$ACTIVITY" --ef avatarTargetX 0.0 --ef avatarTargetY 0.0
sleep "$WAIT"
uv run "$POSITION_PY" --capture "$ARTIFACTS_DIR/avatar-center.png" \
  --annotate "$ARTIFACTS_DIR/avatar-center-annotated.png" \
  --expected-side CENTER || SWEEP_ERRORS=$((SWEEP_ERRORS + 1))

# Clear override — resume auto-positioning
echo "[5/5] Clearing override (resuming auto-positioning)..."
"$ADB" shell am start -n "$ACTIVITY" --ez avatarClearOverride true
sleep "$WAIT"
uv run "$POSITION_PY" --capture "$ARTIFACTS_DIR/avatar-auto.png"

echo ""
echo "Screenshots saved to $ARTIFACTS_DIR/:"
ls -lh "$ARTIFACTS_DIR"/avatar-*.png 2>/dev/null || true

# ── Position verification ─────────────────────────────────────────────────────
echo ""
echo "=== Position verification (diff + ordering) ==="

VERIFY_EXIT=0
uv run "$POSITION_PY" --verify "$ARTIFACTS_DIR/" || VERIFY_EXIT=$?

echo ""
echo "=== Logcat positions (ground truth) ==="
uv run "$POSITION_PY" --logcat

echo ""
echo "=== Avatar movement test complete ==="
if [ "$SWEEP_ERRORS" -gt 0 ]; then
  echo "  FAIL: $SWEEP_ERRORS position(s) did not match expected side"
fi
exit $((VERIFY_EXIT + SWEEP_ERRORS))
