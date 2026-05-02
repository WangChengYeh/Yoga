# YogaFlow 3D Architecture (v7)

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

Current onboarding behavior:

```text
Not Ready
→ show setup guidance
→ draw body framing box + fixed guide frame
→ wait for body framing + orientation Ready
→ hold Ready for ~1500ms
→ auto-start class
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

## Observability + Tuning Pipeline

The runtime exposes a debug overlay and threshold tuning panel for validation and calibration:

```text
PoseDetectionResult
  ↓
PoseGeometry
  ↓
DebugPoseInfo
  ↓
debugText overlay
  ↓
Threshold UI
  ↓
ThresholdConfig
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
```

Threshold UI currently supports:

```text
Squat knee hold max threshold
Bridge hip lift / hold max threshold
```

Threshold values are persisted with `SharedPreferences`, so user calibration survives app restarts.

Purpose:

- tune pose thresholds
- inspect jitter
- validate stability window behavior
- understand why a step is or is not matched
- preserve user-specific calibration

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
- reads runtime threshold config when supported
- outputs `(matched, state, cue)`

Mapping dispatch is handled by:

```text
PoseDetectionRouter
```

This keeps `MainActivity` focused on session orchestration and UI, while mapper selection stays in the coach layer.

---

## Runtime Stability Layer

The runtime uses stability windows at two levels:

### Camera onboarding stability

```text
Ready = framing GOOD + orientation GOOD
Ready must stay stable for ~1500ms before auto-start
```

Purpose:

```text
avoid accidental class start from one good frame
```

### Pose detection stability

All pose mappers use:

- EMA smoothing
- deadband filtering
- stability window (~300ms)
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

---

## Current Capability

```text
✔ Forward Fold
✔ Twist
✔ Squat
✔ Bridge
✔ Full beginner class (5 poses)
✔ Camera setup panel before class start
✔ Ready gating from framing + orientation
✔ Stable auto-start after sustained Ready state
✔ Visual body framing box
✔ Fixed framing guide frame
✔ Event-driven runtime
✔ Stability-aware detection
✔ PoseDetectionRouter mapper dispatch
✔ Debug overlay for angle / state / matched inspection
✔ Runtime threshold tuning UI
✔ Persistent user calibration with SharedPreferences
✔ LLM + TTS coaching
```

---

## Roadmap

### Near-term

- Add color-coded ready / not-ready visual states
- Add direction-aware framing hints
- Add threshold reset button
- Extract mapper interface (`PoseDetectionMapper`)

### Perception Quality

- Add variance-based stability scoring
- Add record / replay debugging
- Add auto calibration from observed range of motion
- Add scoring system for pose quality

### Product Expansion

- Add more pose families
- Improve coaching personalization
- Add calibration profiles / per-user presets
- Replace cover drawable with real generated images (#13)

---

## Privacy Model

```text
All processing is on-device
```

- no camera upload
- no pose upload
- no cloud dependency
- debug overlay uses local pose geometry only
- threshold calibration is stored locally
- camera onboarding uses local landmarks only
