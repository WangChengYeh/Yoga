package com.yogaflow.coach

import com.yogaflow.flow.YogaFlow

class PoseFlowEngine {

    private var currentFlowId: String? = null
    private var currentStepIndex = 0
    private var stepStartTime = System.currentTimeMillis()

    fun update(flow: YogaFlow, detectedState: CoachState): Pair<CoachState, String> {
        resetIfFlowChanged(flow.id)

        if (flow.steps.isEmpty()) {
            return CoachState.HOLD to flow.endCue.ifBlank { "維持姿勢" }
        }

        val currentStep = flow.steps[currentStepIndex]

        if (detectedState == currentStep.state) {
            val elapsed = System.currentTimeMillis() - stepStartTime
            if (elapsed >= currentStep.durationMs && currentStepIndex < flow.steps.lastIndex) {
                currentStepIndex++
                stepStartTime = System.currentTimeMillis()
            }
        }

        val step = flow.steps[currentStepIndex]
        return step.state to step.cue
    }

    fun reset() {
        currentStepIndex = 0
        stepStartTime = System.currentTimeMillis()
    }

    private fun resetIfFlowChanged(flowId: String) {
        if (currentFlowId != flowId) {
            currentFlowId = flowId
            reset()
        }
    }
}
