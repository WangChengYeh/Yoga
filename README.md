# YogaFlow 3D

> **自己的資訊，自己掌控。數據零離機，專業不妥協。**

YogaFlow 3D is a production-oriented on-device AI yoga coach for Android. It turns live camera frames into 3D pose geometry, maps the user’s body state to structured yoga flow steps, and gives real-time voice coaching through local LLM + TTS.

---

## ✨ Flow DSL (Runtime-Controlled)

YogaFlow now supports **runtime-configurable detection logic directly from flow files**.

Each step can define:

- pose angle thresholds (`angle.*`)
- stability window (`stability.ms`)
- smoothing (`ema.alpha`)
- jitter filtering (`deadband.degrees`)

👉 See full spec: `docs/flow-dsl.md`

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
- Tuning mode: runtime Squat / Bridge threshold sliders
- Calibration: persisted user thresholds via SharedPreferences
- Privacy: no camera frames or pose landmarks need to leave the device

... (rest unchanged)
