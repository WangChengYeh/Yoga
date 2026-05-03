# YogaFlow 3D Architecture (v8)

YogaFlow 3D 是一個 production-oriented on-device AI 瑜伽教練系統。

核心概念：

```text
Camera → Perception → Onboarding Gate → Detection Mapping → Flow → Coaching
                         ↓
                  Visual Feedback
                         ↓
                    Observability
                         ↓
                      Tuning
```

Architecture design principles:

```text
Flow JSON is the lesson source of truth.
Detection must be explicit and debuggable.
LLM may adapt phrasing, but must not plan or reorder lessons.
Camera and pose data should stay on device.
Runtime tuning must not mutate packaged Flow JSON.
```

---

## Full Runtime Pipeline

```text
CameraX
  ↓
CameraPosePipeline
  ↓
MediaPipe Pose
  ↓
PoseDetectionResult
  ↓
CameraFramingCoach + ViewOrientation
  ↓
Camera Setup Panel
  ↓
Ready Stability Window (~1500ms)
  ↓
Auto-start Gate
  ↓
Flow step.detect
  ↓
Detection Mapper
  ↓
Smoothing (EMA + Deadband)
  ↓
Stability Window (~300ms)
  ↓
PoseFlowEngine (Event)
  ↓
LLM Coach / Fallback
  ↓
TTS Voice
```

Target controller pipeline:

```text
MainActivity.handlePoseFrame
  ↓
CameraSetupController.handleFrame
  ↓
LiveCoachSessionController.handleReadyPoseFrame
  ↓
CoachCueController
```

`MainActivity` should remain the Android shell: lifecycle, permissions, view binding, camera lifecycle, and callback wiring.

---

## Camera Onboarding Pipeline

Before class flow starts, YogaFlow runs a perception-driven onboarding gate:

```text
PoseDetectionResult
  ↓
CameraFramingCoach
  ↓
ViewOrientation
  ↓
Ready Gate
  ↓
Ready Stability Window
  ↓
Auto-start
  ↓
FlowEngine
```

The onboarding layer prevents users from entering a class until the system has reliable camera framing and view orientation.

Current intended onboarding behavior:

```text
Not Ready
→ show setup guidance
→ draw body framing box + fixed guide frame
→ wait for body framing + orientation Ready
→ hold Ready for ~1500ms
→ auto-start class
```

Known integration gap:

```text
CameraSetupController exists, but latest main still needs final MainActivity wiring cleanup.
MainActivity still contains local handleCameraSetupFrame logic.
```

This gap should be fixed before further feature work.

---

## Visual Feedback Pipeline

The runtime visualizes perception state directly in the camera view:

```text
PoseDetectionResult.imageLandmarks
  ↓
PoseOverlayView
  ↓
Skeleton overlay
  ↓
Dynamic body framing box
  ↓
Fixed guide frame
```

Purpose:

- make camera setup understandable
- show the user how the system sees their body
- reduce trial-and-error before class start
- make perception behavior explainable

---

## Flow DSL Runtime

YogaFlow lessons are defined by JSON Flow DSL v2.

```text
.flow.json
  ↓
FlowJsonValidator
  ↓
FlowParser
  ↓
DetectKey
  ↓
RuntimeParams
  ↓
FlowValidator
  ↓
PoseFlowEngine
```

Flow JSON defines:

- step state
- duration
- cue text
- detect key
- correction text
- runtime thresholds
- smoothing parameters
- stability timing

Example step:

```json
{
  "state": "HOLD",
  "durationMs": 8000,
  "cue": "停在這裡，保持呼吸。",
  "detect": "forward_hold",
  "runtime": {
    "stabilityMs": 650,
    "emaAlpha": 0.25,
    "angles": {
      "knee": { "hold": { "min": 145 } },
      "hip": { "hold": { "min": 50, "max": 130 } }
    }
  }
}
```

Important rule:

```text
Flow JSON remains the source of truth.
LLM cannot invent, remove, or reorder flow steps.
```

---

## Observability + Tuning Pipeline

The runtime exposes a debug overlay, threshold tuning panel, and session recorder for validation, calibration, and later review:

```text
PoseDetectionResult
  ↓
PoseGeometry
  ↓
DebugPoseInfo
  ↓                  ↓
debugText overlay    SessionRecorder
  ↓                  ↓
Threshold UI         JSONL session file
  ↓
Runtime Override UI
  ↓
RuntimeOverrideStore
  ↓
RuntimeOverrideMerger
  ↓
Detection Mapper
```

The tuning loop is closed:

```text
observe angles → adjust threshold → mapper behavior changes → observe matched result
```

Debug overlay shows:

```text
pose id
detect key
CoachState
matched
left / right knee angle
left / right hip angle
torso twist estimate
effective runtime params
active runtime overrides
numeric fail reason
auto tuning suggestion
```

Session recordings capture:

```text
frame samples:
  flow id
  step number
  detect key
  CoachState
  matched
  landmark count
  frame size
  runtime summary
  active runtime overrides
  numeric fail reason
  plain-language fail explanation
  auto tuning suggestion

cue events:
  raw flow cue
  displayed/polished cue
  flow completion cue
  source
```

Recordings are newline-delimited JSON files saved in:

```text
<app external files>/session-recordings/yogaflow-session-YYYYMMDD-HHMMSS.jsonl
```

Numeric fail reasons are stored in both compact and plain-language forms:

```text
failReason: knee=48.2 < min=155.0
failExplanation: Observed knee angle was 48.2 degrees; this step requires at least 155.0 degrees.
```

Interpretation:

```text
knee=48.2       observed knee angle from hip-knee-ankle landmarks
<               observed value is below the required threshold
min=155.0       the active flow step requires at least 155 degrees
matched=false   this frame does not count toward step completion
```

Human-facing UI should prefer `failExplanation`. `failReason` exists so tuning tools can parse repeated failures reliably.

Runtime override paths are scoped by flow, step, detect key, and parameter path:

```kotlin
data class RuntimeOverrideKey(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val path: String
)
```

Example paths:

```text
runtime.stabilityMs
runtime.emaAlpha
runtime.deadbandDegrees
runtime.angles.knee.hold.min
runtime.angles.hip.hold.max
```

Design rule:

```text
Packaged Flow JSON is never mutated.
User tuning is applied through RuntimeOverrideStore only.
```

---

## Detection Mapping Layer

Current supported mappers:

```text
ForwardFoldDetectionMapper
TwistDetectionMapper
SquatDetectionMapper
BridgeDetectionMapper
```

Each mapper:

- converts geometry → semantic state
- applies threshold rules
- applies smoothing + stability window
- reads typed RuntimeParams
- outputs matched / not matched result
- provides numeric fail reasons when possible

Mapping dispatch is handled by:

```text
PoseDetectionRouter
```

Current strict routing behavior:

```text
unsupported poseId → error
unsupported detect key in mapper → error
missing required runtime param → error
```

Current exception:

```text
mountain still uses legacy PoseStateMachine fallback
```

Planned direction:

```text
add strict MountainDetectionMapper
remove final fallback path
```

---

## Detection Mapper Session

Detection mappers contain smoothing and stability-window state. This state must be scoped to a live coaching session, not shared globally.

Current lifecycle owner:

```text
DetectionMapperSession
  ├─ ForwardFoldDetectionMapper
  ├─ TwistDetectionMapper
  ├─ SquatDetectionMapper
  └─ BridgeDetectionMapper
```

Responsibilities:

- create mapper instances
- expose a configured `PoseDetectionRouter`
- reset mapper state on playlist restart
- reset mapper state on flow transition

This prevents cross-flow contamination and prepares the architecture for future multi-session support.

---

## Runtime Stability Layer

The runtime uses stability windows at two levels:

### Camera onboarding stability

```text
Ready = framing GOOD + orientation GOOD
Ready must stay stable for ~600ms before auto-start
```

Purpose:

```text
avoid accidental class start from one good frame
```

### Pose detection stability

All pose mappers use:

- EMA smoothing
- deadband filtering
- stability window
- cue throttling

Purpose:

```text
avoid jitter → produce human-like coaching
```

---

## Event-driven Flow Engine

`PoseFlowEngine` emits:

```text
Cue
StepCompleted
FlowCompleted
```

Flow progression requires both:

```text
matched = true
state == currentStep.state
```

This ensures:

- no duplicate triggers
- no false progression when a mapper reports `matched = false`
- correct step transitions
- stable timing behavior

---

## Coaching Layer

Priority:

```text
Camera Setup
→ Framing
→ Orientation
→ Pose Mapping
→ Flow Cue
```

Design rule:

```text
System decides WHAT
LLM decides HOW
```

`CoachCueController` owns:

- cue rate limiting
- stale async request cancellation
- LLM phrase polishing
- deterministic fallback cue
- final TTS output

---

## Session State

Current session states:

```text
IDLE
RUNNING
PAUSED
COMPLETED
```

Recommended ownership:

```text
session/SessionState.kt
```

Reason:

- avoid defining session lifecycle inside `MainActivity.kt`
- improve reuse across controllers
- reduce package coupling

Known gap:

```text
SessionState is still defined in MainActivity.kt in latest main.
```

---

## Current Capability

```text
✔ Forward Fold
✔ Twist
✔ Squat
✔ Bridge
✔ Full beginner class (5 poses)
✔ Flow JSON DSL v2
✔ Strict DetectKey validation
✔ RuntimeOverrideStore
✔ RuntimeOverrideMerger
✔ AutoTuningAdvisor
✔ Numeric fail reasons
✔ PoseDetectionRouter mapper dispatch
✔ DetectionMapperSession mapper lifecycle owner
✔ Debug overlay for angle / state / matched inspection
✔ Runtime threshold tuning UI
✔ Persistent user calibration with SharedPreferences
✔ LLM + TTS coaching
```

Partially integrated / known gaps:

```text
△ CameraSetupController exists, but final MainActivity wiring cleanup is still needed
△ Mountain still uses legacy fallback
△ Teacher-video-to-flow pipeline is not fully automated
```

---

## Roadmap

### Near-term

- Restore full CameraSetupController wiring in MainActivity
- Move SessionState to a dedicated file
- Add strict MountainDetectionMapper
- Add Flow JSON CI validation
- Add threshold reset button
- Extract mapper interface (`PoseDetectionMapper`)

### Perception Quality

- Add variance-based stability scoring
- Add replay for saved session recordings
- Add auto calibration from observed range of motion
- Add scoring system for pose quality

### Product Expansion

- Add more pose families
- Improve coaching personalization
- Add calibration profiles / per-user presets
- Replace cover drawable with real generated images (#13)
- Explore AI-assisted teacher-video-to-flow authoring

---

## Recommended Next Architecture PRs

### PR 1: Restore CameraSetupController wiring

Scope:

- route setup frames through `CameraSetupController.handleFrame(frame)`
- remove local `MainActivity.handleCameraSetupFrame`
- fix pause/resume readiness gating
- decide auto-start behavior

### PR 2: Extract SessionState

Scope:

- move `SessionState` into a dedicated file
- update imports across controllers

### PR 3: Strict MountainDetectionMapper

Scope:

- implement mapper for mountain pose
- remove final legacy `PoseStateMachine` fallback path

### PR 4: Flow JSON CI

Scope:

- validate every `*.flow.json` in CI
- reject unsupported detect keys / missing runtime params

---

## Privacy Model

```text
All processing is on-device
```

- no camera upload
- no pose upload
- no cloud dependency required for core detection
- debug overlay uses local pose geometry only
- threshold calibration is stored locally
- camera onboarding uses local landmarks only

---

## Architecture Summary

```text
Flow JSON defines the lesson.
Camera detects the body.
Geometry measures the pose.
Mappers evaluate correctness.
Flow engine advances the class.
Coach controller speaks the cue.
Runtime tuning adapts thresholds.
Debug output explains every decision.
```
