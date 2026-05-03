package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object SquatDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String, val reason: String = "")

    private var smoothedKnee: Double? = null
    private var stableDetect: DetectKey? = null
    private var stableSince = 0L

    fun reset() {
        smoothedKnee = null
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: DetectKey, frame: PoseDetectionResult, params: RuntimeParams): Result {
        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        if (listOf(leftKnee, rightKnee, leftHip, rightHip).any { it.confidence == PoseGeometry.Confidence.INVALID }) {
            reset()
            return Result(false, CoachState.CORRECTION, "請讓髖部、膝蓋和腳踝都進入畫面。", "required landmarks invalid")
        }

        val knee = smooth(minOf(leftKnee.degrees, rightKnee.degrees), detect, params)
        val rawResult = when (detect) {
            DetectKey.SQUAT_SETUP -> squatSetup(knee, params)
            DetectKey.SQUAT_DESCENT -> squatDescent(knee, params)
            DetectKey.SQUAT_HOLD -> squatHold(knee, params)
            DetectKey.SQUAT_RETURN -> squatReturn(knee, params)
            else -> error("SquatDetectionMapper received unsupported detect=${detect.jsonKey}")
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
        return if (now - stableSince >= stabilityMs) result else result.copy(matched = false, cue = "穩住這個深蹲位置，再保持一下。", reason = "stableFor=${now - stableSince}ms < required=${stabilityMs}ms")
    }

    private fun smooth(raw: Double, detect: DetectKey, params: RuntimeParams): Double {
        val prev = smoothedKnee
        val deadband = required(params.deadbandDegrees, detect, "runtime.deadbandDegrees")
        val alpha = required(params.emaAlpha, detect, "runtime.emaAlpha")
        if (prev != null && abs(raw - prev) <= deadband) return prev
        val next = if (prev == null) raw else prev + alpha * (raw - prev)
        smoothedKnee = next
        return next
    }

    private fun squatSetup(knee: Double, params: RuntimeParams): Result {
        val min = required(params.angles.knee.setup.min, DetectKey.SQUAT_SETUP, "runtime.angles.knee.setup.min")
        return if (knee < min) fail(CoachState.CORRECTION, "先站穩，膝蓋伸長，準備下蹲。", "knee", knee, "<", "min", min) else Result(true, CoachState.SETUP, "很好，雙腳穩定，準備慢慢下蹲。")
    }

    private fun squatDescent(knee: Double, params: RuntimeParams): Result {
        val min = required(params.angles.knee.descent.min, DetectKey.SQUAT_DESCENT, "runtime.angles.knee.descent.min")
        val max = required(params.angles.knee.descent.max, DetectKey.SQUAT_DESCENT, "runtime.angles.knee.descent.max")
        return when {
            knee > max -> fail(CoachState.MOVEMENT, "慢慢往下蹲，膝蓋跟腳尖方向一致。", "knee", knee, ">", "max", max)
            knee < min -> fail(CoachState.CORRECTION, "不要蹲太低，先往上回一點。", "knee", knee, "<", "min", min)
            else -> Result(true, CoachState.MOVEMENT, "很好，深度可以，保持控制。")
        }
    }

    private fun squatHold(knee: Double, params: RuntimeParams): Result {
        val min = required(params.angles.knee.hold.min, DetectKey.SQUAT_HOLD, "runtime.angles.knee.hold.min")
        val max = required(params.angles.knee.hold.max, DetectKey.SQUAT_HOLD, "runtime.angles.knee.hold.max")
        return when {
            knee > max -> fail(CoachState.MOVEMENT, "再往下一點，找到穩定深蹲位置。", "knee", knee, ">", "max", max)
            knee < min -> fail(CoachState.CORRECTION, "深度太多了，往上回一點。", "knee", knee, "<", "min", min)
            else -> Result(true, CoachState.HOLD, "很好，穩住，保持呼吸。")
        }
    }

    private fun squatReturn(knee: Double, params: RuntimeParams): Result {
        val min = required(params.angles.knee.returnPhase.min, DetectKey.SQUAT_RETURN, "runtime.angles.knee.return.min")
        return if (knee < min) fail(CoachState.TRANSITION, "慢慢站起來，不要突然彈起。", "knee", knee, "<", "min", min) else Result(true, CoachState.TRANSITION, "很好，回到站姿。")
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
