# Architecture (Product-Level)

YogaFlow 3D 已從技術 demo 演進為完整 App Prototype。本文件描述「產品級架構」，包含 UI、Session 控制、Flow Runtime 與本地 AI。

---

## Full Product Pipeline

```text
Home Course Page
        ↓
Course Selection (Card UI)
        ↓
Class Session Controller
(Start / Pause / Restart)
        ↓
CameraX (Preview + Frame)
        ↓
MediaPipe Pose Landmarker
        ↓
33 Keypoints + Skeleton Overlay
        ↓
PoseStateMachine
        ↓
PoseFlowEngine (Flow Runtime)
        ↓
LLM Coach (Gemma) / Fallback
        ↓
CoachPhrasePolisher
        ↓
TTS Speaker
        ↓
User Feedback (Voice + UI)
```

---

## System Layers

### 1. Presentation Layer (UI)
- Home screen (course selection)
- Course cover image
- Class screen (camera + overlay)
- Progress bar / step indicator
- Countdown timer
- LLM status indicator
- Control buttons (Start / Pause / Restart)

### 2. Session Layer
- SessionState: IDLE / RUNNING / PAUSED / COMPLETED
- Controls lifecycle of a yoga class
- Blocks processing when paused

### 3. Camera Layer
- CameraX PreviewView
- ImageAnalysis pipeline
- Back camera default

### 4. Perception Layer
- MediaPipe Pose Landmarker
- 33 landmarks detection
- PoseOverlayView rendering

### 5. Yoga Domain Layer
- YogaPose
- YogaPoseCatalog

### 6. State Machine Layer
- PoseStateMachine
- SETUP / MOVEMENT / HOLD / TRANSITION / CORRECTION

### 7. Flow Runtime Layer
- PoseFlowEngine
- Multi-step yoga instruction
- Time-based transitions

### 8. LLM Layer
- LlmCoach
- MediaPipe Gemma
- Fallback mode

### 9. Voice Layer
- CoachPhrasePolisher
- CoachSpeaker (TTS)

---

## Design Principles

### Deterministic Core + LLM Enhancement

```text
Pose → State Machine → Flow → LLM phrasing
```

### On-Device First
- No cloud
- Full local inference

### Flow-Driven System
- `.flow.txt` = behavior definition

### High-End Only
- Optimized for flagship devices

---

## Future Extensions

- Flow Playlist (multi-flow class)
- Multi-course system
- Personalized coaching
- AI-generated flows
