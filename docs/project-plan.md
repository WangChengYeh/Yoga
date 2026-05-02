# YogaFlow 3D Project Plan

## Executive Summary
YogaFlow 3D 是一個運行於高階 Android 手機的 on-device AI 瑜伽教練系統，整合姿勢辨識、動作流程控制與本地 LLM，提供即時語音指導。

## Product Direction

- High-end Android only
- Fully on-device (no cloud inference)
- Real-time coaching loop (<1s latency)
- Flow-based instruction（不是單幀判斷）

## Core Innovation

### 1. Pose-aware State Machine

將姿勢判斷拆成：
- setup
- movement
- hold
- correction
- transition


### 2. Flow Engine

每個動作是一段流程，而不是單一姿勢：

```text
Forward Fold:
站直 → 前彎 → 停留 → 回來
```


### 3. LLM as Language Layer

LLM 不負責判斷，只負責語氣轉換：

```text
State Machine → Flow → LLM → Human coaching sentence
```


### 4. On-device Privacy

- Camera data 不上傳
- Pose data 不持久化
- LLM inference 本地執行

## System Components

- CameraX (input)
- MediaPipe Pose (perception)
- State Machine (logic)
- Flow Engine (temporal control)
- Gemma LLM (language)
- TTS (output)

## Product Features

- Skeleton overlay
- Pose selection UI
- Real-time coaching text
- Voice coach with debounce
- Flow-based instruction

## Future Expansion

- 100+ Yoga flows
- YouTube-aligned training sequences
- Personalized difficulty
- AI-generated pose library
