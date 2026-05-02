package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object TwistDetectionMapper {

    data class Result(
        val matched: Boolean,
        val state: CoachState,
        val cue: String
    )

    private var smoothedTwist: Double? = null
    private var stableDetect: String? = null
    private var stableSince = 0L

    fun reset() {
        smoothedTwist = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(
        detect: String,
        frame: PoseDetectionResult,
        params: Map<String, Double> = emptyMap()
    ): Result {
        val leftShoulder = PoseGeometry.angle(frame, 11, 23, 25)
        val rightShoulder = PoseGeometry.angle(frame, 12, 24, 26)

        if (leftShoulder.confidence == PoseGeometry.Confidence.INVALID ||
            rightShoulder.confidence == PoseGeometry.Confidence.INVALID
        ) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓上半身完整進入畫面，保持肩膀可見。")
        }

        val rawTwist = abs(leftShoulder.degrees - rightShoulder.degrees)
        val twist = smooth(rawTwist, params)

        val rawResult = when (detect) {
            "stable_base" -> stableBase(twist, params)
            "twist_start" -> twistStart(twist, params)
            "twist_hold" -> twistHold(twist, params)
            "return_center" -> returnCenter(twist, params)
            else -> Result(true, CoachState.HOLD, "維持姿勢")
        }

        return applyStabilityWindow(detect, rawResult, params)
    }

    private fun applyStabilityWindow(
        detect: String,
        result: Result,
        params: Map<String, Double>
    ): Result {
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
        val stabilityMs = params["stability.ms"]?.toLong() ?: STABILITY_WINDOW_MS
        return if (stableFor >= stabilityMs) {
            result
        } else {
            result.copy(
                matched = false,
                cue = "穩住這個扭轉位置，再保持一下。"
            )
        }
    }

    private fun smooth(raw: Double, params: Map<String, Double>): Double {
        val prev = smoothedTwist
        val deadband = params["deadband.degrees"] ?: ANGLE_DEADBAND_DEGREES
        val alpha = params["ema.alpha"] ?: TWIST_EMA_ALPHA
        if (prev != null && abs(raw - prev) <= deadband) return prev
        val next = if (prev == null) raw else prev + alpha * (raw - prev)
        smoothedTwist = next
        return next
    }

    private fun stableBase(twist: Double, params: Map<String, Double>): Result {
        val centerMax = params["angle.twist.max"] ?: STABLE_BASE_TWIST_MAX_DEGREES
        return if (twist > centerMax) {
            Result(false, CoachState.CORRECTION, "先回到正中間，讓身體穩定再開始。")
        } else {
            Result(true, CoachState.SETUP, "很好，身體穩定，準備開始扭轉。")
        }
    }

    private fun twistStart(twist: Double, params: Map<String, Double>): Result {
        val startMin = params["angle.twist.min"] ?: TWIST_START_MIN_DEGREES
        val startMax = params["angle.twist.max"] ?: TWIST_START_MAX_DEGREES
        return when {
            twist < startMin -> Result(false, CoachState.MOVEMENT, "慢慢開始扭轉，從脊椎帶動。")
            twist > startMax -> Result(false, CoachState.CORRECTION, "不要用力過猛，輕柔一點。")
            else -> Result(true, CoachState.MOVEMENT, "很好，持續溫和扭轉。")
        }
    }

    private fun twistHold(twist: Double, params: Map<String, Double>): Result {
        val holdMin = params["angle.twist.min"] ?: TWIST_HOLD_MIN_DEGREES
        val holdMax = params["angle.twist.max"] ?: TWIST_HOLD_MAX_DEGREES
        return when {
            twist < holdMin -> Result(false, CoachState.MOVEMENT, "再多一點扭轉，但保持舒適。")
            twist > holdMax -> Result(false, CoachState.CORRECTION, "稍微退回一點，避免過度拉扯。")
            else -> Result(true, CoachState.HOLD, "很好，維持扭轉，放鬆呼吸。")
        }
    }

    private fun returnCenter(twist: Double, params: Map<String, Double>): Result {
        val centerMax = params["angle.twist.max"] ?: RETURN_CENTER_TWIST_MAX_DEGREES
        return if (twist > centerMax) {
            Result(false, CoachState.TRANSITION, "慢慢回到中間，不要急。")
        } else {
            Result(true, CoachState.TRANSITION, "很好，已回到中間。")
        }
    }

    private const val STABLE_BASE_TWIST_MAX_DEGREES = 20.0
    private const val TWIST_START_MIN_DEGREES = 15.0
    private const val TWIST_START_MAX_DEGREES = 60.0
    private const val TWIST_HOLD_MIN_DEGREES = 20.0
    private const val TWIST_HOLD_MAX_DEGREES = 70.0
    private const val RETURN_CENTER_TWIST_MAX_DEGREES = 20.0
    private const val TWIST_EMA_ALPHA = 0.4
    private const val ANGLE_DEADBAND_DEGREES = 2.0
    private const val STABILITY_WINDOW_MS = 300L
}
