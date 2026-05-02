package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object BridgeDetectionMapper {

    data class Result(
        val matched: Boolean,
        val state: CoachState,
        val cue: String
    )

    private var smoothedHip: Double? = null
    private var stableDetect: String? = null
    private var stableSince = 0L

    fun reset() {
        smoothedHip = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: String, frame: PoseDetectionResult): Result {
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        if (leftHip.confidence == PoseGeometry.Confidence.INVALID ||
            rightHip.confidence == PoseGeometry.Confidence.INVALID
        ) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓髖部與膝蓋進入畫面。")
        }

        val rawHip = minOf(leftHip.degrees, rightHip.degrees)
        val hip = smooth(rawHip)

        val rawResult = when (detect) {
            "bridge_setup" -> setup()
            "bridge_lift" -> lift(hip)
            "bridge_hold" -> hold(hip)
            "bridge_return" -> ret()
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
            result.copy(matched = false, cue = "穩住這個橋式位置，再保持一下。")
        }
    }

    private fun smooth(raw: Double): Double {
        val prev = smoothedHip
        if (prev != null && abs(raw - prev) <= ANGLE_DEADBAND_DEGREES) return prev
        val next = if (prev == null) raw else prev + HIP_EMA_ALPHA * (raw - prev)
        smoothedHip = next
        return next
    }

    private fun setup(): Result {
        return Result(true, CoachState.SETUP, "很好，腳踩穩，準備慢慢抬起臀部。")
    }

    private fun lift(hip: Double): Result {
        return when {
            hip > 155 -> Result(false, CoachState.MOVEMENT, "慢慢抬起臀部，從骨盆帶動。")
            hip < 75 -> Result(false, CoachState.CORRECTION, "不要抬太高，稍微放低一點，避免壓腰。")
            else -> Result(true, CoachState.MOVEMENT, "很好，持續抬起，保持控制。")
        }
    }

    private fun hold(hip: Double): Result {
        return when {
            hip > 150 -> Result(false, CoachState.MOVEMENT, "再抬高一點臀部，但不要拱腰。")
            hip < 75 -> Result(false, CoachState.CORRECTION, "稍微放低一點，讓腰保持舒服。")
            else -> Result(true, CoachState.HOLD, "很好，維持橋式，保持呼吸。")
        }
    }

    private fun ret(): Result {
        return Result(true, CoachState.TRANSITION, "很好，慢慢回到地面。")
    }

    private const val HIP_EMA_ALPHA = 0.35
    private const val ANGLE_DEADBAND_DEGREES = 2.0
    private const val STABILITY_WINDOW_MS = 300L
}
