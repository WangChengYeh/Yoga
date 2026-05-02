package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object SquatDetectionMapper {

    data class Result(
        val matched: Boolean,
        val state: CoachState,
        val cue: String
    )

    private var smoothedKnee: Double? = null
    private var stableDetect: String? = null
    private var stableSince = 0L

    fun reset() {
        smoothedKnee = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: String, frame: PoseDetectionResult): Result {
        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        val required = listOf(leftKnee, rightKnee, leftHip, rightHip)
        if (required.any { it.confidence == PoseGeometry.Confidence.INVALID }) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓髖部、膝蓋和腳踝都進入畫面。")
        }

        val rawKnee = minOf(leftKnee.degrees, rightKnee.degrees)
        val knee = smooth(rawKnee)

        val rawResult = when (detect) {
            "squat_setup" -> squatSetup(knee)
            "squat_descent" -> squatDescent(knee)
            "squat_hold" -> squatHold(knee)
            "squat_return" -> squatReturn(knee)
            else -> Result(true, CoachState.HOLD, "維持姿勢")
        }

        return applyStabilityWindow(detect, rawResult)
    }

    private fun applyStabilityWindow(detect: String, result: Result): Result {
        if (!result.matched) {
            stableDetect = null
            stableSince = 0L
            return result
        }

        val now = System.currentTimeMillis()
        if (stableDetect != detect) {
            stableDetect = detect
            stableSince = now
        }

        val stableFor = now - stableSince
        return if (stableFor >= STABILITY_WINDOW_MS) {
            result
        } else {
            result.copy(matched = false, cue = "穩住這個深蹲位置，再保持一下。")
        }
    }

    private fun smooth(raw: Double): Double {
        val prev = smoothedKnee
        if (prev != null && abs(raw - prev) <= ANGLE_DEADBAND_DEGREES) return prev
        val next = if (prev == null) raw else prev + KNEE_EMA_ALPHA * (raw - prev)
        smoothedKnee = next
        return next
    }

    private fun squatSetup(knee: Double): Result {
        return if (knee < 155) {
            Result(false, CoachState.CORRECTION, "先站穩，膝蓋伸長，準備下蹲。")
        } else {
            Result(true, CoachState.SETUP, "很好，雙腳穩定，準備慢慢下蹲。")
        }
    }

    private fun squatDescent(knee: Double): Result {
        return when {
            knee > 150 -> Result(false, CoachState.MOVEMENT, "慢慢往下蹲，膝蓋跟腳尖方向一致。")
            knee < SQUAT_MIN_KNEE_DEGREES -> Result(false, CoachState.CORRECTION, "不要蹲太低，先往上回一點。")
            else -> Result(true, CoachState.MOVEMENT, "很好，深度可以，保持控制。")
        }
    }

    private fun squatHold(knee: Double): Result {
        val holdMax = ThresholdConfig.squatHoldKneeMaxDegrees
        return when {
            knee > holdMax -> Result(false, CoachState.MOVEMENT, "再往下一點，找到穩定深蹲位置。")
            knee < SQUAT_MIN_KNEE_DEGREES -> Result(false, CoachState.CORRECTION, "深度太多了，往上回一點。")
            else -> Result(true, CoachState.HOLD, "很好，穩住，保持呼吸。")
        }
    }

    private fun squatReturn(knee: Double): Result {
        return if (knee < 150) {
            Result(false, CoachState.TRANSITION, "慢慢站起來，不要突然彈起。")
        } else {
            Result(true, CoachState.TRANSITION, "很好，回到站姿。")
        }
    }

    private const val SQUAT_MIN_KNEE_DEGREES = 80.0
    private const val KNEE_EMA_ALPHA = 0.35
    private const val ANGLE_DEADBAND_DEGREES = 2.0
    private const val STABILITY_WINDOW_MS = 300L
}
