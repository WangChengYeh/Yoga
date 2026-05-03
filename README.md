# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D is a production-oriented on-device AI yoga coach for Android. It turns live camera frames into 3D pose geometry, maps the user’s body state to structured yoga flow steps, and gives real-time voice coaching through local LLM + TTS.

---

## ✨ Flow DSL

YogaFlow uses a **JSON-only, type-safe, strict Flow DSL**.

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

Removed:

```text
.flow.txt
legacy detect strings
Map<String, Double> runtime params
silent fallback defaults
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

## Product Milestone

```text
v0.3-camera-onboarding
Camera setup screen + ready gating + stable auto-start + visual framing box
```

This milestone adds a complete onboarding loop:

```text
Camera → Framing + Orientation → Setup Panel → Visual Framing Box → Ready Gate → Auto-start → FlowEngine
```

Previous milestone:

```text
v0.2-threshold-ui
Runtime threshold tuning + persistent user calibration
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
PoseDetectionRouter
        ↓
Pose-specific Detection Mapper
        ↓
PoseFlowEngine
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

Legacy `.flow.txt` files are removed and unsupported.

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

---

## Debug Overlay

Debug mode displays live runtime information such as:

```text
pose id
detect key
coach state
matched / not matched
knee angle
hip angle
torso twist estimate
```

This is intended for development, validation, and flow tuning.

---

## Privacy Model

YogaFlow is designed around on-device processing.

```text
camera frames stay on device
pose landmarks stay on device
flow execution is local
voice coaching can be local
```

The product principle is:

```text
自己的資訊，自己掌控。數據零離機，專業不妥協。
```

---

## Development Notes

Recommended validation before merging flow changes:

```text
1. Ensure no .flow.txt files exist.
2. Validate all .flow.json files.
3. Confirm FlowJsonValidator passes.
4. Confirm FlowValidator passes.
5. Run app and inspect debug overlay.
```

Suggested guard:

```bash
if git ls-files | grep -q '\.flow\.txt$'; then
  echo "ERROR: legacy .flow.txt files are not supported"
  exit 1
fi
```

---

## Roadmap

Potential next steps:

```text
CI validation for all flow JSON files
Debug overlay for merged RuntimeParams
Flow Editor UI
DSL v3 constraint expressions
```

Example DSL v3 direction:

```text
knee > 145 && hip in 50..130
```

---

## Status

```text
JSON-only DSL: complete
DetectKey enum: complete
RuntimeParams typed model: complete
Strict mappers: complete
Flow-level defaults: complete
Legacy .flow.txt removal: complete
```
