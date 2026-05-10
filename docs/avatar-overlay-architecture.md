# Avatar Overlay Architecture

## Goal

Define how the 3D coach avatar is placed, moved, animated, and controlled inside YogaFlow 3D.

This document resolves the core UI decision:

```text
Android owns screen layout and overlay stacking.
Godot owns avatar placement and movement inside the avatar overlay.
Kotlin owns coach decisions and sends semantic avatar intent.
```

The normal coach experience must not move the Android `View`, `GodotFragment`, or avatar container around the screen. The avatar itself moves inside the Godot overlay.

---

## Architectural Rule

```text
Do not move the Android avatar view for normal coach movement.
Move the avatar node inside Godot.
```

Correct responsibility split:

```text
Camera / Pose / Flow / Coach decision = Android Kotlin
Screen layout / overlay stacking = Android View hierarchy
Avatar render / animation / position / highlight = Godot
Avatar model / rig production = Blender / GLB asset pipeline
```

---

## Screen Composition

Recommended Android hierarchy:

```text
MainActivity root
  ├─ CameraPreview
  ├─ PoseOverlayView
  ├─ GodotFragment / AvatarCoachOverlay   fixed overlay layer
  ├─ Coach cue text / debug overlay
  └─ Controls
```

The `GodotFragment` or Godot host view may be full-screen or constrained to a stable region, but it should not be animated every time the avatar changes position.

Preferred first version:

```text
Godot avatar overlay = full-screen transparent or visually composited layer
Avatar character = movable Node3D / Control inside Godot
```

---

## Why Not Move the Android View?

Moving the Android view for avatar behavior creates several problems:

- It mixes screen layout with character behavior.
- It makes camera preview, pose overlay, coach bubble, and avatar alignment harder to reason about.
- It makes future avatar actions such as pointing, turning, walking, highlighting, and demo positioning harder.
- It couples Android layout animation to Godot scene animation.
- It makes testing harder because character state is split between Android view transforms and Godot node transforms.

The avatar should behave like a 3D coach inside a stable stage, not like a draggable Android widget.

---

## Runtime Data Flow

```text
Android Kotlin
  Camera frame
  ↓
MediaPipe pose landmarks
  ↓
PoseGeometry / yoga detection mapper
  ↓
PoseFlowEngine / CoachCueController
  ↓
Avatar intent
  ↓
PoseCoachFrame JSON
  ↓
GodotAvatarBridge
  ↓
AvatarCoachOverlay.gd
  ↓
Avatar node moves / animates / highlights
```

Kotlin decides what the coach should communicate. Godot decides how the avatar body expresses it visually.

---

## PoseCoachFrame Avatar Contract

The avatar payload should carry semantic intent, not Android view coordinates.

Recommended payload:

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

Field meaning:

| Field | Owner | Meaning |
| --- | --- | --- |
| `action` | Kotlin decides, Godot executes | Animation or behavior state, for example `hold_forward_fold` or `correct_knees`. |
| `emotion` | Kotlin decides, Godot executes | Coach expression style, for example `calm`, `focused`, or `encouraging`. |
| `highlight` | Kotlin decides, Godot executes | Body area to visually mark, for example `knees`, `hips`, or `spine`. |
| `position` | Kotlin decides intent, Godot maps to coordinates | Semantic stage position, for example `left_side`, `right_side`, `near_knees`. |
| `facing` | Kotlin decides intent, Godot maps to rotation | Direction the avatar should face, for example `user`, `camera`, or `demo`. |
| `scale` | Kotlin may suggest, Godot clamps | Relative visual size inside the overlay. |

---

## Semantic Positions

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

Suggested interpretation:

| Position | Intended use |
| --- | --- |
| `left_side` | Coach stands beside the user without blocking the pose overlay. |
| `right_side` | Alternative side placement when the user occupies the left side. |
| `center` | Brief full-body demonstration. |
| `demo_area` | Default avatar demonstration area. |
| `near_knees` | Correction focused on knee alignment. |
| `near_hips` | Correction focused on hip hinge / pelvis movement. |
| `near_spine` | Correction focused on back or torso alignment. |

Godot owns the mapping from these names to actual scene coordinates.

---

## Kotlin-Side Implementation Target

Add or centralize avatar decision logic in a mapper such as:

```text
app/src/main/java/com/yogaflow/avatar/AvatarCoachStateMapper.kt
```

Suggested model:

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
    "knees" -> AvatarIntent(
        action = "correct_knees",
        emotion = "focused",
        highlight = "knees",
        position = "near_knees"
    )
    "hips" -> AvatarIntent(
        action = "correct_hips",
        emotion = "focused",
        highlight = "hips",
        position = "near_hips"
    )
    "spine" -> AvatarIntent(
        action = "correct_spine",
        emotion = "focused",
        highlight = "spine",
        position = "near_spine"
    )
    else -> AvatarIntent(
        action = "hold_forward_fold",
        emotion = "calm",
        highlight = null,
        position = "demo_area"
    )
}
```

Kotlin must not do this for normal coach movement:

```kotlin
avatarView.x = x
avatarView.y = y
godotFragmentView.translationX = x
godotFragmentView.translationY = y
```

---

## Godot-Side Implementation Target

`AvatarCoachOverlay.gd` or `AvatarController.gd` should apply the semantic avatar intent.

Suggested shape:

```gdscript
func apply_pose_coach_frame(frame: Dictionary) -> void:
    var avatar_payload = frame.get("avatar", {})

    var action = avatar_payload.get("action", "idle")
    var highlight = avatar_payload.get("highlight", null)
    var position_name = avatar_payload.get("position", "demo_area")
    var facing = avatar_payload.get("facing", "user")
    var scale = avatar_payload.get("scale", 1.0)

    play_action(action)
    apply_highlight(highlight)
    move_avatar_to(position_name)
    apply_facing(facing)
    apply_scale(scale)
```

Example Godot mapping:

```gdscript
func move_avatar_to(position_name: String) -> void:
    match position_name:
        "left_side":
            avatar.position = Vector3(-1.2, 0.0, 0.0)
        "right_side":
            avatar.position = Vector3(1.2, 0.0, 0.0)
        "center":
            avatar.position = Vector3(0.0, 0.0, 0.0)
        "demo_area":
            avatar.position = Vector3(1.0, 0.0, 0.0)
        "near_knees":
            avatar.position = Vector3(0.8, -0.5, 0.0)
        "near_hips":
            avatar.position = Vector3(0.8, 0.1, 0.0)
        "near_spine":
            avatar.position = Vector3(0.8, 0.5, 0.0)
        _:
            avatar.position = Vector3(1.0, 0.0, 0.0)
```

The actual coordinates should be tuned in Godot based on camera framing, avatar scale, and visual composition.

---

## Allowed Exception: Floating Widget Mode

Moving the Android view is only acceptable for a separate product mode such as:

```text
floating mini coach
picture-in-picture avatar
user-draggable coach widget
```

That mode is not the default YogaFlow live coach experience.

If implemented later, it should be explicitly named and isolated from the normal full-screen coach overlay architecture.

---

## First Implementation Scope

Implementation should start with named semantic positions and high-level actions only.

Do implement first:

```text
AvatarIntent data model
PoseCoachFrame avatar.position / facing / scale fields
AvatarCoachStateMapper
Godot move_avatar_to(position_name)
Godot action / highlight / position application
Forward fold correction mapping for knees / hips / spine
```

Do not implement first:

```text
raw per-frame Android view translation
raw screen coordinate streaming
full-body bone rotation streaming
IK retargeting from MediaPipe landmarks
user-draggable avatar view
```

---

## Acceptance Criteria

Done when:

```text
1. Godot overlay remains stable in Android layout.
2. Kotlin emits avatar intent through PoseCoachFrame.
3. Godot receives avatar.position and maps it to scene coordinates.
4. Avatar can move between demo_area, near_knees, near_hips, and near_spine without moving the Android view.
5. Avatar action and highlight still respond to coach correction state.
6. No normal coach behavior depends on Android View.translationX / translationY for avatar motion.
```

---

## Summary

The product should feel like a coach standing inside a stable visual stage.

```text
Android provides the stage.
Kotlin tells the coach what to do.
Godot moves and animates the coach body.
```
