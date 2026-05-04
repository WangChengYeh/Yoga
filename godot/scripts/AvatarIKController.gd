extends Node3D

# AvatarIKController receives AvatarRigFrame dictionaries from Kotlin and applies
# lightweight humanoid IK hints to a Mixamo-style Skeleton3D.
#
# Expected scene setup:
# AvatarIKController
# └─ CoachAvatar
#    └─ Armature
#       └─ Skeleton3D
#
# Optional target nodes can be created automatically at runtime.

@export var skeleton_path: NodePath = NodePath("CoachAvatar/Armature/Skeleton3D")
@export var smoothing: float = 0.25
@export var ik_weight: float = 0.8
@export var ground_lock_feet: bool = true
@export var foot_ground_y: float = 0.0
@export var min_confidence: float = 0.35
@export var max_rotation_step_degrees: float = 18.0
@export var min_joint_bend_degrees: float = 4.0
@export var max_joint_bend_degrees: float = 170.0
@export var pole_distance: float = 0.25

@onready var skeleton: Skeleton3D = get_node_or_null(skeleton_path)

var _bone_directions := {}
var _targets := {}
var _last_left_foot_y: float = 0.0
var _last_right_foot_y: float = 0.0
var _last_poles := {}

const MP_TO_GODOT_AXIS := Vector3(1.0, -1.0, -1.0)

const CHAINS := {
    "left_arm": {
        "upper": "LeftArm",
        "lower": "LeftForeArm",
        "end": "LeftHand",
        "target": "LeftHandIKTarget",
        "pole": "LeftElbowPole"
    },
    "right_arm": {
        "upper": "RightArm",
        "lower": "RightForeArm",
        "end": "RightHand",
        "target": "RightHandIKTarget",
        "pole": "RightElbowPole"
    },
    "left_leg": {
        "upper": "LeftUpLeg",
        "lower": "LeftLeg",
        "end": "LeftFoot",
        "target": "LeftFootIKTarget",
        "pole": "LeftKneePole"
    },
    "right_leg": {
        "upper": "RightUpLeg",
        "lower": "RightLeg",
        "end": "RightFoot",
        "target": "RightFootIKTarget",
        "pole": "RightKneePole"
    }
}

func _ready() -> void:
    if skeleton == null:
        push_warning("AvatarIKController: Skeleton3D not found at %s" % skeleton_path)
        return
    _ensure_targets()

func apply_avatar_rig_frame(frame: Dictionary) -> void:
    if skeleton == null:
        return
    if frame.get("type", "") != "avatar_rig_frame":
        return

    _bone_directions.clear()
    for bone_data in frame.get("bones", []):
        var bone_name := str(bone_data.get("bone", ""))
        var direction := bone_data.get("direction", {})
        var confidence := float(bone_data.get("confidence", 0.0))
        if confidence < min_confidence:
            continue
        var dir := Vector3(
            float(direction.get("x", 0.0)) * MP_TO_GODOT_AXIS.x,
            float(direction.get("y", 0.0)) * MP_TO_GODOT_AXIS.y,
            float(direction.get("z", 0.0)) * MP_TO_GODOT_AXIS.z
        )
        if dir.length() <= 0.0001:
            continue
        _bone_directions[bone_name] = dir.normalized()

    for chain_name in CHAINS.keys():
        _apply_chain_ik(chain_name, CHAINS[chain_name])

func _ensure_targets() -> void:
    for chain_name in CHAINS.keys():
        var chain = CHAINS[chain_name]
        _targets[chain["target"]] = _get_or_create_marker(chain["target"])
        _targets[chain["pole"]] = _get_or_create_marker(chain["pole"])

func _get_or_create_marker(node_name: String) -> Node3D:
    var existing := get_node_or_null(node_name)
    if existing != null and existing is Node3D:
        return existing
    var marker := Node3D.new()
    marker.name = node_name
    add_child(marker)
    return marker

func _apply_chain_ik(chain_name: String, chain: Dictionary) -> void:
    var upper_name: String = chain["upper"]
    var lower_name: String = chain["lower"]
    var end_name: String = chain["end"]

    if not _bone_directions.has(upper_name) or not _bone_directions.has(lower_name):
        return

    var upper_idx := skeleton.find_bone(upper_name)
    var lower_idx := skeleton.find_bone(lower_name)
    var end_idx := skeleton.find_bone(end_name)
    if upper_idx == -1 or lower_idx == -1 or end_idx == -1:
        return

    var upper_origin := skeleton.global_transform * skeleton.get_bone_global_pose(upper_idx).origin
    var lower_origin := skeleton.global_transform * skeleton.get_bone_global_pose(lower_idx).origin
    var end_origin := skeleton.global_transform * skeleton.get_bone_global_pose(end_idx).origin

    var upper_len := max(upper_origin.distance_to(lower_origin), 0.001)
    var lower_len := max(lower_origin.distance_to(end_origin), 0.001)

    var upper_dir: Vector3 = _bone_directions[upper_name]
    var lower_dir: Vector3 = _bone_directions[lower_name]
    var constrained := _constrain_chain_dirs(chain_name, upper_dir, lower_dir)
    upper_dir = constrained[0]
    lower_dir = constrained[1]

    var target_pos := upper_origin + upper_dir * upper_len + lower_dir * lower_len
    var pole_pos := lower_origin + _compute_pole_offset(upper_dir, lower_dir, chain_name)

    if ground_lock_feet and (chain_name == "left_leg" or chain_name == "right_leg"):
        target_pos.y = _apply_foot_ground_lock(chain_name, target_pos.y)

    _move_marker(chain["target"], target_pos)
    _move_marker(chain["pole"], pole_pos)

    _rotate_bone_toward(upper_idx, upper_dir, ik_weight)
    _rotate_bone_toward(lower_idx, lower_dir, ik_weight)

func _constrain_chain_dirs(chain_name: String, upper_dir: Vector3, lower_dir: Vector3) -> Array:
    upper_dir = upper_dir.normalized()
    lower_dir = lower_dir.normalized()

    var angle := rad_to_deg(acos(clamp(upper_dir.dot(lower_dir), -1.0, 1.0)))
    if angle < min_joint_bend_degrees:
        lower_dir = _nudge_bend(chain_name, upper_dir, lower_dir, min_joint_bend_degrees)
    elif angle > max_joint_bend_degrees:
        lower_dir = _nudge_bend(chain_name, upper_dir, lower_dir, max_joint_bend_degrees)

    lower_dir = _prevent_reverse_bend(chain_name, upper_dir, lower_dir)
    return [upper_dir.normalized(), lower_dir.normalized()]

func _nudge_bend(chain_name: String, upper_dir: Vector3, lower_dir: Vector3, target_degrees: float) -> Vector3:
    var pole := _stable_pole_normal(chain_name, upper_dir, lower_dir)
    var axis := upper_dir.cross(pole)
    if axis.length() <= 0.0001:
        axis = Vector3.RIGHT
    axis = axis.normalized()
    var q := Quaternion(axis, deg_to_rad(target_degrees))
    return q * upper_dir

func _prevent_reverse_bend(chain_name: String, upper_dir: Vector3, lower_dir: Vector3) -> Vector3:
    var pole := _stable_pole_normal(chain_name, upper_dir, lower_dir)
    var bend_side := upper_dir.cross(lower_dir).dot(pole)
    if bend_side >= 0.0:
        return lower_dir

    var projected := lower_dir - pole * lower_dir.dot(pole)
    if projected.length() <= 0.0001:
        return _nudge_bend(chain_name, upper_dir, lower_dir, min_joint_bend_degrees)
    return projected.normalized()

func _stable_pole_normal(chain_name: String, upper_dir: Vector3, lower_dir: Vector3) -> Vector3:
    var normal := upper_dir.cross(lower_dir)
    if normal.length() <= 0.0001:
        normal = _last_poles.get(chain_name, Vector3.FORWARD)
    normal = normal.normalized()

    var side := 1.0
    if chain_name.begins_with("right"):
        side = -1.0
    normal *= side

    var previous: Vector3 = _last_poles.get(chain_name, normal)
    if previous.dot(normal) < 0.0:
        normal = -normal
    normal = previous.lerp(normal, smoothing).normalized()
    _last_poles[chain_name] = normal
    return normal

func _compute_pole_offset(upper_dir: Vector3, lower_dir: Vector3, chain_name: String) -> Vector3:
    return _stable_pole_normal(chain_name, upper_dir, lower_dir) * pole_distance

func _apply_foot_ground_lock(chain_name: String, current_y: float) -> float:
    var locked_y := max(current_y, foot_ground_y)
    if chain_name == "left_leg":
        _last_left_foot_y = lerp(_last_left_foot_y, locked_y, smoothing)
        return _last_left_foot_y
    _last_right_foot_y = lerp(_last_right_foot_y, locked_y, smoothing)
    return _last_right_foot_y

func _move_marker(marker_name: String, target_pos: Vector3) -> void:
    var marker: Node3D = _targets.get(marker_name)
    if marker == null:
        return
    marker.global_position = marker.global_position.lerp(target_pos, smoothing)

func _rotate_bone_toward(bone_idx: int, target_dir: Vector3, weight: float) -> void:
    if target_dir.length() <= 0.0001:
        return

    var current_q := skeleton.get_bone_pose_rotation(bone_idx)
    var target_basis := Basis().looking_at(target_dir.normalized(), Vector3.UP)
    var target_q := target_basis.get_rotation_quaternion()
    var step := clamp(weight * smoothing, 0.0, 1.0)
    var max_step := deg_to_rad(max_rotation_step_degrees)
    var angle := current_q.angle_to(target_q)
    if angle > max_step and angle > 0.0001:
        step = min(step, max_step / angle)
    var blended := current_q.slerp(target_q, step)
    skeleton.set_bone_pose_rotation(bone_idx, blended)
