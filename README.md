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
CameraFramingCoach + ViewOrientation + PoseGeometry
        ↓
PoseStateMachine + Flow Engine
        ↓
LLM Coach / Fallback
        ↓
Voice Coaching
```

---

## Implemented Features

### Core System
- Flow DSL (`.flow.txt`)
- Flow parser
- Pose state machine
- Flow runtime engine
- Flow playlist (multi-flow class)
- Auto flow discovery (`assets/flows`)

### App Orchestration
- Complete `MainActivity` orchestration layer
- Course selection wiring
- Session lifecycle: IDLE / RUNNING / PAUSED / COMPLETED
- Playlist reset / restart / transition handling
- Camera lifecycle delegated to `CameraPosePipeline`
- Full MainActivity wiring for 3D pose, framing, orientation, flow, LLM, and TTS

### Camera / Pose Pipeline
- Reusable `CameraPosePipeline.kt`
- CameraX `RGBA_8888` image analysis path
- `STRATEGY_KEEP_ONLY_LATEST` backpressure control
- Bitmap-based rotation before MediaPipe inference
- `PoseHelper` owns `ImageProxy.close()`
- `PoseHelper` emits `PoseDetectionResult`
- `ImageProcessingOptions` removed from pose input path

### Geometry / Camera Coaching
- `PoseGeometry`: 3D world-landmark joint angle calculation
- 2D fallback with image width / height scaling and low confidence marking
- `ViewOrientation`: detects whether the body is facing the camera using 3D depth ratio
- `CameraFramingCoach`: detects full-body framing, too close / too far, left / right offset, top / bottom crop
- Coaching priority: framing → orientation → pose correction

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

---

## Demo Courses

- Beginner Flow (Mountain → Forward Fold → Twist)
- Stretch Class (Forward Fold)
- Recovery Class (Twist)

---

## Remaining Product Work

- Replace cover drawable with real generated images (#13)
- Optional: visual body framing box overlay
- Optional: voice pacing rules for production polish

---

## Requirements

- Android 15+
- High-end device (NPU / 12GB RAM recommended)

---

## Tags

`#Android15` `#MediaPipe` `#LocalLLM` `#OnDeviceAI` `#3DPose` `#CameraCoaching`
