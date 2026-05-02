# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D 是一款高階 Android 手機限定的 on-device AI 瑜伽教練 App。它整合 CameraX、MediaPipe Pose、Flow Engine、本地 Gemma LLM 與 TTS，提供即時姿勢分析、課程流程引導與語音教練。

## Current Demo Status

目前 repo 已具備一個完整可展示的 MVP prototype：

```text
Home Course Page
        ↓
Beginner Flow Card
        ↓
Camera Class View
        ↓
Pose Detection + Skeleton Overlay
        ↓
Flow Runtime + State Machine
        ↓
LLM Coach / Fallback Coach
        ↓
TTS Voice Coaching
```

## Product Positioning

**YogaFlow 3D 只支援高階 Android 手機。**

本專案不追求低階或中階手機相容性。核心優先順序：

1. 本地端 AI 推理
2. 即時姿勢分析
3. 本地語音教練
4. 隱私資料零離機
5. 低延遲互動體驗

## Recommended Device

- Android 15 或以上優先
- 旗艦級 SoC / NPU
- RAM 12GB 以上建議
- 可穩定執行 CameraX + MediaPipe Pose + Gemma LLM + TTS

## Implemented Features

- Home course selection page
- Beginner Flow course card
- Course cover drawable (`cover_beginner`)
- CameraX preview + frame analyzer
- MediaPipe Pose Landmarker integration
- 33-point skeleton overlay
- Text-based Yoga Flow format (`.flow.txt`)
- Flow parser + asset loader
- Pose state machine
- Flow runtime engine
- Start / Pause / Restart controls
- Progress bar, step counter, countdown display
- Countdown voice cue
- Gemma LLM wrapper via MediaPipe GenAI
- Fallback coach when LLM model is unavailable
- Debounced TTS voice coaching

## Runtime Architecture

```text
CameraX
  → MediaPipe Pose
  → PoseStateMachine
  → PoseFlowEngine
  → PromptBuilder
  → LlmCoach / fallback
  → CoachPhrasePolisher
  → CoachSpeaker / TTS
```

## Demo Course

Current demo flow:

```text
Beginner Flow
Mountain → Forward Fold → Twist
```

目前 MainActivity 預設使用：

```text
app/src/main/assets/flows/02_forward_fold_main.flow.txt
```

其他已建立 demo flows：

```text
app/src/main/assets/flows/01_mountain_warmup.flow.txt
app/src/main/assets/flows/03_twist_cooldown.flow.txt
```

## Required Local Models

### Pose model

Place MediaPipe pose model here:

```text
app/src/main/assets/pose_landmarker_lite.task
```

If missing, the app displays a clear pose model error.

### Gemma LLM model

For real on-device LLM mode, push the model to device:

```bash
adb shell mkdir -p /data/local/tmp/llm/
adb push gemma.task /data/local/tmp/llm/gemma.task
```

If missing, app falls back to deterministic coaching and shows `LLM: OFF`.

## Repo Structure

```text
app/
  src/main/
    java/com/yogaflow/
      coach/
      flow/
      llm/
      pose/
      yoga/
    assets/flows/
    res/drawable/
    res/layout/

docs/
  architecture.md
  flow-format.md
  project-plan.md
  roadmap.md

flows/
  demo_forward_fold.flow.txt
```

## Open Productization Issues

- #13 Replace XML demo cover with real generated PNG/JPG
- #14 Add multi-course home page
- #15 Auto-load flows from `assets/flows`
- #16 Add course UI animations

## Tags

`#Android15` `#MediaPipe` `#LocalLLM` `#AgenticAI` `#HighEndAndroidOnly` `#OnDeviceAI`
