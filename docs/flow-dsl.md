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

`.flow.txt` is permanently removed.

```text
Supported:   *.flow.json
Unsupported: *.flow.txt
```

There is no legacy parser, no text-flow fallback, and no dual-source-of-truth behavior.

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

## DSL v2 Only

```text
No .flow.txt
No fallback
No legacy flat angle keys
No detect string encoding
No Map<String, Double> runtime params
No string detect in runtime
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

The old string-based runtime is removed:

```text
detect|angle.hip.hold.min=50        # removed
params["angle.hip.hold.min"]        # removed
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

Example:

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
      "runtime": {
        "stabilityMs": 650,
        "angles": {
          "hip": {
            "hold": { "min": 50, "max": 130 }
          }
        }
      }
    }
  ]
}
```

Becomes typed runtime access:

```kotlin
params.angles.hip.hold.min == 50.0
params.angles.hip.hold.max == 130.0
params.stabilityMs == 650L
params.emaAlpha == 0.35
params.deadbandDegrees == 3.0
```

---

## Angle Model

JSON structure:

```text
runtime.angles.<joint>.<phase>.<bound>
```

Kotlin access:

```text
params.angles.<joint>.<phase>.<bound>
```

| Segment | Supported values |
|---|---|
| joint | `knee`, `hip`, `twist` |
| phase | `ready`, `setup`, `hinge`, `fold`, `hold`, `return`, `neutral`, `start`, `center`, `descent`, `lift` |
| bound | `min`, `max` |

Because `return` is a Kotlin keyword, the typed property is:

```kotlin
params.angles.hip.returnPhase.min
params.angles.knee.returnPhase.min
```

---

## Required Runtime Params by Detect

### Forward Fold

| detect | Required runtime angles |
|---|---|
| `ready_forward_fold` | `knee.ready.min`, `hip.ready.min` |
| `tall_spine_setup` | `knee.setup.min`, `hip.setup.min` |
| `hip_hinge` | `knee.hinge.min`, `hip.hinge.max` |
| `controlled_forward_fold` | `knee.fold.min`, `hip.fold.min`, `hip.fold.max` |
| `forward_hold` | `knee.hold.min`, `hip.hold.min`, `hip.hold.max` |
| `return_standing` | `knee.return.min`, `hip.return.min` |
| `neutral_finish` | `knee.neutral.min`, `hip.neutral.min` |

### Squat

| detect | Required runtime angles |
|---|---|
| `squat_setup` | `knee.setup.min` |
| `squat_descent` | `knee.descent.min`, `knee.descent.max` |
| `squat_hold` | `knee.hold.min`, `knee.hold.max` |
| `squat_return` | `knee.return.min` |

### Twist

| detect | Required runtime angles |
|---|---|
| `stable_base` | `twist.center.max` |
| `twist_start` | `twist.start.min`, `twist.start.max` |
| `twist_hold` | `twist.hold.min`, `twist.hold.max` |
| `return_center` | `twist.center.max` |

### Bridge

| detect | Required runtime angles |
|---|---|
| `bridge_setup` | none |
| `bridge_lift` | `hip.lift.min`, `hip.lift.max` |
| `bridge_hold` | `hip.hold.min`, `hip.hold.max` |
| `bridge_return` | none |

---

## Required Runtime Controls

Strict mapper steps require these controls, usually inherited from `defaults.runtime`:

```json
"stabilityMs": 300,
"emaAlpha": 0.35,
"deadbandDegrees": 3
```

These map to:

```kotlin
params.stabilityMs
params.emaAlpha
params.deadbandDegrees
```

Mapper behavior is strict:

```text
missing required runtime param → error
missing required angle param → error
```

---

## Validation Pipeline

YogaFlow uses two validators before runtime execution.

### 1. FlowJsonValidator

Checks JSON shape, type, enum, and numeric ranges before parsing. It also validates `defaults.runtime`.

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

Checks semantic requirements after defaults and step runtime have been merged.

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
knee.hold.min
hip.hold.max
```

---

## Strict Runtime Policy

Invalid DSL should fail fast.

```text
Invalid JSON shape → FlowJsonValidator error
Invalid detect string → DetectKey parse error
Invalid DSL semantics → FlowValidator error
Missing mapper-required param → mapper error
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

## Current Runtime Guarantees

```text
0 .flow.txt files
0 string detect in runtime
0 Map<String, Double> runtime params
0 detect|k=v encoding
0 fallback defaults in strict mappers
```

The runtime contract is now:

```text
DetectKey + RuntimeParams → strict mapper evaluation
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

Potential next runtime model:

```text
DetectKey + sealed typed params per detect
```
