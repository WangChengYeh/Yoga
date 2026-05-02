# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D 是一款高階 Android 手機限定的 on-device AI 瑜伽教練 App。整合 CameraX、MediaPipe Pose、3D Pose Geometry、Camera Coaching、Flow Engine、本地 Gemma LLM 與 TTS，提供即時站位、面向、姿勢與語音教練。

---

## Current Product State

```text
Home (Multi-course)
        ↓
Course Selection (Beginner / Stretch / Recovery)
        ↓
MainActivity Orchestration
        ↓
CameraPosePipeline
        ↓
PoseHelper / MediaPipe Pose
        ↓
PoseDetectionResult (2D image landmarks + 3D world landmarks + image size)
        ↓
CameraFramingCoach + ViewOrientation
        ↓
Flow current step.detect
        ↓
Pose Detection Mapper (ForwardFold / Twist)
        ↓
EMA smoothing + deadband + stability window
        ↓
PoseFlowEngine FlowEvent
        ↓
LLM Coach / Fallback on background executor
        ↓
TTS Voice Coaching
```

---

## Implemented Features

### Core System
- Flow DSL (`.flow.txt`)
- Flow parser
- Pose runtime engine
- Event-driven `PoseFlowEngine`
- Flow playlist (multi-flow class)
- Auto flow discovery (`assets/flows`)
- Flow step-level `detect` mapping

### App Orchestration
- Complete `MainActivity` orchestration layer
- Course selection wiring
- Session lifecycle: IDLE / RUNNING / PAUSED / COMPLETED
- Playlist reset / restart / transition handling
- Camera lifecycle delegated to `CameraPosePipeline`
- Full MainActivity wiring for 3D pose, framing, orientation, detection mapping, event flow, LLM, and TTS
- Safe flow loading with `runCatching`
- Empty playlist / restart guardrails

### Camera / Pose Pipeline
- Reusable `CameraPosePipeline.kt`
- CameraX `RGBA_8888` image analysis path
- `STRATEGY_KEEP_ONLY_LATEST` backpressure control
- Bitmap-based rotation before MediaPipe inference
- `PoseHelper` owns `ImageProxy.close()`
- `PoseHelper` emits `PoseDetectionResult`
- Camera start error callback
- `ImageProcessingOptions` removed from pose input path

### Geometry / Camera Coaching
- `PoseGeometry`: 3D world-landmark joint angle calculation
- 2D fallback with image width / height scaling and low confidence marking
- `ViewOrientation`: detects whether the body is facing the camera using 3D depth ratio
- `CameraFramingCoach`: detects full-body framing, too close / too far, left / right offset, top / bottom crop
- Coaching priority: framing → orientation → pose detection mapping → flow cue

### Pose Detection Mapping
- `ForwardFoldDetectionMapper`
  - `ready_forward_fold`
  - `tall_spine_setup`
  - `hip_hinge`
  - `controlled_forward_fold`
  - `forward_hold`
  - `return_standing`
  - `neutral_finish`
- `TwistDetectionMapper`
  - `stable_base`
  - `twist_start`
  - `twist_hold`
  - `return_center`
- Forward Fold uses bilateral knee / hip angles
- Twist uses left / right torso angle difference
- Mapping returns `matched`, `CoachState`, and live correction cue

### Runtime Stability
- EMA angle smoothing
- Angle deadband to ignore small jitter
- Forward Fold stability window before matched state is accepted
- FlowEvent model:
  - `Cue`
  - `StepCompleted`
  - `FlowCompleted`
- LLM generation moved off UI thread
- Coach cue throttle:
  - minimum cue interval
  - same-cue repeat interval
- UI text and TTS spoken text separated so fallback labels are not spoken

### UI / UX
- Multi-course home screen
- Course cover (drawable)
- Flow index + step index
- Progress bar animation
- Countdown animation
- Countdown voice cue (3,2,1)
- Flow transition animation
- Start / Pause / Restart controls

### AI
- MediaPipe Pose (33 keypoints)
- 3D world-landmark pose reasoning
- LLM Coach (Gemma via MediaPipe)
- Fallback coach
- TTS voice coaching
- Background executor for LLM generation

---

## Demo Courses

- Beginner Flow (Mountain → Forward Fold → Twist)
- Stretch Class (Forward Fold)
- Recovery Class (Twist)

---

## Supported Live-Coached Poses

### Forward Fold

Full live-coach pipeline:

```text
flow step.detect
→ ForwardFoldDetectionMapper
→ knee / hip angle smoothing
→ stability window
→ FlowEvent
→ LLM/TTS cue
```

Safety priorities:

- knees long but not locked
- forward movement from hip hinge
- no forced depth
- controlled return to neutral

### Twist

Live-coach mapping:

```text
flow step.detect
→ TwistDetectionMapper
→ torso twist estimate
→ FlowEvent
→ LLM/TTS cue
```

Safety priorities:

- stable base first
- gentle twist start
- no excessive rotation
- slow return to center

---

## Remaining Product Work

- Apply stability window to Twist mapping
- Reset all mapper smoothing state on playlist restart / flow transition
- Optional: extract mapper interface (`PoseDetectionMapper`)
- Optional: visual body framing box overlay
- Optional: angle / state debug overlay
- Replace cover drawable with real generated images (#13)

---

## Requirements

- Android 15+
- High-end device (NPU / 12GB RAM recommended)

---

## Tags

`#Android15` `#MediaPipe` `#LocalLLM` `#OnDeviceAI` `#3DPose` `#CameraCoaching` `#OnDeviceYogaCoach`
