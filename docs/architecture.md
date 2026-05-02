# Architecture

YogaFlow 3D 是一個高階 Android 手機限定的 on-device AI yoga coach。核心原則是：影像不離機、判斷在本地完成、LLM 只負責教練語氣轉換。

## Target Device Policy

本專案只支援高階 Android 手機，不追求低階裝置相容性。

建議條件：
- Android 15 優先
- 旗艦級 SoC / NPU
- RAM 12GB 以上建議
- 可穩定執行 CameraX + MediaPipe Pose + Gemma LLM + TTS

## Runtime Pipeline

```text
CameraX Preview / ImageAnalysis
        ↓
MediaPipe Pose Landmarker
        ↓
33 keypoints + skeleton overlay
        ↓
YogaPose selection
        ↓
PoseStateMachine
        ↓
PoseFlowEngine
        ↓
PromptBuilder
        ↓
Gemma LLM via MediaPipe GenAI
        ↓
CoachSpeaker / TTS
```

## Layers

### 1. Camera Layer
- CameraX PreviewView
- ImageAnalysis frame callback
- Back camera default

### 2. Perception Layer
- MediaPipe Pose Landmarker
- 33 normalized landmarks
- Skeleton overlay rendering

### 3. Yoga Domain Layer
- `YogaPose`
- `YogaPoseCatalog`
- 可擴充姿勢選單

### 4. State Layer
- `CoachState`
- `PoseStateMachine`
- 支援 SETUP / MOVEMENT / HOLD / CORRECTION / TRANSITION

### 5. Flow Layer
- `PoseFlowStep`
- `PoseFlowEngine`
- 將單幀判斷升級為完整動作流程

### 6. LLM Layer
- `PromptBuilder`
- `LlmCoach`
- MediaPipe GenAI `LlmInference`
- Gemma `.task` 模型路徑：`/data/local/tmp/llm/gemma.task`
- 若模型不存在，fallback 到 deterministic coaching

### 7. Output Layer
- On-screen coach text
- TTS voice coach
- Debounced speech output via `CoachSpeaker`

## Design Principle

LLM 不負責判斷姿勢。姿勢判斷由 state machine 與 rule-based analyzer 完成；LLM 只將系統判斷轉成自然、短句、可朗讀的教練語句。

```text
Correct:
Pose → State Machine → Flow → LLM phrasing

Avoid:
Pose → LLM decides everything
```
