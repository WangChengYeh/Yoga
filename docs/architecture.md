# YogaFlow 3D Architecture (v9)

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
Camera Setup Panel (shown only when Camera toggle = ON)
  ↓
Ready Stability Window (~1500ms)
  ↓
Manual Start Gate (user taps Start — no auto-start since #70)
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
LLM Coach / Fallback        PoseCoachFrame JSON (screen_side + action + highlight)
  ↓          ↓                          ↓
TTS Voice  LlmInteractionDb      Godot Avatar (WebSocket → AvatarController.gd)
           (SQLite: prompt/response/timing, #69)
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
Manual Start (user taps Start button)
  ↓
FlowEngine
```

The onboarding layer prevents users from entering a class until the system has reliable camera framing and view orientation.

Current onboarding behavior (updated #70):

```text
App launches → Camera toggle = OFF (camera setup panel hidden)
→ User taps "Camera: OFF" button → Camera toggle = ON
→ show setup guidance
→ draw body framing box + fixed guide frame
→ wait for body framing + orientation Ready
→ Start button becomes enabled
→ User taps Start → class begins (no auto-start)
```

Integration status:

```text
CameraSetupController is wired in MainActivity (imported and initialized).
```

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
failReason: knee=48.2 < min=120.0
failExplanation: Observed knee angle (膝蓋角度) was 48.2 degrees; this step requires at least 120.0 degrees.
```

Interpretation:

```text
knee=48.2       observed knee angle (膝蓋角度) from hip-knee-ankle landmarks
<               observed value is below the required threshold
min=120.0       the active flow step requires at least 120 degrees
matched=false   this frame does not count toward step completion
```

Human-facing UI should prefer `failExplanation`. `failReason` exists so tuning tools can parse repeated failures reliably.

Metric glossary:

| Metric | Chinese (Taiwan) | Unit | Meaning |
| --- | --- | --- | --- |
| `knee` | 膝蓋角度 | degrees | Knee joint angle from hip-knee-ankle landmarks. |
| `hip` | 髖部/身體角度 | degrees | Hip/body angle from shoulder-hip-knee landmarks. |
| `twist` | 軀幹扭轉角度 | degrees | Approximate torso rotation. |
| `stableFor` | 穩定維持時間 | milliseconds | Time the required pose has been continuously detected. |

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
app/src/main/java/com/yogaflow/SessionState.kt
```

Reason:

- avoid defining session lifecycle inside `MainActivity.kt`
- improve reuse across controllers
- reduce package coupling

Status:

```text
SessionState is in `app/src/main/java/com/yogaflow/SessionState.kt` (extracted from MainActivity).
```

---

## Implementation Status Pointer

This architecture document describes system design and component responsibilities.

Current shipped/unfinished product status is intentionally tracked elsewhere:

- GitHub issue #118 for the current one-page snapshot, doc index, and verification baseline (replaces former `docs/project-status.md`).
- `docs/roadmap.md` for finished, active, unfinished, and future work.
- `docs/test-plan.md` for verification commands and test gaps.

Current architecture-level facts:

```text
Flow JSON remains the lesson source of truth.
Detection is routed through typed DetectKey values.
Mapper state is owned per DetectionMapperSession.
Runtime tuning is applied as an override layer, not by mutating packaged flows.
Godot avatar rendering is an instructional overlay and does not drive lesson progression.
```

---

## Godot Avatar Coach Overlay

Goal:

```text
Show a 3D coach avatar floating over the live camera so users can compare
their body with the target pose in real time.
```

The avatar demonstrates the target step; it does not mirror the user's skeleton. It is an instructional overlay separate from pose detection. Appearance changes must not affect pose detection or lesson progression.

### Implementation (shipped)

Architecture: Canvas-first approach was evaluated but skipped. The team chose Godot 4.x directly because a GLB model asset (`female_yoga_coach.glb`) was available and Godot's GDScript allows rapid pose animation without a custom renderer.

```text
Android (Kotlin)
  ↓  PoseCoachFrame JSON via local WebSocket (127.0.0.1:9090)
Godot 4 (GodotFragment embedded in FragmentContainerView)
  ↓
AvatarCoachOverlay.gd  ←  WebSocket server
  ↓
AvatarController.gd
  ├─ play_action(action)        — pose animation / fallback tween
  ├─ apply_screen_side(side)    — move avatar to opposite side of human
  ├─ apply_highlight(bone, sev) — visual correction feedback
  └─ apply_skin(name)           — Classic / Nature / Ocean lighting
```

PoseCoachFrame avatar object (current schema):

```json
{
  "action": "hold_forward_fold",
  "emotion": "calm",
  "highlight": null,
  "screen_side": "left"
}
```

`screen_side` is computed per-frame by `humanScreenSide()` in Kotlin: returns the OPPOSITE side of where the human body center is detected, so the avatar always steps aside. See `docs/avatar.md` for full details.

GDScript deployment note: Godot compiles `.gd` source to `.gdc` bytecode during export. The bytecode in `app/src/main/assets/scripts/*.gdc` must be kept in sync with the source in `godot/scripts/`. Currently the `.gd.remap` files point directly to the source `.gd` files to avoid stale-bytecode bugs; this means any Godot export must also update the remap files.

Design constraints (unchanged):

```text
Changing coach skin changes only rendering (light colors).
Changing skin must not change:
- Flow DSL
- DetectKey routing
- RuntimeParams
- pose thresholds
- cue timing
- session recording semantics
```

---

## Architecture Work Items

Architecture work items that become product roadmap items should be tracked in `docs/roadmap.md`.

Current architecture maintenance priorities:

- Keep `MainActivity` as an Android shell for lifecycle, permissions, view binding, camera lifecycle, and callback wiring.
- Keep camera setup, live session handling, detection mapping, flow runtime, and avatar bridge responsibilities separated.
- Keep Godot script copies synchronized between `godot/scripts/*.gd` and `app/src/main/assets/scripts/*.gd`.
- Keep Flow DSL validation strict enough that unsupported detect keys and missing runtime params fail in tests.
- Prefer deterministic JVM tests for parser/router/runtime contracts and targeted device tests for camera/Godot behavior.

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
