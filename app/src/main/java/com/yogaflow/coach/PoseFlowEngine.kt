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
    private var stepStartTime = System.currentTimeMillis()
    private var completedFlowId: String? = null

    fun update(flow: YogaFlow, detectedState: CoachState, matched: Boolean): FlowEvent {
        resetIfFlowChanged(flow.id)

        if (flow.steps.isEmpty()) {
            completedFlowId = flow.id
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { "課程完成。" })
        }

        if (completedFlowId == flow.id) {
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { "課程完成。" })
        }

        val currentStep = flow.steps[currentStepIndex]

        if (!matched || detectedState != currentStep.state) {
            stepStartTime = System.currentTimeMillis()
            return FlowEvent.Cue(currentStep.state, currentStep.correction.ifBlank { currentStep.cue })
        }

        val elapsed = System.currentTimeMillis() - stepStartTime
        if (elapsed < currentStep.durationMs) {
            return FlowEvent.Cue(currentStep.state, currentStep.cue)
        }

        if (currentStepIndex >= flow.steps.lastIndex) {
            completedFlowId = flow.id
            return FlowEvent.FlowCompleted(flow.endCue.ifBlank { currentStep.cue })
        }

        currentStepIndex++
        stepStartTime = System.currentTimeMillis()
        val nextStep = flow.steps[currentStepIndex]
        return FlowEvent.StepCompleted(nextStep.state, nextStep.cue)
    }

    fun currentStepNumber(): Int = currentStepIndex + 1

    fun totalSteps(flow: YogaFlow): Int = flow.steps.size.coerceAtLeast(1)

    fun remainingSeconds(flow: YogaFlow): Long {
        if (flow.steps.isEmpty()) return 0
        if (completedFlowId == flow.id) return 0
        val step = flow.steps[currentStepIndex]
        val elapsed = System.currentTimeMillis() - stepStartTime
        val remainingMs = (step.durationMs - elapsed).coerceAtLeast(0)
        return (remainingMs + 999) / 1000
    }

    fun reset() {
        currentFlowId = null
        completedFlowId = null
        resetStepState()
    }

    private fun resetStepState() {
        currentStepIndex = 0
        stepStartTime = System.currentTimeMillis()
    }

    private fun resetIfFlowChanged(flowId: String) {
        if (currentFlowId != flowId) {
            currentFlowId = flowId
            completedFlowId = null
            resetStepState()
        }
    }
}
