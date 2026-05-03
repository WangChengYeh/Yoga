package com.yogaflow.pose

import com.yogaflow.coach.CoachState

data class DebugPoseInfo(
    val poseId: String,
    val detect: String,
    val state: CoachState,
    val matched: Boolean,
    val leftKneeAngle: Double?,
    val rightKneeAngle: Double?,
    val leftHipAngle: Double?,
    val rightHipAngle: Double?,
    val torsoTwistEstimate: Double?,
    val effectiveRuntimeSummary: String = "",
    val overrideSummary: String = "",
    val failReason: String = ""
) {
    fun toDisplayText(): String {
        return buildString {
            appendLine("DEBUG")
            appendLine("pose=$poseId")
            appendLine("detect=$detect")
            appendLine("state=$state matched=$matched")
            appendLine("L knee=${leftKneeAngle.fmt()} R knee=${rightKneeAngle.fmt()}")
            appendLine("L hip=${leftHipAngle.fmt()} R hip=${rightHipAngle.fmt()}")
            appendLine("twist=${torsoTwistEstimate.fmt()}")
            if (effectiveRuntimeSummary.isNotBlank()) appendLine("runtime=$effectiveRuntimeSummary")
            if (overrideSummary.isNotBlank()) appendLine("overrides=$overrideSummary")
            if (!matched && failReason.isNotBlank()) appendLine("FAIL: $failReason")
        }
    }

    private fun Double?.fmt(): String {
        return this?.let { "%.1f°".format(it) } ?: "--"
    }
}
