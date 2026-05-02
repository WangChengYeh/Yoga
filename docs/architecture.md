# YogaFlow 3D Architecture (v4)

YogaFlow 3D 是一個 on-device AI 瑜伽教練系統。

核心概念：

```text
Camera → Perception → Detection Mapping → Flow → Coaching
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
- outputs (matched, state, cue)

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

This ensures:

- no duplicate triggers
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
✔ LLM + TTS coaching
```

---

## Next Steps

- Extract mapper interface
- Add variance-based stability scoring
- Add UI debug overlay
- Add more pose families
- Improve coaching personalization

---

## Privacy Model

```text
All processing is on-device
```

- no camera upload
- no pose upload
- no cloud dependency
