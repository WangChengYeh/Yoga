extends Node3D

@onready var animation_tree: AnimationTree = _find_first_node_of_type(self, "AnimationTree") as AnimationTree
@onready var animation_player: AnimationPlayer = _find_first_node_of_type(self, "AnimationPlayer") as AnimationPlayer
@onready var skeleton: Skeleton3D = _find_first_node_of_type(self, "Skeleton3D") as Skeleton3D

var current_action := ""
var _base_position := Vector3.ZERO
var _base_rotation := Vector3.ZERO
var _base_scale := Vector3.ONE
var _breathing_enabled := false

func _ready() -> void:
    _base_position = position
    _base_rotation = rotation
    _base_scale = scale

func _process(_delta: float) -> void:
    if not _breathing_enabled:
        return
    var t := float(Time.get_ticks_msec()) / 1000.0
    var breath := sin(t * 2.0) * 0.015
    scale = Vector3(_base_scale.x, _base_scale.y * (1.0 + breath), _base_scale.z)

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
    if _try_play_animation(state_name):
        return
    _apply_fallback_pose(state_name)

func apply_highlight(highlight, severity: int) -> void:
    if skeleton == null:
        return
    var highlight_scale := clamp(float(severity) / 3.0, 0.0, 1.0)
    if highlight == null or highlight_scale <= 0.0:
        return
    # Keep the first version non-invasive: visible action comes from pose changes,
    # while future material or marker highlights can hook in here.

func apply_breathing(frame: Dictionary) -> void:
    var avatar = frame.get("avatar", {})
    var action := str(avatar.get("action", ""))
    _breathing_enabled = action.begins_with("hold_")

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
    var target_rotation := _base_rotation
    var target_position := _base_position
    match state_name:
        "forward_fold":
            target_rotation.x = deg_to_rad(-18.0)
            target_position.y = _base_position.y - 0.04
        "correct_knees":
            target_rotation.x = deg_to_rad(-10.0)
            target_rotation.z = deg_to_rad(4.0)
        "correct_hips":
            target_rotation.x = deg_to_rad(-14.0)
            target_position.z = _base_position.z + 0.04
        "correct_spine":
            target_rotation.x = deg_to_rad(-6.0)
            target_rotation.z = deg_to_rad(-4.0)
        "squat":
            target_position.y = _base_position.y - 0.10
            target_rotation.x = deg_to_rad(-8.0)
        "twist":
            target_rotation.y = deg_to_rad(18.0)
        _:
            target_rotation = _base_rotation
            target_position = _base_position
    rotation = rotation.lerp(target_rotation, 0.35)
    position = position.lerp(target_position, 0.35)

func _find_first_node_of_type(node: Node, type_name: String) -> Node:
    if node.is_class(type_name):
        return node
    for child in node.get_children():
        var found := _find_first_node_of_type(child, type_name)
        if found != null:
            return found
    return null
