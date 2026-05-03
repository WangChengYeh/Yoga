package com.yogaflow.coach

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.yoga.YogaPose

class PoseDetectionRouter(
    private val forwardFoldDetectionMapper: ForwardFoldDetectionMapper = ForwardFoldDetectionMapper(),
    private val twistDetectionMapper: TwistDetectionMapper = TwistDetectionMapper(),
    private val squatDetectionMapper: SquatDetectionMapper = SquatDetectionMapper(),
    private val bridgeDetectionMapper: BridgeDetectionMapper = BridgeDetectionMapper()
) {

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
                val r = forwardFoldDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue, if (!r.matched) r.reason else "")
            }
            "twist" -> {
                val r = twistDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue, if (!r.matched) r.reason else "")
            }
            "squat" -> {
                val r = squatDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue, if (!r.matched) r.reason else "")
            }
            "bridge" -> {
                val r = bridgeDetectionMapper.evaluate(detect, frame, params)
                PoseDetectionMappingResult(r.matched, r.state, r.cue, if (!r.matched) r.reason else "")
            }
            "mountain" -> {
                val (state, cue) = fallback.update(currentPose, frame)
                val matched = state != CoachState.CORRECTION
                PoseDetectionMappingResult(
                    matched = matched,
                    state = state,
                    cue = cue,
                    reason = if (!matched) cue else ""
                )
            }
            else -> error("No detection mapper registered for poseId=$poseId detect=${detect.jsonKey}")
        }
    }
}
