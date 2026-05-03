package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object BridgeDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String, val reason: String = "")

    private var smoothedHip: Double? = null
    private var stableDetect: DetectKey? = null
    private var stableSince = 0L

    fun reset() {
        smoothedHip = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: DetectKey, frame: PoseDetectionResult, params: RuntimeParams): Result {
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        if (leftHip.confidence == PoseGeometry.Confidence.INVALID || rightHip.confidence == PoseGeometry.Confidence.INVALID) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓髖部與膝蓋進入畫面。", "required landmarks invalid")
        }

        val hip = smooth(minOf(leftHip.degrees, rightHip.degrees), detect, params)
        val rawResult = when (detect) {
            DetectKey.BRIDGE_SETUP -> setup()
            DetectKey.BRIDGE_LIFT -> lift(hip, params)
            DetectKey.BRIDGE_HOLD -> hold(hip, params)
            DetectKey.BRIDGE_RETURN -> ret()
            else -> error("BridgeDetectionMapper received unsupported detect=${detect.jsonKey}")
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
        return if (now - stableSince >= stabilityMs) result else result.copy(matched = false, cue = "穩住這個橋式位置，再保持一下。", reason = "stableFor=${now - stableSince}ms < required=${stabilityMs}ms")
    }

    private fun smooth(raw: Double, detect: DetectKey, params: RuntimeParams): Double {
        val prev = smoothedHip
        val deadband = required(params.deadbandDegrees, detect, "runtime.deadbandDegrees")
        val alpha = required(params.emaAlpha, detect, "runtime.emaAlpha")
        if (prev != null && abs(raw - prev) <= deadband) return prev
        val next = if (prev == null) raw else prev + alpha * (raw - prev)
        smoothedHip = next
        return next
    }

    private fun setup(): Result {
        return Result(true, CoachState.SETUP, "很好，腳踩穩，準備慢慢抬起臀部。")
    }

    private fun lift(hip: Double, params: RuntimeParams): Result {
        val min = required(params.angles.hip.lift.min, DetectKey.BRIDGE_LIFT, "runtime.angles.hip.lift.min")
        val max = required(params.angles.hip.lift.max, DetectKey.BRIDGE_LIFT, "runtime.angles.hip.lift.max")
        return when {
            hip > max -> fail(CoachState.MOVEMENT, "慢慢抬起臀部，從骨盆帶動。", "hip", hip, ">", "max", max)
            hip < min -> fail(CoachState.CORRECTION, "不要抬太高，稍微放低一點，避免壓腰。", "hip", hip, "<", "min", min)
            else -> Result(true, CoachState.MOVEMENT, "很好，持續抬起，保持控制。")
        }
    }

    private fun hold(hip: Double, params: RuntimeParams): Result {
        val min = required(params.angles.hip.hold.min, DetectKey.BRIDGE_HOLD, "runtime.angles.hip.hold.min")
        val max = required(params.angles.hip.hold.max, DetectKey.BRIDGE_HOLD, "runtime.angles.hip.hold.max")
        return when {
            hip > max -> fail(CoachState.MOVEMENT, "再抬高一點臀部，但不要拱腰。", "hip", hip, ">", "max", max)
            hip < min -> fail(CoachState.CORRECTION, "稍微放低一點，讓腰保持舒服。", "hip", hip, "<", "min", min)
            else -> Result(true, CoachState.HOLD, "很好，維持橋式，保持呼吸。")
        }
    }

    private fun ret(): Result {
        return Result(true, CoachState.TRANSITION, "很好，慢慢回到地面。")
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
