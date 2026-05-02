package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object ForwardFoldDetectionMapper {

    data class Result(
        val matched: Boolean,
        val state: CoachState,
        val cue: String
    )

    private data class SmoothedAngles(
        val knee: Double,
        val hip: Double
    )

    private data class AngleRange(
        val kneeMin: Double,
        val kneeMax: Double?,
        val hipMin: Double?,
        val hipMax: Double?
    )

    private var smoothedKnee: Double? = null
    private var smoothedHip: Double? = null
    private var lastFrameAt = 0L
    private var stableDetect: String? = null
    private var stableSince = 0L

    fun reset() {
        smoothedKnee = null
        smoothedHip = null
        lastFrameAt = 0L
        stableDetect = null
        stableSince = 0L
    }

    fun evaluate(
        detect: String,
        frame: PoseDetectionResult,
        params: Map<String, Double> = emptyMap()
    ): Result {
        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26)

        val required = listOf(leftKnee, rightKnee, leftHip, rightHip)
        if (required.any { it.confidence == PoseGeometry.Confidence.INVALID }) {
            reset()
            return Result(
                matched = false,
                state = CoachState.CORRECTION,
                cue = "我目前看不清楚你的膝蓋和髖部，請讓雙腿和上半身都進入畫面。"
            )
        }

        val rawKnee = minOf(leftKnee.degrees, rightKnee.degrees)
        val rawHip = minOf(leftHip.degrees, rightHip.degrees)
        val smoothed = smoothAngles(rawKnee, rawHip, params)
        val confidence = required.map { it.confidence }.let { confidences ->
            if (confidences.any { it == PoseGeometry.Confidence.LOW_2D_FALLBACK }) {
                PoseGeometry.Confidence.LOW_2D_FALLBACK
            } else {
                PoseGeometry.Confidence.HIGH_3D
            }
        }
        val prefix = confidencePrefix(confidence)

        val rawResult = when (detect) {
            "ready_forward_fold" -> readyForwardFold(smoothed.knee, smoothed.hip, prefix, params)
            "tall_spine_setup" -> tallSpineSetup(smoothed.knee, smoothed.hip, prefix, params)
            "hip_hinge" -> hipHinge(smoothed.knee, smoothed.hip, prefix, params)
            "controlled_forward_fold" -> controlledForwardFold(smoothed.knee, smoothed.hip, prefix, params)
            "forward_hold" -> forwardHold(smoothed.knee, smoothed.hip, prefix, params)
            "return_standing" -> returnStanding(smoothed.knee, smoothed.hip, prefix, params)
            "neutral_finish" -> neutralFinish(smoothed.knee, smoothed.hip, prefix, params)
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
            result.copy(matched = false, cue = "穩住這個位置，再保持一下。")
        }
    }

    private fun smoothAngles(rawKnee: Double, rawHip: Double, params: Map<String, Double>): SmoothedAngles {
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

    private fun smoothValue(previous: Double, raw: Double, params: Map<String, Double>): Double {
        val deadband = params["deadband.degrees"] ?: ANGLE_DEADBAND_DEGREES
        val alpha = params["ema.alpha"] ?: ANGLE_EMA_ALPHA
        if (abs(raw - previous) <= deadband) return previous
        return previous + alpha * (raw - previous)
    }

    private fun readyForwardFold(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "ready", DEFAULT_READY_KNEE_MIN, null, DEFAULT_READY_HIP_MIN, null)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}先把膝蓋伸長，但不要鎖死。")
            hipBelowMin(hip, range) -> Result(false, CoachState.CORRECTION, "${prefix}先回到比較直立的位置，再準備前傾。")
            else -> Result(true, CoachState.SETUP, "${prefix}準備好了，雙腿伸長，身體保持穩定。")
        }
    }

    private fun tallSpineSetup(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "setup", DEFAULT_TALL_SPINE_KNEE_MIN, null, DEFAULT_TALL_SPINE_HIP_MIN, null)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋再伸長一點，先建立穩定的腿。")
            hipBelowMin(hip, range) -> Result(false, CoachState.CORRECTION, "${prefix}你已經太早往前了，先把背拉長一點。")
            else -> Result(true, CoachState.SETUP, "${prefix}很好，背拉長，胸口打開。")
        }
    }

    private fun hipHinge(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "hinge", DEFAULT_HIP_HINGE_KNEE_MIN, null, null, DEFAULT_HIP_HINGE_HIP_MAX)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多了，先減少深度，讓腿重新伸長。")
            hipAboveMax(hip, range) -> Result(false, CoachState.MOVEMENT, "${prefix}從髖部再往前一點，不要只低頭。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}很好，正在從髖部前傾。")
        }
    }

    private fun controlledForwardFold(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "fold", DEFAULT_CONTROLLED_KNEE_MIN, null, DEFAULT_CONTROLLED_HIP_MIN, DEFAULT_CONTROLLED_HIP_MAX)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋開始彎了，退回一點，保持腿伸長。")
            hipAboveMax(hip, range) -> Result(false, CoachState.MOVEMENT, "${prefix}再從髖部往前一點，到舒服的位置就好。")
            hipBelowMin(hip, range) -> Result(false, CoachState.CORRECTION, "${prefix}深度太多了，先退回一點，不要硬壓。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}深度可以，保持控制，不要硬壓。")
        }
    }

    private fun forwardHold(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "hold", DEFAULT_HOLD_KNEE_MIN, null, DEFAULT_HOLD_HIP_MIN, DEFAULT_HOLD_HIP_MAX)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多，退回一點，讓大腿後側慢慢伸展。")
            hipAboveMax(hip, range) -> Result(false, CoachState.MOVEMENT, "${prefix}如果身體還很高，從髖部再往前一點。")
            hipBelowMin(hip, range) -> Result(false, CoachState.CORRECTION, "${prefix}不要再往下壓，退回安全深度。")
            else -> Result(true, CoachState.HOLD, "${prefix}很好，停在這裡，保持呼吸。")
        }
    }

    private fun returnStanding(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "return", DEFAULT_RETURN_KNEE_MIN, null, DEFAULT_RETURN_HIP_MIN, null)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}回來時膝蓋也保持穩定，不要突然彎掉。")
            hipBelowMin(hip, range) -> Result(false, CoachState.TRANSITION, "${prefix}慢慢回到中間，先不要急著抬頭。")
            else -> Result(true, CoachState.TRANSITION, "${prefix}很好，已經回到中間。")
        }
    }

    private fun neutralFinish(knee: Double, hip: Double, prefix: String, params: Map<String, Double>): Result {
        val range = range(params, "neutral", DEFAULT_NEUTRAL_KNEE_MIN, null, DEFAULT_NEUTRAL_HIP_MIN, null)
        return when {
            knee < range.kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}最後把雙腿伸長，回到穩定位置。")
            hipBelowMin(hip, range) -> Result(false, CoachState.TRANSITION, "${prefix}再慢慢回正一點。")
            else -> Result(true, CoachState.HOLD, "${prefix}完成，回到穩定呼吸。")
        }
    }

    private fun range(
        params: Map<String, Double>,
        phase: String,
        defaultKneeMin: Double,
        defaultKneeMax: Double?,
        defaultHipMin: Double?,
        defaultHipMax: Double?
    ): AngleRange {
        return AngleRange(
            kneeMin = params["angle.knee.$phase.min"] ?: defaultKneeMin,
            kneeMax = params["angle.knee.$phase.max"] ?: defaultKneeMax,
            hipMin = params["angle.hip.$phase.min"] ?: defaultHipMin,
            hipMax = params["angle.hip.$phase.max"] ?: defaultHipMax
        )
    }

    private fun hipBelowMin(hip: Double, range: AngleRange): Boolean {
        return range.hipMin != null && hip < range.hipMin
    }

    private fun hipAboveMax(hip: Double, range: AngleRange): Boolean {
        return range.hipMax != null && hip > range.hipMax
    }

    private fun confidencePrefix(confidence: PoseGeometry.Confidence): String {
        return when (confidence) {
            PoseGeometry.Confidence.HIGH_3D -> ""
            PoseGeometry.Confidence.LOW_2D_FALLBACK -> "我先用畫面估算，"
            PoseGeometry.Confidence.INVALID -> ""
        }
    }

    private const val DEFAULT_READY_KNEE_MIN = 155.0
    private const val DEFAULT_READY_HIP_MIN = 120.0
    private const val DEFAULT_TALL_SPINE_KNEE_MIN = 155.0
    private const val DEFAULT_TALL_SPINE_HIP_MIN = 115.0
    private const val DEFAULT_HIP_HINGE_KNEE_MIN = 150.0
    private const val DEFAULT_HIP_HINGE_HIP_MAX = 140.0
    private const val DEFAULT_CONTROLLED_KNEE_MIN = 150.0
    private const val DEFAULT_CONTROLLED_HIP_MIN = 55.0
    private const val DEFAULT_CONTROLLED_HIP_MAX = 135.0
    private const val DEFAULT_HOLD_KNEE_MIN = 145.0
    private const val DEFAULT_HOLD_HIP_MIN = 50.0
    private const val DEFAULT_HOLD_HIP_MAX = 130.0
    private const val DEFAULT_RETURN_KNEE_MIN = 145.0
    private const val DEFAULT_RETURN_HIP_MIN = 120.0
    private const val DEFAULT_NEUTRAL_KNEE_MIN = 150.0
    private const val DEFAULT_NEUTRAL_HIP_MIN = 120.0
    private const val ANGLE_EMA_ALPHA = 0.35
    private const val ANGLE_DEADBAND_DEGREES = 2.0
    private const val SMOOTHING_RESET_GAP_MS = 750L
    private const val STABILITY_WINDOW_MS = 300L
}
