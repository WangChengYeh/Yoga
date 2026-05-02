package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.yoga.YogaPose

class PoseStateMachine {

    fun update(pose: YogaPose, frame: PoseDetectionResult): Pair<CoachState, String> {
        val knee = PoseGeometry.angle(frame, 23, 25, 27)
        val hip = PoseGeometry.angle(frame, 11, 23, 25)

        if (knee.confidence == PoseGeometry.Confidence.INVALID || hip.confidence == PoseGeometry.Confidence.INVALID) {
            return CoachState.CORRECTION to "我目前看不清楚你的身體角度，請讓全身進入畫面。"
        }

        return when (pose.id) {
            "forward_fold" -> handleForwardFold(knee.degrees, hip.degrees, knee.confidence)
            "squat" -> handleSquat(knee.degrees, knee.confidence)
            else -> CoachState.HOLD to "維持姿勢"
        }
    }

    private fun handleForwardFold(
        knee: Double,
        hip: Double,
        confidence: PoseGeometry.Confidence
    ): Pair<CoachState, String> {
        val prefix = confidencePrefix(confidence)
        return when {
            knee < 150 -> CoachState.CORRECTION to "${prefix}膝蓋再伸直一點"
            hip > 140 -> CoachState.MOVEMENT to "${prefix}從髖部往前折"
            else -> CoachState.HOLD to "${prefix}很好，保持呼吸"
        }
    }

    private fun handleSquat(
        knee: Double,
        confidence: PoseGeometry.Confidence
    ): Pair<CoachState, String> {
        val prefix = confidencePrefix(confidence)
        return when {
            knee > 160 -> CoachState.MOVEMENT to "${prefix}再往下蹲"
            knee < 120 -> CoachState.CORRECTION to "${prefix}不要蹲太低，穩住"
            else -> CoachState.HOLD to "${prefix}穩住這個位置"
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
