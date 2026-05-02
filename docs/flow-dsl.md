# YogaFlow Flow DSL

YogaFlow Flow DSL is the runtime language for class content, step progression, coaching cues, and pose detection behavior.

The current DSL is **JSON-only DSL v2**.

```text
.flow.json
→ FlowJsonValidator        # JSON shape / type validation
→ FlowParser               # JSON → YogaFlow / YogaFlowStep
→ FlowValidator            # semantic DSL validation
→ PoseDetectionRouter      # typed detect + params dispatch
→ Detection Mapper         # pose-specific evaluation
→ PoseFlowEngine           # step progression
```

---

## Core Principle

```text
Flow = source of truth
Code = execution engine
```

Flow defines what to detect and which runtime parameters to use. Kotlin code only executes the detection engine.

---

## DSL v2 Only

```text
No .flow.txt
No fallback
No legacy flat angle keys
No detect string encoding
No ambiguity
```

All flow files must use:

```text
app/src/main/assets/flows/*.flow.json
```

---

## Runtime Contract

A flow step is parsed into:

```kotlin
data class YogaFlowStep(
    val state: CoachState,
    val durationMs: Long,
    val cue: String,
    val detect: String,
    val correction: String,
    val params: Map<String, Double> = emptyMap()
)
```

The runtime dispatch path is typed:

```text
currentStep.detect
currentStep.params
→ PoseDetectionRouter.evaluate(...)
→ Mapper.evaluate(detect, frame, params)
```

The old string format is removed:

```text
detect|angle.hip.hold.min=50    # removed
```

---

## Basic JSON Flow Structure

```json
{
  "version": "dsl-v2",
  "flow": {
    "id": "02_forward_fold_main",
    "name": "前屈 · Forward Fold",
    "pose": "forward_fold",
    "language": "zh-TW",
    "level": "beginner"
  },
  "steps": [
    {
      "state": "MOVEMENT",
      "durationMs": 5000,
      "cue": "維持膝蓋伸長，身體只到舒服的位置。",
      "detect": "controlled_forward_fold",
      "correction": "如果膝蓋彎了，先減少前傾深度，把腿重新伸長。",
      "runtime": {
        "angles": {
          "knee": {
            "fold": { "min": 150 }
          },
          "hip": {
            "fold": { "min": 55, "max": 135 }
          }
        },
        "stabilityMs": 300,
        "emaAlpha": 0.4,
        "deadbandDegrees": 3
      }
    }
  ],
  "end": {
    "cue": "完成，回到穩定呼吸。"
  }
}
```

---

## Step Fields

| JSON field | Kotlin field | Required | Description |
|---|---|---:|---|
| `state` | `state` | Yes | `SETUP`, `MOVEMENT`, `HOLD`, `TRANSITION`, `CORRECTION` |
| `durationMs` | `durationMs` | Yes | Step duration in milliseconds |
| `cue` | `cue` | Yes | Coaching cue |
| `detect` | `detect` | Yes | Detection key routed to mapper |
| `correction` | `correction` | Recommended | Correction cue |
| `runtime` | `params` | Required for validated detect keys | Detection thresholds and stability behavior |

---

## Runtime JSON → Runtime Params

The parser flattens JSON runtime values into typed params.

| JSON | Runtime param |
|---|---|
| `runtime.stabilityMs` | `stability.ms` |
| `runtime.emaAlpha` | `ema.alpha` |
| `runtime.deadbandDegrees` | `deadband.degrees` |
| `runtime.angles.hip.hold.min` | `angle.hip.hold.min` |
| `runtime.angles.knee.fold.min` | `angle.knee.fold.min` |

Example:

```json
{
  "runtime": {
    "angles": {
      "hip": {
        "hold": { "min": 50, "max": 130 }
      }
    },
    "stabilityMs": 650
  }
}
```

Becomes:

```text
params["angle.hip.hold.min"] = 50
params["angle.hip.hold.max"] = 130
params["stability.ms"] = 650
```

---

## Angle Key Model

JSON structure:

```text
runtime.angles.<joint>.<phase>.<bound>
```

Runtime param:

```text
angle.<joint>.<phase>.<bound>
```

| Segment | Supported values |
|---|---|
| joint | `knee`, `hip`, `twist` |
| phase | `ready`, `setup`, `hinge`, `fold`, `hold`, `return`, `neutral`, `start`, `center`, `descent`, `lift` |
| bound | `min`, `max` |

---

## Forward Fold Phase Table

| detect | phase | Required runtime angles |
|---|---|---|
| `ready_forward_fold` | `ready` | `knee.ready.min`, `hip.ready.min` |
| `tall_spine_setup` | `setup` | `knee.setup.min`, `hip.setup.min` |
| `hip_hinge` | `hinge` | `knee.hinge.min`, `hip.hinge.max` |
| `controlled_forward_fold` | `fold` | `knee.fold.min`, `hip.fold.min`, `hip.fold.max` |
| `forward_hold` | `hold` | `knee.hold.min`, `hip.hold.min`, `hip.hold.max` |
| `return_standing` | `return` | `knee.return.min`, `hip.return.min` |
| `neutral_finish` | `neutral` | `knee.neutral.min`, `hip.neutral.min` |

---

## Runtime Controls

### Stability

```json
"stabilityMs": 500
```

Controls how long a matched state must remain stable before the mapper accepts it.

### Smoothing

```json
"emaAlpha": 0.35
```

Controls angle smoothing.

| Value | Behavior |
|---:|---|
| `0.2` | More stable, slower response |
| `0.35` | Balanced |
| `0.6` | More responsive, more jitter-sensitive |

### Deadband

```json
"deadbandDegrees": 3
```

Ignores tiny angle changes below the configured degree threshold.

---

## Validation Pipeline

YogaFlow uses two validators.

### 1. FlowJsonValidator

Checks JSON shape, type, enum, and numeric ranges before parsing.

Examples it catches:

```json
"min": "abc"
```

```text
steps[3].runtime.angles.hip.fold.min must be a number
```

```json
"state": "MOVE"
```

```text
expected one of SETUP, MOVEMENT, HOLD, TRANSITION, CORRECTION
```

```json
"angles": { "hips": {} }
```

```text
angles.hips is not a supported joint
```

### 2. FlowValidator

Checks semantic requirements after parsing.

Example:

```json
{
  "detect": "forward_hold",
  "runtime": {
    "angles": {
      "hip": {
        "hold": { "min": 50 }
      }
    }
  }
}
```

Fails because `forward_hold` also requires:

```text
angle.knee.hold.min
angle.hip.hold.max
```

---

## Failure Policy

Invalid DSL should fail fast.

```text
Invalid JSON shape → FlowJsonValidator error
Invalid DSL semantics → FlowValidator error
```

No silent fallback is allowed.

---

## Flow Editor Schema

The strongly typed schema lives at:

```text
schemas/flow-editor.schema.json
```

Use this schema for:

```text
Flow Editor form generation
LLM-generated flow validation
pre-commit validation
CI validation
```

---

## Roadmap

Potential DSL v3 syntax:

```text
angle.hip.hold.range = 50..130
constraint.hold = knee > 145 && hip in 50..130
confidence.min
hold.frames
```
