package com.yogaflow.coach

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.yoga.YogaPose
import kotlin.math.abs

class PoseStateMachine {

    private var state = CoachState.SETUP

    fun update(pose: YogaPose, landmarks: List<NormalizedLandmark>): Pair<CoachState, String> {

        val knee = angle(landmarks, 23, 25, 27)
        val hip = angle(landmarks, 11, 23, 25)

        return when (pose.id) {

            "forward_fold" -> handleForwardFold(knee, hip)

            "squat" -> handleSquat(knee)

            else -> CoachState.HOLD to "維持姿勢"
        }
    }

    private fun handleForwardFold(knee: Double, hip: Double): Pair<CoachState, String> {
        return when {
            knee < 150 -> CoachState.CORRECTION to "膝蓋再伸直一點"
            hip > 140 -> CoachState.MOVEMENT to "從髖部往前折"
            else -> CoachState.HOLD to "很好，保持呼吸"
        }
    }

    private fun handleSquat(knee: Double): Pair<CoachState, String> {
        return when {
            knee > 160 -> CoachState.MOVEMENT to "再往下蹲"
            knee < 120 -> CoachState.CORRECTION to "不要蹲太低，穩住"
            else -> CoachState.HOLD to "穩住這個位置"
        }
    }

    private fun angle(l: List<NormalizedLandmark>, a: Int, b: Int, c: Int): Double {
        val ab = floatArrayOf(
            l[a].x() - l[b].x(),
            l[a].y() - l[b].y()
        )
        val cb = floatArrayOf(
            l[c].x() - l[b].x(),
            l[c].y() - l[b].y()
        )

        val dot = ab[0]*cb[0] + ab[1]*cb[1]
        val magA = kotlin.math.sqrt((ab[0]*ab[0] + ab[1]*ab[1]).toDouble())
        val magB = kotlin.math.sqrt((cb[0]*cb[0] + cb[1]*cb[1]).toDouble())

        return Math.toDegrees(kotlin.math.acos((dot / (magA*magB)).coerceIn(-1.0,1.0)))
    }
}
