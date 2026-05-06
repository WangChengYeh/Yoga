package com.yogaflow.coach

import org.json.JSONObject

data class PoseMetrics(
    val leftKneeAngle: Double?,
    val rightKneeAngle: Double?,
    val hipAngle: Double?,
    val spineAngle: Double?,
    val ankleDistanceRatio: Double?
) {
    fun toJson(): JSONObject = JSONObject()
        .putNullable("leftKneeAngle", leftKneeAngle?.round1())
        .putNullable("rightKneeAngle", rightKneeAngle?.round1())
        .putNullable("hipAngle", hipAngle?.round1())
        .putNullable("spineAngle", spineAngle?.round1())
        .putNullable("ankleDistanceRatio", ankleDistanceRatio?.round2())
}

data class CoachVisualState(
    val state: String,
    val error: String?,
    val message: String,
    val severity: Int
) {
    fun toJson(): JSONObject = JSONObject()
        .put("state", state)
        .putNullable("error", error)
        .put("message", message)
        .put("severity", severity)
}

data class AvatarCommand(
    val action: String,
    val emotion: String,
    val highlight: String?,
    val screenSide: String = "right"
) {
    fun toJson(): JSONObject = JSONObject()
        .put("action", action)
        .put("emotion", emotion)
        .putNullable("highlight", highlight)
        .put("screen_side", screenSide)
}

data class PoseCoachFrame(
    val timestampMs: Long,
    val stepId: String,
    val phase: String,
    val pose: PoseMetrics,
    val coach: CoachVisualState,
    val avatar: AvatarCommand
) {
    fun toJson(): JSONObject = JSONObject()
        .put("timestampMs", timestampMs)
        .put("stepId", stepId)
        .put("phase", phase)
        .put("pose", pose.toJson())
        .put("coach", coach.toJson())
        .put("avatar", avatar.toJson())
}

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject {
    return put(name, value ?: JSONObject.NULL)
}

private fun Double.round1(): Double = kotlin.math.round(this * 10.0) / 10.0
private fun Double.round2(): Double = kotlin.math.round(this * 100.0) / 100.0
