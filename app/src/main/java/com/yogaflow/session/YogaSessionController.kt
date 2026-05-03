package com.yogaflow.session

import com.yogaflow.coach.CoachCommand
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.PoseDetectionRouter
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.flow.RuntimeTuningController
import com.yogaflow.flow.YogaFlow
import com.yogaflow.pose.CameraReadinessController
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.ui.UiState
import com.yogaflow.yoga.YogaPose

/**
 * Phase-5 core extraction target.
 *
 * Owns:
 * - session state routing (IDLE / RUNNING / PAUSED / COMPLETED)
 * - camera readiness integration
 * - flow step evaluation
 * - tuning integration
 * - coach command generation
 * - ui state generation
 * - side effects (auto start / flow complete)
 *
 * MainActivity should gradually delegate handlePoseFrame() here.
 */
class YogaSessionController(
    private val readiness: CameraReadinessController,
    private val flowEngine: PoseFlowEngine,
    private val tuning: RuntimeTuningController
) {

    fun onFrame(
        frame: PoseDetectionResult,
        sessionState: SessionState,
        flow: YogaFlow,
        pose: YogaPose
    ): YogaSessionFrameResult {

        val readinessResult = readiness.analyze(
            frame = frame,
            sessionIdle = (sessionState == SessionState.IDLE)
        )

        // --- IDLE ---
        if (sessionState == SessionState.IDLE) {
            return YogaSessionFrameResult(
                uiState = UiState(
                    showCameraSetup = true,
                    cameraSetupText = readinessResult.setupMessage,
                    coachText = readinessResult.coachCue,
                    startEnabled = readinessResult.ready
                ),
                coachCommand = CoachCommand.Raw(readinessResult.coachCue),
                sideEffect = if (readinessResult.shouldAutoStart)
                    YogaSessionSideEffect.AutoStartClass
                else YogaSessionSideEffect.None
            )
        }

        // --- NOT READY ---
        if (!readinessResult.ready) {
            return YogaSessionFrameResult(
                uiState = UiState(
                    showCameraSetup = true,
                    cameraSetupText = readinessResult.setupMessage,
                    coachText = readinessResult.coachCue,
                    startEnabled = false
                ),
                coachCommand = CoachCommand.Raw(readinessResult.coachCue)
            )
        }

        // --- FLOW STEP ---
        val stepIndex = flowEngine.currentStepNumber() - 1
        val currentStep = flow.steps.getOrNull(stepIndex)

        if (currentStep == null) {
            return YogaSessionFrameResult(
                coachCommand = CoachCommand.Raw(flow.endCue.ifBlank { "課程完成，很好。" }),
                sideEffect = YogaSessionSideEffect.CompleteFlow(flow.endCue)
            )
        }

        val effectiveParams = tuning.effectiveParams(
            flow.id,
            stepIndex,
            currentStep.detect,
            currentStep.params
        )

        val mapping = PoseDetectionRouter.evaluate(
            poseId = pose.id,
            detect = currentStep.detect,
            params = effectiveParams,
            frame = frame,
            fallback = null,
            currentPose = pose
        )

        if (!mapping.matched) {
            tuning.observeFailReason(
                flow.id,
                stepIndex,
                currentStep.detect,
                mapping.reason
            )
        }

        val tuningSummary = tuning.buildDebugSummary(
            flow.id,
            stepIndex,
            currentStep.detect,
            effectiveParams
        )

        val event = flowEngine.update(flow, mapping.state, mapping.matched)

        val coachCommand = when (event) {
            is PoseFlowEngine.FlowEvent.Cue ->
                CoachCommand.Generate(
                    pose = pose,
                    state = mapping.state,
                    cue = if (mapping.matched) event.text else mapping.cue,
                    flowId = flow.id,
                    stepNumber = flowEngine.currentStepNumber()
                )

            is PoseFlowEngine.FlowEvent.StepCompleted ->
                CoachCommand.Generate(
                    pose = pose,
                    state = event.state,
                    cue = event.text,
                    flowId = flow.id,
                    stepNumber = flowEngine.currentStepNumber()
                )

            is PoseFlowEngine.FlowEvent.FlowCompleted ->
                CoachCommand.Raw(event.text)
        }

        val sideEffect = when (event) {
            is PoseFlowEngine.FlowEvent.StepCompleted -> YogaSessionSideEffect.AnimateFlowTransition
            is PoseFlowEngine.FlowEvent.FlowCompleted -> YogaSessionSideEffect.CompleteFlow(event.text)
            else -> YogaSessionSideEffect.None
        }

        return YogaSessionFrameResult(
            coachCommand = coachCommand,
            tuning = tuningSummary,
            debug = YogaSessionDebug(
                detect = currentStep.detect.jsonKey,
                matched = mapping.matched,
                failReason = mapping.reason
            ),
            sideEffect = sideEffect
        )
    }
}
