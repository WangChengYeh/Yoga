package com.yogaflow.runtime

import android.view.View
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.PoseDetectionRouter
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.RuntimeOverrideMerger
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.YogaFlow
import com.yogaflow.pose.CameraFramingCoach
import com.yogaflow.pose.CameraFramingResult
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.pose.ViewOrientation
import com.yogaflow.pose.ViewOrientationResult
import com.yogaflow.pose.ViewOrientationStatus
import com.yogaflow.yoga.YogaPose

/**
 * Handles one live pose frame and routes it through camera onboarding,
 * strict pose detection, flow progression, tuning observation, and UI callbacks.
 *
 * MainActivity should own Android lifecycle and views; this class owns the
 * runtime frame decision tree that used to live in MainActivity.handlePoseFrame.
 */
class PoseFrameRuntimeHandler(
    private val overlayView: PoseOverlayView,
    private val cameraSetupPanel: View,
    private val flowEngine: PoseFlowEngine,
    private val stateMachine: PoseStateMachine,
    private val runtimeOverrideStore: RuntimeOverrideStore,
    private val autoTuningAdvisor: AutoTuningAdvisor,
    private val getSessionState: () -> SessionState,
    private val setSessionState: (SessionState) -> Unit,
    private val getCurrentFlow: () -> YogaFlow,
    private val setCurrentFlow: (YogaFlow) -> Unit,
    private val getCurrentPose: () -> YogaPose,
    private val setCurrentPose: (YogaPose) -> Unit,
    private val isCurrentPoseReady: () -> Boolean,
    private val updateCameraSetupPanel: (ready: Boolean, framingMessage: String, orientationMessage: String) -> Unit,
    private val maybeAutoStartClass: () -> Boolean,
    private val cameraSetupCue: (CameraFramingResult, ViewOrientationResult) -> String,
    private val updateDebugOverlay: (
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean,
        runtimeSummary: String,
        overrideSummary: String,
        failReason: String,
        suggestionSummary: String
    ) -> Unit,
    private val updateRuntimeTuningControls: () -> Unit,
    private val updateUi: (animated: Boolean) -> Unit,
    private val speakCoachCue: (CoachState, String) -> Unit,
    private val completeCurrentFlow: (String) -> Unit,
    private val animateFlowTransition: () -> Unit,
    private val buildRuntimeSummary: (com.yogaflow.flow.RuntimeParams) -> String,
    private val buildOverrideSummary: () -> String,
    private val buildSuggestionSummary: (flowId: String, stepIndex: Int, detect: com.yogaflow.flow.DetectKey) -> String
) {
    fun handle(frame: PoseDetectionResult) {
        overlayView.setLandmarks(frame.imageLandmarks)

        val framing = CameraFramingCoach.analyze(frame)
        val orientation = ViewOrientation.analyze(frame)
        val ready = framing.status == CameraFramingStatus.GOOD &&
            orientation.status == ViewOrientationStatus.GOOD

        when (getSessionState()) {
            SessionState.IDLE -> {
                updateCameraSetupPanel(ready, framing.message, orientation.message)
                if (maybeAutoStartClass()) return
                updateDebugOverlay(
                    frame,
                    "camera_setup",
                    CoachState.SETUP,
                    ready,
                    "",
                    "",
                    "",
                    ""
                )
                updateUi(false)
                return
            }

            SessionState.PAUSED -> {
                cameraSetupPanel.visibility = View.GONE
                updateDebugOverlay(frame, "paused", CoachState.SETUP, ready, "", "", "", "")
                updateUi(false)
                return
            }

            SessionState.COMPLETED -> {
                cameraSetupPanel.visibility = View.GONE
                updateDebugOverlay(frame, "completed", CoachState.HOLD, true, "", "", "", "")
                updateUi(false)
                return
            }

            SessionState.RUNNING -> Unit
        }

        cameraSetupPanel.visibility = View.GONE

        if (!ready) {
            val setupCue = cameraSetupCue(framing, orientation)
            speakCoachCue(CoachState.CORRECTION, setupCue)
            updateDebugOverlay(frame, "camera_setup", CoachState.CORRECTION, false, "", "", "", "")
            updateUi(false)
            return
        }

        if (!isCurrentPoseReady()) return

        val currentFlow = getCurrentFlow()
        val currentPose = getCurrentPose()
        val currentStep = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        if (currentStep == null) {
            completeCurrentFlow(currentFlow.endCue.ifBlank { "課程完成，很好。" })
            updateDebugOverlay(frame, "flow_complete", CoachState.HOLD, true, "", "", "", "")
            updateUi(true)
            return
        }

        val stepIndex = flowEngine.currentStepNumber() - 1
        val overrides = runtimeOverrideStore.overridesFor(currentFlow.id, stepIndex, currentStep.detect)
        val effectiveParams = RuntimeOverrideMerger.apply(currentStep.params, overrides)
        val runtimeSummary = buildRuntimeSummary(effectiveParams)
        val overrideSummary = buildOverrideSummary()

        val mapping = PoseDetectionRouter.evaluate(
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

        updateRuntimeTuningControls()
        updateDebugOverlay(
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
            is PoseFlowEngine.FlowEvent.Cue -> {
                speakCoachCue(mapping.state, if (mapping.matched) event.text else mapping.cue)
            }
            is PoseFlowEngine.FlowEvent.StepCompleted -> {
                animateFlowTransition()
                speakCoachCue(event.state, event.text)
            }
            is PoseFlowEngine.FlowEvent.FlowCompleted -> completeCurrentFlow(event.text)
        }

        updateUi(true)
    }
}
