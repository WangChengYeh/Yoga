package com.yogaflow.coach

import com.yogaflow.flow.YogaFlow

class PoseFlowEngine {

    sealed class FlowEvent {
        data class Cue(val state: CoachState, val text: String) : FlowEvent()
        data class StepCompleted(val state: CoachState, val text: String) : FlowEvent()
        data class FlowCompleted(val text: String) : FlowEvent()
    }

    private var currentFlowId: String? = null
    private var currentStepIndex = 0
    private var matchedStepMs = 0L
    private var lastUpdateTime = System.currentTimeMillis()
    private var completedFlowId: String? = null

    fun update(flow: YogaFlow, detectedState: CoachState, matched: Boolean): FlowEvent {
        resetIfFlowChanged(flow.id)
        val now = System.currentTimeMillis()
        val frameDeltaMs = (now - lastUpdateTime).coerceIn(0L, MAX_FRAME_DELTA_MS)
        lastUpdateTime = now

        if (flow.steps.isEmpty()) {
            completedFlowId = flow.id
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { "課程完成。" })
        }

        if (completedFlowId == flow.id) {
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { "課程完成。" })
        }

        val currentStep = flow.steps[currentStepIndex]

        if (!matched || detectedState != currentStep.state) {
            return FlowEvent.Cue(currentStep.state, currentStep.correction.ifBlank { currentStep.cue })
        }

        matchedStepMs += frameDeltaMs
        if (matchedStepMs < currentStep.durationMs) {
            return FlowEvent.Cue(currentStep.state, currentStep.cue)
        }

        if (currentStepIndex >= flow.steps.lastIndex) {
            completedFlowId = flow.id
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { currentStep.cue })
        }

        currentStepIndex++
        resetStepProgress()
        val nextStep = flow.steps[currentStepIndex]
        return FlowEvent.StepCompleted(nextStep.state, nextStep.cue)
    }

    fun currentStepNumber(): Int = currentStepIndex + 1

    fun totalSteps(flow: YogaFlow): Int = flow.steps.size.coerceAtLeast(1)

    fun remainingSeconds(flow: YogaFlow): Long {
        if (flow.steps.isEmpty()) return 0
        if (completedFlowId == flow.id) return 0
        val step = flow.steps[currentStepIndex]
        val remainingMs = (step.durationMs - matchedStepMs).coerceAtLeast(0)
        return (remainingMs + 999) / 1000
    }

    fun reset() {
        currentFlowId = null
        completedFlowId = null
        resetStepState()
    }

    private fun resetStepState() {
        currentStepIndex = 0
        resetStepProgress()
    }

    private fun resetStepProgress() {
        matchedStepMs = 0L
        lastUpdateTime = System.currentTimeMillis()
    }

    private fun resetIfFlowChanged(flowId: String) {
        if (currentFlowId != flowId) {
            currentFlowId = flowId
            completedFlowId = null
            resetStepState()
        }
    }

    private companion object {
        const val MAX_FRAME_DELTA_MS = 250L
    }
}
