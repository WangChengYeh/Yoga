# YogaFlow 3D — Test Plan

All commands use:
```bash
ADB="/Users/wangchengye/Library/Android/sdk/platform-tools/adb"
PKG="com.yogaflow"
ACTIVITY="$PKG/.MainActivity"
JAVA_HOME="/opt/homebrew/Cellar/openjdk@17/17.0.16/libexec/openjdk.jdk/Contents/Home"
```

---

## 1. Build Verification

**Goal**: APK compiles clean from source.

```bash
cd /Users/wangchengye/Documents/GitHub/Yoga
$JAVA_HOME/bin/java -version          # must be 17.x
$JAVA_HOME/../../../bin/javac -version
JAVA_HOME="$JAVA_HOME" ./gradlew assembleDebug 2>&1 | tail -5
```

**Pass**: `BUILD SUCCESSFUL` with no errors or unresolved symbols.

**Also check**:
```bash
JAVA_HOME="$JAVA_HOME" ./gradlew test 2>&1 | tail -10   # unit tests
```

---

## 2. Install & Launch

**Goal**: APK installs and app opens without crash.

```bash
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell input keyevent KEYCODE_WAKEUP
$ADB shell wm dismiss-keyguard 2>/dev/null || true
sleep 1
$ADB shell am start -n "$ACTIVITY"
sleep 3
$ADB logcat -d | grep -E "FATAL|AndroidRuntime|YogaFlow" | tail -10
```

**Pass**: No `FATAL EXCEPTION`, no `Force close`, app shows home screen.

---

## 3. Godot Bridge Initialization

**Goal**: Godot GDScript VM starts and WebSocket server listens on port 9090.

```bash
$ADB shell am force-stop "$PKG"
sleep 1
$ADB shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 10
$ADB logcat -d | grep -E "YogaFlow|Godot|AvatarCoach|AvatarController|FATAL" | tail -30
$ADB shell cat /proc/net/tcp6 | awk '{print $2}' | grep -i "235A"  # port 9090 = 0x238A
```

**Pass criteria**:
- Logcat shows `Godot main loop started` or `AvatarCoachOverlay listening on port 9090`
- `/proc/net/tcp6` has `0000000000000000:238A` in LISTEN state
- No `Failed to connect to /127.0.0.1:9090`
- `AvatarController.gdc` is present in `app/src/main/assets/scripts/` — absence causes silent Godot crash

**Known regression signal**: If Godot does NOT initialize after a fresh `adb install -r`, check:
```bash
ls -la app/src/main/assets/scripts/AvatarController.gdc
xxd app/src/main/assets/scripts/AvatarController.gdc | head -2   # should start with GDSC
```

---

## 4. Avatar Self-Test (Visual Smoke Test)

**Goal**: Avatar is visible and renders pose animations without a live camera.

```bash
$ADB shell am force-stop "$PKG"
sleep 1
$ADB shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 8
$ADB shell screencap -p /sdcard/avatar-selftest.png
$ADB pull /sdcard/avatar-selftest.png test-artifacts/avatar-selftest.png
$ADB shell rm /sdcard/avatar-selftest.png
ls -lh test-artifacts/avatar-selftest.png
```

**Pass**: Screenshot file size ≥ 500 KB (indicates avatar rendered, not just home screen).

---

## 5. Avatar Position Override (ADB)

**Goal**: ADB intents move the avatar to specific X/Y positions and the change is visible.

```bash
# Start with self-test so avatar is visible
$ADB shell am force-stop "$PKG" && sleep 1
$ADB shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 8

# Run full sweep test
bash scripts/test-avatar-movement.sh
```

**Or step by step**:
```bash
# LEFT
$ADB shell am start -n "$ACTIVITY" --ef avatarTargetX -1.5 --ef avatarTargetY 0.0
sleep 2
$ADB shell screencap -p /sdcard/pos-left.png && $ADB pull /sdcard/pos-left.png test-artifacts/pos-left.png

# RIGHT
$ADB shell am start -n "$ACTIVITY" --ef avatarTargetX 1.5 --ef avatarTargetY 0.0
sleep 2
$ADB shell screencap -p /sdcard/pos-right.png && $ADB pull /sdcard/pos-right.png test-artifacts/pos-right.png

# CLEAR override
$ADB shell am start -n "$ACTIVITY" --ez avatarClearOverride true

# Compare hashes — must differ
shasum test-artifacts/pos-left.png test-artifacts/pos-right.png
```

**Pass**:
- Both screenshots ≥ 500 KB
- SHA-256 hashes are different (positions differ)
- Avatar visually appears on left side in `pos-left.png`, right side in `pos-right.png`
- After `avatarClearOverride`, avatar returns to automatic positioning

---

## 6. Avatar Auto-Positioning (Camera-Driven)

**Goal**: When a person is detected on the right side of frame, avatar moves to the left (and vice versa).

```bash
$ADB shell am force-stop "$PKG" && sleep 1
$ADB shell am start -n "$ACTIVITY"
sleep 5
# Stand in front of camera
# — Stand on RIGHT half of frame → avatar should appear on LEFT
# — Move to LEFT half → avatar should move to RIGHT
$ADB shell screencap -p /sdcard/avatar-auto-right.png && $ADB pull /sdcard/avatar-auto-right.png test-artifacts/
$ADB logcat -d | grep "YogaFlow.*avatar\|screenSide\|autoPosition" | tail -10
```

**Pass**: Avatar appears on the opposite side from the detected person.

---

## 7. Camera Framing Overlay

**Goal**: Colored rectangle overlay indicates framing quality (green = good, yellow/red = adjust).

```bash
$ADB shell am start -n "$ACTIVITY"
sleep 3
# Stand fully in frame → overlay should be green
# Move too close to camera → overlay should turn red/yellow
# Move offscreen (no person) → overlay should indicate unknown/bad
$ADB shell screencap -p /sdcard/framing.png && $ADB pull /sdcard/framing.png test-artifacts/
```

**Pass**:
- `PoseOverlayView.drawFramingBox()` draws a visible colored rect
- Color changes with framing status (`GOOD`=green, other=yellow/red)
- `CameraSetupController.analyze()` produces framing messages shown in UI

---

## 8. Pose Detection (MediaPipe)

**Goal**: MediaPipe returns 3D world landmarks and angles are computed correctly.

```bash
$ADB shell am start -n "$ACTIVITY"
sleep 5
$ADB logcat -d | grep -E "YogaFlow.*angle\|YogaFlow.*knee\|YogaFlow.*hip\|PoseGeometry\|worldLandmarks" | tail -20
```

**Manual check**: Stand in Mountain pose (arms at sides, standing straight).
- Hip angle ~170–180°
- Knee angle ~170–180°
- No `INVALID` confidence reported

**Tuning knobs** (via `MainActivityTuning.kt` sliders or dev menu):
- EMA alpha (smoothing)
- Deadband degrees
- Stability threshold ms

---

## 9. Flow Engine — Step Transitions

**Goal**: The pose state machine transitions through SETUP → MOVEMENT → HOLD → TRANSITION steps correctly.

**Setup**: Use flow `01_mountain_warmup.flow.json` (shortest, 3 SETUP steps + HOLD).

```bash
$ADB shell am start -n "$ACTIVITY"
# Select Mountain Warmup from home screen
# Observe logcat
$ADB logcat -d | grep -E "YogaFlow.*step\|YogaFlow.*state\|PoseState\|FlowEngine\|transition" | tail -30
```

**Pass**:
- Each step's `durationMs` elapses before advancing (or pose condition met)
- SETUP steps require `ready` angle conditions
- HOLD steps stay until duration elapses
- TRANSITION steps complete before next step
- No step skipped; no step repeated infinitely without correction

---

## 10. Coach Audio (TTS + Pacing)

**Goal**: Voice cues fire at correct intervals; same cue is not repeated within `sameCueIntervalMs`.

```bash
$ADB shell am start -n "$ACTIVITY"
# Start a yoga session, listen for voice output
$ADB logcat -d | grep -E "YogaFlow.*speak\|CoachSpeaker\|CoachCue\|TTS\|speak" | tail -20
```

**Pass**:
- Voice cue plays on each new step
- Correction cue repeats at most every `sameCueIntervalMs` (default: configurable)
- Different cues play within `minCueIntervalMs` (no rapid-fire)
- LLM-polished phrases differ from raw DSL cues when Gemma is loaded
- Fallback: raw DSL cue used when LLM unavailable

---

## 11. Session Lifecycle

**Goal**: Session transitions correctly through `IDLE → RUNNING → PAUSED → COMPLETED`.

```bash
$ADB logcat -d | grep -E "YogaFlow.*session\|SessionState\|IDLE\|RUNNING\|PAUSED\|COMPLETED" | tail -20
```

**Manual checks**:
- **Start**: Tap course → tap Start → session enters RUNNING, camera opens, TTS begins
- **Pause**: Tap Pause → session enters PAUSED, camera freezes, TTS stops
- **Resume**: Tap Resume → RUNNING resumes from paused step
- **Complete**: All steps finish → COMPLETED, end cue plays, home button appears
- **Restart**: Tap Restart → returns to first step, same flow

---

## 12. Home UI — Course Selection

**Goal**: All 10 flows are discoverable, display correct names, and launch correctly.

```bash
ls app/src/main/assets/flows/*.flow.json | wc -l   # should be 10
```

**Manual checks**:
- Home screen shows course cards for all discovered flows
- Each card shows: flow name (Chinese + English), pose type, level
- Tapping a course card enters the course session
- Back navigation returns to home without crash

---

## 13. Multi-Flow Playlist

**Goal**: `MainActivityPlaylist` sequences multiple flows end-to-end.

**Manual check**:
- Select a multi-course "class" (if exposed in UI)
- Flow 1 completes → Flow 2 auto-starts without returning to home
- All playlist flows complete → COMPLETED state

---

## 14. ADB Developer Controls (Full Reference)

| Intent Extra | Type | Effect |
|---|---|---|
| `devDisableCameraSetup true` | bool | Skip camera permission setup screen |
| `devEnableCameraSetup true` | bool | Re-enable camera setup screen |
| `avatarSelfTest true` | bool | Run avatar pose animation loop without camera |
| `avatarTargetX <float>` | float | Override avatar X position (e.g. `-1.5` = left, `1.5` = right) |
| `avatarTargetY <float>` | float | Override avatar Y position |
| `avatarClearOverride true` | bool | Clear position override, resume auto-positioning |

**Example launch with multiple flags**:
```bash
$ADB shell am start -n "$ACTIVITY" \
  --ez devDisableCameraSetup true \
  --ez avatarSelfTest true \
  --ef avatarTargetX 0.0 \
  --ef avatarTargetY 0.0
```

---

## 15. Regression Checklist (After Any APK Change)

Run before every commit that touches Godot assets or MainActivity:

```bash
# 1. Build
JAVA_HOME="$JAVA_HOME" ./gradlew assembleDebug | tail -3

# 2. Install
$ADB install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Godot init check
$ADB shell am force-stop "$PKG" && sleep 1
$ADB shell am start -n "$ACTIVITY" --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 10
$ADB logcat -d | grep -E "YogaFlow|Godot|FATAL" | tail -20
# Must see port 9090 or bridge connect

# 4. Avatar position check
bash scripts/test-avatar-movement.sh

# 5. Quick logcat check for crashes
$ADB logcat -d | grep -E "FATAL|AndroidRuntime" | tail -5
```

**All steps must pass before pushing to main.**

---

## 16. Key Files for Debugging

| Issue | Where to look |
|---|---|
| Godot not starting | `app/src/main/assets/scripts/AvatarController.gdc` (must exist) |
| Bridge not connecting | `app/src/main/assets/scripts/AvatarCoachOverlay.gd` `_ready()` — `tcp_server.listen(9090)` |
| Avatar not moving | `app/src/main/assets/scripts/AvatarController.gd` `set_override_position()` |
| Pose angles wrong | `app/src/main/java/com/yogaflow/pose/PoseGeometry.kt` |
| Step not advancing | `app/src/main/java/com/yogaflow/flow/PoseFlowEngine.kt` |
| Coach cue not playing | `app/src/main/java/com/yogaflow/coach/CoachCueController.kt` `shouldEmit()` |
| Camera framing box | `app/src/main/java/com/yogaflow/pose/PoseOverlayView.kt` `drawFramingBox()` |
| TTS not speaking | `app/src/main/java/com/yogaflow/coach/CoachSpeaker.kt` |
