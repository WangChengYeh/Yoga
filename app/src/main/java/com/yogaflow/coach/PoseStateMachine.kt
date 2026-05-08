package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.yoga.YogaPose

class PoseStateMachine {

    fun getJointStatus(pose: YogaPose, frame: PoseDetectionResult): Map<Int, Boolean> {
        return when (pose.id) {
            "forward_fold" -> {
                buildMap {
                    val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                    if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(25, leftKnee.degrees >= 150.0)
                    }

                    val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                    if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(26, rightKnee.degrees >= 150.0)
                    }

                    val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
                    if (leftHip.confidence != PoseGeometry.Confidence.INVALID) {
                        put(23, leftHip.degrees <= 140.0)
                    }

                    val rightHip = PoseGeometry.angle(frame, 12, 24, 26)
                    if (rightHip.confidence != PoseGeometry.Confidence.INVALID) {
                        put(24, rightHip.degrees <= 140.0)
                    }
                }
            }

            "squat" -> {
                buildMap {
                    val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                    if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(25, leftKnee.degrees in 120.0..160.0)
                    }

                    val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                    if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(26, rightKnee.degrees in 120.0..160.0)
                    }
                }
            }

            else -> emptyMap()
        }
    }

    fun update(pose: YogaPose, frame: PoseDetectionResult): Pair<CoachState, String> {
        return when (pose.id) {
            "forward_fold" -> {
                val knee = PoseGeometry.angle(frame, 23, 25, 27)
                val hip = PoseGeometry.angle(frame, 11, 23, 25)

                if (knee.confidence == PoseGeometry.Confidence.INVALID || hip.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "我目前看不清楚你的膝蓋和髖部角度，請讓全身進入畫面。"
                } else {
                    handleForwardFold(knee.degrees, hip.degrees, knee.confidence)
                }
            }

            "squat" -> {
                val knee = PoseGeometry.angle(frame, 23, 25, 27)

                if (knee.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "我目前看不清楚你的膝蓋角度，請讓雙腿進入畫面。"
                } else {
                    handleSquat(knee.degrees, knee.confidence)
                }
            }

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
