# YogaFlow 3D Architecture (v3)

YogaFlow 3D 是一個 on-device AI 瑜伽教練系統。

系統核心不是單純做 pose detection，而是：

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

## Key Architecture Shift

### Before (legacy)

```text
PoseStateMachine → FlowEngine → Cue
```

Problems:
- ambiguous step detection
- unstable behavior near thresholds
- hard to scale to multiple poses

---

### Now (current)

```text
Flow step.detect
→ Detection Mapper
→ Stable match
→ FlowEvent
→ Coach
```

This separates:

- content (flow)
- perception (pose geometry)
- decision (mapping)
- runtime (flow engine)

---

## Detection Mapping Layer

### ForwardFoldDetectionMapper

Uses:
- bilateral knee angles
- bilateral hip angles

Handles:
- setup
- hip hinge
- controlled fold
- hold
- return

---

### TwistDetectionMapper

Uses:
- left/right torso angle difference

Handles:
- stable base
- twist start
- hold
- return center

---

## Runtime Stability Layer

To avoid jitter from pose detection:

### 1. EMA Smoothing

```text
smooth = prev + α * (raw - prev)
```

### 2. Deadband

```text
small changes ignored
```

### 3. Stability Window

```text
must stay stable ~300ms before accepted
```

### 4. Cue Throttling

```text
avoid repeated voice spam
```

---

## Event-driven Flow Engine

`PoseFlowEngine` emits:

```text
Cue
StepCompleted
FlowCompleted
```

This avoids:

- double trigger
- skipped steps
- timing bugs

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

## Privacy Model

```text
All processing is on-device
```

- no camera upload
- no pose upload
- no cloud dependency for core loop

---

## Current Capability

```text
✔ Forward Fold live coaching
✔ Twist live coaching
✔ Multi-flow playlist
✔ Event-driven runtime
✔ Stability-aware detection
✔ LLM + TTS coaching
```

---

## Next Steps

- Apply stability window to Twist
- Extract mapper interface
- Add more poses (squat / bridge)
- UI debug overlay
- Full class flow (5+ poses)
