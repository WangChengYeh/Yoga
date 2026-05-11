extends Node3D

@onready var animation_tree: AnimationTree = _find_first_node_of_type(self, "AnimationTree") as AnimationTree
@onready var animation_player: AnimationPlayer = _find_first_node_of_type(self, "AnimationPlayer") as AnimationPlayer
@onready var skeleton: Skeleton3D = _find_first_node_of_type(self, "Skeleton3D") as Skeleton3D
@onready var key_light: DirectionalLight3D = $KeyLight
@onready var fill_light: OmniLight3D = $FillLight
@onready var avatar_node: Node3D = $female_yoga_coach

var current_action := ""
var _base_position := Vector3.ZERO
var _base_rotation := Vector3.ZERO
var _base_scale := Vector3.ONE
var _override_active: bool = false
var _override_x: float = 0.0
var _override_y: float = 0.0

var _semantic_offsets := {
    "left_side": Vector3(-1.45, 0.0, 0.0),
    "right_side": Vector3(1.45, 0.0, 0.0),
    "center": Vector3(0.0, 0.0, 0.0),
    "demo_area": Vector3(0.0, 0.0, 0.0),
    "near_knees": Vector3(0.0, -0.35, 0.32),
    "near_hips": Vector3(0.0, -0.14, 0.24),
    "near_spine": Vector3(0.0, 0.08, 0.18)
}

func _ready() -> void:
    print("AvatarController v3: semantic position/facing/scale support active")
    _base_position = avatar_node.position
    _base_rotation = avatar_node.rotation
    _base_scale = avatar_node.scale
    _setup_animations()

func _setup_animations() -> void:
    if animation_player == null:
        return

    var idle := Animation.new()
    idle.length = 4.0
    idle.loop_mode = Animation.LOOP_LINEAR
    var idle_scale_track := idle.add_track(Animation.TYPE_VALUE)
    idle.track_set_path(idle_scale_track, NodePath(".:scale"))
    idle.track_insert_key(idle_scale_track, 0.0, _base_scale)
    idle.track_insert_key(idle_scale_track, 2.0, Vector3(_base_scale.x, _base_scale.y * 1.015, _base_scale.z))
    idle.track_insert_key(idle_scale_track, 4.0, _base_scale)

    var forward_fold := Animation.new()
    forward_fold.length = 1.5
    forward_fold.loop_mode = Animation.LOOP_NONE
    var forward_fold_rotation_track := forward_fold.add_track(Animation.TYPE_VALUE)
    forward_fold.track_set_path(forward_fold_rotation_track, NodePath(".:rotation"))
    forward_fold.track_insert_key(forward_fold_rotation_track, 0.0, _base_rotation)
    forward_fold.track_insert_key(forward_fold_rotation_track, 1.5, Vector3(deg_to_rad(-35.0), _base_rotation.y, _base_rotation.z))

    var library := AnimationLibrary.new()
    library.add_animation("idle", idle)
    library.add_animation("forward_fold", forward_fold)
    animation_player.add_animation_library("", library)

func apply_pose_coach_frame(frame: Dictionary) -> void:
    var avatar = frame.get("avatar", {})
    var coach = frame.get("coach", {})
    var pose = frame.get("pose", {})
    var step_id = str(frame.get("stepId", ""))

    var action = avatar.get("action", "hold_mountain")
    var highlight = avatar.get("highlight", null)
    var severity = coach.get("severity", 0)
    var override_pos = avatar.get("override_position", null)

    if override_pos != null:
        set_override_position(float(override_pos.get("x", 0.0)), float(override_pos.get("y", 0.0)))
    else:
        # Keep override latched while self-test/normal frames keep streaming.
        # Only explicit adb_override frame without override_position clears it.
        if _override_active:
            if step_id == "adb_override":
                clear_override_position()
                move_avatar_to(str(avatar.get("position", "demo_area")))
        else:
            move_avatar_to(str(avatar.get("position", "demo_area")))

    apply_facing(str(avatar.get("facing", "user")))
    apply_scale(float(avatar.get("scale", 1.0)))
    play_action(action)
    apply_highlight(highlight, severity)
    apply_pose_metrics(pose)

func move_avatar_to(position_name: String) -> void:
    var offset: Vector3 = _semantic_offsets.get(position_name, _semantic_offsets["demo_area"])
    var target = _base_position + offset
    var tween = create_tween()
    tween.tween_property(avatar_node, "position", target, 0.35)

func apply_facing(facing: String) -> void:
    var target_y := _base_rotation.y
    match facing:
        "left_side":
            target_y = _base_rotation.y + deg_to_rad(-18.0)
        "right_side":
            target_y = _base_rotation.y + deg_to_rad(18.0)
        _:
            target_y = _base_rotation.y
    var tween = create_tween()
    tween.tween_property(avatar_node, "rotation:y", target_y, 0.3)

func apply_scale(scale_value: float) -> void:
    var clamped = clamp(scale_value, 0.8, 1.2)
    var target = _base_scale * clamped
    var tween = create_tween()
    tween.tween_property(avatar_node, "scale", target, 0.25)

func set_override_position(world_x: float, world_y: float) -> void:
    _override_active = true
    _override_x = world_x
    _override_y = world_y
    # Treat override as absolute semantic scene position so -1.5/0/+1.5
    # reliably maps left/center/right regardless of current base anchor.
    var target = Vector3(world_x, _base_position.y + world_y, _base_position.z)
    print("AvatarController override -> x=", world_x, " y=", world_y, " target=", target)
    var tween = create_tween()
    tween.tween_property(avatar_node, "position", target, 0.35)

func clear_override_position() -> void:
    _override_active = false

func apply_skin(skin_name: String) -> void:
    match skin_name:
        "nature":
            key_light.light_color = Color(0.75, 1.0, 0.65)
            fill_light.light_color = Color(0.55, 0.85, 0.45)
        "ocean":
            key_light.light_color = Color(0.65, 0.82, 1.0)
            fill_light.light_color = Color(0.45, 0.65, 1.0)
        _:
            key_light.light_color = Color.WHITE
            fill_light.light_color = Color(1.0, 0.80, 0.50)

func apply_pose_metrics(pose: Dictionary) -> void:
    if skeleton == null:
        return
    pass

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
        "walk_step_left":
            _set_animation_state("walk_step_left")
        "walk_step_right":
            _set_animation_state("walk_step_right")
        _ when action.begins_with("hold_"):
            _set_animation_state("idle")
        _:
            _set_animation_state("idle")

func _set_animation_state(state_name: String) -> void:
    if _try_play_animation(state_name):
        return
    _apply_fallback_pose(state_name)

func apply_highlight(highlight, severity: int) -> void:
    if skeleton == null:
        return
    var highlight_scale: float = clamp(float(severity) / 3.0, 0.0, 1.0)
    if highlight == null or highlight_scale <= 0.0:
        return

func _try_play_animation(state_name: String) -> bool:
    if animation_player != null:
        var animation_names := [
            state_name,
            "hold_" + state_name,
            state_name.capitalize().replace(" ", "_")
        ]
        for animation_name in animation_names:
            if animation_player.has_animation(animation_name):
                animation_player.play(animation_name)
                return true
    if animation_tree != null:
        animation_tree.active = true
        var playback = animation_tree.get("parameters/playback")
        if playback != null and playback.has_method("travel"):
            playback.travel(state_name)
            return true
    return false

func _apply_fallback_pose(state_name: String) -> void:
    var target_rotation := avatar_node.rotation
    var target_position := avatar_node.position
    match state_name:
        "forward_fold":
            target_rotation.x = deg_to_rad(-35.0)
            target_position.y -= 0.10
        "correct_knees":
            target_rotation.x = deg_to_rad(-20.0)
            target_rotation.z = deg_to_rad(8.0)
        "correct_hips":
            target_rotation.x = deg_to_rad(-28.0)
            target_position.z += 0.08
        "correct_spine":
            target_rotation.x = deg_to_rad(-12.0)
            target_rotation.z = deg_to_rad(-8.0)
        "squat":
            target_position.y -= 0.22
            target_rotation.x = deg_to_rad(-18.0)
        "twist":
            target_rotation.y += deg_to_rad(38.0)
        "walk_step_left":
            target_position.x -= 0.12
            target_position.y -= 0.04
            target_rotation.y += deg_to_rad(15.0)
        "walk_step_right":
            target_position.x += 0.12
            target_position.y -= 0.04
            target_rotation.y += deg_to_rad(-15.0)
    var tween = create_tween().set_parallel(true)
    tween.tween_property(avatar_node, "rotation", target_rotation, 0.5)
    tween.tween_property(avatar_node, "position", target_position, 0.5)

func _find_first_node_of_type(node: Node, type_name: String) -> Node:
    if node.is_class(type_name):
        return node
    for child in node.get_children():
        var found := _find_first_node_of_type(child, type_name)
        if found != null:
            return found
    return null
