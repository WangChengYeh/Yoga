# YogaFlow 3D Project Status

Last updated: 2026-05-12

## Overall View

YogaFlow 3D is an Android on-device AI yoga coaching app built around this runtime path:

```text
CameraX -> MediaPipe Pose -> Camera setup gate -> Detection mapping -> Flow runtime -> Coaching feedback
                                                        |                         |
                                                        v                         v
                                                Pose overlay / framing      TTS / LLM fallback / Godot avatar
```

Core architecture rules:

- Flow JSON is the lesson source of truth.
- Detection logic is explicit, typed, and debuggable through `DetectKey` and runtime parameters.
- LLM output may polish cue phrasing, but must not plan, remove, or reorder lesson steps.
- Camera and pose processing stay on device.
- Runtime tuning overlays user overrides on top of packaged Flow JSON and must not mutate packaged assets.

Important current implementation points:

- Android app logic is Kotlin under `app/src/main/java/com/yogaflow/`.
- Godot avatar runtime assets are bundled under `app/src/main/assets/`.
- Godot script source exists in both `godot/scripts/*.gd` and `app/src/main/assets/scripts/*.gd`; these copies must stay identical.
- Packaged flow library currently contains 20 `.flow.json` files in `app/src/main/assets/flows/`.
- App starts on the home/course screen with camera off; camera setup is idle until the user enables the camera toggle.
- Build requires JDK 17 via `JAVA_HOME=/opt/homebrew/opt/openjdk@17`.

## Test Coverage

Current JVM test areas:

- Avatar contract tests:
  - avatar PiP layout contract
  - rig frame behavior
  - Mixamo bone names
  - Godot scene/script asset wiring
  - MediaPipe-to-avatar mapper bounds and NaN safety
- Flow tests:
  - Flow parser validity for packaged assets
  - Flow library integration checks for DSL v2, catalog-routable poses, detect keys, unique IDs, and base pose coverage
  - Playlist sequencing over packaged flows
- Runtime / coach tests:
  - Pose detection router does not throw for packaged flow steps
  - PoseFlowEngine completes packaged flows when matching states are supplied
  - LiveCoachSessionController emits correction/debug state for invalid frames
  - Coach cue pacing and severity override behavior
  - Coach phrase polishing
- Pose / LLM tests:
  - PoseGeometry 3D/2D angle calculation and invalid landmark handling
  - PromptBuilder persona and prompt shape

Primary verification commands:

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew assembleDebug
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew test
JAVA_HOME=/opt/homebrew/opt/openjdk@17 ./gradlew connectedDebugAndroidTest
```

Required device smoke check:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.yogaflow/.MainActivity
TS=$(date +%Y%m%d-%H%M%S)
mkdir -p session-recordings
adb shell mkdir -p /sdcard/session-recordings
adb shell screencap -p /sdcard/session-recordings/screencap-${TS}.png
adb pull /sdcard/session-recordings/screencap-${TS}.png session-recordings/screencap-${TS}.png
adb logcat -d | grep -E "Yoga|Godot|MediaPipe|AndroidRuntime" | head -40
```

Last known verification from this workspace:

- `assembleDebug`: passed
- `test`: passed
- `connectedDebugAndroidTest`: passed
- APK install: passed
- App launch: passed
- Screenshot smoke check: home screen rendered with Beginner Flow and START button
- Clean logcat sample: no current `AndroidRuntime` crash observed

## Finished

Product / architecture:

- Android Kotlin app shell with modular MainActivity responsibilities.
- CameraX + MediaPipe pose pipeline.
- Camera setup/onboarding gate with manual camera toggle before session start.
- Pose overlay with skeleton, framing box, and FILL_CENTER coordinate mapping.
- Typed Flow DSL v2 parser, JSON validator, runtime params, and validator.
- Flow playlist engine and packaged flow discovery.
- Pose detection router and pose-specific detection mappers for core poses.
- Runtime override/tuning layer that does not mutate packaged flows.
- Coach cue controller with pacing and same-cue suppression.
- LLM prompt builder and rule-based fallback path.
- SQLite logging for LLM interactions and session history.
- Godot avatar embedded as a corner PiP overlay with Android-to-Godot WebSocket bridge.
- Avatar self-test and ADB developer controls for camera bypass and avatar position overrides.
- Home/course UI with filters, session controls, progress, countdown, and course cards.
- Session completion/history/statistics features documented in roadmap.

Content:

- 20 packaged Flow DSL files currently present.
- Base pose coverage includes mountain, forward_fold, twist, squat, and bridge.
- Expanded poses include warrior_1, warrior_2, downward_dog, child_pose, and pigeon.
- Packaged cues are zh-TW.

Testing:

- JVM unit coverage exists for geometry, flow parsing, routing, avatar contracts, cue pacing, prompt building, and integration-level flow/runtime contracts.
- Device build/install/launch/screenshot flow has been verified on a connected device in this workspace.

## Unfinished / Open Work

Product roadmap:

- Expand flow library further toward 30+ flows and eventually a larger production catalog.
- Add ROM baseline tracking for each user's joint range of motion over time.
- Add bilateral balance analysis comparing left/right movement and stability.
- Continue Apple Fitness+ style home screen polish, carousel cards, and stronger course art presentation.
- Expand personalized coaching beyond current fallback/LLM phrase polishing.

Testing gaps:

- Add Android UI tests for home navigation, course selection, camera toggle, Start/Pause/Restart, and completion screen.
- Add instrumentation tests for CameraSetupController behavior with synthetic or fixture-based pose frames.
- Add screenshot or golden-style tests for layout regressions on common phone/tablet sizes.
- Add Godot avatar bridge smoke tests that assert WebSocket startup and avatar command delivery from Android.
- Add regression tests for session history persistence and statistics dashboard calculations.
- Add tests for flow category/filter behavior and all current 20 packaged flow cards.
- Add CI configuration that runs `assembleDebug` and JVM tests with JDK 17.

Documentation gaps:

- `docs/test-plan.md` has older references to 10 flows; current asset count is 20.
- Some project notes still refer to 15 flows; update those references or clarify historical milestones versus current state.
- Add a dedicated device-test checklist for camera-toggle-first startup sequences.

Operational gaps:

- Gemma model installation remains a manual device setup step; fallback coaching remains the reliable default.
- Full camera/pose verification still depends on physical device conditions and manual body positioning.
- Repository contains local untracked artifacts in this workspace; these should stay out of commits unless intentionally promoted.
