package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

class MountainDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String, val reason: String = "")

    private var smoothedKnee: Double? = null
    private var smoothedHip: Double? = null
    private var lastFrameAt = 0L
    private var stableDetect: DetectKey? = null
    private var stableSince = 0L

    fun reset() {
        smoothedKnee = null
        smoothedHip = null
        lastFrameAt = 0L
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(detect: DetectKey, frame: PoseDetectionResult, params: RuntimeParams): Result {
        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        val required = listOf(leftKnee, rightKnee, leftHip, rightHip)
        if (required.any { it.confidence == PoseGeometry.Confidence.INVALID }) {
            reset()
            return Result(false, CoachState.CORRECTION, "我目前看不清楚你的雙腿和上半身，請讓全身進入畫面。", "required landmarks invalid")
        }

        val smoothed = smoothAngles(
            rawKnee = minOf(leftKnee.degrees, rightKnee.degrees),
            rawHip = minOf(leftHip.degrees, rightHip.degrees),
            detect = detect,
            params = params
        )

        val prefix = confidencePrefix(
            if (required.any { it.confidence == PoseGeometry.Confidence.LOW_2D_FALLBACK }) {
                PoseGeometry.Confidence.LOW_2D_FALLBACK
            } else {
                PoseGeometry.Confidence.HIGH_3D
            }
        )

        val rawResult = when (detect) {
            DetectKey.STANDING_CENTERED -> standingCentered(smoothed.knee, smoothed.hip, prefix)
            DetectKey.SPINE_LENGTHENED -> spineLengthened(smoothed.knee, smoothed.hip, prefix)
            DetectKey.MOUNTAIN_HOLD -> mountainHold(smoothed.knee, smoothed.hip, prefix)
            DetectKey.READY_FOR_NEXT_POSE -> readyForNextPose(smoothed.knee, smoothed.hip, prefix)
            else -> error("MountainDetectionMapper received unsupported detect=${detect.jsonKey}")
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

        // Default to 500ms since flow JSON might not provide it yet
        val stabilityMs = params.stabilityMs ?: 500L
        return if (now - stableSince >= stabilityMs) {
            result
        } else {
            result.copy(matched = false, cue = "穩住這個位置，再保持一下。", reason = "stableFor=${now - stableSince}ms < required=${stabilityMs}ms")
        }
    }

    private data class SmoothedAngles(val knee: Double, val hip: Double)

    private fun smoothAngles(rawKnee: Double, rawHip: Double, detect: DetectKey, params: RuntimeParams): SmoothedAngles {
        val now = System.currentTimeMillis()
        val resetSmoothing = lastFrameAt == 0L || now - lastFrameAt > SMOOTHING_RESET_GAP_MS
        lastFrameAt = now

        if (resetSmoothing || smoothedKnee == null || smoothedHip == null) {
            smoothedKnee = rawKnee
            smoothedHip = rawHip
            return SmoothedAngles(rawKnee, rawHip)
        }

        val nextKnee = smoothValue(smoothedKnee!!, rawKnee, params)
        val nextHip = smoothValue(smoothedHip!!, rawHip, params)
        smoothedKnee = nextKnee
        smoothedHip = nextHip
        return SmoothedAngles(nextKnee, nextHip)
    }

    private fun smoothValue(previous: Double, raw: Double, params: RuntimeParams): Double {
        val deadband = params.deadbandDegrees ?: 2.0
        val alpha = params.emaAlpha ?: 0.5
        if (abs(raw - previous) <= deadband) return previous
        return previous + alpha * (raw - previous)
    }

    private fun standingCentered(knee: Double, hip: Double, prefix: String): Result {
        return evaluateMountain(knee, hip, prefix)
    }

    private fun spineLengthened(knee: Double, hip: Double, prefix: String): Result {
        return evaluateMountain(knee, hip, prefix)
    }

    private fun mountainHold(knee: Double, hip: Double, prefix: String): Result {
        return evaluateMountain(knee, hip, prefix)
    }

    private fun readyForNextPose(knee: Double, hip: Double, prefix: String): Result {
        return evaluateMountain(knee, hip, prefix)
    }

    private fun evaluateMountain(knee: Double, hip: Double, prefix: String): Result {
        return when {
            knee < 160.0 -> fail(CoachState.CORRECTION, "${prefix}膝蓋打直，雙腿完全伸展。", "knee", knee, "<", "min", 160.0)
            hip < 160.0 -> fail(CoachState.CORRECTION, "${prefix}站直，骨盆不要前傾。", "hip", hip, "<", "min", 160.0)
            else -> Result(true, CoachState.HOLD, "${prefix}山式到位，脊椎向上延伸，均勻呼吸。")
        }
    }

    private fun fail(
        state: CoachState,
        cue: String,
        metric: String,
        observed: Double,
        op: String,
        limitName: String,
        limitValue: Double
    ): Result {
        val reason = String.format("%s=%.1f %s %s=%.1f", metric, observed, op, limitName, limitValue)
        return Result(false, state, cue, reason)
    }

    private fun confidencePrefix(confidence: PoseGeometry.Confidence): String {
        return when (confidence) {
            PoseGeometry.Confidence.HIGH_3D -> ""
            PoseGeometry.Confidence.LOW_2D_FALLBACK -> "我先用畫面估算，"
            PoseGeometry.Confidence.INVALID -> ""
        }
    }

    companion object {
        private const val SMOOTHING_RESET_GAP_MS = 750L
    }
}
