# YogaFlow 3D Test Plan

This file owns verification commands, device procedures, and test gaps. Product status lives in `docs/project-status.md`; finished/unfinished product work lives in `docs/roadmap.md`.

All local Gradle commands must use JDK 17:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17
PKG=com.yogaflow
ACTIVITY=$PKG/.MainActivity
```

The default `java_home -v 17` path is not reliable on this machine.

## 1. Build And JVM Tests

Goal: APK compiles and JVM tests pass.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test
```

Pass criteria:

- `BUILD SUCCESSFUL` for both commands.
- No unresolved symbols.
- JVM tests include parser, geometry, routing, avatar contracts, cue pacing, and integration-level flow/runtime coverage.

## 2. Instrumentation Tests

Goal: connected-device instrumentation task runs cleanly.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedDebugAndroidTest
```

Pass criteria:

- `BUILD SUCCESSFUL`.
- Device remains connected throughout the run.

## 3. Install, Launch, Screenshot, Logcat

Goal: APK installs, app opens, screenshot renders, and logcat has no current crash.

Screenshot naming rule: always use `screencap-YYYYMMDD-HHmmss.png`. Do not use generic names like `yoga_screen.png`.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.yogaflow/.MainActivity
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p session-recordings
adb shell mkdir -p /sdcard/session-recordings
adb shell screencap -p /sdcard/session-recordings/screencap-${TS}.png
adb pull /sdcard/session-recordings/screencap-${TS}.png session-recordings/screencap-${TS}.png
adb logcat -d | grep -E "Yoga|Godot|MediaPipe|AndroidRuntime" | head -40
```

Pass criteria:

- Install reports `Success`.
- Launch intent starts or delivers to `com.yogaflow/.MainActivity`.
- Screenshot file exists under `session-recordings/` with timestamped name.
- Screenshot visibly shows the app UI.
- Current logcat sample has no `AndroidRuntime` crash.

## 4. Camera Toggle Startup Flow

Goal: session start respects the manual camera gate.

Current product rule: camera setup and framing checks are idle until Camera is ON.

Manual sequence:

```bash
adb shell am start -n com.yogaflow/.MainActivity
# Check screenshot for actual coordinates first.
adb shell input tap <camera_button_x> <camera_button_y>
# Wait for framing check and Start enablement.
adb shell input tap <start_button_x> <start_button_y>
```

Pass criteria:

- Camera starts only after tapping Camera ON.
- Setup panel appears only after the camera toggle is enabled.
- Start is disabled until the ready gate is satisfied, except when explicit development bypass flags are used.
- Session does not auto-start without the user tapping Start.

## 5. Flow Library Checks

Goal: all packaged flows are discoverable, parseable, and launchable.

```bash
find app/src/main/assets/flows -maxdepth 1 -name '*.flow.json' | sort
find app/src/main/assets/flows -maxdepth 1 -name '*.flow.json' | wc -l
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests 'com.yogaflow.integration.FlowLibraryIntegrationTest'
```

Current expected count: 20 packaged flow files.

Pass criteria:

- Flow count is 20 unless intentionally changed.
- Flow DSL version is `dsl-v2`.
- Flow IDs are unique.
- Flow poses exist in `YogaPoseCatalog`.
- Detect keys are registered in `DetectKey`.
- Base pose coverage includes mountain, forward_fold, twist, squat, and bridge.

## 6. Flow Runtime Checks

Goal: parsed flows and runtime engine integrate correctly.

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests 'com.yogaflow.integration.FlowRuntimeIntegrationTest'
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test --tests 'com.yogaflow.coach.PoseDetectionRouterTest'
```

Pass criteria:

- `PoseFlowEngine` can complete every packaged flow when matching step states are supplied.
- `LiveCoachSessionController` emits correction/debug state for invalid frames.
- Router does not throw for packaged flow steps.

## 7. Godot Bridge Initialization

Goal: Godot starts and Android connects to the avatar bridge.

```bash
adb shell am force-stop com.yogaflow
adb logcat -c
adb shell am start -n com.yogaflow/.MainActivity --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 10
adb logcat -d | grep -E "YogaFlow|Godot|AvatarCoach|AvatarController|GodotAvatarBridge|FATAL|AndroidRuntime" | tail -40
```

Pass criteria:

- Logs show Godot setup completed or main loop started.
- Logs show bridge connected or avatar self-test commands sent.
- No `FATAL EXCEPTION` or current `AndroidRuntime` crash.

Known regression signals:

- Godot starts but bridge never connects.
- `.gd` source differs between `godot/scripts/` and `app/src/main/assets/scripts/`.
- `.gd.remap` points at stale bytecode when the runtime expects source `.gd`.

## 8. Avatar Self-Test

Goal: avatar renders and cycles target poses without requiring camera input.

```bash
adb shell am force-stop com.yogaflow
adb logcat -c
adb shell am start -n com.yogaflow/.MainActivity --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 8
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p session-recordings
adb shell mkdir -p /sdcard/session-recordings
adb shell screencap -p /sdcard/session-recordings/screencap-${TS}.png
adb pull /sdcard/session-recordings/screencap-${TS}.png session-recordings/screencap-${TS}.png
adb logcat -d | grep -E "YogaFlow|Godot|AvatarSelfTest|GodotAvatarBridge|AndroidRuntime" | tail -40
```

Pass criteria:

- Screenshot shows app and avatar area.
- Logcat shows avatar self-test commands.
- No current crash.

## 9. Avatar Position Override

Goal: ADB intent extras move the avatar target position.

```bash
adb shell am force-stop com.yogaflow
adb shell am start -n com.yogaflow/.MainActivity --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 8
bash scripts/test-avatar-movement.sh
```

Pass criteria:

- Left and right screenshots differ.
- Avatar visibly changes side.
- `avatarClearOverride` returns to automatic positioning.

## 10. Camera Framing Overlay

Goal: overlay communicates framing quality.

Manual checks:

- Stand fully in frame: framing box should be good/green.
- Move too close: message and color should indicate adjustment.
- Move partly offscreen: offset/crop status should appear.
- Leave frame: unknown/low-visibility state should appear.

Relevant files:

- `app/src/main/java/com/yogaflow/pose/CameraFramingCoach.kt`
- `app/src/main/java/com/yogaflow/pose/PoseOverlayView.kt`
- `app/src/main/java/com/yogaflow/session/CameraSetupController.kt`

## 11. Pose Detection And Angles

Goal: MediaPipe returns usable landmarks and geometry remains plausible.

```bash
adb shell am start -n com.yogaflow/.MainActivity
adb logcat -d | grep -E "YogaFlow.*angle|YogaFlow.*knee|YogaFlow.*hip|PoseGeometry|worldLandmarks" | tail -20
```

Manual mountain check:

- Hip and knee angles should be near straight standing values.
- No repeated `INVALID` confidence when the full body is visible.

## 12. Session Lifecycle

Goal: session transitions correctly through `IDLE -> RUNNING -> PAUSED -> COMPLETED`.

Manual checks:

- Start: enable Camera, pass framing, tap Start, session enters RUNNING.
- Pause: tap Pause, session enters PAUSED and camera/setup handling pauses appropriately.
- Resume: tap Resume, RUNNING resumes from paused state.
- Complete: all steps finish, end cue plays, completion screen appears.
- Restart: returns to the first flow/step.

## 13. Home UI And Course Selection

Goal: course cards match packaged flows/categories and launch without crash.

Manual checks:

- Home screen shows expected categories and course cards.
- Course cards reflect packaged flow names/categories.
- Tapping a course card enters the class screen.
- Back navigation returns home without crash.
- Category filters expose the intended flow groups.

## 14. ADB Developer Controls

| Intent Extra | Type | Effect |
|---|---|---|
| `devDisableCameraSetup true` | bool | Skip camera setup readiness gate for development runs. |
| `devEnableCameraSetup true` | bool | Re-enable camera setup gate. |
| `avatarSelfTest true` | bool | Run avatar pose animation loop without camera. |
| `avatarTargetX <float>` | float | Override avatar X position, for example `-1.5` or `1.5`. |
| `avatarTargetY <float>` | float | Override avatar Y position. |
| `avatarClearOverride true` | bool | Clear position override and resume automatic positioning. |

Example:

```bash
adb shell am start -n com.yogaflow/.MainActivity \
  --ez devDisableCameraSetup true \
  --ez avatarSelfTest true \
  --ef avatarTargetX 0.0 \
  --ef avatarTargetY 0.0
```

## 15. Regression Checklist

Run before pushing changes that touch runtime, Godot assets, MainActivity, flow parsing, or detection logic:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug test
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop com.yogaflow
adb logcat -c
adb shell am start -n com.yogaflow/.MainActivity --ez devDisableCameraSetup true --ez avatarSelfTest true
sleep 8
adb logcat -d | grep -E "YogaFlow|Godot|FATAL|AndroidRuntime" | tail -40
```

For full acceptance, also capture and inspect a timestamped screenshot using the command in section 3.

## 16. Current Test Gaps

- Android UI tests for home navigation, camera toggle, Start/Pause/Restart, and completion.
- Fixture-based instrumentation tests for CameraSetupController readiness behavior.
- Screenshot/golden regression tests for common device sizes.
- Godot bridge smoke tests that assert startup and command delivery.
- Session history and statistics dashboard persistence tests.
- CI workflow running JDK 17 `assembleDebug` and JVM tests.

## 17. Key Files For Debugging

| Issue | Where to look |
|---|---|
| Build JDK mismatch | `docs/project-environment-setup.md`, Gradle command `JAVA_HOME` |
| Flow parse/validation failure | `app/src/main/java/com/yogaflow/flow/` |
| Flow count/category issue | `app/src/main/assets/flows/`, `MainActivityPlaylist.kt` |
| Step not advancing | `PoseFlowEngine.kt`, `PoseDetectionRouter.kt`, detection mapper for the pose |
| Camera setup blocked | `CameraSetupController.kt`, camera toggle state in `MainActivity` |
| Pose angles wrong | `PoseGeometry.kt`, `PoseOverlayView.kt` |
| Coach cue pacing | `CoachCueController.kt`, `CoachSpeaker.kt` |
| Godot not starting | `app/src/main/assets/project.pck`, `.gd.remap`, Godot logcat lines |
| Avatar not moving | `AvatarController.gd`, `GodotAvatarBridge.kt`, `PoseCoachFrame` payload |
| Script sync regression | `GodotScriptAssetSyncTest.kt` |
