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
- Course selection (Full / Stretch / Recovery / Strength categories)
- Session control: Start / Pause / Restart
- Flow index + step index
- Progress bar
- Countdown display
- Countdown voice cue: 3, 2, 1
- UI animations:
  - progress
  - countdown
  - flow transition
- 2D skeleton overlay with FILL_CENTER coordinate mapping (#65)
- Visual body framing box overlay (green/red border via PoseOverlayView)
- Voice pacing rules: 5s min between cues, 8s before same cue repeats
- Full-screen landscape mode — `resizeableActivity=false`, immersive sticky (#73)
- Manual camera toggle button (Camera: OFF/ON) — no auto-start (#70)
- Home screen class filters: Stretch→forward_fold, Recovery→bridge+twist, Strength→squat (#74, #75)
- Godot avatar corner PiP (110dp×196dp, bottom-right) — no camera overlap (#66)
- Transparent Godot avatar composited over live camera via TextureView + setZOrderOnTop (#83)
- Demo mode — avatar cycles poses without skeleton overlay (#90)
- Debug/record buttons collapsed into ⋮ overflow row
- Flow info panel repositioned to top-left; buttons reordered by user flow (#85, #84, #93)
- Real course cover image (cover_beginner.jpg) replacing XML placeholder (#13)

### AI / Voice
- Local LLM coach via Gemma / MediaPipe GenAI
- Rule-based fallback coach (active fallback — Gemma model requires manual device install, #79)
- Famous model coach one-on-one persona in PromptBuilder (#89)
- Coach phrase polishing
- TTS voice coaching
- `LlmInteractionDb` — SQLite logging of all LLM prompt/response pairs with timing (#69)

### Testing
- Unit tests: avatar rig, bone names, scene wiring (#86)
- Unit tests: MediaPipeAvatarMapper landmark bounds (#87)

### Content
- 15 flow JSON files in `assets/flows/` (01–15), dsl-v2, zh-TW, covering all 5 pose types (#58)

### Documentation
- README updated to reflect 3D pose + camera coaching architecture
- `architecture.md` rewritten as product-level architecture with 3D + camera coaching diagram
- `YogaFlow3D-Proposal.pdf` generated (#67, #88)

---

## Completed (P0–P2 all shipped)

### P0: Device verification
- ✔ Gradle build verified (JDK 17 via Homebrew)
- ✔ MediaPipe pose detection running on device
- ✔ CameraX RGBA pipeline confirmed
- ✔ Gemma LLM issue documented and closed (#79); rule-based fallback active

### P1: Product polish
- ✔ Replace cover drawable with real generated course images (#13)
- ✔ Add visual body framing box overlay
- ✔ Add camera setup screen / toggle before class start (#70)
- ✔ Add voice pacing rules (5s/8s intervals)
- ✔ Add Godot 3D avatar coach overlay (GodotFragment + WebSocket IPC)
- ✔ Avatar auto-positioning — moves to opposite side of detected human
- ✔ Selectable coach skins (Classic, Nature, Ocean)
- ✔ Joint angle deviation overlay — red/green visual feedback on skeleton (#94)
- ✔ Session completion screen — post-session summary (duration, poses, correction count) (#95)
- ✔ Session history — persist completion summaries to SQLite + history list overlay (#100, #102)
- ✔ Styled gradient cover cards for Stretch, Recovery, Strength (#103)

### P2: Content expansion
- ✔ Expand flow library to 15 flows
- ✔ Add pose-specific geometry rules for warrior_2, downward_dog, bridge, warrior_1, child_pose, pigeon, twist, mountain (#96, #98, #99)
- ✔ Beginner / flexibility / recovery / strength categories on home screen

---

## Completed (recent)
- ✔ Record course name per session in SessionHistoryDb (#104)
- ✔ Progress tracking dashboard — 統計 tab with summary + per-course bar chart (#106)
- ✔ Correction direction arrows — amber bisector arrows on deviated joints (#107)
- ✔ Geometry rules coverage audit (#108)
- ✔ Weekly summary bar — 本週 N 堂 · time · streak in history list (#105)

## Next Work

### P1
- Expand flow library from 15 → 30 flows (all categories)
- ROM baseline — track each user's joint range of motion over time

### P2
- Apple Fitness+ style home screen — carousel cards with course art
- Bilateral balance analysis — compare left vs right side angles per pose

---

## Future

- Personalized coaching (ROM baseline, fatigue sensing, bilateral balance analysis)
- AI-generated flows
- YouTube-aligned class generation — import video, extract coach skeleton, generate flow
- 100+ flow library
- Multi-model perception: Pose + Hand + Face
- Native 3D coach model with full pose animations
- Downloadable coach skin packs
- Biomechanics scoring and goal gap analysis
- Apple Fitness+ style course cards and carousel UI
