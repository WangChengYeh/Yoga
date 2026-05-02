# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

---

## System Architecture

```mermaid
flowchart TD
    User[User] --> Camera[CameraX]
    Camera --> Pose[MediaPipe Pose]

    Pose --> Landmarks[2D + 3D Landmarks]

    Landmarks --> Framing[Framing Check]
    Landmarks --> Orientation[Orientation Check]
    Landmarks --> Geometry[Pose Geometry]

    Framing --> Gate{Ready?}
    Orientation --> Gate

    Gate -- No --> Setup[Camera Coaching]
    Setup --> Voice1[TTS]

    Gate -- Yes --> Flow[Flow Step]

    Flow --> Mapper[Detection Mapper]
    Geometry --> Mapper

    Mapper --> Stability[Stability Layer\nEMA + Deadband + Window]

    Stability --> Engine[Flow Engine\n(Event Driven)]

    Engine --> Coach[LLM Coach / Rule-based]

    Coach --> Voice2[TTS]
    Coach --> UI[UI Feedback]
```

---

YogaFlow 3D 是一款高階 Android 手機限定的 on-device AI 瑜伽教練 App。整合 CameraX、MediaPipe Pose、3D Pose Geometry、Camera Coaching、Flow Engine、本地 Gemma LLM 與 TTS，提供即時站位、面向、姿勢與語音教練。

---

## Architecture

Full system architecture: [`docs/architecture.md`](docs/architecture.md)

```text
CameraX
  ↓
MediaPipe Pose
  ↓
PoseDetectionResult
  ↓
Framing / Orientation Gate
  ↓
Flow step.detect
  ↓
Detection Mapper
  ↓
Smoothing / Stability
  ↓
PoseFlowEngine FlowEvent
  ↓
LLM Coach / Fallback
  ↓
TTS Voice Coaching
```

---

## Current Product State

YogaFlow 3D is a demo-ready on-device live yoga coach prototype with a complete beginner class.

The current runtime supports:

- camera setup coaching before pose correction
- flow-driven class progression
- step-level pose detection mapping
- event-driven flow runtime
- local LLM coaching on a background executor
- TTS voice coaching
- multi-pose class chaining

---

## Implemented Features

### Core System
- Flow DSL (`.flow.txt`)
- Flow parser
- Event-driven `PoseFlowEngine`
- Flow playlist (multi-flow class)
- Auto flow discovery (`assets/flows`)
- Flow step-level `detect` mapping

### Runtime Pipeline
- `CameraPosePipeline`
- `PoseHelper` / MediaPipe Pose
- `PoseDetectionResult`
- `CameraFramingCoach`
- `ViewOrientation`
- `PoseGeometry`
- `ForwardFoldDetectionMapper`
- `TwistDetectionMapper`
- `SquatDetectionMapper`
- `BridgeDetectionMapper`
- `LlmCoach`
- `CoachSpeaker`

### Detection Mapping
- Forward Fold mapping:
  - `ready_forward_fold`
  - `tall_spine_setup`
  - `hip_hinge`
  - `controlled_forward_fold`
  - `forward_hold`
  - `return_standing`
  - `neutral_finish`
- Twist mapping:
  - `stable_base`
  - `twist_start`
  - `twist_hold`
  - `return_center`
- Squat mapping:
  - `squat_setup`
  - `squat_descent`
  - `squat_hold`
  - `squat_return`
- Bridge mapping:
  - `bridge_setup`
  - `bridge_lift`
  - `bridge_hold`
  - `bridge_return`

### Runtime Stability
- EMA angle smoothing
- Angle deadband
- Stability window before matched state is accepted
- Mapper reset on playlist restart / flow transition
- Coach cue throttle
- LLM generation off UI thread
- Camera startup error callback
- Safe flow loading with `runCatching`

---

## Demo Courses

- Beginner Flow (Mountain → Forward Fold → Twist → Squat → Bridge)
- Stretch Class (Forward Fold)
- Recovery Class (Twist)

---

## Supported Live-Coached Poses

### Forward Fold

```text
flow step.detect
→ ForwardFoldDetectionMapper
→ bilateral knee / hip geometry
→ smoothing + stability window
→ FlowEvent
→ LLM/TTS cue
```

### Twist

```text
flow step.detect
→ TwistDetectionMapper
→ torso twist estimate
→ smoothing + stability window
→ FlowEvent
→ LLM/TTS cue
```

### Squat

```text
flow step.detect
→ SquatDetectionMapper
→ bilateral knee geometry
→ smoothing + stability window
→ FlowEvent
→ LLM/TTS cue
```

### Bridge

```text
flow step.detect
→ BridgeDetectionMapper
→ bilateral hip geometry
→ smoothing + stability window
→ FlowEvent
→ LLM/TTS cue
```

---

## Requirements

- Android 15+
- High-end device (NPU / 12GB RAM recommended)

---

## Remaining Product Work

- Extract mapper interface (`PoseDetectionMapper`)
- Add visual body framing box overlay
- Add angle / state debug overlay
- Add variance-based stability scoring
- Replace cover drawable with real generated images (#13)

---

## Tags

`#Android15` `#MediaPipe` `#LocalLLM` `#OnDeviceAI` `#3DPose` `#CameraCoaching` `#OnDeviceYogaCoach`
