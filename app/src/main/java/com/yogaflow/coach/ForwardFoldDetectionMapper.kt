package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry

object ForwardFoldDetectionMapper {

    data class Result(
        val matched: Boolean,
        val state: CoachState,
        val cue: String
    )

    fun evaluate(detect: String, frame: PoseDetectionResult): Result {
        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        val required = listOf(leftKnee, rightKnee, leftHip, rightHip)
        if (required.any { it.confidence == PoseGeometry.Confidence.INVALID }) {
            return Result(
                matched = false,
                state = CoachState.CORRECTION,
                cue = "我目前看不清楚你的膝蓋和髖部，請讓雙腿和上半身都進入畫面。"
            )
        }

        val knee = minOf(leftKnee.degrees, rightKnee.degrees)
        val hip = minOf(leftHip.degrees, rightHip.degrees)
        val confidence = required.map { it.confidence }.let { confidences ->
            if (confidences.any { it == PoseGeometry.Confidence.LOW_2D_FALLBACK }) {
                PoseGeometry.Confidence.LOW_2D_FALLBACK
            } else {
                PoseGeometry.Confidence.HIGH_3D
            }
        }
        val prefix = confidencePrefix(confidence)

        return when (detect) {
            "ready_forward_fold" -> readyForwardFold(knee, hip, prefix)
            "tall_spine_setup" -> tallSpineSetup(knee, hip, prefix)
            "hip_hinge" -> hipHinge(knee, hip, prefix)
            "controlled_forward_fold" -> controlledForwardFold(knee, hip, prefix)
            "forward_hold" -> forwardHold(knee, hip, prefix)
            "return_standing" -> returnStanding(knee, hip, prefix)
            "neutral_finish" -> neutralFinish(knee, hip, prefix)
            else -> Result(true, CoachState.HOLD, "維持姿勢")
        }
    }

    private fun readyForwardFold(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 155 -> Result(false, CoachState.CORRECTION, "${prefix}先把膝蓋伸長，但不要鎖死。")
            hip < 120 -> Result(false, CoachState.CORRECTION, "${prefix}先回到比較直立的位置，再準備前傾。")
            else -> Result(true, CoachState.SETUP, "${prefix}準備好了，雙腿伸長，身體保持穩定。")
        }
    }

    private fun tallSpineSetup(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 155 -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋再伸長一點，先建立穩定的腿。")
            hip < 115 -> Result(false, CoachState.CORRECTION, "${prefix}你已經太早往前了，先把背拉長一點。")
            else -> Result(true, CoachState.SETUP, "${prefix}很好，背拉長，胸口打開。")
        }
    }

    private fun hipHinge(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 150 -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多了，先減少深度，讓腿重新伸長。")
            hip > 140 -> Result(false, CoachState.MOVEMENT, "${prefix}從髖部再往前一點，不要只低頭。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}很好，正在從髖部前傾。")
        }
    }

    private fun controlledForwardFold(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 150 -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋開始彎了，退回一點，保持腿伸長。")
            hip > 135 -> Result(false, CoachState.MOVEMENT, "${prefix}再從髖部往前一點，到舒服的位置就好。")
            hip < 55 -> Result(false, CoachState.CORRECTION, "${prefix}深度太多了，先退回一點，不要硬壓。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}深度可以，保持控制，不要硬壓。")
        }
    }

    private fun forwardHold(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 145 -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多，退回一點，讓大腿後側慢慢伸展。")
            hip > 130 -> Result(false, CoachState.MOVEMENT, "${prefix}如果身體還很高，從髖部再往前一點。")
            hip < 50 -> Result(false, CoachState.CORRECTION, "${prefix}不要再往下壓，退回安全深度。")
            else -> Result(true, CoachState.HOLD, "${prefix}很好，停在這裡，保持呼吸。")
        }
    }

    private fun returnStanding(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 145 -> Result(false, CoachState.CORRECTION, "${prefix}回來時膝蓋也保持穩定，不要突然彎掉。")
            hip < 120 -> Result(false, CoachState.TRANSITION, "${prefix}慢慢回到中間，先不要急著抬頭。")
            else -> Result(true, CoachState.TRANSITION, "${prefix}很好，已經回到中間。")
        }
    }

    private fun neutralFinish(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 150 -> Result(false, CoachState.CORRECTION, "${prefix}最後把雙腿伸長，回到穩定位置。")
            hip < 120 -> Result(false, CoachState.TRANSITION, "${prefix}再慢慢回正一點。")
            else -> Result(true, CoachState.HOLD, "${prefix}完成，回到穩定呼吸。")
        }
    }

    private fun confidencePrefix(confidence: PoseGeometry.Confidence): String {
        return when (confidence) {
            PoseGeometry.Confidence.HIGH_3D -> ""
            PoseGeometry.Confidence.LOW_2D_FALLBACK -> "我先用畫面估算，"
            PoseGeometry.Confidence.INVALID -> ""
        }
    }
}
