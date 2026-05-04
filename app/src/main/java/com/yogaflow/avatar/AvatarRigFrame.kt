package com.yogaflow.avatar

data class Vec3(val x: Float, val y: Float, val z: Float)

data class BoneRotation(
    val bone: String,
    val direction: Vec3,
    val confidence: Float
)

data class AvatarRigFrame(
    val timestampMs: Long,
    val bones: List<BoneRotation>
)
