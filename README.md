# YogaFlow 3D

> Visual AI Yoga Coach — see your mistakes, fix them in real time, and keep your movement data on your own device.

YogaFlow 3D is an Android, on-device AI yoga coaching app. It turns live camera frames into pose geometry, evaluates the user against structured yoga flow steps, and gives real-time visual / voice guidance.

**數據自主 • 即時引導 • 個性化成長**

---

## Why it matters

Most home yoga practice is still one-way video playback. The user follows a teacher, but cannot see whether their knees are bending, their hips are aligned, or their spine is collapsing.

YogaFlow 3D addresses this visual blind spot:

- Real-time body pose tracking
- Joint-angle based correction
- Flow-based lesson progression
- Local-first privacy model
- Personalized movement tuning over time

The long-term vision is simple:

> Make expert movement feedback available at home, without sending private camera data to the cloud.

---

## Product vision

YogaFlow 3D starts with yoga, but the underlying system is a motion-intelligence platform.

Potential expansion areas:

- physical therapy
- mobility training
- dance learning
- sports form correction
- post-injury movement recovery

The current implementation focuses on a practical MVP: live camera pose detection, strict flow execution, angle-based feedback, and voice coaching.

---

## Current capability

```text
Camera → Pose Detection → Geometry → Strict Mapper → Flow Engine → Coach Cue
```

Implemented core pieces:

- Android native app
- CameraX live camera pipeline
- MediaPipe pose integration
- 3D / 2D fallback geometry helpers
- JSON Flow DSL v2
- strict `DetectKey` validation
- pose-specific detection mappers
- runtime tuning overrides
- auto tuning suggestions from numeric fail reasons
- debug overlay for explainability
- session recorder for pose data and coach cues
- LLM-assisted phrase polishing
- TTS voice coaching

Current limitation:

```text
YouTube / teacher-video auto extraction is not fully automated yet.
```

Today, YogaFlow uses structured Flow JSON. Flows can be authored manually or prepared with AI assistance, but the app does not yet provide a complete automatic YouTube-to-flow pipeline.

---

## How it works

### 1. Flow JSON defines the lesson

The lesson sequence is not improvised by the LLM. It is defined by a strict Flow DSL.

```json
{
  "state": "HOLD",
  "durationMs": 8000,
  "cue": "停在這裡，保持呼吸。",
  "detect": "forward_hold",
  "runtime": {
    "stabilityMs": 650,
    "emaAlpha": 0.25,
    "angles": {
      "knee": { "hold": { "min": 145 } },
      "hip": { "hold": { "min": 50, "max": 130 } }
    }
  }
}
```

### 2. Camera frames become pose geometry

```text
CameraPosePipeline
        ↓
PoseDetectionResult
        ↓
PoseGeometry
```

### 3. Pose-specific mappers evaluate body state

```text
DetectKey + RuntimeParams + PoseDetectionResult
        ↓
ForwardFoldDetectionMapper / SquatDetectionMapper / TwistDetectionMapper / BridgeDetectionMapper
        ↓
matched / not matched + reason + cue
```

### 4. The flow engine advances the class

```text
PoseDetectionRouter
        ↓
PoseFlowEngine
        ↓
CoachCueController
        ↓
CoachSpeaker / LlmCoach / TTS
```

### 5. Session recording captures what happened

The class view includes a `Record Session` control. When enabled, the app records sampled pose evaluation data and coach cue events, then saves a JSONL file when recording stops.

Recorded frame events include:

```text
flow id
step number
detect key
CoachState
matched
landmark count
frame size
runtime summary
numeric fail reason
auto tuning suggestion
```

Frame event fields:

| Field | Meaning |
| --- | --- |
| `flowId` | Flow JSON id, for example `02_forward_fold_main`. |
| `step` | 1-based step number inside the active flow. |
| `detect` | Detection key from the Flow DSL, for example `ready_forward_fold`. |
| `state` | Detected coach state: `SETUP`, `MOVEMENT`, `HOLD`, `CORRECTION`, or `TRANSITION`. |
| `matched` | Whether the current frame satisfied the active detector and can count toward step progress. |
| `landmarks` | Number of pose landmarks returned by MediaPipe for this frame. `33` means a full pose result was available. |
| `imageWidth` / `imageHeight` | Camera frame size used for pose analysis. |
| `mirrored` | Whether the camera frame was mirrored before display/analysis. |
| `runtime` | Effective runtime parameters after flow defaults, step overrides, and user overrides. |
| `overrides` | Count or summary of active user tuning overrides. |
| `failReason` | Compact machine-readable reason the detector did not match, when available. |
| `failExplanation` | Plain-language explanation of `failReason` for people reading the recording. |
| `suggestion` | Auto-tuning suggestion derived from repeated numeric failures. |

Recorded cue events include the raw flow cue, displayed/polished cue, completion cue, flow id, step, and source.

Cue event fields:

| Field | Meaning |
| --- | --- |
| `flowId` | Flow JSON id active when the cue was emitted. |
| `step` | 1-based step number active when the cue was emitted. |
| `state` | Coach state associated with the cue. |
| `source` | Cue source, such as displayed coach text or flow completion. |
| `text` | Cue text shown/spoken by the app. |

Files are saved under the app external files directory:

```text
session-recordings/yogaflow-session-YYYYMMDD-HHMMSS.jsonl
```

---

## Architecture

```text
CameraPosePipeline
        ↓
PoseDetectionResult
        ↓
MainActivity.handlePoseFrame
        ↓
LiveCoachSessionController
        ↓
RuntimeOverrideMerger
        ↓
PoseDetectionRouter
        ↓
Pose-specific Detection Mapper
        ↓
PoseFlowEngine
        ↓
CoachCueController
        ↓
LlmCoach + CoachPhrasePolisher + CoachSpeaker/TTS
```

Design rule:

```text
Flow JSON is the source of truth.
LLM must not plan, reorder, or invent lesson steps.
LLM can only adapt phrase tone for flow-provided cues.
```

---

## Flow DSL

YogaFlow uses a type-safe JSON DSL v2.

```text
.flow.json
→ FlowJsonValidator
→ FlowParser
→ DetectKey
→ RuntimeParams
→ FlowValidator
→ PoseDetectionRouter
→ Strict Detection Mapper
→ PoseFlowEngine
```

Flow files live in:

```text
app/src/main/assets/flows/
```

Current production flows:

```text
01_mountain_warmup.flow.json
02_forward_fold_main.flow.json
03_twist_cooldown.flow.json
04_squat.flow.json
05_bridge.flow.json
```

Full DSL notes:

```text
docs/flow-dsl.md
```

---

## Runtime tuning

YogaFlow separates packaged lesson design from user-specific runtime tuning.

```text
Flow JSON defaults
        ↓
RuntimeParams
        ↓
RuntimeOverrideStore
        ↓
RuntimeOverrideMerger
        ↓
EffectiveRuntimeParams
        ↓
Detection Mapper
```

Override keys are scoped by:

```kotlin
data class RuntimeOverrideKey(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val path: String
)
```

Example paths:

```text
runtime.stabilityMs
runtime.emaAlpha
runtime.deadbandDegrees
runtime.angles.knee.hold.min
runtime.angles.hip.hold.max
```

---

## Explainable feedback

YogaFlow tries to make every correction debuggable.

Example numeric fail reason:

```text
knee=138.2 < min=145.0
```

Numeric fail reasons are compact diagnostics. They are meant for tuning, not for end-user coaching copy.

Plain-language example:

```text
knee=48.2 < min=155.0
```

Means:

```text
The app estimated the user's knee angle as 48.2 degrees.
This step required the knee angle to be at least 155.0 degrees.
Because 48.2 is too low, the frame did not count toward completing the step.
```

In session recordings, the same event also includes `failExplanation`:

```text
Observed knee angle was 48.2 degrees; this step requires at least 155.0 degrees.
```

Machine-readable format:

```text
<metric>=<observed value> <comparison> <bound name>=<required threshold>
```

Common metrics:

| Metric | Unit | Meaning |
| --- | --- | --- |
| `knee` | degrees | Knee joint angle estimated from hip-knee-ankle landmarks. Larger values mean a straighter leg; smaller values mean a more bent knee. |
| `hip` | degrees | Hip/body angle estimated from shoulder-hip-knee landmarks. The interpretation depends on the pose and detect phase. |
| `twist` | degrees | Approximate torso twist derived from left/right upper-body geometry. Larger values mean more rotation. |
| `stableFor` | milliseconds | How long the detector has continuously seen the required pose. |

Common bounds:

| Bound | Meaning |
| --- | --- |
| `min` | Observed value must be greater than or equal to this threshold. |
| `max` | Observed value must be less than or equal to this threshold. |
| `required` | Required stability duration in milliseconds, used with `stableFor`. |

Example auto tuning suggestion:

```text
knee.min 145.0 → 140.0 (9 samples, medium confidence)
```

Debug overlay can show:

- detect key
- coach state
- matched / not matched
- runtime params
- active overrides
- fail reason
- tuning suggestion

---

## Privacy model

YogaFlow is designed around local-first processing.

```text
camera frames stay on device
pose landmarks stay on device
flow execution is local
coaching can run locally
```

This is important because yoga practice happens in private spaces.

---

## Pitch deck

The product proposal is available as a Google Slides pitch deck:

```text
https://docs.google.com/presentation/d/1e0uUybgMie-YGJHSP8k9FjXeGxIeCdmMKvC8RVVI-nk/edit?usp=drivesdk
```

Pitch theme:

```text
視覺化智慧：重新定義居家瑜珈
```

---

## Roadmap

Near-term engineering priorities:

- restore full CameraSetupController wiring in MainActivity
- add CI validation for all Flow JSON files
- persist runtime overrides
- improve mapper lifecycle tests
- add strict MountainDetectionMapper
- add variance-based tuning confidence
- build a Flow Editor UI
- explore AI-assisted teacher-video-to-flow authoring

Tracked roadmap:

```text
docs/detection-refactor-roadmap.md
```

---

## Development notes

Recommended validation before merging flow changes:

```text
1. Validate all .flow.json files
2. Confirm FlowJsonValidator passes
3. Confirm FlowValidator passes
4. Run app and inspect debug overlay
5. Verify suggestions write only to RuntimeOverrideStore
```

Safe refactor rule:

```text
Do not patch MainActivity with placeholder content.
When using GitHub contents API, always fetch the current SHA and write a complete Kotlin file.
Prefer local IDE refactor for controller integration.
```

---

## Status

```text
Status: active prototype / architecture hardening
Platform: Android
Core: CameraX + MediaPipe + Flow DSL + local coach loop
Privacy: local-first
```

Current known gap:

```text
CameraSetupController exists, but latest main still needs final MainActivity wiring cleanup.
```

---

## Contact

```text
GitHub: WangChengYeh/Yoga
Email: contact@yogaflow3d.ai
```
