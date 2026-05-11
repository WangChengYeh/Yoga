# YogaFlow 3D Roadmap

YogaFlow 3D has moved from a pose-detection demo into a product-level on-device AI coaching architecture.

This file is the **shipped-capability inventory** — a record of what is already in production. Active, near-term, and unfinished work lives in GitHub issues (`gh issue list --repo WangChengYeh/Yoga --state open`). Architecture details live in `docs/architecture.md`; verification details live in `docs/test-plan.md`.

## Finished

### Core Runtime

- CameraX + MediaPipe Pose pipeline.
- `PoseDetectionResult` with 2D image landmarks, 3D world landmarks, image size, and mirroring metadata.
- `PoseGeometry` 3D joint-angle calculation with 2D fallback.
- Camera setup/onboarding gate before session start.
- Manual camera toggle: camera setup stays idle until the user taps Camera ON.
- Session lifecycle states: `IDLE`, `RUNNING`, `PAUSED`, `COMPLETED`.
- Flow DSL v2 parser, JSON validator, typed `DetectKey`, runtime params, and flow validator.
- Flow playlist engine for multi-flow classes.
- Runtime override/tuning layer that does not mutate packaged Flow JSON.
- Auto tuning advisor that suggests threshold changes from numeric fail reasons.

### Detection And Coaching

- Explicit detection routing via `PoseDetectionRouter`.
- Mapper lifecycle ownership via `DetectionMapperSession`.
- Pose-specific detection mappers for core poses, including strict mountain detection.
- Coaching priority: camera framing, body orientation, then pose correction.
- Coach cue pacing with minimum interval and same-cue suppression.
- Rule-based fallback coach for reliable local operation.
- Local LLM prompt builder and phrase polishing path.
- TTS voice coaching.
- `LlmInteractionDb` logging for prompt/response/timing.

### Camera And Visual Feedback

- CameraX `RGBA_8888` deterministic image path.
- `STRATEGY_KEEP_ONLY_LATEST` backpressure.
- Bitmap rotation before MediaPipe inference.
- Single `ImageProxy` ownership handled by `PoseHelper`.
- `CameraFramingCoach` for too close, too far, offset, crop, unknown, and good states.
- `ViewOrientation` front-facing/off-axis/too-rotated analysis.
- `PoseOverlayView` skeleton overlay with FILL_CENTER coordinate mapping.
- Visual body framing box overlay.
- Joint angle deviation overlay and correction direction arrows.

### App / UX

- Multi-course home UI.
- Course selection and category filters.
- Session controls: Start, Pause, Restart.
- Flow index, step index, progress bar, countdown, and countdown voice cue.
- Full-screen landscape immersive mode.
- Debug/record controls collapsed into overflow row.
- Flow info panel repositioned for camera usability.
- Session completion screen.
- Session history with course names, weekly summary, dashboard, and per-course chart.
- Real beginner course cover image and styled gradient covers for other categories.

### Godot Avatar

- Godot 4 avatar embedded as Android `GodotFragment`.
- Corner PiP coach view: 110dp x 196dp, bottom-end.
- Transparent Godot avatar composited over the camera layer.
- Android-to-Godot local WebSocket bridge.
- Semantic avatar commands through `PoseCoachFrame`.
- Avatar auto-positioning to the opposite side of the detected human.
- Avatar self-test and ADB developer controls.
- Selectable coach skins.
- Godot source script mirroring tests.

### Content

- 20 packaged Flow DSL v2 files in `app/src/main/assets/flows/`.
- Packaged cues are zh-TW.
- Base pose coverage: mountain, forward_fold, twist, squat, bridge.
- Expanded pose coverage: warrior_1, warrior_2, downward_dog, child_pose, pigeon.
- Beginner, flexibility, recovery, and strength course categories.

### Testing And Documentation

- JVM tests for geometry, flow parsing, flow integration, routing, runtime flow completion, avatar contracts, cue pacing, phrase polishing, and prompt building.
- Device build/install/launch/screenshot smoke path verified locally.
- Architecture, flow DSL, avatar overlay, rig skeleton, test plan, proposal, environment setup, and project status docs exist.

## Active, Near-Term, and Unfinished Work

Tracked in GitHub issues only — this doc no longer mirrors a backlog.

```bash
GITHUB_TOKEN="" gh issue list --repo WangChengYeh/Yoga --state open --json number,title,labels
```

Filter by label for priority:

```bash
gh issue list --repo WangChengYeh/Yoga --state open --label P1
gh issue list --repo WangChengYeh/Yoga --state open --label P2
```

Unlabelled open issues are product-vision / backlog work without a current near-term plan.
