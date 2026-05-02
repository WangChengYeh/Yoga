# YogaFlow 3D Architecture (v6)

YogaFlow 3D 是一個 on-device AI 瑜伽教練系統。

核心概念：

```text
Camera → Perception → Detection Mapping → Flow → Coaching
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
  ↓ (Gate)
Flow step.detect
  ↓
Detection Mapper
  ↓
Smoothing (EMA + Deadband)
  ↓
Stability Window
  ↓
PoseFlowEngine (Event)
  ↓
LLM Coach / Fallback
  ↓
TTS Voice
```

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

All poses use:

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
Framing
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

- Add threshold reset button
- Add calibration profiles
- Add visual body framing box overlay
- Extract mapper interface (`PoseDetectionMapper`)

### Perception Quality

- Add variance-based stability scoring
- Add record / replay debugging
- Add auto calibration from observed range of motion
- Add scoring system for pose quality

### Product Expansion

- Add more pose families
- Improve coaching personalization
- Add per-user calibration presets
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
