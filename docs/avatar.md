# YogaFlow 3D Avatar — Design, Architecture, and Asset Pipeline

This is the consolidated avatar reference for YogaFlow 3D. It replaces the previously separate
`avatar-notes.md`, `avatar-overlay-architecture.md`, `avatar-rig-skeleton.md`, and
`godot-avatar-coach-overlay.md`.

Scope:

1. Product direction
2. Architecture & responsibility split
3. Android screen composition
4. PoseCoachFrame avatar contract
5. Avatar positioning (full-screen overlay, free move, semantic shortcuts, ADB override)
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

Current shipped layout: the `GodotFragment` covers the **full screen** as a transparent overlay
on top of the camera preview and pose skeleton. The Android view is never animated; only the
avatar Node3D inside Godot moves.

```text
Godot avatar overlay = full-screen transparent layer (match_parent)
Avatar character     = movable Node3D inside Godot, tweened between positions
Android view tree    = stable; camera preview + pose overlay underneath
```

```text
┌────────────────────────┐
│                        │
│   Camera View          │
│   + user skeleton      │
│                        │
│      ┌──────┐          │  ← avatar is a Node3D placed anywhere
│      │ 3D   │             in the full-screen Godot scene; it
│      │Coach │             tweens from its current position to
│      │      │             a target position over ~0.35s.
│      └──────┘          │
│                        │
└────────────────────────┘
   Full-screen Godot overlay (transparent), avatar moves inside it.
```

The avatar is free to occupy any screen position. Movement from current to target is a Godot
tween — `tween_property(avatar_node, "position", target, 0.35)` — so transitions are smooth
regardless of where the avatar starts or ends.

Historical note: an earlier version used a 110dp × 196dp corner PiP pinned to `bottom|end`.
That layout was replaced by the full-screen overlay; the corresponding "do not revert to
match_parent" rule in AGENTS.md / GEMINI.md was removed when this doc shipped.

---

## 4. PoseCoachFrame Avatar Contract

The avatar payload carries semantic intent (and optionally explicit world coordinates), not
Android view coordinates.

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
    "position": "left_side",
    "facing": "user",
    "scale": 1.0
  }
}
```

Correction example:

```json
{
  "avatar": {
    "action": "correct_knees",
    "emotion": "focused",
    "highlight": "knees",
    "position": "near_knees",
    "facing": "user",
    "scale": 1.0
  }
}
```

Explicit override (ADB developer controls or any code path that needs a free target — bypasses
the semantic name):

```json
{
  "avatar": {
    "action": "hold_mountain",
    "position": "demo_area",
    "override_position": { "x": -1.5, "y": 0.0 }
  }
}
```

Field meaning:

| Field               | Owner                              | Meaning                                                                                                                                              |
| ------------------- | ---------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| `action`            | Kotlin decides, Godot executes     | Animation / behaviour state, e.g. `hold_forward_fold`, `correct_knees`                                                                               |
| `emotion`           | Kotlin decides, Godot executes     | Coach expression style: `calm`, `focused`, `encouraging`                                                                                             |
| `highlight`         | Kotlin decides, Godot executes     | Body area to mark, e.g. `knees`, `hips`, `spine`                                                                                                     |
| `position`          | Kotlin decides intent, Godot maps  | Semantic shortcut name (`left_side`, `right_side`, `center`, `demo_area`, `near_knees`, `near_hips`, `near_spine`). Resolves to a Vector3 offset.   |
| `override_position` | Kotlin (or ADB) sets explicit `{x, y}` world floats | Free target position. When present, supersedes the `position` semantic shortcut. Latched until an explicit clear frame. |
| `facing`            | Kotlin decides intent, Godot maps  | Direction the avatar faces: `user`, `left_side`, `right_side`                                                                                        |
| `scale`             | Kotlin may suggest, Godot clamps   | Relative visual size; Godot clamps to `[0.8, 1.2]`                                                                                                   |

`screen_side` (an earlier discrete `"left"` / `"right"` field) has been removed. The same intent
is now expressed by setting `position` to `left_side` / `right_side` directly, or by emitting an
explicit `override_position`.

Suggested Kotlin model:

```kotlin
data class AvatarIntent(
    val action: String,
    val emotion: String = "calm",
    val highlight: String? = null,
    val position: String = "demo_area",
    val overridePosition: Pair<Float, Float>? = null,
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

Even though the Godot overlay is full-screen, the Android view stays put. Avatar movement
happens inside the Godot scene only.

---

## 5. Avatar Positioning

The Godot overlay is full-screen. The avatar can occupy any screen position. Movement from the
current position to a target position is a smooth tween — every position change goes through
`tween_property(avatar_node, "position", target, 0.35)`, so transitions look natural regardless
of where the avatar starts or ends.

There are three layers of positioning, in order of precedence:

```text
override_position (raw {x, y} world coords) ← highest precedence, latched
  ↓ when absent
position (semantic name from a fixed shortcut table)
  ↓
demo_area default
```

### 5.1 Free move from any current to any target

The primitive is `set_override_position(world_x, world_y)`. It accepts arbitrary world
coordinates and tweens the avatar from wherever it currently is to the requested target.

```gdscript
func set_override_position(world_x: float, world_y: float) -> void:
    _override_active = true
    _override_x = world_x
    _override_y = world_y
    var target = Vector3(world_x, _base_position.y + world_y, _base_position.z)
    var tween = create_tween()
    tween.tween_property(avatar_node, "position", target, 0.35)
```

Once an override is set, it stays latched across subsequent frames until an explicit clear
frame arrives (`stepId == "adb_override"` with no `override_position` field). This keeps the
avatar where the developer / coach asked it to go, instead of being snapped back by every new
pose-coach frame.

ADB developer entry points (also documented in `docs/test-plan.md` §14):

```bash
adb shell am start -n com.yogaflow/.MainActivity \
  --ef avatarTargetX -1.5 \
  --ef avatarTargetY  0.0
adb shell am start -n com.yogaflow/.MainActivity --ez avatarClearOverride true
```

`avatarTargetX` / `avatarTargetY` get marshalled into an `override_position` field on the next
PoseCoachFrame. `avatarClearOverride true` emits the clear frame and returns the avatar to
semantic positioning.

### 5.2 Semantic shortcuts

For common stage placements, Kotlin sends `avatar.position = "<name>"` and Godot looks up an
offset from a fixed table:

```gdscript
var _semantic_offsets := {
    "left_side":   Vector3(-1.45, 0.00, 0.00),
    "right_side":  Vector3( 1.45, 0.00, 0.00),
    "center":      Vector3( 0.00, 0.00, 0.00),
    "demo_area":   Vector3( 0.00, 0.00, 0.00),
    "near_knees":  Vector3( 0.00,-0.35, 0.32),
    "near_hips":   Vector3( 0.00,-0.14, 0.24),
    "near_spine":  Vector3( 0.00, 0.08, 0.18)
}

func move_avatar_to(position_name: String) -> void:
    var offset: Vector3 = _semantic_offsets.get(position_name, _semantic_offsets["demo_area"])
    var target = _base_position + offset
    var tween = create_tween()
    tween.tween_property(avatar_node, "position", target, 0.35)
```

| Name         | Intent                                                                |
| ------------ | --------------------------------------------------------------------- |
| `left_side`  | Coach stands beside the user on the left of the screen                |
| `right_side` | Coach stands beside the user on the right of the screen               |
| `center`     | Brief full-body demonstration in the middle of the stage              |
| `demo_area`  | Default demonstration position (currently identical to `center`)      |
| `near_knees` | Correction focused on knee alignment — drops avatar slightly forward  |
| `near_hips`  | Correction focused on hip hinge / pelvis movement                     |
| `near_spine` | Correction focused on back or torso alignment                         |

Positive x is screen-right in Godot 3D coordinates. The semantic offset table is tunable in
`AvatarController.gd::_semantic_offsets`. Tune there if visual composition needs to change.

### 5.3 Choosing a side automatically

The high-level coach logic in Kotlin picks `left_side` or `right_side` based on where the user
is in the frame so the avatar steps aside instead of overlapping the user. This is just an
ordinary `position` value — there is no separate `screen_side` field anymore. Example:

```kotlin
// Shipped in com.yogaflow.avatar.AvatarPositioning.
fun oppositeSide(frame: PoseDetectionResult?): String {
    val landmarks = frame?.imageLandmarks ?: return "demo_area"
    val screenX = noseX(landmarks)                   // landmark 0 if visible
        ?: shoulderMidpointX(landmarks)              // (LM 11 + LM 12) / 2 if both visible
        ?: return "demo_area"
    return if (screenX < 0.5f) "right_side" else "left_side"
}
```

Visibility is gated by MediaPipe's per-landmark confidence (`landmark.visibility()`) — an
unreliable nose falls through to the shoulder midpoint, and an unreliable pair falls through to
`demo_area`. Returns the **avatar's target side**, not where the user is.

Known limitation: when the user spans the full frame (deep forward fold, prone poses) some
overlap is unavoidable on either side. Acceptable for v1.

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
play_action(action)                      — pose animation / fallback tween
move_avatar_to(position_name)            — tween to a semantic shortcut offset
set_override_position(world_x, world_y)  — tween to an arbitrary world coord (latches)
clear_override_position()                — release the latch; semantic positioning resumes
apply_facing(facing)                     — yaw the avatar toward a side / the user
apply_scale(scale_value)                 — uniform scale, clamped to [0.8, 1.2]
apply_highlight(bone, sev)               — visual correction feedback
apply_skin(name)                         — Classic / Nature / Ocean lighting
```

Shipped frame handler (simplified):

```gdscript
func apply_pose_coach_frame(frame: Dictionary) -> void:
    var avatar  = frame.get("avatar", {})
    var coach   = frame.get("coach", {})
    var step_id = str(frame.get("stepId", ""))

    var override_pos = avatar.get("override_position", null)
    if override_pos != null:
        set_override_position(float(override_pos.get("x", 0.0)),
                              float(override_pos.get("y", 0.0)))
    else:
        # Latch semantics: keep the override active across normal frames.
        # Only an explicit clear frame (stepId == "adb_override" with no
        # override_position) returns the avatar to semantic positioning.
        if _override_active:
            if step_id == "adb_override":
                clear_override_position()
                move_avatar_to(str(avatar.get("position", "demo_area")))
        else:
            move_avatar_to(str(avatar.get("position", "demo_area")))

    apply_facing(str(avatar.get("facing", "user")))
    apply_scale(float(avatar.get("scale", 1.0)))
    play_action(str(avatar.get("action", "hold_mountain")))
    apply_highlight(avatar.get("highlight", null), coach.get("severity", 0))
```

Action dispatch:

```gdscript
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

Godot-side per-frame handler (sketch — see §10 for the shipped version):

```gdscript
func on_pose_coach_frame(frame):
    avatar_controller.apply_pose_coach_frame(frame)
```

`apply_pose_coach_frame` is the single entry point on the Godot side. It owns dispatch to
`set_override_position` / `move_avatar_to`, `apply_facing`, `apply_scale`, `play_action`, and
`apply_highlight` based on the payload.

---

## 12. Implementation Status

Shipped:

- Godot 4 avatar embedded as Android `GodotFragment`
- Full-screen transparent Godot overlay composited over the camera and pose skeleton layers
- Avatar tweens smoothly from any current position to any target position
  (`tween_property(avatar_node, "position", target, 0.35)`)
- Three positioning layers: explicit `override_position {x, y}` (latched), `position` semantic
  shortcut name, and a `demo_area` default
- ADB developer controls — `avatarTargetX` / `avatarTargetY` floats and `avatarClearOverride`
  bool — for free-target placement and reset
- Android↔Godot local WebSocket bridge (`127.0.0.1:9090`)
- `PoseCoachFrame` JSON contract with `action`, `emotion`, `highlight`, `position`,
  `override_position`, `facing`, `scale`
- `AvatarController.gd` API: `play_action`, `move_avatar_to`, `set_override_position`,
  `clear_override_position`, `apply_facing`, `apply_scale`, `apply_highlight`, `apply_skin`
- Avatar self-test and ADB developer controls (see `docs/test-plan.md` §8–§9, §14)
- Selectable coach skins (Classic / Nature / Ocean)
- `GodotScriptAssetSyncTest.kt` to enforce dual-file sync
- Mixamo-style skeleton standard defined; Ch47 GLB ships with the app
- Programmatic `idle` and `forward_fold` animations in `AvatarController.gd`

Open / next: tracked in GitHub issues, not in this doc. Search open issues for avatar work
(`gh issue list --repo WangChengYeh/Yoga --state open` and look for the avatar-related entries).

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
