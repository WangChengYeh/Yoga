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

## Angle Parameters

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

---

## Roadmap

Potential future parameters:

```text
confidence.min       # minimum landmark confidence
hold.frames          # frame-based stability acceptance
velocity.max         # maximum allowed movement speed
range.source         # user calibration source
```

These would extend Flow DSL from threshold tuning into a full real-time motion control DSL.
