# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D is a production-oriented on-device AI yoga coach for Android. It turns live camera frames into 3D pose geometry, maps the user’s body state to structured yoga flow steps, and gives real-time voice coaching through local LLM + TTS.

---

## ✨ Flow DSL

YogaFlow uses a **JSON DSL v2**, type-safe, strict Flow DSL.

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

Supported:

```text
app/src/main/assets/flows/*.flow.json
flows/*.flow.json
```

Each strict detection flow can define flow-level runtime defaults:

```json
{
  "defaults": {
    "runtime": {
      "stabilityMs": 300,
      "emaAlpha": 0.35,
      "deadbandDegrees": 3
    }
  }
}
```

Each step defines only its detection-specific angles unless it needs to override defaults:

```json
{
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

Full spec:

```text
docs/flow-dsl.md
```

---

## App Controller Architecture

MainActivity is intentionally kept as the Android shell: lifecycle, permissions, view binding, camera lifecycle, and wiring. Live coaching logic is split into controllers.

```text
CameraPosePipeline
        ↓
PoseDetectionResult
        ↓
MainActivity.handlePoseFrame
        ├─ Camera setup gate
        │      CameraFramingCoach + ViewOrientation
        │
        └─ LiveCoachSessionController
               RuntimeOverrideMerger
               PoseDetectionRouter
               PoseFlowEngine
               AutoTuningAdvisor
               FlowEvent
                    ↓
             CoachCueController
               LlmCoach + CoachPhrasePolisher + CoachSpeaker/TTS
```

Current responsibility split:

```text
MainActivity
  Android lifecycle, permission, camera start/stop, UI wiring, playlist buttons, controller callbacks

CoachCueController
  LLM generation, phrase polishing, fallback detection, TTS, cue rate limiting, async request cancellation

LiveCoachSessionController
  ready pose frame handling, runtime override merge, pose detection routing, flow events, debug/tuning callbacks

CameraSetupController
  intended owner of full-body framing, orientation, ready gate, setup correction cues, and auto-start wiring
```

Current integration status:

```text
CoachCueController: integrated in MainActivity
LiveCoachSessionController: integrated in MainActivity
CameraSetupController: file exists; MainActivity still uses handleCameraSetupFrame as the stable pre-integration baseline
```

Design rule:

```text
LLM must not plan or reorder lessons.
LLM is only a phrase / tone adapter for flow-provided cues.
Flow JSON remains the source of truth for lesson sequence and detection behavior.
```

---

## Runtime Tuning Architecture

```text
                         ┌──────────────────────────┐
                         │     Flow JSON (DSL)      │
                         │  assets/flows/*.json     │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │       FlowLoader         │
                         │   parse + validation     │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │      RuntimeParams       │
                         │   typed DSL params       │
                         └────────────┬─────────────┘
                                      │
                     ┌────────────────┴────────────────┐
                     │                                 │
                     ▼                                 ▼
     ┌──────────────────────────┐        ┌──────────────────────────┐
     │ TunableParamExtractor    │        │   RuntimeOverrideStore   │
     │ DSL → UI params          │        │ user tuning layer        │
     └────────────┬─────────────┘        └────────────┬─────────────┘
                  │                                   │
                  ▼                                   ▼
        ┌──────────────────────┐           ┌────────────────────────┐
        │   Slider UI          │           │  RuntimeOverrideKey    │
        │ dynamic binding      │           │ flow/step/detect/path  │
        └────────────┬─────────┘           └────────────┬───────────┘
                     │                                  │
                     └──────────────┬───────────────────┘
                                    ▼
                         ┌──────────────────────────┐
                         │  RuntimeOverrideMerger   │
                         │ DSL + override merge     │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │  EffectiveRuntimeParams  │
                         │ final detection config   │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │  PoseDetectionRouter     │
                         │ detect + mapper logic    │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │   PoseFlowEngine         │
                         │ state machine + flow     │
                         └────────────┬─────────────┘
                                      │
                                      ▼
                         ┌──────────────────────────┐
                         │     Coach Output         │
                         │ voice / UI feedback      │
                         └──────────────────────────┘
```

Minimal mental model:

```text
DSL → Runtime → Override → Detection → Explain
```

---

## Runtime Overrides

Runtime tuning is scoped by flow, step, detect key, and parameter path:

```kotlin
data class RuntimeOverrideKey(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val path: String
)
```

Example override paths:

```text
runtime.stabilityMs
runtime.emaAlpha
runtime.deadbandDegrees
runtime.angles.knee.hold.min
runtime.angles.hip.hold.max
```

The packaged flow JSON remains the source of truth. User tuning is applied as a runtime override layer and merged into effective runtime params for detection.

---

## Adaptive Auto Tuning

YogaFlow can observe numeric fail reasons and propose runtime tuning suggestions without mutating packaged flow JSON.

```text
numeric FAIL reason
        ↓
AutoTuningAdvisor.observeReason(...)
        ↓
recent scoped samples
        ↓
average actual angle
        ↓
AutoTuningSuggestion
        ↓
RuntimeOverrideStore apply action
```

Example fail reason:

```text
FAIL: knee=138.2 < min=145.0
```

Example suggestion:

```text
SUGGEST: knee.min 145.0 → 140.0 (9 samples, medium)
```

Suggestion confidence is currently based on sample count:

```text
15+ samples → high
8+ samples  → medium
5+ samples  → low
```

Apply behavior:

```text
Apply button / restart long-press
        ↓
applyLatestSuggestion()
        ↓
RuntimeOverrideStore.set(...)
        ↓
slider + detection update immediately
```

Design rules:

```text
Flow JSON is never mutated
suggestions are user-in-the-loop
runtime override is the only write target
suggestions are scoped by flow / step / detect
stability timing failures are ignored for angle tuning
```

Current limitation:

```text
confidence is sample-count based only
variance-based confidence is planned
multi-param apply is planned
```

---

## Product Experience

```text
Beginner Class
Mountain → Forward Fold → Twist → Squat → Bridge
```

- Platform: Android
- Runtime: On-device camera + pose + coach loop
- Coaching mode: live correction + flow progression
- Camera onboarding: setup panel, ready gating, stable auto-start
- Visual feedback: body framing box + fixed guide frame
- Debug mode: live pose angle / state / matched overlay
- Flow language: JSON Flow DSL v2
- Detection runtime: `DetectKey + RuntimeParams`
- Privacy: no camera frames or pose landmarks need to leave the device

---

## Core Architecture

```text
CameraPosePipeline
        ↓
PoseDetectionResult
        ↓
PoseGeometry
        ↓
LiveCoachSessionController
        ↓
PoseDetectionRouter
        ↓
Pose-specific Detection Mapper
        ↓
PoseFlowEngine
        ↓
CoachCueController
        ↓
CoachSpeaker / LlmCoach / TTS
```

### Flow Runtime

```text
FlowLoader
        ↓
FlowJsonValidator
        ↓
FlowParser
        ↓
YogaFlow / YogaFlowStep
        ↓
FlowValidator
        ↓
PoseFlowEngine
```

### Strict Detection Runtime

```text
JSON detect string
        ↓
DetectKey enum
        ↓
RuntimeParams typed model
        ↓
RuntimeOverrideStore
        ↓
EffectiveRuntimeParams
        ↓
Strict mapper evaluation
```

---

## Flow Files

Production flows live in:

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

Demo flows may live in:

```text
flows/
```

---

## Detection Mappers

Pose-specific mappers evaluate typed detect keys and typed runtime params.

```text
ForwardFoldDetectionMapper
SquatDetectionMapper
TwistDetectionMapper
BridgeDetectionMapper
```

Mapper contract:

```kotlin
fun evaluate(
    detect: DetectKey,
    frame: PoseDetectionResult,
    params: RuntimeParams
): Result
```

Strict policy:

```text
missing required runtime param → error
missing required angle param → error
unsupported detect in mapper → error
```

---

## Camera Onboarding

Before a class starts, YogaFlow checks:

```text
full-body framing
view orientation
camera readiness stability
```

When the user is ready and stable, the class can auto-start.

```text
CameraFramingCoach
ViewOrientation
Ready Gate
Auto-start Timer
```

Current implementation:

```text
MainActivity.handleCameraSetupFrame is the stable baseline.
CameraSetupController exists and is intended to own this logic after final integration.
```

---

## Debug Overlay

Debug mode displays pose, runtime, override data, fail reason, and tuning suggestions for explainability:

```text
pose id
detect key
coach state
matched / not matched
knee angle
hip angle
torso twist estimate
effective runtime params
active runtime overrides
numeric fail reason
auto tuning suggestion
```

Example:

```text
runtime=stab=650 ema=0.25 dead=3.0 | knee.hold.min=145 hip.hold.max=130
overrides=knee.hold.min=150
FAIL: knee=138.2 < min=145.0
SUGGEST: knee.min 145.0 → 140.0 (9 samples, medium)
```

This makes each detection result traceable to pose data, DSL runtime params, user tuning overrides, and adaptive suggestions.

---

## Privacy Model

YogaFlow is designed around on-device processing.

```text
camera frames stay on device
pose landmarks stay on device
flow execution is local
voice coaching can be local
```

---

## Development Notes

Recommended validation before merging flow changes:

```text
1. Validate all .flow.json files
2. Confirm FlowJsonValidator passes
3. Confirm FlowValidator passes
4. Run app and inspect debug overlay
5. Verify Apply button / restart long-press only writes runtime overrides
```

Safe refactor rule for MainActivity:

```text
Do not patch MainActivity with placeholder content.
When using GitHub content API, always fetch current SHA and write a complete Kotlin file.
Prefer local IDE refactor for controller integration.
```

---

## Roadmap

```text
Integrate CameraSetupController into MainActivity
CI validation for all flow JSON files
Runtime override persistence
Multi-param tuning UI
Variance-based confidence
Multi-param suggestion apply
Flow Editor UI
DSL v3 constraint expressions
```

Example DSL direction:

```text
knee > 145 && hip in 50..130
```

---

## Status

```text
JSON DSL v2
DetectKey enum
RuntimeParams typed model
Strict mappers
Flow-level defaults
Runtime override store
Dynamic tuning UI
Debug explainability
Numeric fail reason
Auto tuning advisor
Sample-count confidence
Apply suggestion action
CoachCueController integrated
LiveCoachSessionController integrated
CameraSetupController exists, pending MainActivity integration
```
