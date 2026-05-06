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

## Agentic AI Development Workflow

YogaFlow 3D 的開發本身也是一個 AI 代理協作系統：

### 角色分工

| 代理 | 角色 | 職責 |
|------|------|------|
| Claude (PM) | 專案經理 | 讀取 GitHub Issues、決定優先順序、撰寫任務 prompt、審查代理輸出、提交、關閉 issue |
| Codex | 主要實作者 | 多檔案代碼修改、重構、PR 級別的提交 |
| Gemini CLI | 次要實作者 | Codex 達到速率限制時接手、代碼審查、繼續實作 |

### 任務流程

```
GitHub Issue
    ↓
Claude 讀取並分析 → 撰寫精準 prompt
    ↓
Codex 實作 → 構建 + 設備測試
    ↓ (如 Codex 受阻)
Gemini 繼續 → 構建 + 設備測試
    ↓
Claude 審查 → git commit → issue comment → close
```

### 指令頻道

- **GitHub Issues**：所有任務的唯一來源（功能請求、bug、里程碑）
- **Claude iMessage 頻道**：即時指令（`Go project` 觸發每小時 triage 循環）

### 關鍵規則

- 每個 Codex/Gemini prompt 必須包含「如何驗證」：`./gradlew assembleDebug` + adb 設備測試
- 代理必須自行測試，不得留下「待驗證」的輸出
- GitHub 是所有任務狀態的唯一真相來源
