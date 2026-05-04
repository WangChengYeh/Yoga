# Roadmap (Product-Level)

YogaFlow 3D has moved from a pose-detection demo into a product-level on-device AI coaching architecture.

---

## Completed

### Core System
- CameraX + MediaPipe Pose
- Flow DSL + parser
- Pose state machine
- Flow runtime engine
- Flow playlist engine for multi-flow classes
- Auto flow discovery from `assets/flows`

### Camera Pipeline
- `CameraPosePipeline` modular camera layer
- CameraX `RGBA_8888` deterministic image path
- `STRATEGY_KEEP_ONLY_LATEST` backpressure control
- Bitmap rotation before MediaPipe inference
- Single `ImageProxy` ownership handled by `PoseHelper`
- Camera lifecycle delegated out of `MainActivity`

### Pose / Geometry System
- `PoseDetectionResult` data model
  - 2D image landmarks
  - 3D world landmarks
  - image width / height
- `PoseGeometry` 3D joint-angle calculation
- 2D fallback with image width / height scaling
- Angle confidence states:
  - `HIGH_3D`
  - `LOW_2D_FALLBACK`
  - `INVALID`
- Fixed the old 2D projection distortion issue for knee / hip angles

### Camera Coaching
- `CameraFramingCoach`
  - full-body framing
  - too close / too far
  - left / right offset
  - top / bottom crop
  - unknown / low visibility state
- `ViewOrientation`
  - front-facing detection
  - off-axis detection
  - too-rotated detection
  - shoulder / hip depth-to-width ratio

### App Orchestration
- Complete `MainActivity` orchestration layer
- Full wiring completed:
  - `PoseHelper`
  - `PoseDetectionResult`
  - `CameraFramingCoach`
  - `ViewOrientation`
  - `PoseStateMachine`
  - `PoseFlowEngine`
  - `LlmCoach`
  - `TTS`
- Coaching priority implemented:
  1. camera framing
  2. body orientation
  3. pose correction
- Session lifecycle:
  - `IDLE`
  - `RUNNING`
  - `PAUSED`
  - `COMPLETED`

### App / UX
- Multi-course home UI
- Course selection
- Session control: Start / Pause / Restart
- Flow index + step index
- Progress bar
- Countdown display
- Countdown voice cue: 3, 2, 1
- UI animations:
  - progress
  - countdown
  - flow transition
- 2D skeleton overlay

### AI / Voice
- Local LLM coach via Gemma / MediaPipe GenAI
- Rule-based fallback coach
- Coach phrase polishing
- TTS voice coaching

### Documentation
- README updated to reflect 3D pose + camera coaching architecture
- `architecture.md` rewritten as product-level architecture with 3D + camera coaching diagram

---

## Remaining Product Work

### P0: Verify on device
- Run Gradle build
- Confirm MediaPipe `worldLandmarks()` availability with pinned dependency
- Test CameraX RGBA pipeline on target high-end Android phones
- Validate TTS + LLM fallback behavior during live camera inference

### P1: Product polish
- Replace cover drawable with real generated course images (#13)
- Add visual body framing box overlay
- Add clearer camera setup screen before class start
- Add voice pacing rules so repeated cues do not speak too often
- Add a compact virtual coach overlay in the camera view
- Add selectable virtual coach skins for different coach styles
- Build the first virtual coach with Canvas, not a full 3D engine

### P2: Content expansion
- Expand flow library to 10–30 flows
- Add more pose-specific geometry rules
- Add beginner / flexibility / recovery / strength categories

---

## Future

- Personalized coaching
- AI-generated flows
- YouTube-aligned class generation
- 100+ flow library
- Multi-model perception: Pose + Hand + Face
- Native 3D coach model with pose animations
- Downloadable coach skin packs
- Biomechanics scoring
- Progress tracking
- Apple Fitness+ style course cards and carousel UI
