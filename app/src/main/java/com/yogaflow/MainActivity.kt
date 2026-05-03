// only showing diff-relevant parts for brevity

// add import
import com.yogaflow.flow.AutoTuningAdvisor

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val autoTuningAdvisor = AutoTuningAdvisor()

    private fun handlePoseFrame(frame: PoseDetectionResult) {
        ...

        val mapping = PoseDetectionRouter.evaluate(
            poseId = currentPose.id,
            detect = currentStep.detect,
            params = effectiveParams,
            frame = frame,
            fallback = stateMachine,
            currentPose = currentPose
        )

        // observe fail reason
        if (!mapping.matched) {
            autoTuningAdvisor.observeReason(
                flowId = currentFlow.id,
                stepIndex = stepIndex,
                detect = currentStep.detect,
                reason = mapping.reason
            )
        }

        val suggestions = autoTuningAdvisor
            .suggestionsFor(currentFlow.id, stepIndex, currentStep.detect)
            .take(2)
            .joinToString(" | ") { it.label }

        updateDebugOverlay(
            frame = frame,
            detect = currentStep.detect.jsonKey,
            state = mapping.state,
            matched = mapping.matched,
            runtimeSummary = runtimeSummary,
            overrideSummary = overrideSummary,
            suggestionSummary = suggestions
        )

        ...
    }

    private fun updateDebugOverlay(
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean,
        runtimeSummary: String = "",
        overrideSummary: String = "",
        suggestionSummary: String = ""
    ) {
        ...

        val debugInfo = DebugPoseInfo(
            poseId = if (::currentPose.isInitialized) currentPose.id else "none",
            detect = detect,
            state = state,
            matched = matched,
            leftKneeAngle = leftKnee,
            rightKneeAngle = rightKnee,
            leftHipAngle = leftHip,
            rightHipAngle = rightHip,
            torsoTwistEstimate = torsoTwist,
            effectiveRuntimeSummary = runtimeSummary,
            overrideSummary = overrideSummary,
            failReason = "",
            tuningSuggestionSummary = suggestionSummary
        )

        debugText.text = debugInfo.toDisplayText()
    }

}
