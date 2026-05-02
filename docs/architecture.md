# Architecture (Product-Level)

YogaFlow 3D 為完整 on-device AI 瑜珈系統。

---

## Full Pipeline

```text
Home (Multi-course)
        ↓
Course Selection
        ↓
MainActivity (Orchestration Layer)
        ↓
CameraPosePipeline
        ↓
PoseHelper (Inference Layer)
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

### Orchestration Layer
- MainActivity (single source of truth)
- Session state management
- Playlist control
- Flow transition handling

### Camera Layer
- CameraPosePipeline
- RGBA_8888 deterministic pipeline
- Backpressure control

### Inference Layer
- PoseHelper
- Bitmap rotation + MPImage conversion
- MediaPipe Pose

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
- Pipeline modularization (Camera / Inference / Orchestration)

---

## Status

All core architecture implemented (production-ready MVP).

---

## Future

- Personalized coaching
- AI-generated flows
- Biomechanics (Python)
