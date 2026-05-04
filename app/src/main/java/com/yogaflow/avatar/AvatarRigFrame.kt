package com.yogaflow.avatar

import org.json.JSONArray
import org.json.JSONObject

data class Vec3(val x: Float, val y: Float, val z: Float) {
    fun toJson(): JSONObject = JSONObject()
        .put("x", x)
        .put("y", y)
        .put("z", z)
}

data class BoneRotation(
    val bone: String,
    val direction: Vec3,
    val confidence: Float
) {
    fun toJson(): JSONObject = JSONObject()
        .put("bone", bone)
        .put("direction", direction.toJson())
        .put("confidence", confidence)
}

data class AvatarRigFrame(
    val timestampMs: Long,
    val bones: List<BoneRotation>
) {
    fun toJson(): JSONObject = JSONObject()
        .put("type", "avatar_rig_frame")
        .put("timestampMs", timestampMs)
        .put("standard", "mixamo-style-v1")
        .put("bones", JSONArray().apply {
            bones.forEach { put(it.toJson()) }
        })
}
