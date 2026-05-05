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
