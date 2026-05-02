# Architecture (Product-Level v2)

YogaFlow 3D 是一個 on-device AI 健身教練系統。核心設計不是單純做 pose detection，而是先理解使用者在鏡頭中的位置，再理解身體面向，最後才進入 3D 姿勢判斷與教練指令。

---

## Product Principle

```text
先看人在哪裡
        ↓
再看人有沒有面向鏡頭
        ↓
再看人體 3D 關節角度
        ↓
最後才給姿勢 coaching
```

這個順序避免在使用者太近、太遠、偏離畫面或側身時，直接給出錯誤的姿勢修正。

---

## Full System Pipeline

```text
Home / Course Selection
        ↓
MainActivity
(Orchestration Layer)
        ↓
CameraPosePipeline
(CameraX Preview + ImageAnalysis)
        ↓
PoseHelper
(MediaPipe Pose Inference)
        ↓
PoseDetectionResult
├── imageLandmarks: 2D normalized landmarks
├── worldLandmarks: 3D world landmarks
├── imageWidth
└── imageHeight
        ↓
┌──────────────────────────────────────────────┐
│              AI Perception Layer              │
│                                              │
│  CameraFramingCoach                          │
│    → full body framing                        │
│    → too close / too far                      │
│    → left / right offset                      │
│    → top / bottom crop                        │
│                                              │
│  ViewOrientation                              │
│    → front-facing / off-axis / too rotated    │
│    → shoulder + hip depth ratio               │
│                                              │
│  PoseGeometry                                 │
│    → 3D joint angles from world landmarks     │
│    → 2D fallback with image-size scaling      │
│    → confidence marking                       │
└──────────────────────────────────────────────┘
        ↓
┌──────────────────────────────────────────────┐
│                Decision Layer                 │
│                                              │
│  PoseStateMachine                             │
│    → pose correctness                         │
│    → setup / movement / hold / correction     │
│                                              │
│  PoseFlowEngine                               │
│    → current flow step                        │
│    → hold timing                              │
│    → transition readiness                     │
│                                              │
│  FlowPlaylistEngine                           │
│    → multi-flow class progression             │
└──────────────────────────────────────────────┘
        ↓
┌──────────────────────────────────────────────┐
│                Coaching Layer                 │
│                                              │
│  Priority:                                   │
│    1. Camera framing                          │
│    2. View orientation                        │
│    3. Pose correction                         │
│                                              │
│  LLM Coach / Fallback                         │
│  TTS Voice Coaching                           │
└──────────────────────────────────────────────┘
```

---

## Layer Breakdown

### 1. UI Layer

Responsible for user-facing class experience.

Implemented by:
- `MainActivity`
- `activity_main.xml`
- `PoseOverlayView`

Responsibilities:
- Course selection
- Start / Pause / Restart
- Progress bar
- Countdown display
- Flow name / step display
- 2D skeleton overlay

---

### 2. Orchestration Layer

Implemented by:
- `MainActivity`

Responsibilities:
- Owns session state: `IDLE`, `RUNNING`, `PAUSED`, `COMPLETED`
- Wires camera result into AI perception modules
- Applies coaching priority
- Updates UI
- Triggers LLM / fallback coach
- Triggers TTS

MainActivity does not own camera internals or pose inference internals. It coordinates modules only.

---

### 3. Camera Layer

Implemented by:
- `CameraPosePipeline.kt`

Responsibilities:
- CameraX preview binding
- CameraX `ImageAnalysis`
- `RGBA_8888` deterministic image path
- `STRATEGY_KEEP_ONLY_LATEST` backpressure control
- Lifecycle binding
- Sends `ImageProxy` to `PoseHelper`

Important ownership rule:

```text
CameraPosePipeline passes ImageProxy
PoseHelper owns ImageProxy.close()
```

This prevents double-close, early-close, and frame ownership bugs.

---

### 4. Inference Layer

Implemented by:
- `PoseHelper.kt`

Responsibilities:
- Convert CameraX RGBA frame to Bitmap
- Rotate Bitmap before inference
- Convert Bitmap to MediaPipe `MPImage`
- Run MediaPipe Pose in live-stream mode
- Emit `PoseDetectionResult`

Output:

```kotlin
PoseDetectionResult(
    imageLandmarks = ...,   // 2D normalized landmarks for overlay / fallback
    worldLandmarks = ...,   // 3D world landmarks for geometry
    imageWidth = ...,
    imageHeight = ...
)
```

---

### 5. Pose Data Model

Implemented by:
- `PoseDetectionResult.kt`

Purpose:
- Keep 2D drawing data and 3D reasoning data together
- Preserve image dimensions for safe 2D fallback calculations
- Avoid passing raw landmark lists through the app

Fields:

```kotlin
imageLandmarks: List<NormalizedLandmark>
worldLandmarks: List<Landmark>
imageWidth: Int
imageHeight: Int
```

---

### 6. AI Perception Layer

This layer decides whether pose coaching is safe and meaningful.

#### CameraFramingCoach

Implemented by:
- `CameraFramingCoach.kt`

Detects:
- Full body visibility
- Too close
- Too far
- Too far left
- Too far right
- Top crop
- Bottom crop
- Unknown / insufficient visibility

Example outputs:

```text
請退後一步
請往右移一點
請讓全身進入畫面
```

#### ViewOrientation

Implemented by:
- `ViewOrientation.kt`

Uses 3D world landmarks to estimate whether the user is front-facing.

Signal:

```text
score = average shoulder/hip depth difference ÷ average shoulder/hip width
```

Detects:
- `GOOD`
- `OFF_AXIS`
- `TOO_ROTATED`
- `UNKNOWN`

Example outputs:

```text
請更正面面對鏡頭
請轉回來，讓肩膀和骨盆更正面面對鏡頭
```

#### PoseGeometry

Implemented by:
- `PoseGeometry.kt`

Responsibilities:
- Prefer 3D world-landmark joint angles
- Fall back to 2D pixel-space angles only when 3D landmarks are unavailable
- Mark fallback confidence as low

Angle source priority:

```text
1. worldLandmarks → HIGH_3D
2. imageLandmarks * imageWidth/imageHeight → LOW_2D_FALLBACK
3. missing data → INVALID
```

This fixes the old 2D projection problem where side-facing users caused incorrect knee / hip angle measurements.

---

### 7. Decision Layer

#### PoseStateMachine

Implemented by:
- `PoseStateMachine.kt`

Responsibilities:
- Uses `PoseDetectionResult`
- Calls `PoseGeometry.angle(...)`
- Produces deterministic `CoachState` and rule-based cue

Example:

```text
forward_fold:
- knee angle too small → correction
- hip angle not folded enough → movement cue
- otherwise → hold
```

#### PoseFlowEngine

Implemented by:
- `PoseFlowEngine.kt`

Responsibilities:
- Tracks current step in a flow
- Handles setup / movement / hold / correction / transition timing
- Determines whether the current step is satisfied

#### FlowPlaylistEngine

Implemented by:
- `FlowPlaylistEngine.kt`

Responsibilities:
- Treats multiple flow files as one full class
- Moves to the next flow only after the current flow is completed

---

### 8. Coaching Layer

The coaching layer always prioritizes setup quality before pose correction.

Priority order:

```kotlin
when {
    framing.status != CameraFramingStatus.GOOD -> framing.message
    orientation.status != ViewOrientationStatus.GOOD -> orientation.message
    poseCue.isNotBlank() -> poseCue
    else -> flowCue
}
```

Meaning:

```text
If the user is not fully visible → fix framing first
If the user is too rotated → fix orientation next
If the user is framed and facing camera → give pose coaching
```

This prevents bad coaching caused by poor camera setup.

---

### 9. Language Layer

Implemented by:
- `LlmCoach.kt`
- `CoachPhrasePolisher.kt`
- `CoachSpeaker.kt`

Responsibilities:
- Convert deterministic cue into natural coaching language
- Fall back to rule-based cue when LLM is unavailable
- Speak final coaching text via TTS

Design rule:

```text
The deterministic system decides what to say.
The LLM only decides how to say it.
```

---

## Product-Level Behavior

### Bad framing

```text
User too close / cropped / off center
        ↓
Coach: 請退後一步 / 請往右移一點
```

### Bad orientation

```text
User is sideways or off-axis
        ↓
Coach: 請更正面面對鏡頭
```

### Good camera setup

```text
User is visible and facing camera
        ↓
Coach: 膝蓋再伸直一點 / 從髖部往前折
```

---

## Design Principles

### 1. Perception first

The system must understand camera setup before making pose judgments.

```text
Framing → Orientation → Pose
```

### 2. Deterministic core

State machines and geometry decide correctness. LLM output is language polish only.

### 3. 3D over 2D

3D world landmarks are used for body angles whenever available. 2D is fallback only.

### 4. Single ownership

`PoseHelper` owns `ImageProxy.close()`.

### 5. Modular pipeline

Camera, inference, perception, decision, and language are separate modules.

---

## Current Capability

```text
✔ CameraX live-stream pipeline
✔ MediaPipe Pose inference
✔ 2D skeleton overlay
✔ 3D world-landmark geometry
✔ Camera framing coaching
✔ View orientation coaching
✔ Pose state machine
✔ Flow runtime engine
✔ Multi-flow class playlist
✔ LLM/fallback coaching
✔ TTS voice coaching
```

---

## Future Work

- Visual body framing box overlay
- Voice pacing rules
- More flows and pose-specific rules
- Personalized coaching
- Multi-model support: Pose + Hand + Face
- AI-generated flows
- Biomechanics scoring
