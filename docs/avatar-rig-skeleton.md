# Avatar Rig Skeleton: Blender to Godot Pipeline

## Goal

Build a human 3D coach avatar in Blender, standardize it with a Mixamo-style humanoid bone abstraction, export it as GLB, and integrate it into the existing Godot avatar coach overlay.

This document defines the model production pipeline, the internal avatar skeleton standard, MediaPipe-to-avatar mapping, Godot import rules, and implementation milestones.

The core architectural rule remains:

```text
Kotlin = brain
Godot = coach body
Blender = model production / rig cleanup tool
Mixamo-style skeleton = avatar rig standard
```

The existing Android/Kotlin app should continue to own camera, MediaPipe pose detection, pose metrics, step engine, coach decisions, TTS, and text cues. Godot should only render and animate the 3D coach avatar.

---

## High-Level Runtime Architecture

```text
Android / Kotlin
  Camera
  ↓
  MediaPipe Pose Landmarks
  ↓
  PoseGeometry / Yoga Rules
  ↓
  PoseCoachFrame JSON
  ↓
Godot Avatar Overlay
  ↓
  AvatarActionController
  ↓
  Mixamo-style Humanoid Skeleton
  ↓
  3D Coach Model
```

The first version should not stream full-body bone rotations every frame. It should use high-level avatar actions from Kotlin and let Godot play/blend prepared animations with optional IK and visual highlights.

---

## Blender Model Production Pipeline

### Role of Blender

Blender is used for:

- human model editing
- clothing and proportions
- armature inspection or cleanup
- weight paint cleanup
- rest pose normalization
- GLB export

Blender is not required to own the whole runtime animation system. Runtime animation should live in Godot.

### Recommended First Production Flow

```text
MakeHuman / MB-Lab / existing base mesh
  ↓
Blender: adjust model-fit yoga coach body
  ↓
Blender: add or normalize Mixamo-style armature
  ↓
Blender: skinning / weight paint cleanup
  ↓
Blender: export GLB
  ↓
Godot: import GLB and wire animations
```

Avoid sculpting a full realistic human from zero for the first version. The project needs a stable rig and clean silhouette more than ultra-realistic mesh detail.

### Avatar Visual Direction

The first coach avatar should follow the existing product direction:

```text
名模一對一教你
```

Recommended look:

- female yoga coach
- model-fit / editorial fitness body proportion
- healthy, lean, athletic shape
- clear shoulders, hips, knees, ankles, and spine line
- sport bra top
- high-waist leggings
- barefoot
- simple ponytail, tied hair, or short hair that does not cover shoulders/neck
- calm, focused, confident expression

Avoid:

- overly bodybuilder-like proportions
- unhealthy thinness
- exaggerated proportions that make yoga poses less credible
- loose clothing that hides body alignment
- high-poly model unsuitable for mobile

### Performance Budget

Recommended first-version asset targets:

| Item | Target |
|---|---:|
| Triangles | 20k-50k |
| Texture size | 1K or 2K |
| Mesh parts | body / top / leggings / hair separated if practical |
| Runtime format | GLB |
| Rest pose | A-pose or T-pose |
| Bone style | Mixamo-style humanoid |

---

## Mixamo-Style Humanoid Bone Abstraction

The repo should define its own stable internal avatar skeleton abstraction. The model can come from Blender, Mixamo, MB-Lab, MakeHuman, or another source, but the runtime should depend on this internal abstraction.

### Required Bone Names

The first version should support this core bone set:

```text
Hips
Spine
Spine1
Spine2
Neck
Head

LeftShoulder
LeftArm
LeftForeArm
LeftHand

RightShoulder
RightArm
RightForeArm
RightHand

LeftUpLeg
LeftLeg
LeftFoot
LeftToeBase

RightUpLeg
RightLeg
RightFoot
RightToeBase
```

Optional future bones:

```text
LeftHandThumb1 / LeftHandIndex1 / ...
RightHandThumb1 / RightHandIndex1 / ...
LeftEye / RightEye
Jaw
```

The first version should not depend on fingers, face bones, or facial rigging.

### Required Hierarchy

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

### Blender Rest Pose Requirements

Before export:

```text
Pose: A-pose or T-pose
Scale: applied, final object scale = 1.0
Rotation: applied
Feet: on ground
Model forward: consistent with Godot import settings
Origin: consistent, preferably near feet center or hips; document the chosen convention
Armature bone names: match required bone names
Mesh: bound to armature
Weights: checked around shoulders, elbows, hips, knees, ankles
```

Blender checklist:

```text
1. Apply object scale and rotation.
2. Confirm armature hierarchy.
3. Confirm bone names.
4. Set rest pose to A-pose or T-pose.
5. Check shoulder and hip deformation.
6. Check knee and elbow bending.
7. Reduce mesh density if needed.
8. Export as glTF Binary (.glb).
```

Recommended export path:

```text
godot/assets/avatars/female_yoga_coach.glb
```

---

## Kotlin-Side Avatar Abstraction

Add a package:

```text
app/src/main/java/com/yogaflow/avatar/
```

Recommended files:

```text
AvatarBone.kt
MixamoBoneNames.kt
MediaPipeAvatarMapper.kt
AvatarRigHint.kt
```

### AvatarBone.kt

```kotlin
package com.yogaflow.avatar

enum class AvatarBone {
    HIPS,
    SPINE,
    SPINE1,
    SPINE2,
    NECK,
    HEAD,

    LEFT_SHOULDER,
    LEFT_ARM,
    LEFT_FORE_ARM,
    LEFT_HAND,

    RIGHT_SHOULDER,
    RIGHT_ARM,
    RIGHT_FORE_ARM,
    RIGHT_HAND,

    LEFT_UP_LEG,
    LEFT_LEG,
    LEFT_FOOT,
    LEFT_TOE_BASE,

    RIGHT_UP_LEG,
    RIGHT_LEG,
    RIGHT_FOOT,
    RIGHT_TOE_BASE
}
```

### MixamoBoneNames.kt

```kotlin
package com.yogaflow.avatar

object MixamoBoneNames {
    val names = mapOf(
        AvatarBone.HIPS to "Hips",
        AvatarBone.SPINE to "Spine",
        AvatarBone.SPINE1 to "Spine1",
        AvatarBone.SPINE2 to "Spine2",
        AvatarBone.NECK to "Neck",
        AvatarBone.HEAD to "Head",

        AvatarBone.LEFT_SHOULDER to "LeftShoulder",
        AvatarBone.LEFT_ARM to "LeftArm",
        AvatarBone.LEFT_FORE_ARM to "LeftForeArm",
        AvatarBone.LEFT_HAND to "LeftHand",

        AvatarBone.RIGHT_SHOULDER to "RightShoulder",
        AvatarBone.RIGHT_ARM to "RightArm",
        AvatarBone.RIGHT_FORE_ARM to "RightForeArm",
        AvatarBone.RIGHT_HAND to "RightHand",

        AvatarBone.LEFT_UP_LEG to "LeftUpLeg",
        AvatarBone.LEFT_LEG to "LeftLeg",
        AvatarBone.LEFT_FOOT to "LeftFoot",
        AvatarBone.LEFT_TOE_BASE to "LeftToeBase",

        AvatarBone.RIGHT_UP_LEG to "RightUpLeg",
        AvatarBone.RIGHT_LEG to "RightLeg",
        AvatarBone.RIGHT_FOOT to "RightFoot",
        AvatarBone.RIGHT_TOE_BASE to "RightToeBase"
    )
}
```

### AvatarRigHint.kt

Full bone streaming is not required for the first version, but the data model should leave room for IK hints later.

```kotlin
package com.yogaflow.avatar

data class AvatarRigHint(
    val bone: AvatarBone,
    val targetDirection: FloatArray? = null,
    val weight: Float = 1f
)
```

For the first version, Kotlin should mainly send:

```text
avatar.action
avatar.emotion
avatar.highlight
coach.severity
pose metrics
```

This preserves the existing PoseCoachFrame design while enabling a future bone/IK layer.

---

## MediaPipe to Avatar Mapping

The existing app uses MediaPipe pose landmarks. These landmarks are a detection skeleton, not an avatar rig skeleton. The mapping layer translates MediaPipe landmarks into avatar-oriented body concepts.

### Landmark to Avatar Concept Mapping

```text
0  Nose              → Head direction
11 LeftShoulder      → LeftShoulder
12 RightShoulder     → RightShoulder
13 LeftElbow         → LeftArm / LeftForeArm
14 RightElbow        → RightArm / RightForeArm
15 LeftWrist         → LeftHand
16 RightWrist        → RightHand

23 LeftHip           → LeftUpLeg
24 RightHip          → RightUpLeg
25 LeftKnee          → LeftLeg
26 RightKnee         → RightLeg
27 LeftAnkle         → LeftFoot
28 RightAnkle        → RightFoot
31 LeftFootIndex     → LeftToeBase
32 RightFootIndex    → RightToeBase
```

### Derived Centers

```text
Hips center  = midpoint(LeftHip, RightHip)
Chest center = midpoint(LeftShoulder, RightShoulder)
Spine vector = Hips center → Chest center
Head vector  = Chest center → Nose or ear midpoint
```

### First-Version Runtime Usage

For version 1, use MediaPipe metrics to decide avatar action, not to drive every bone directly.

Example:

```text
leftKneeAngle < threshold OR rightKneeAngle < threshold
  → avatar.action = correct_knees
  → avatar.highlight = knees

hipAngle not deep enough
  → avatar.action = correct_hips
  → avatar.highlight = hips

spineAngle too rounded or misaligned
  → avatar.action = correct_spine
  → avatar.highlight = spine
```

---

## PoseCoachFrame Extension Strategy

The existing PoseCoachFrame JSON can stay mostly unchanged.

Current high-level payload style:

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
    "highlight": null
  }
}
```

Future optional extension:

```json
{
  "avatarRig": {
    "standard": "mixamo-style-v1",
    "hints": [
      {
        "bone": "LEFT_LEG",
        "targetDirection": [0.0, -1.0, 0.1],
        "weight": 0.5
      }
    ]
  }
}
```

Do not add `avatarRig` until the high-level action workflow is stable.

---

## Godot Project Structure

Recommended structure:

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

### MixamoBoneMap.gd

```gdscript
extends Node

const BONE_NAMES = {
    "HIPS": "Hips",
    "SPINE": "Spine",
    "SPINE1": "Spine1",
    "SPINE2": "Spine2",
    "NECK": "Neck",
    "HEAD": "Head",

    "LEFT_SHOULDER": "LeftShoulder",
    "LEFT_ARM": "LeftArm",
    "LEFT_FORE_ARM": "LeftForeArm",
    "LEFT_HAND": "LeftHand",

    "RIGHT_SHOULDER": "RightShoulder",
    "RIGHT_ARM": "RightArm",
    "RIGHT_FORE_ARM": "RightForeArm",
    "RIGHT_HAND": "RightHand",

    "LEFT_UP_LEG": "LeftUpLeg",
    "LEFT_LEG": "LeftLeg",
    "LEFT_FOOT": "LeftFoot",
    "LEFT_TOE_BASE": "LeftToeBase",

    "RIGHT_UP_LEG": "RightUpLeg",
    "RIGHT_LEG": "RightLeg",
    "RIGHT_FOOT": "RightFoot",
    "RIGHT_TOE_BASE": "RightToeBase"
}
```

### AvatarController.gd

```gdscript
extends Node3D

@onready var animation_tree: AnimationTree = $AnimationTree
@onready var skeleton: Skeleton3D = $CoachAvatar/Armature/Skeleton3D

var current_action := ""

func apply_pose_coach_frame(frame: Dictionary) -> void:
    var avatar = frame.get("avatar", {})
    var coach = frame.get("coach", {})

    var action = avatar.get("action", "hold_mountain")
    var highlight = avatar.get("highlight", null)
    var severity = coach.get("severity", 0)

    play_action(action)
    apply_highlight(highlight, severity)
    apply_breathing(frame)

func play_action(action: String) -> void:
    if action == current_action:
        return

    current_action = action

    match action:
        "hold_forward_fold":
            _set_animation_state("forward_fold")
        "correct_knees":
            _set_animation_state("correct_knees")
        "correct_hips":
            _set_animation_state("correct_hips")
        "correct_spine":
            _set_animation_state("correct_spine")
        "hold_squat":
            _set_animation_state("squat")
        "hold_twist":
            _set_animation_state("twist")
        _:
            _set_animation_state("idle")

func _set_animation_state(state_name: String) -> void:
    # Wire this to AnimationTree state machine playback.
    pass

func apply_highlight(highlight, severity: int) -> void:
    # First version can show simple material tint, marker mesh, or outline.
    pass

func apply_breathing(frame: Dictionary) -> void:
    # Add subtle torso/shoulder motion for hold states.
    pass
```

---

## Android to Godot Communication

### Phase 1: WebSocket Demo

Use WebSocket first to validate data flow and animation switching.

```text
Android Kotlin
  GodotAvatarBridge.send(PoseCoachFrame)
    ↓
WebSocket JSON
    ↓
Godot AvatarCoachOverlay.gd
```

Benefits:

- easier debugging
- Kotlin and Godot stay decoupled
- no Android View lifecycle blocking while validating the avatar behavior

### Phase 2: Godot Android Library / Plugin Bridge

After the WebSocket demo works, evaluate tighter Android integration:

```text
FrameLayout
 ├─ CameraPreviewView
 ├─ PoseOverlayView
 └─ GodotAvatarView
```

This matches the existing overlay direction. Godot should appear as a picture-in-picture coach first. Transparent overlay can come later.

---

## First Validation Pose: Forward Fold

Use forward fold as the first complete validation because the existing metrics already support it:

```text
leftKneeAngle
rightKneeAngle
hipAngle
spineAngle
ankleDistanceRatio
```

Required first actions:

```text
hold_forward_fold
correct_knees
correct_hips
correct_spine
```

Expected behavior:

```text
ok:
  Avatar holds forward fold with subtle breathing.

knees bent:
  Avatar plays correct_knees and highlights knees.

hips not folding enough:
  Avatar plays correct_hips and highlights hips.

spine alignment issue:
  Avatar plays correct_spine and highlights spine.
```

---

## Implementation Milestones

### Milestone 1: Skeleton Standard Document

Create this document and use it as the asset contract.

Done when:

```text
Any model that follows this document can be imported into Godot and used by AvatarController.
```

### Milestone 2: Blender Prototype Model

Output:

```text
godot/assets/avatars/female_yoga_coach.glb
```

Done when:

```text
Godot can import the GLB.
Skeleton3D exists.
Required bones can be found by name.
Idle animation can play.
```

### Milestone 3: Godot Avatar Controller

Add:

```text
AvatarController.gd
MixamoBoneMap.gd
AnimationTree state machine
```

Done when:

```text
action=hold_forward_fold → plays forward fold
action=correct_knees → plays knee correction
highlight=knees → highlights knees
```

### Milestone 4: Kotlin Avatar Abstraction

Add:

```text
app/src/main/java/com/yogaflow/avatar/AvatarBone.kt
app/src/main/java/com/yogaflow/avatar/MixamoBoneNames.kt
app/src/main/java/com/yogaflow/avatar/MediaPipeAvatarMapper.kt
```

Done when:

```text
Existing pose detection remains unchanged.
PoseCoachFrame still sends high-level avatar action/highlight.
The codebase has a stable place to add future avatar rig hints.
```

### Milestone 5: Android Overlay Integration

Done when:

```text
Camera preview works.
PoseOverlayView works.
Godot avatar overlay works.
Kotlin commands change Godot avatar animation.
```

---

## Non-Goals for Version 1

Do not implement these in the first version:

```text
full-body bone rotation streaming every frame
complete IK retargeting from MediaPipe to avatar
highly realistic facial rig
finger animation
transparent AR overlay
custom Blender runtime plugin
```

The first version should prove the product experience:

```text
Camera pose detection stays stable.
Godot avatar appears beside the user.
Avatar demonstrates the current yoga action.
Avatar visibly reacts to correction states.
```

---

## Summary

The correct first implementation is:

```text
Blender exports one clean female yoga coach GLB
  ↓
GLB follows Mixamo-style humanoid bone names
  ↓
Godot imports the GLB and maps required bones
  ↓
Kotlin keeps current MediaPipe / coach logic
  ↓
Kotlin sends PoseCoachFrame action/highlight/severity
  ↓
Godot plays/blends prepared animations and highlights correction areas
```

This keeps the existing Android app stable while adding a concrete 3D coach body that can evolve toward IK and more realistic motion later.
