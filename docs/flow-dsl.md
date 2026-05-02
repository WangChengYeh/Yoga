# YogaFlow Flow DSL

YogaFlow uses `.flow.txt` files to define yoga class content, step progression, coaching cues, and runtime detection behavior.

DSL v2 is the only supported format.

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
Flow fully controls behavior.
```

---

## DSL v2 Only

```text
No fallback.
No legacy keys.
No ambiguity.
```

All parameters must use structured, phase-aware keys.

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

## DSL v2 Key Format

```text
angle.<joint>.<phase>.<bound>
```

| Segment | Example |
|---|---|
| joint | knee / hip / twist |
| phase | ready / setup / hinge / fold / hold / return / neutral |
| bound | min / max |

---

## Forward Fold Phase Table

| detect | phase |
|---|---|
| ready_forward_fold | ready |
| tall_spine_setup | setup |
| hip_hinge | hinge |
| controlled_forward_fold | fold |
| forward_hold | hold |
| return_standing | return |
| neutral_finish | neutral |

---

## Example — Forward Fold DSL v2

```text
[STEP 4]
state = MOVEMENT
detect = controlled_forward_fold

angle.knee.fold.min = 150
angle.hip.fold.min = 55
angle.hip.fold.max = 135

stability.ms = 300
ema.alpha = 0.4
deadband.degrees = 3
```

```text
[STEP 5]
state = HOLD
detect = forward_hold

angle.knee.hold.min = 145
angle.hip.hold.min = 50
angle.hip.hold.max = 130

stability.ms = 650
ema.alpha = 0.25
deadband.degrees = 3
```

---

## Runtime Parameters

```text
angle.*
stability.ms
ema.alpha
deadband.degrees
```

---

## Stability

```text
stability.ms = 500
```

---

## Smoothing

```text
ema.alpha = 0.35
```

---

## Deadband

```text
deadband.degrees = 3
```

---

## Design Principle

```text
Flow = source of truth
Code = execution engine
```

---

## Roadmap

```text
angle.hip.hold.range = 50..130
constraint.hold = knee > 145 && hip in 50..130
confidence.min
hold.frames
```
