package com.yogaflow.flow

/**
 * Type-safe detect keys used by JSON Flow DSL v2.
 *
 * JSON uses snake_case strings. Kotlin runtime uses this enum.
 */
enum class DetectKey(val jsonKey: String) {
    // Mountain / generic
    STANDING_CENTERED("standing_centered"),
    SPINE_LENGTHENED("spine_lengthened"),
    MOUNTAIN_HOLD("mountain_hold"),
    READY_FOR_NEXT_POSE("ready_for_next_pose"),

    // Forward Fold
    READY_FORWARD_FOLD("ready_forward_fold"),
    TALL_SPINE_SETUP("tall_spine_setup"),
    HIP_HINGE("hip_hinge"),
    CONTROLLED_FORWARD_FOLD("controlled_forward_fold"),
    FORWARD_HOLD("forward_hold"),
    RETURN_STANDING("return_standing"),
    NEUTRAL_FINISH("neutral_finish"),

    // Twist
    STABLE_BASE("stable_base"),
    TWIST_START("twist_start"),
    TWIST_HOLD("twist_hold"),
    RETURN_CENTER("return_center"),

    // Squat
    SQUAT_SETUP("squat_setup"),
    SQUAT_DESCENT("squat_descent"),
    SQUAT_HOLD("squat_hold"),
    SQUAT_RETURN("squat_return"),

    // Bridge
    BRIDGE_SETUP("bridge_setup"),
    BRIDGE_LIFT("bridge_lift"),
    BRIDGE_HOLD("bridge_hold"),
    BRIDGE_RETURN("bridge_return");

    companion object {
        private val byJsonKey = entries.associateBy { it.jsonKey }

        fun fromJsonKey(value: String): DetectKey {
            return byJsonKey[value]
                ?: error("Invalid detect key '$value'. Expected one of: ${entries.joinToString { it.jsonKey }}")
        }

        fun isValidJsonKey(value: String): Boolean {
            return byJsonKey.containsKey(value)
        }

        fun jsonKeys(): Set<String> {
            return byJsonKey.keys
        }
    }
}
