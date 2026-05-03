# YogaFlow Flow DSL

YogaFlow Flow DSL is the runtime language for class content, step progression, coaching cues, and pose detection behavior.

The current DSL is **JSON-only DSL v2** with a **type-safe strict runtime**.

```text
.flow.json
→ FlowJsonValidator        # JSON shape / type / enum validation
→ FlowParser               # JSON → YogaFlow / YogaFlowStep
→ DetectKey                # detect string → enum
→ RuntimeParams            # runtime JSON → typed params
→ FlowValidator            # semantic DSL validation
→ PoseDetectionRouter      # DetectKey + RuntimeParams dispatch
→ Detection Mapper         # strict pose-specific evaluation
→ PoseFlowEngine           # step progression
```

---

## Current Status

Supported format:

```text
*.flow.json
```

Production flows live under:

```text
app/src/main/assets/flows/*.flow.json
```

Optional demo flows may live under:

```text
flows/*.flow.json
```

---

## Core Principle

```text
Flow = source of truth
Code = execution engine
```

Flow defines what to detect and which runtime parameters to use. Kotlin code only executes the detection engine.

---

## DSL v2

```text
Type-safe runtime
No fallback
No ambiguity
```

---

## Runtime Contract

A flow step is parsed into a fully typed Kotlin model:

```kotlin
data class YogaFlowStep(
    val state: CoachState,
    val durationMs: Long,
    val cue: String,
    val detect: DetectKey,
    val correction: String,
    val params: RuntimeParams = RuntimeParams.EMPTY
)
```

Runtime params are typed:

```kotlin
data class RuntimeParams(
    val stabilityMs: Long? = null,
    val emaAlpha: Double? = null,
    val deadbandDegrees: Double? = null,
    val angles: AngleParams = AngleParams()
)
```

Angles are accessed by typed properties:

```kotlin
params.angles.hip.hold.min
params.angles.knee.fold.min
params.angles.twist.center.max
```

---

## Flow-level Default Runtime

Strict mappers require runtime controls:

```json
"stabilityMs": 300,
"emaAlpha": 0.35,
"deadbandDegrees": 3
```

To avoid repeating those values in every step, use flow-level defaults:

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

Step runtime is merged on top of defaults:

```text
step.runtime > defaults.runtime
```

Recommended pattern:

```json
{
  "defaults": {
    "runtime": {
      "stabilityMs": 300,
      "emaAlpha": 0.35,
      "deadbandDegrees": 3
    }
  },
  "steps": [
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
  ]
}
```

In this example:

```text
stabilityMs = 650       # step override
emaAlpha = 0.25         # step override
deadbandDegrees = 3     # inherited from defaults
```

For cue-only / generic flows that do not use strict mappers, omit `defaults.runtime` and omit step `runtime` entirely.

---

## Type-safe DetectKey

JSON uses snake_case strings:

```json
"detect": "forward_hold"
```

Runtime uses enum values:

```kotlin
DetectKey.FORWARD_HOLD
```

Examples:

| JSON detect | Kotlin enum |
|---|---|
| `ready_forward_fold` | `DetectKey.READY_FORWARD_FOLD` |
| `forward_hold` | `DetectKey.FORWARD_HOLD` |
| `twist_hold` | `DetectKey.TWIST_HOLD` |
| `squat_hold` | `DetectKey.SQUAT_HOLD` |
| `bridge_hold` | `DetectKey.BRIDGE_HOLD` |

Invalid detect strings fail during parsing.

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
  "defaults": {
    "runtime": {
      "stabilityMs": 300,
      "emaAlpha": 0.35,
      "deadbandDegrees": 3
    }
  },
  "steps": [
    {
      "state": "MOVEMENT",
      "durationMs": 5000,
      "cue": "維持膝蓋伸長，身體只到舒服的位置。",
      "detect": "controlled_forward_fold",
      "correction": "如果膝蓋彎了，先減少前傾深度，把腿重新伸長。",
      "runtime": {
        "emaAlpha": 0.4,
        "angles": {
          "knee": {
            "fold": { "min": 150 }
          },
          "hip": {
            "fold": { "min": 55, "max": 135 }
          }
        }
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
| `detect` | `DetectKey` | Yes | Type-safe detection key routed to mapper |
| `correction` | `correction` | Recommended | Correction cue |
| `runtime` | `RuntimeParams` | Required for strict mapper steps | Detection thresholds and optional runtime overrides |

---

## Runtime JSON → RuntimeParams

The parser converts JSON runtime values into typed Kotlin objects after merging defaults.

| JSON | Kotlin access |
|---|---|
| `defaults.runtime.stabilityMs` or `runtime.stabilityMs` | `params.stabilityMs` |
| `defaults.runtime.emaAlpha` or `runtime.emaAlpha` | `params.emaAlpha` |
| `defaults.runtime.deadbandDegrees` or `runtime.deadbandDegrees` | `params.deadbandDegrees` |
| `runtime.angles.hip.hold.min` | `params.angles.hip.hold.min` |
| `runtime.angles.knee.fold.min` | `params.angles.knee.fold.min` |
| `runtime.angles.twist.center.max` | `params.angles.twist.center.max` |

---

## Validation Pipeline

YogaFlow uses two validators before runtime execution.

### 1. FlowJsonValidator

Checks JSON shape, type, enum, and numeric ranges before parsing. It also validates `defaults.runtime`.

### 2. FlowValidator

Checks semantic requirements after defaults and step runtime have been merged.

---

## Strict Runtime Policy

Invalid DSL should fail fast.

```text
Invalid JSON shape → FlowJsonValidator error
Invalid detect string → DetectKey parse error
Invalid DSL semantics → FlowValidator error
Missing mapper-required param → mapper error
```

---

## Flow Editor Schema

The strongly typed schema lives at:

```text
schemas/flow-editor.schema.json
```

---

## Runtime Guarantees

```text
Type-safe runtime params
Enum-based detect keys
No silent fallback
```

---

## Roadmap

```text
constraint expressions
flow editor
runtime visualization
```
