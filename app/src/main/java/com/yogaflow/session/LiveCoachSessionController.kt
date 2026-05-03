package com.yogaflow.session

import com.yogaflow.coach.CoachState
import com.yogaflow.coach.PoseDetectionRouter
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.RuntimeOverrideMerger
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.yoga.YogaPose
import com.yogaflow.flow.YogaFlow

class LiveCoachSessionController(
    private val stateMachine: PoseStateMachine,
    private val flowEngine: PoseFlowEngine,
    private val poseDetectionRouter: PoseDetectionRouter,
    private val runtimeOverrideStore: RuntimeOverrideStore,
    private val autoTuningAdvisor: AutoTuningAdvisor,
    private val onFlowCompleted: (String) -> Unit,
    private val onUpdateRuntimeTuningControls: () -> Unit,
    private val onUpdateDebugOverlay: (
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean,
        runtimeSummary: String,
        overrideSummary: String,
        failReason: String,
        suggestionSummary: String
    ) -> Unit,
    private val onSpeakCoachCue: (CoachState, String) -> Unit,
    private val onAnimateFlowTransition: () -> Unit,
    private val onUpdateUi: (Boolean) -> Unit,
    private val buildRuntimeSummary: (RuntimeParams) -> String,
    private val buildOverrideSummary: () -> String,
    private val buildSuggestionSummary: (String, Int, com.yogaflow.flow.DetectKey) -> String
) {
    fun handleReadyPoseFrame(
        frame: PoseDetectionResult,
        currentFlow: YogaFlow,
        currentPose: YogaPose
    ) {
        val currentStep = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        if (currentStep == null) {
            onFlowCompleted(currentFlow.endCue.ifBlank { "課程完成，很好。" })
            onUpdateDebugOverlay(frame, "flow_complete", CoachState.HOLD, true, "", "", "", "")
            onUpdateUi(true)
            return
        }

        val stepIndex = flowEngine.currentStepNumber() - 1
        val overrides = runtimeOverrideStore.overridesFor(currentFlow.id, stepIndex, currentStep.detect)
        val effectiveParams = RuntimeOverrideMerger.apply(currentStep.params, overrides)
        val runtimeSummary = buildRuntimeSummary(effectiveParams)
        val overrideSummary = buildOverrideSummary()

        val mapping = poseDetectionRouter.evaluate(
            poseId = currentPose.id,
            detect = currentStep.detect,
            params = effectiveParams,
            frame = frame,
            fallback = stateMachine,
            currentPose = currentPose
        )

        if (!mapping.matched) {
            autoTuningAdvisor.observeReason(currentFlow.id, stepIndex, currentStep.detect, mapping.reason)
        }

        val suggestionSummary = buildSuggestionSummary(currentFlow.id, stepIndex, currentStep.detect)
        val event = flowEngine.update(currentFlow, mapping.state, mapping.matched)

        onUpdateRuntimeTuningControls()
        onUpdateDebugOverlay(
            frame,
            currentStep.detect.jsonKey,
            mapping.state,
            mapping.matched,
            runtimeSummary,
            overrideSummary,
            mapping.reason,
            suggestionSummary
        )

        when (event) {
            is PoseFlowEngine.FlowEvent.Cue -> onSpeakCoachCue(mapping.state, if (mapping.matched) event.text else mapping.cue)
            is PoseFlowEngine.FlowEvent.StepCompleted -> {
                onAnimateFlowTransition()
                onSpeakCoachCue(event.state, event.text)
            }
            is PoseFlowEngine.FlowEvent.FlowCompleted -> onFlowCompleted(event.text)
        }

        onUpdateUi(true)
    }
}
