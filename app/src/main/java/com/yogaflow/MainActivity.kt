// partial replacement of evaluateCurrentStep

    private fun evaluateCurrentStep(
        detect: String,
        frame: PoseDetectionResult
    ): ForwardFoldDetectionMapper.Result {
        return when (currentPose.id) {
            "forward_fold" -> ForwardFoldDetectionMapper.evaluate(detect, frame)
            "twist" -> {
                val r = TwistDetectionMapper.evaluate(detect, frame)
                ForwardFoldDetectionMapper.Result(r.matched, r.state, r.cue)
            }
            else -> {
                val (state, cue) = stateMachine.update(currentPose, frame)
                ForwardFoldDetectionMapper.Result(
                    matched = state != CoachState.CORRECTION,
                    state = state,
                    cue = cue
                )
            }
        }
    }
