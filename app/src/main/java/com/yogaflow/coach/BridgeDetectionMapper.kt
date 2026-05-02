package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object BridgeDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String)

    private var smoothedHip: Double? = null
    private var stableDetect: String? = null
    private var stableSince = 0L

    fun reset() {
        smoothedHip = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: String, frame: PoseDetectionResult, params: Map<String, Double> = emptyMap()): Result {
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        if (leftHip.confidence == PoseGeometry.Confidence.INVALID || rightHip.confidence == PoseGeometry.Confidence.INVALID) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓髖部與膝蓋進入畫面。")
        }

        val hip = smooth(minOf(leftHip.degrees, rightHip.degrees), params)
        val rawResult = when (detect) {
            "bridge_setup" -> setup()
            "bridge_lift" -> lift(hip, params)
            "bridge_hold" -> hold(hip, params)
            "bridge_return" -> ret()
            else -> Result(true, CoachState.HOLD, "維持姿勢")
        }
        return applyStabilityWindow(detect, rawResult, params)
    }

    private fun applyStabilityWindow(detect: String, result: Result, params: Map<String, Double>): Result {
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
        val stabilityMs = params["stability.ms"]?.toLong() ?: STABILITY_WINDOW_MS
        return if (now - stableSince >= stabilityMs) result else result.copy(matched = false, cue = "穩住這個橋式位置，再保持一下。")
    }

    private fun smooth(raw: Double, params: Map<String, Double>): Double {
        val prev = smoothedHip
        val deadband = params["deadband.degrees"] ?: ANGLE_DEADBAND_DEGREES
        val alpha = params["ema.alpha"] ?: HIP_EMA_ALPHA
        if (prev != null && abs(raw - prev) <= deadband) return prev
        val next = if (prev == null) raw else prev + alpha * (raw - prev)
        smoothedHip = next
        return next
    }

    private fun setup(): Result {
        return Result(true, CoachState.SETUP, "很好，腳踩穩，準備慢慢抬起臀部。")
    }

    private fun lift(hip: Double, params: Map<String, Double>): Result {
        val liftMin = params["angle.hip.lift.min"] ?: BRIDGE_MIN_HIP_DEGREES
        val liftMax = params["angle.hip.lift.max"] ?: ThresholdConfig.bridgeLiftHipMaxDegrees
        return when {
            hip > liftMax -> Result(false, CoachState.MOVEMENT, "慢慢抬起臀部，從骨盆帶動。")
            hip < liftMin -> Result(false, CoachState.CORRECTION, "不要抬太高，稍微放低一點，避免壓腰。")
            else -> Result(true, CoachState.MOVEMENT, "很好，持續抬起，保持控制。")
        }
    }

    private fun hold(hip: Double, params: Map<String, Double>): Result {
        val holdMin = params["angle.hip.hold.min"] ?: BRIDGE_MIN_HIP_DEGREES
        val holdMax = params["angle.hip.hold.max"] ?: ThresholdConfig.bridgeLiftHipMaxDegrees
        return when {
            hip > holdMax -> Result(false, CoachState.MOVEMENT, "再抬高一點臀部，但不要拱腰。")
            hip < holdMin -> Result(false, CoachState.CORRECTION, "稍微放低一點，讓腰保持舒服。")
            else -> Result(true, CoachState.HOLD, "很好，維持橋式，保持呼吸。")
        }
    }

    private fun ret(): Result {
        return Result(true, CoachState.TRANSITION, "很好，慢慢回到地面。")
    }

    private const val BRIDGE_MIN_HIP_DEGREES = 75.0
    private const val HIP_EMA_ALPHA = 0.35
    private const val ANGLE_DEADBAND_DEGREES = 2.0
    private const val STABILITY_WINDOW_MS = 300L
}
