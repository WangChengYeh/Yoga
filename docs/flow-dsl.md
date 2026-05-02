# YogaFlow Flow DSL

YogaFlow uses `.flow.txt` files to define yoga class content, step progression, coaching cues, and runtime detection behavior.

Starting from the runtime-parameterized Flow DSL, a flow step can control not only what the coach says, but also how pose detection is evaluated.

```text
Flow DSL
→ FlowParser
→ PoseDetectionRouter
→ Detection Mapper
→ Stability Layer
→ PoseFlowEngine
```

---

## Core Principle

```text
Flow defines WHAT to detect.
Code defines HOW to execute detection.
```

This keeps the runtime engine deterministic while allowing pose thresholds and stability behavior to be tuned without changing Kotlin code.

---

## DSL Versions

| Version | Style | Example | Status |
|---|---|---|---|
| DSL v1 | Flat runtime params | `angle.hip.min = 55` | Supported |
| DSL v2 | Phase-aware structured params | `angle.hip.fold.min = 55` | Preferred for multi-phase poses |

DSL v2 is backward compatible with DSL v1.

Resolution order:

```text
phase-aware key → generic key → mapper default
```

Example:

```text
angle.hip.hold.min → angle.hip.min → DEFAULT_HOLD_HIP_MIN
```

---

## Basic Flow Structure

```text
[FLOW]
id = 04_squat
name = 深蹲 · Squat
pose = squat
language = zh-TW
level = beginner

[STEP 1]
state = SETUP
duration_ms = 3000
cue = 雙腳站穩，腳尖自然朝前或微微外開，膝蓋對齊腳尖。
detect = squat_setup
correction = 先站穩，讓髖部、膝蓋和腳踝都進入畫面。

[END]
cue = 深蹲完成，很好。
```

---

## Step Fields

| Field | Required | Description |
|---|---:|---|
| `state` | Yes | One of `SETUP`, `MOVEMENT`, `HOLD`, `TRANSITION`, `CORRECTION` |
| `duration_ms` | Yes | Step duration used by `PoseFlowEngine` |
| `cue` | Yes | Main coaching cue |
| `detect` | Yes | Detection key dispatched to the pose mapper |
| `correction` | Recommended | Human-readable correction cue |

---

## Runtime Parameters

Runtime parameters are optional. If omitted, each mapper falls back to its built-in default values.

Supported parameter groups:

```text
angle.*
stability.ms
ema.alpha
deadband.degrees
```

---

## Angle Parameters — DSL v1

Angle parameters define acceptable pose geometry ranges at the flow-step level.

```text
angle.knee.min = 85
angle.knee.max = 110

angle.hip.min = 70
angle.hip.max = 140

angle.twist.min = 20
angle.twist.max = 60
```

Common usage:

| Parameter | Meaning |
|---|---|
| `angle.knee.min` | Lower bound for knee angle |
| `angle.knee.max` | Upper bound for knee angle |
| `angle.hip.min` | Lower bound for hip angle |
| `angle.hip.max` | Upper bound for hip angle |
| `angle.twist.min` | Lower bound for torso twist estimate |
| `angle.twist.max` | Upper bound for torso twist estimate |

Example:

```text
[STEP 3]
state = HOLD
duration_ms = 7000
cue = 停在穩定深蹲位置，胸口打開，保持呼吸。
detect = squat_hold
correction = 如果太低，往上回一點；如果太高，再慢慢下去一點。

angle.knee.min = 85
angle.knee.max = 110
```

---

## Angle Parameters — DSL v2 Phase-aware Keys

DSL v2 adds phase-aware keys for poses that reuse the same joint across multiple movement phases.

Generic DSL v1:

```text
angle.hip.min = 55
angle.hip.max = 135
```

Phase-aware DSL v2:

```text
angle.hip.fold.min = 55
angle.hip.fold.max = 135
```

The phase-aware version is preferred because it makes the intent explicit.

---

## DSL v2 Key Pattern

```text
angle.<joint>.<phase>.<bound> = value
```

Where:

| Segment | Example | Meaning |
|---|---|---|
| `joint` | `knee`, `hip`, `twist` | Geometry signal used by the mapper |
| `phase` | `hold`, `fold`, `return` | Semantic phase for the current detect key |
| `bound` | `min`, `max` | Lower or upper bound |

Examples:

```text
angle.knee.fold.min = 150
angle.hip.fold.min = 55
angle.hip.fold.max = 135
angle.hip.hold.min = 50
angle.hip.hold.max = 130
```

---

## Forward Fold Phase Table

Forward Fold currently supports the richest DSL v2 mapping.

| Detect key | Phase | Supported structured keys | Legacy fallback |
|---|---|---|---|
| `ready_forward_fold` | `ready` | `angle.knee.ready.min`, `angle.hip.ready.min` | `angle.knee.min`, `angle.hip.min` |
| `tall_spine_setup` | `setup` | `angle.knee.setup.min`, `angle.hip.setup.min` | `angle.knee.min`, `angle.hip.min` |
| `hip_hinge` | `hinge` | `angle.knee.hinge.min`, `angle.hip.hinge.max` | `angle.knee.min`, `angle.hip.max` |
| `controlled_forward_fold` | `fold` | `angle.knee.fold.min`, `angle.hip.fold.min`, `angle.hip.fold.max` | `angle.knee.min`, `angle.hip.min`, `angle.hip.max` |
| `forward_hold` | `hold` | `angle.knee.hold.min`, `angle.hip.hold.min`, `angle.hip.hold.max` | `angle.knee.min`, `angle.hip.min`, `angle.hip.max` |
| `return_standing` | `return` | `angle.knee.return.min`, `angle.hip.return.min` | `angle.knee.min`, `angle.hip.min` |
| `neutral_finish` | `neutral` | `angle.knee.neutral.min`, `angle.hip.neutral.min` | `angle.knee.min`, `angle.hip.min` |

---

## Forward Fold DSL v2 Example

```text
[STEP 4]
state = MOVEMENT
duration_ms = 5000
cue = 維持膝蓋伸長，身體只到舒服的位置。
detect = controlled_forward_fold
correction = 如果膝蓋彎了，先減少前傾深度，把腿重新伸長。

# Forward Fold DSL v2
angle.knee.fold.min = 150
angle.hip.fold.min = 55
angle.hip.fold.max = 135

# Runtime stability
stability.ms = 300
ema.alpha = 0.4
deadband.degrees = 3
```

```text
[STEP 5]
state = HOLD
duration_ms = 8000
cue = 停在這裡，肩膀放鬆，呼吸保持穩定。
detect = forward_hold
correction = 不需要再壓更深，先穩住呼吸。

# Forward Fold DSL v2
angle.knee.hold.min = 145
angle.hip.hold.min = 50
angle.hip.hold.max = 130

# More strict hold acceptance
stability.ms = 650
ema.alpha = 0.25
deadband.degrees = 3
```

---

## DSL v1 vs DSL v2 Example

Both examples are valid.

### DSL v1

```text
[STEP 5]
detect = forward_hold
angle.knee.min = 145
angle.hip.min = 50
angle.hip.max = 130
```

### DSL v2

```text
[STEP 5]
detect = forward_hold
angle.knee.hold.min = 145
angle.hip.hold.min = 50
angle.hip.hold.max = 130
```

Prefer DSL v2 for readability and future UI generation.

---

## Stability Parameters

### `stability.ms`

Defines how long a mapper result must remain matched before the step can be accepted.

```text
stability.ms = 500
```

Default behavior is mapper-specific. Existing mappers commonly use around `300ms`.

Use higher values for hold steps:

```text
[STEP 3]
state = HOLD
detect = squat_hold
stability.ms = 700
```

Use lower values for movement steps:

```text
[STEP 2]
state = MOVEMENT
detect = squat_descent
stability.ms = 200
```

---

## Smoothing Parameters

### `ema.alpha`

Controls exponential moving average smoothing for angle values.

```text
ema.alpha = 0.35
```

Guideline:

| Value | Behavior |
|---:|---|
| `0.2` | More stable, slower response |
| `0.35` | Balanced default |
| `0.6` | More responsive, more jitter-sensitive |

Example:

```text
[STEP 2]
state = MOVEMENT
detect = squat_descent
ema.alpha = 0.45
```

---

## Deadband Parameters

### `deadband.degrees`

Ignores tiny angle changes below the configured degree threshold.

```text
deadband.degrees = 3
```

Guideline:

| Value | Behavior |
|---:|---|
| `1` | Strict, sensitive |
| `2` | Balanced default |
| `4` | Stable, forgiving |

Example:

```text
[STEP 3]
state = HOLD
detect = bridge_hold
deadband.degrees = 4
```

---

## Full Example

```text
[STEP 3]
state = HOLD
duration_ms = 7000
cue = 穩住深蹲位置，保持呼吸。
detect = squat_hold
correction = 如果太低，往上回一點；如果太高，再慢慢下去一點。

# Angle control
angle.knee.min = 85
angle.knee.max = 110

# Stability control
stability.ms = 500

# Smoothing
ema.alpha = 0.45

# Jitter control
deadband.degrees = 3
```

---

## Recommended Presets

### HOLD Steps

```text
stability.ms = 500
ema.alpha = 0.25
deadband.degrees = 3
```

Goal: stable acceptance, less jitter.

### MOVEMENT Steps

```text
stability.ms = 200
ema.alpha = 0.45
deadband.degrees = 2
```

Goal: responsive feedback while the user is moving.

### Beginner Mode

```text
stability.ms = 300
ema.alpha = 0.35
deadband.degrees = 4
```

Goal: forgiving detection.

### Advanced Mode

```text
stability.ms = 700
ema.alpha = 0.25
deadband.degrees = 1
```

Goal: stricter, more controlled detection.

---

## Runtime Encoding

The parser internally encodes runtime parameters into the detect string before dispatch.

Flow input:

```text
detect = squat_hold
angle.knee.min = 85
angle.knee.max = 110
stability.ms = 500
```

Runtime representation:

```text
squat_hold|angle.knee.max=110.0|angle.knee.min=85.0|stability.ms=500.0
```

`PoseDetectionRouter` parses this into:

```kotlin
DetectionSpec(
    detect = "squat_hold",
    params = mapOf(
        "angle.knee.min" to 85.0,
        "angle.knee.max" to 110.0,
        "stability.ms" to 500.0
    )
)
```

Then the selected mapper evaluates the frame using the step-specific params.

---

## Backward Compatibility

Existing flow files remain valid.

This still works:

```text
detect = squat_hold
```

The mapper will use its default thresholds and stability settings.

This also works:

```text
detect = forward_hold
angle.hip.min = 50
```

For Forward Fold, DSL v2 keys override generic keys:

```text
detect = forward_hold
angle.hip.min = 55
angle.hip.hold.min = 50
```

Effective value:

```text
angle.hip.hold.min = 50
```

---

## Roadmap

Potential future parameters:

```text
confidence.min       # minimum landmark confidence
hold.frames          # frame-based stability acceptance
velocity.max         # maximum allowed movement speed
range.source         # user calibration source
```

Potential DSL v3 syntax:

```text
angle.hip.hold.range = 50..130
constraint.hold = knee > 145 && hip in 50..130
```

These would extend Flow DSL from threshold tuning into a full real-time motion control DSL.
