package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import kotlin.math.abs

object ForwardFoldDetectionMapper {

    data class Result(val matched: Boolean, val state: CoachState, val cue: String)

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
            return Result(false, CoachState.CORRECTION, "我目前看不清楚你的膝蓋和髖部，請讓雙腿和上半身都進入畫面。")
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
            DetectKey.READY_FORWARD_FOLD -> readyForwardFold(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.TALL_SPINE_SETUP -> tallSpineSetup(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.HIP_HINGE -> hipHinge(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.CONTROLLED_FORWARD_FOLD -> controlledForwardFold(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.FORWARD_HOLD -> forwardHold(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.RETURN_STANDING -> returnStanding(smoothed.knee, smoothed.hip, prefix, params)
            DetectKey.NEUTRAL_FINISH -> neutralFinish(smoothed.knee, smoothed.hip, prefix, params)
            else -> error("ForwardFoldDetectionMapper received unsupported detect=${detect.jsonKey}")
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
        return if (now - stableSince >= stabilityMs) {
            result
        } else {
            result.copy(matched = false, cue = "穩住這個位置，再保持一下。")
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

        val nextKnee = smoothValue(smoothedKnee!!, rawKnee, detect, params)
        val nextHip = smoothValue(smoothedHip!!, rawHip, detect, params)
        smoothedKnee = nextKnee
        smoothedHip = nextHip
        return SmoothedAngles(nextKnee, nextHip)
    }

    private fun smoothValue(previous: Double, raw: Double, detect: DetectKey, params: RuntimeParams): Double {
        val deadband = required(params.deadbandDegrees, detect, "runtime.deadbandDegrees")
        val alpha = required(params.emaAlpha, detect, "runtime.emaAlpha")
        if (abs(raw - previous) <= deadband) return previous
        return previous + alpha * (raw - previous)
    }

    private fun readyForwardFold(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.ready.min, DetectKey.READY_FORWARD_FOLD, "runtime.angles.knee.ready.min")
        val hipMin = required(params.angles.hip.ready.min, DetectKey.READY_FORWARD_FOLD, "runtime.angles.hip.ready.min")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}先把膝蓋伸長，但不要鎖死。")
            hip < hipMin -> Result(false, CoachState.CORRECTION, "${prefix}先回到比較直立的位置，再準備前傾。")
            else -> Result(true, CoachState.SETUP, "${prefix}準備好了，雙腿伸長，身體保持穩定。")
        }
    }

    private fun tallSpineSetup(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.setup.min, DetectKey.TALL_SPINE_SETUP, "runtime.angles.knee.setup.min")
        val hipMin = required(params.angles.hip.setup.min, DetectKey.TALL_SPINE_SETUP, "runtime.angles.hip.setup.min")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋再伸長一點，先建立穩定的腿。")
            hip < hipMin -> Result(false, CoachState.CORRECTION, "${prefix}你已經太早往前了，先把背拉長一點。")
            else -> Result(true, CoachState.SETUP, "${prefix}很好，背拉長，胸口打開。")
        }
    }

    private fun hipHinge(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.hinge.min, DetectKey.HIP_HINGE, "runtime.angles.knee.hinge.min")
        val hipMax = required(params.angles.hip.hinge.max, DetectKey.HIP_HINGE, "runtime.angles.hip.hinge.max")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多了，先減少深度，讓腿重新伸長。")
            hip > hipMax -> Result(false, CoachState.MOVEMENT, "${prefix}從髖部再往前一點，不要只低頭。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}很好，正在從髖部前傾。")
        }
    }

    private fun controlledForwardFold(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.fold.min, DetectKey.CONTROLLED_FORWARD_FOLD, "runtime.angles.knee.fold.min")
        val hipMin = required(params.angles.hip.fold.min, DetectKey.CONTROLLED_FORWARD_FOLD, "runtime.angles.hip.fold.min")
        val hipMax = required(params.angles.hip.fold.max, DetectKey.CONTROLLED_FORWARD_FOLD, "runtime.angles.hip.fold.max")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋開始彎了，退回一點，保持腿伸長。")
            hip > hipMax -> Result(false, CoachState.MOVEMENT, "${prefix}再從髖部往前一點，到舒服的位置就好。")
            hip < hipMin -> Result(false, CoachState.CORRECTION, "${prefix}深度太多了，先退回一點，不要硬壓。")
            else -> Result(true, CoachState.MOVEMENT, "${prefix}深度可以，保持控制，不要硬壓。")
        }
    }

    private fun forwardHold(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.hold.min, DetectKey.FORWARD_HOLD, "runtime.angles.knee.hold.min")
        val hipMin = required(params.angles.hip.hold.min, DetectKey.FORWARD_HOLD, "runtime.angles.hip.hold.min")
        val hipMax = required(params.angles.hip.hold.max, DetectKey.FORWARD_HOLD, "runtime.angles.hip.hold.max")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}膝蓋彎太多，退回一點，讓大腿後側慢慢伸展。")
            hip > hipMax -> Result(false, CoachState.MOVEMENT, "${prefix}如果身體還很高，從髖部再往前一點。")
            hip < hipMin -> Result(false, CoachState.CORRECTION, "${prefix}不要再往下壓，退回安全深度。")
            else -> Result(true, CoachState.HOLD, "${prefix}很好，停在這裡，保持呼吸。")
        }
    }

    private fun returnStanding(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.returnPhase.min, DetectKey.RETURN_STANDING, "runtime.angles.knee.return.min")
        val hipMin = required(params.angles.hip.returnPhase.min, DetectKey.RETURN_STANDING, "runtime.angles.hip.return.min")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}回來時膝蓋也保持穩定，不要突然彎掉。")
            hip < hipMin -> Result(false, CoachState.TRANSITION, "${prefix}慢慢回到中間，先不要急著抬頭。")
            else -> Result(true, CoachState.TRANSITION, "${prefix}很好，已經回到中間。")
        }
    }

    private fun neutralFinish(knee: Double, hip: Double, prefix: String, params: RuntimeParams): Result {
        val kneeMin = required(params.angles.knee.neutral.min, DetectKey.NEUTRAL_FINISH, "runtime.angles.knee.neutral.min")
        val hipMin = required(params.angles.hip.neutral.min, DetectKey.NEUTRAL_FINISH, "runtime.angles.hip.neutral.min")
        return when {
            knee < kneeMin -> Result(false, CoachState.CORRECTION, "${prefix}最後把雙腿伸長，回到穩定位置。")
            hip < hipMin -> Result(false, CoachState.TRANSITION, "${prefix}再慢慢回正一點。")
            else -> Result(true, CoachState.HOLD, "${prefix}完成，回到穩定呼吸。")
        }
    }

    private fun confidencePrefix(confidence: PoseGeometry.Confidence): String {
        return when (confidence) {
            PoseGeometry.Confidence.HIGH_3D -> ""
            PoseGeometry.Confidence.LOW_2D_FALLBACK -> "我先用畫面估算，"
            PoseGeometry.Confidence.INVALID -> ""
        }
    }

    private fun required(value: Double?, detect: DetectKey, key: String): Double {
        return value ?: error("Missing required param for ${detect.jsonKey}: $key")
    }

    private fun required(value: Long?, detect: DetectKey, key: String): Long {
        return value ?: error("Missing required param for ${detect.jsonKey}: $key")
    }

    private const val SMOOTHING_RESET_GAP_MS = 750L
}
