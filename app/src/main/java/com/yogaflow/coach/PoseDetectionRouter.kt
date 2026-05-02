package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.yoga.YogaPose

object PoseDetectionRouter {

    data class DetectionSpec(
        val detect: String,
        val params: Map<String, Double>
    )

    private fun parseSpec(raw: String): DetectionSpec {
        val parts = raw.split("|")
        val detect = parts.firstOrNull().orEmpty()

        val params = parts.drop(1)
            .mapNotNull {
                val kv = it.split("=")
                if (kv.size == 2) kv[0] to kv[1].toDoubleOrNull() else null
            }
            .filter { it.second != null }
            .associate { it.first to it.second!! }

        return DetectionSpec(detect, params)
    }

    fun evaluate(
        poseId: String,
        detect: String,
        frame: PoseDetectionResult,
        fallback: PoseStateMachine,
        currentPose: YogaPose
    ): PoseDetectionMappingResult {

        val spec = parseSpec(detect)

        return when (poseId) {
            "forward_fold" -> {
                val r = ForwardFoldDetectionMapper.evaluate(spec.detect, frame, spec.params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "twist" -> {
                val r = TwistDetectionMapper.evaluate(spec.detect, frame, spec.params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "squat" -> {
                val r = SquatDetectionMapper.evaluate(spec.detect, frame, spec.params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "bridge" -> {
                val r = BridgeDetectionMapper.evaluate(spec.detect, frame, spec.params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            else -> {
                val (state, cue) = fallback.update(currentPose, frame)
                PoseDetectionMappingResult(
                    matched = state != CoachState.CORRECTION,
                    state = state,
                    cue = cue
                )
            }
        }
    }
}
