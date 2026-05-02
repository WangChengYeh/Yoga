# Architecture (Product-Level)

YogaFlow 3D 為完整 on-device AI 瑜珈系統。

---

## Full Pipeline

```text
Home (Multi-course)
        ↓
Course Selection
        ↓
Session Controller
        ↓
CameraX
        ↓
MediaPipe Pose
        ↓
PoseStateMachine
        ↓
FlowPlaylistEngine
        ↓
PoseFlowEngine
        ↓
LLM Coach / Fallback
        ↓
TTS
```

---

## Implemented Modules

### UI Layer
- Multi-course home
- Course selection
- Progress + countdown + animation
- Session controls

### Session Layer
- IDLE / RUNNING / PAUSED / COMPLETED

### Flow Layer
- Flow DSL
- FlowParser
- FlowPlaylistEngine
- PoseFlowEngine

### AI Layer
- MediaPipe Pose
- LLM Coach (Gemma)
- TTS

---

## Principles

- Deterministic core, LLM for language only
- On-device first
- Flow-driven design

---

## Status

All core architecture implemented.

---

## Future

- Personalized coaching
- AI-generated flows
- Biomechanics (Python)
