package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

class TwistDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String, val reason: String = "")

    private var smoothedTwist: Double? = null
    private var stableDetect: DetectKey? = null
    private var stableSince = 0L

    fun reset() {
        smoothedTwist = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: DetectKey, frame: PoseDetectionResult, params: RuntimeParams): Result {
        val leftShoulder = PoseGeometry.angle(frame, 11, 23, 25)
        val rightShoulder = PoseGeometry.angle(frame, 12, 24, 26)

        if (leftShoulder.confidence == PoseGeometry.Confidence.INVALID || rightShoulder.confidence == PoseGeometry.Confidence.INVALID) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓上半身完整進入畫面，保持肩膀可見。", "required landmarks invalid")
        }

        val twist = smooth(abs(leftShoulder.degrees - rightShoulder.degrees), detect, params)
        val rawResult = when (detect) {
            DetectKey.STABLE_BASE -> stableBase(twist, params)
            DetectKey.TWIST_START -> twistStart(twist, params)
            DetectKey.TWIST_HOLD -> twistHold(twist, params)
            DetectKey.RETURN_CENTER -> returnCenter(twist, params)
            else -> error("TwistDetectionMapper received unsupported detect=${detect.jsonKey}")
        }
        return applyStabilityWindow(detect, rawResult, params)
    }

    private fun applyStabilityWindow(detect: DetectKey, result: Result, params: RuntimeParams): Result {
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
        val stabilityMs = required(params.stabilityMs, detect, "runtime.stabilityMs")
        return if (now - stableSince >= stabilityMs) result else result.copy(matched = false, cue = "穩住這個扭轉位置，再保持一下。", reason = "stableFor=${now - stableSince}ms < required=${stabilityMs}ms")
    }

    private fun smooth(raw: Double, detect: DetectKey, params: RuntimeParams): Double {
        val prev = smoothedTwist
        val deadband = required(params.deadbandDegrees, detect, "runtime.deadbandDegrees")
        val alpha = required(params.emaAlpha, detect, "runtime.emaAlpha")
        if (prev != null && abs(raw - prev) <= deadband) return prev
        val next = if (prev == null) raw else prev + alpha * (raw - prev)
        smoothedTwist = next
        return next
    }

    private fun stableBase(twist: Double, params: RuntimeParams): Result {
        val centerMax = required(params.angles.twist.center.max, DetectKey.STABLE_BASE, "runtime.angles.twist.center.max")
        return if (twist > centerMax) fail(CoachState.CORRECTION, "先回到正中間，讓身體穩定再開始。", "twist", twist, ">", "max", centerMax) else Result(true, CoachState.SETUP, "很好，身體穩定，準備開始扭轉。")
    }

    private fun twistStart(twist: Double, params: RuntimeParams): Result {
        val min = required(params.angles.twist.start.min, DetectKey.TWIST_START, "runtime.angles.twist.start.min")
        val max = required(params.angles.twist.start.max, DetectKey.TWIST_START, "runtime.angles.twist.start.max")
        return when {
            twist < min -> fail(CoachState.MOVEMENT, "慢慢開始扭轉，從脊椎帶動。", "twist", twist, "<", "min", min)
            twist > max -> fail(CoachState.CORRECTION, "不要用力過猛，輕柔一點。", "twist", twist, ">", "max", max)
            else -> Result(true, CoachState.MOVEMENT, "很好，持續溫和扭轉。")
        }
    }

    private fun twistHold(twist: Double, params: RuntimeParams): Result {
        val min = required(params.angles.twist.hold.min, DetectKey.TWIST_HOLD, "runtime.angles.twist.hold.min")
        val max = required(params.angles.twist.hold.max, DetectKey.TWIST_HOLD, "runtime.angles.twist.hold.max")
        return when {
            twist < min -> fail(CoachState.MOVEMENT, "再多一點扭轉，但保持舒適。", "twist", twist, "<", "min", min)
            twist > max -> fail(CoachState.CORRECTION, "稍微退回一點，避免過度拉扯。", "twist", twist, ">", "max", max)
            else -> Result(true, CoachState.HOLD, "很好，維持扭轉，放鬆呼吸。")
        }
    }

    private fun returnCenter(twist: Double, params: RuntimeParams): Result {
        val centerMax = required(params.angles.twist.center.max, DetectKey.RETURN_CENTER, "runtime.angles.twist.center.max")
        return if (twist > centerMax) fail(CoachState.TRANSITION, "慢慢回到中間，不要急。", "twist", twist, ">", "max", centerMax) else Result(true, CoachState.TRANSITION, "很好，已回到中間。")
    }

    private fun fail(state: CoachState, cue: String, metric: String, actual: Double, op: String, boundName: String, expected: Double): Result {
        return Result(false, state, cue, "$metric=${actual.fmt()} $op $boundName=${expected.fmt()}")
    }

    private fun Double.fmt(): String = "%.1f".format(this)

    private fun required(value: Double?, detect: DetectKey, key: String): Double {
        return value ?: error("Missing required param for ${detect.jsonKey}: $key")
    }

    private fun required(value: Long?, detect: DetectKey, key: String): Long {
        return value ?: error("Missing required param for ${detect.jsonKey}: $key")
    }
}
