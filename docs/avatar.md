# YogaFlow 3D Avatar — Design, Architecture, and Asset Pipeline

This is the consolidated avatar reference for YogaFlow 3D. It replaces the previously separate
`avatar-notes.md`, `avatar-overlay-architecture.md`, `avatar-rig-skeleton.md`, and
`godot-avatar-coach-overlay.md`.

Scope:

1. Product direction
2. Architecture & responsibility split
3. Android screen composition
4. PoseCoachFrame avatar contract
5. Semantic positions & `screen_side` auto-avoidance (#54)
6. Avatar skeleton standard (Mixamo-style)
7. MediaPipe-to-avatar mapping
8. Blender → Godot asset pipeline
9. Avatar source asset & licensing notes
10. Godot project structure & implementation
11. Android ↔ Godot communication
12. Implementation status
13. Non-goals for v1

---

## 1. Product Direction

The avatar is the visual body of an instructional coach overlaid on the live camera. Product
tagline:

```text
名模一對一教你
```

The first coach character follows a **Model-fit / Editorial Fitness** direction — closer to a
sportswear-ad model than a generic yoga teacher — while preserving enough professionalism for
movement credibility.

Recommended look:

- Female yoga coach
- Lean, athletic, model-fit proportions
- Clean shoulders / hips / knees / ankles / spine line for pose readability
- Sport bra top + high-waist leggings
- Barefoot
- Ponytail, tied hair, or short hair that does not cover the shoulders/neck
- Calm, focused, confident expression

Avoid:

- Bodybuilder-like proportions
- Unhealthy thinness
- Exaggerated proportions that hurt pose credibility
- Loose clothing that hides body alignment
- High-poly models unsuitable for mobile

Visual style mix:

```text
30% professional yoga coach
70% sportswear-ad / editorial fitness model
```

Color palette: black / dark grey / dark blue / muted neutrals. Logos and patterns are minimised
so the avatar reads as a clean reference body rather than competing with the user's pose.

Motion personality:

- Eased animation transitions, no instant snaps
- Subtle breathing motion in hold states
- Small natural weight shifts
- Soft hands and shoulders (no stiffness)
- Occasional head turn toward the user
- Correction states use clear demonstrations, not just pose swaps
- Overall pacing reads like "controlled movement" in a sportswear ad: slow, stable, lined

---

## 2. Architecture & Responsibility Split

Single rule:

```text
Kotlin = brain
Godot = coach body
Blender = model production / rig cleanup tool
Mixamo-style skeleton = rig standard
```

Kotlin / Android owns:

- Camera preview
- MediaPipe pose detection
- User skeleton overlay
- Pose angle / metric computation
- Step engine
- Coach decision
- Correction logic
- TTS / text cues

Godot owns:

- 3D avatar rendering
- Pose / animation playback and blending
- Optional IK adjustments
- Visual correction feedback (highlights)
- Avatar placement inside the avatar stage

Godot does **not** own: camera, pose detection, step engine, or primary coach decisions.

Architectural rule for screen movement:

```text
Do not move the Android avatar view for normal coach movement.
Move the avatar node inside Godot.
```

Why not move the Android view:

- It mixes screen layout with character behaviour
- It complicates camera preview / pose overlay / coach bubble alignment
- It couples Android layout animation to Godot scene animation
- It splits character state between Android view transforms and Godot node transforms
- It makes future avatar actions (pointing, turning, walking, highlighting, demo positioning)
  harder to extend

The avatar should behave like a 3D coach inside a stable visual stage — not like a draggable
Android widget. Moving the Android view is only acceptable for a separate product mode such as a
floating mini coach, PiP avatar, or user-draggable widget. Those modes are explicitly out of
scope for the default live-coach experience.

---

## 3. Android Screen Composition

Recommended hierarchy:

```text
MainActivity root
  ├─ CameraPreview
  ├─ PoseOverlayView
  ├─ GodotFragment / AvatarCoachOverlay   fixed overlay layer
  ├─ Coach cue text / debug overlay
  └─ Controls
```

The `GodotFragment` may be full-screen or constrained to a stable region, but should not be
animated every time the avatar changes position. Preferred first version:

```text
Godot avatar overlay = full-screen transparent or visually composited layer
Avatar character     = movable Node3D / Control inside Godot
```

Current shipped layout (corner PiP):

- `virtualCoachView` (GodotFragment) is a **110dp × 196dp** corner PiP
- Gravity: `bottom|end`
- Avatar centered inside the PiP (`_side_x_offset = 0.0`)
- Do not revert to `match_parent` for this view

```text
┌────────────────────────┐
│                        │
│   Camera View          │
│   + user skeleton      │
│                        │
│              ┌───────┐ │
│              │Godot  │ │
│              │Coach  │ │
│              │Avatar │ │
│              └───────┘ │
└────────────────────────┘
```

---

## 4. PoseCoachFrame Avatar Contract

The avatar payload carries semantic intent, not Android view coordinates.

Current shipped schema:

```json
{
  "timestampMs": 1710000000000,
  "stepId": "forward_fold_setup",
  "phase": "hold",
  "pose": {
    "leftKneeAngle": 168,
    "rightKneeAngle": 165,
    "hipAngle": 82,
    "spineAngle": 18,
    "ankleDistanceRatio": 1.7
  },
  "coach": {
    "state": "ok",
    "error": null,
    "message": "很好，停在這裡，保持呼吸。",
    "severity": 0
  },
  "avatar": {
    "action": "hold_forward_fold",
    "emotion": "calm",
    "highlight": null,
    "screen_side": "left"
  }
}
```

Correction example:

```json
{
  "coach": {
    "state": "needs_correction",
    "error": "knees_bent",
    "message": "我看到你的膝蓋有點彎，再打直一點點。",
    "severity": 2
  },
  "avatar": {
    "action": "correct_knees",
    "emotion": "focused",
    "highlight": "knees",
    "screen_side": "left"
  }
}
```

Forward-looking fields (recommended but not required in v1):

```json
{
  "avatar": {
    "position": "near_knees",
    "facing": "user",
    "scale": 1.0
  }
}
```

Field meaning:

| Field         | Owner                              | Meaning                                                                |
| ------------- | ---------------------------------- | ---------------------------------------------------------------------- |
| `action`      | Kotlin decides, Godot executes     | Animation / behaviour state, e.g. `hold_forward_fold`, `correct_knees` |
| `emotion`     | Kotlin decides, Godot executes     | Coach expression style: `calm`, `focused`, `encouraging`               |
| `highlight`   | Kotlin decides, Godot executes     | Body area to mark, e.g. `knees`, `hips`, `spine`                       |
| `screen_side` | Kotlin decides                     | `"left"` / `"right"` — opposite side of the user, see §5               |
| `position`    | Kotlin decides intent, Godot maps  | Semantic stage position, e.g. `left_side`, `near_knees`                |
| `facing`      | Kotlin decides intent, Godot maps  | Direction the avatar faces: `user`, `camera`, `demo`                   |
| `scale`       | Kotlin may suggest, Godot clamps   | Relative visual size inside the overlay                                |

Suggested Kotlin model for the intent layer:

```kotlin
data class AvatarIntent(
    val action: String,
    val emotion: String = "calm",
    val highlight: String? = null,
    val position: String = "demo_area",
    val facing: String = "user",
    val scale: Float = 1.0f
)
```

Example mapping:

```kotlin
when (correctionArea) {
    "knees" -> AvatarIntent("correct_knees", "focused", "knees", "near_knees")
    "hips"  -> AvatarIntent("correct_hips",  "focused", "hips",  "near_hips")
    "spine" -> AvatarIntent("correct_spine", "focused", "spine", "near_spine")
    else    -> AvatarIntent("hold_forward_fold", "calm", null,  "demo_area")
}
```

Kotlin must **not** do this for normal coach movement:

```kotlin
avatarView.x = x
avatarView.y = y
godotFragmentView.translationX = x
godotFragmentView.translationY = y
```

---

## 5. Semantic Positions & `screen_side` Auto-avoidance (#54)

Use named positions first. Do not start with raw screen coordinates.

Initial supported positions:

```text
left_side
right_side
center
demo_area
near_knees
near_hips
near_spine
```

| Position     | Intended use                                                            |
| ------------ | ----------------------------------------------------------------------- |
| `left_side`  | Coach stands beside the user without blocking the pose overlay          |
| `right_side` | Alternative side placement when the user occupies the left side         |
| `center`     | Brief full-body demonstration                                           |
| `demo_area`  | Default avatar demonstration area                                       |
| `near_knees` | Correction focused on knee alignment                                    |
| `near_hips`  | Correction focused on hip hinge / pelvis movement                       |
| `near_spine` | Correction focused on back or torso alignment                           |

Godot owns the mapping from these names to actual scene coordinates:

```gdscript
func move_avatar_to(position_name: String) -> void:
    match position_name:
        "left_side":   avatar.position = Vector3(-1.2, 0.0, 0.0)
        "right_side":  avatar.position = Vector3( 1.2, 0.0, 0.0)
        "center":      avatar.position = Vector3( 0.0, 0.0, 0.0)
        "demo_area":   avatar.position = Vector3( 1.0, 0.0, 0.0)
        "near_knees":  avatar.position = Vector3( 0.8,-0.5, 0.0)
        "near_hips":   avatar.position = Vector3( 0.8, 0.1, 0.0)
        "near_spine":  avatar.position = Vector3( 0.8, 0.5, 0.0)
        _:             avatar.position = Vector3( 1.0, 0.0, 0.0)
```

### `screen_side` — automatic avoidance

Before #54 the avatar overlapped the user's body. Each `PoseCoachFrame.avatar` now carries
`screen_side` (`"left"` or `"right"`); Godot places the avatar on the opposite side of the
detected user.

Android:

```kotlin
private fun humanScreenSide(frame: PoseDetectionResult): String {
    // PoseHelper already flipped x for the front camera, so this is already screen-space.
    val screenX = frame.imageLandmarks.getOrNull(0)?.x()   // landmark 0 = nose
        ?: ((frame.imageLandmarks.getOrNull(11)?.x() ?: 0.5f) +
            (frame.imageLandmarks.getOrNull(12)?.x() ?: 0.5f)) / 2f
    // user on the left → avatar goes right; user on the right → avatar goes left
    return if (screenX < 0.5f) "right" else "left"
}
```

The returned value is the **avatar's target side**, not where the user is.

Godot:

```gdscript
var _side_x_offset: float = 0.4

func apply_screen_side(side: String) -> void:
    match side:
        "left":  _side_x_offset = -0.6
        "right": _side_x_offset =  0.6
        _:       _side_x_offset =  0.0
    var tween = create_tween()
    tween.tween_property(self, "position:x",
        _base_position.x + _side_x_offset, 0.4)
```

Positive x is screen-right in Godot 3D coordinates. Current offset is ±1.4 Godot units; tune in
`apply_screen_side()` if more separation is needed.

Known limitation: when the user spans the full frame (forward fold, prone poses) some overlap on
either side is acceptable.

---

## 6. Avatar Skeleton Standard (Mixamo-style)

The repo defines its own internal avatar skeleton abstraction. The model can come from Blender,
Mixamo, MB-Lab, MakeHuman, or another source, but the runtime depends on this internal contract.

### Required bones (v1)

```text
Hips
Spine, Spine1, Spine2
Neck, Head

LeftShoulder, LeftArm, LeftForeArm, LeftHand
RightShoulder, RightArm, RightForeArm, RightHand

LeftUpLeg, LeftLeg, LeftFoot, LeftToeBase
RightUpLeg, RightLeg, RightFoot, RightToeBase
```

Optional future bones (not in v1): finger chains, eye/jaw bones, full facial rig.

### Required hierarchy

```text
Hips
├─ Spine
│  └─ Spine1
│     └─ Spine2
│        ├─ Neck
│        │  └─ Head
│        ├─ LeftShoulder
│        │  └─ LeftArm
│        │     └─ LeftForeArm
│        │        └─ LeftHand
│        └─ RightShoulder
│           └─ RightArm
│              └─ RightForeArm
│                 └─ RightHand
├─ LeftUpLeg
│  └─ LeftLeg
│     └─ LeftFoot
│        └─ LeftToeBase
└─ RightUpLeg
   └─ RightLeg
      └─ RightFoot
         └─ RightToeBase
```

### Kotlin abstraction

Package `app/src/main/java/com/yogaflow/avatar/` carries the canonical bone enum and name map:

```kotlin
enum class AvatarBone {
    HIPS, SPINE, SPINE1, SPINE2, NECK, HEAD,
    LEFT_SHOULDER, LEFT_ARM, LEFT_FORE_ARM, LEFT_HAND,
    RIGHT_SHOULDER, RIGHT_ARM, RIGHT_FORE_ARM, RIGHT_HAND,
    LEFT_UP_LEG, LEFT_LEG, LEFT_FOOT, LEFT_TOE_BASE,
    RIGHT_UP_LEG, RIGHT_LEG, RIGHT_FOOT, RIGHT_TOE_BASE
}

object MixamoBoneNames {
    val names = mapOf(
        AvatarBone.HIPS  to "Hips",
        AvatarBone.SPINE to "Spine",
        // ... full table
        AvatarBone.RIGHT_TOE_BASE to "RightToeBase"
    )
}

data class AvatarRigHint(
    val bone: AvatarBone,
    val targetDirection: FloatArray? = null,
    val weight: Float = 1f
)
```

`AvatarRigHint` is the placeholder for a future IK / bone-streaming layer. V1 does not stream
per-frame bone rotations.

### Optional future PoseCoachFrame extension

```json
{
  "avatarRig": {
    "standard": "mixamo-style-v1",
    "hints": [
      { "bone": "LEFT_LEG", "targetDirection": [0.0, -1.0, 0.1], "weight": 0.5 }
    ]
  }
}
```

Do not add `avatarRig` until the high-level action workflow is stable.

---

## 7. MediaPipe → Avatar Mapping

MediaPipe landmarks are a detection skeleton, not an avatar rig. The mapping layer translates
landmarks into avatar-oriented body concepts.

```text
0  Nose            → Head direction
11 LeftShoulder    → LeftShoulder
12 RightShoulder   → RightShoulder
13 LeftElbow       → LeftArm / LeftForeArm
14 RightElbow      → RightArm / RightForeArm
15 LeftWrist       → LeftHand
16 RightWrist      → RightHand

23 LeftHip         → LeftUpLeg
24 RightHip        → RightUpLeg
25 LeftKnee        → LeftLeg
26 RightKnee       → RightLeg
27 LeftAnkle       → LeftFoot
28 RightAnkle      → RightFoot
31 LeftFootIndex   → LeftToeBase
32 RightFootIndex  → RightToeBase
```

Derived centers:

```text
Hips center  = midpoint(LeftHip, RightHip)
Chest center = midpoint(LeftShoulder, RightShoulder)
Spine vector = Hips center → Chest center
Head vector  = Chest center → Nose / ear midpoint
```

V1 uses MediaPipe metrics to decide avatar **action**, not to drive bones directly:

```text
leftKneeAngle < threshold OR rightKneeAngle < threshold
  → avatar.action = correct_knees
  → avatar.highlight = knees

hipAngle not deep enough
  → avatar.action = correct_hips
  → avatar.highlight = hips

spineAngle rounded or misaligned
  → avatar.action = correct_spine
  → avatar.highlight = spine
```

---

## 8. Blender → Godot Asset Pipeline

Blender is the model production / rig cleanup tool. Runtime animation lives in Godot.

Blender responsibilities:

- Human model editing
- Clothing and proportions
- Armature inspection / cleanup
- Weight paint cleanup
- Rest pose normalization
- GLB export

Recommended first production flow:

```text
MakeHuman / MB-Lab / existing base mesh
  ↓
Blender: adjust to model-fit yoga coach body
  ↓
Blender: add or normalize Mixamo-style armature
  ↓
Blender: skinning / weight paint cleanup
  ↓
Blender: export GLB
  ↓
Godot: import GLB and wire animations
```

Do not sculpt a fully realistic human from zero for v1 — stable rig and clean silhouette matter
more than mesh detail.

### Performance budget

| Item          | Target                                          |
| ------------- | ----------------------------------------------- |
| Triangles     | 20k–50k                                         |
| Texture size  | 1K or 2K                                        |
| Mesh parts    | body / top / leggings / hair separated          |
| Runtime format| GLB                                             |
| Rest pose     | A-pose or T-pose                                |
| Bone style    | Mixamo-style humanoid                           |

### Blender export checklist

```text
1. Apply object scale and rotation.
2. Confirm armature hierarchy.
3. Confirm bone names match the standard.
4. Set rest pose to A-pose or T-pose.
5. Check shoulder and hip deformation.
6. Check knee and elbow bending.
7. Reduce mesh density if needed.
8. Export as glTF Binary (.glb).
```

Required pre-export checks:

```text
Pose:        A-pose or T-pose
Scale:       applied, final object scale = 1.0
Rotation:    applied
Feet:        on ground
Forward:     consistent with Godot import settings
Origin:      consistent, near feet center or hips (documented)
Armature:    bone names match the standard
Mesh:        bound to armature
Weights:     checked around shoulders, elbows, hips, knees, ankles
```

Export path:

```text
godot/assets/avatars/female_yoga_coach.glb
```

Godot import flags:

```text
animation/import = true
skins/use_named_skins = true
```

The GLB is imported as a `PackedScene`.

---

## 9. Avatar Source Asset & Licensing Notes

The current shipped avatar source is the **Ch47 character** from the CharacterCreator / ActorCore
pipeline.

- Skeleton: standard Mixamo rig with the `mixamorig1` bone prefix
- Textures: Diffuse, Normal, Specular, Glossiness, Emissive
- Material: uses the `KHR_materials_specular` extension
- Animations: GLB ships with **no embedded animations**; `idle` and `forward_fold` are added
  programmatically in Godot by `AvatarController.gd`
- License: **unknown — must be verified before shipping**

Current model-level limitations:

- No IK
- No per-bone correction highlight
- No Mixamo retarget yet

Adding new animations: either embed them in the GLB (Mixamo retargeting in Blender) or extend
`_setup_animations()` in `AvatarController.gd`.

---

## 10. Godot Project Structure & Implementation

Recommended layout:

```text
godot/
  project.godot
  scenes/
    AvatarCoachOverlay.tscn
    CoachAvatar.tscn
  scripts/
    AvatarCoachOverlay.gd
    AvatarController.gd
    MixamoBoneMap.gd
    PoseCoachFrame.gd
  assets/
    avatars/
      female_yoga_coach.glb
    animations/
      idle_breathing.glb
      hold_forward_fold.glb
      correct_knees.glb
      correct_hips.glb
      correct_spine.glb
      hold_squat.glb
      hold_twist.glb
```

### `AvatarController.gd` surface

```text
play_action(action)         — pose animation / fallback tween
apply_screen_side(side)     — move avatar to opposite side of human (§5)
apply_highlight(bone, sev)  — visual correction feedback
apply_skin(name)            — Classic / Nature / Ocean lighting
```

Suggested skeleton:

```gdscript
extends Node3D

@onready var animation_tree: AnimationTree = $AnimationTree
@onready var skeleton: Skeleton3D = $CoachAvatar/Armature/Skeleton3D

var current_action := ""

func apply_pose_coach_frame(frame: Dictionary) -> void:
    var avatar = frame.get("avatar", {})
    var coach  = frame.get("coach", {})

    play_action(avatar.get("action", "hold_mountain"))
    apply_highlight(avatar.get("highlight", null), coach.get("severity", 0))
    apply_breathing(frame)
    apply_screen_side(avatar.get("screen_side", null))

func play_action(action: String) -> void:
    if action == current_action: return
    current_action = action
    match action:
        "hold_forward_fold": _set_animation_state("forward_fold")
        "correct_knees":     _set_animation_state("correct_knees")
        "correct_hips":      _set_animation_state("correct_hips")
        "correct_spine":     _set_animation_state("correct_spine")
        "hold_squat":        _set_animation_state("squat")
        "hold_twist":        _set_animation_state("twist")
        _:                   _set_animation_state("idle")

func _set_animation_state(state_name: String) -> void:
    pass # wire to AnimationTree state machine

func apply_highlight(highlight, severity: int) -> void:
    pass # simple material tint / marker mesh / outline

func apply_breathing(frame: Dictionary) -> void:
    pass # subtle torso / shoulder motion in hold states
```

### `MixamoBoneMap.gd`

```gdscript
extends Node

const BONE_NAMES = {
    "HIPS": "Hips", "SPINE": "Spine", "SPINE1": "Spine1", "SPINE2": "Spine2",
    "NECK": "Neck", "HEAD": "Head",
    "LEFT_SHOULDER": "LeftShoulder", "LEFT_ARM": "LeftArm",
    "LEFT_FORE_ARM": "LeftForeArm", "LEFT_HAND": "LeftHand",
    "RIGHT_SHOULDER": "RightShoulder", "RIGHT_ARM": "RightArm",
    "RIGHT_FORE_ARM": "RightForeArm", "RIGHT_HAND": "RightHand",
    "LEFT_UP_LEG": "LeftUpLeg", "LEFT_LEG": "LeftLeg",
    "LEFT_FOOT": "LeftFoot", "LEFT_TOE_BASE": "LeftToeBase",
    "RIGHT_UP_LEG": "RightUpLeg", "RIGHT_LEG": "RightLeg",
    "RIGHT_FOOT": "RightFoot", "RIGHT_TOE_BASE": "RightToeBase"
}
```

### GDScript dual-file sync rule

Any change to `godot/scripts/*.gd` MUST also be applied to `app/src/main/assets/scripts/*.gd`.
Both copies must stay identical. The `.gd.remap` files point to `.gd` source (not `.gdc`), so
the assets copy is what runs on device. `GodotScriptAssetSyncTest.kt` enforces this.

Why: Godot compiles `.gd` source to `.gdc` bytecode during export. Stale bytecode in the assets
directory caused real bugs; routing the remap files to source `.gd` avoided them, at the cost of
keeping both copies in sync.

---

## 11. Android ↔ Godot Communication

Final architecture is a **Hybrid Embedded** approach: GodotFragment + local-loopback WebSocket.

```text
Android Kotlin (MainActivity)
  └─ GodotAvatarBridge (OkHttp WebSocket Client)
       ↓ (127.0.0.1:9090 loopback)
FrameLayout / GodotFragment
  └─ Godot Engine (Embedded .pck)
       └─ AvatarCoachOverlay.gd (TCPServer / WebSocketServer)
```

Benefits:

- Android view hierarchy hosts the 3D engine without blocking the camera preview
- Zero network latency — communication is fully in-process on `127.0.0.1`
- Avoids the complex Godot Android Plugin (JNI) setup
- Kotlin and Godot stay decoupled while running natively in one APK

Kotlin-side per-frame loop (sketch):

```kotlin
fun onPoseDetected(landmarks: PoseLandmarks) {
    val pose = poseAnalyzer.computeAngles(landmarks)
    val step = stepEngine.currentStep
    val decision = coachDecisionEngine.evaluate(step, pose)

    skeletonOverlay.update(landmarks, decision)

    val frame = PoseCoachFrame(
        timestampMs = System.currentTimeMillis(),
        stepId = step.id,
        phase = step.phase,
        pose = pose,
        coach = decision.toCoachState(),
        avatar = decision.toAvatarCommand()
    )

    godotBridge.send(frame)
}
```

Godot-side per-frame handler (sketch):

```gdscript
func on_pose_coach_frame(frame):
    avatar_controller.play_action(frame.avatar.action)
    avatar_controller.apply_micro_motion(frame.pose)
    avatar_controller.set_highlight(frame.avatar.highlight, frame.coach.severity)
    avatar_controller.apply_screen_side(frame.avatar.screen_side)
```

---

## 12. Implementation Status

Shipped:

- Godot 4 avatar embedded as Android `GodotFragment`
- Corner PiP overlay (110dp × 196dp, bottom-end)
- Transparent Godot avatar composited over the camera layer
- Android↔Godot local WebSocket bridge (`127.0.0.1:9090`)
- `PoseCoachFrame` JSON contract with `action`, `emotion`, `highlight`, `screen_side`
- `humanScreenSide()` per-frame, opposite-side auto-positioning
- `AvatarController.gd`: `play_action`, `apply_screen_side`, `apply_highlight`, `apply_skin`
- Avatar self-test and ADB developer controls (see `docs/test-plan.md` §8–§9, §14)
- Selectable coach skins (Classic / Nature / Ocean)
- `GodotScriptAssetSyncTest.kt` to enforce dual-file sync
- Mixamo-style skeleton standard defined; Ch47 GLB ships with the app
- Programmatic `idle` and `forward_fold` animations in `AvatarController.gd`

Open / next:

- Replace Ch47 with a license-cleared, model-fit female yoga coach asset
- Add prepared animations: `correct_knees`, `correct_hips`, `correct_spine`, `hold_squat`,
  `hold_twist`, `idle_breathing`
- Materialise `position` / `facing` / `scale` in the PoseCoachFrame contract
- Implement `near_knees` / `near_hips` / `near_spine` correction positions in Godot
- Optional IK / `avatarRig.hints` layer once high-level actions are stable
- Per-bone correction highlight (currently severity-only material tint)

---

## 13. Non-Goals for V1

```text
Full-body bone rotation streaming every frame
Complete IK retargeting from MediaPipe to avatar
Highly realistic facial rig
Finger animation
User-draggable avatar view (PiP / floating widget mode)
Custom Blender runtime plugin
Raw per-frame Android view translation for avatar motion
Raw screen-coordinate streaming
```

V1 only needs to prove the experience:

```text
Camera pose detection stays stable.
Godot avatar appears beside the user.
Avatar demonstrates the current yoga action.
Avatar visibly reacts to correction states.
Avatar stays out of the user's body.
```

---

## Summary

```text
Android provides the stage.
Kotlin tells the coach what to do.
Godot moves and animates the coach body.
Blender produces the model; Mixamo-style skeleton is the rig contract.
```
