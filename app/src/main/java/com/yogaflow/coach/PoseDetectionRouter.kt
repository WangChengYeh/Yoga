package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.yoga.YogaPose

object PoseDetectionRouter {

    fun evaluate(
        poseId: String,
        detect: DetectKey,
        params: RuntimeParams,
        frame: PoseDetectionResult,
        fallback: PoseStateMachine,
        currentPose: YogaPose
    ): PoseDetectionMappingResult {

        return when (poseId) {
            "forward_fold" -> {
                val r = ForwardFoldDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "twist" -> {
                val r = TwistDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "squat" -> {
                val r = SquatDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue)
            }
            "bridge" -> {
                val r = BridgeDetectionMapper.evaluate(detect, frame, params)
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
