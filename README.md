# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D 是一款高階 Android 手機限定的 on-device AI 瑜伽教練 App。整合 CameraX、MediaPipe Pose、Flow Engine、本地 Gemma LLM 與 TTS，提供即時姿勢分析與語音教練。

---

## Current Product State

```text
Home (Multi-course)
        ↓
Course Selection (Beginner / Stretch / Recovery)
        ↓
Camera Class
        ↓
Pose Detection + Flow Engine
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

### UI / UX
- Multi-course home screen
- Course cover (drawable)
- Flow index + step index
- Progress bar animation
- Countdown animation
- Flow transition animation
- Start / Pause / Restart controls

### AI
- MediaPipe Pose (33 keypoints)
- LLM Coach (Gemma via MediaPipe)
- Fallback coach
- TTS voice coaching

---

## Demo Courses

- Beginner Flow (Mountain → Forward Fold → Twist)
- Stretch Class (Forward Fold)
- Recovery Class (Twist)

---

## Remaining Work

- Replace cover drawable with real generated images (#13)

---

## Requirements

- Android 15+
- High-end device (NPU / 12GB RAM recommended)

---

## Tags

`#Android15` `#MediaPipe` `#LocalLLM` `#OnDeviceAI`
