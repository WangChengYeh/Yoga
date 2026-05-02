package com.yogaflow.coach

import com.yogaflow.yoga.YogaPose

class PoseFlowEngine {

    private var currentStepIndex = 0
    private var stepStartTime = System.currentTimeMillis()

    fun update(pose: YogaPose, state: CoachState, raw: String): Pair<CoachState, String> {

        val flow = getFlow(pose)

        val currentStep = flow[currentStepIndex]

        if (state == currentStep.state) {
            val elapsed = System.currentTimeMillis() - stepStartTime

            if (elapsed > currentStep.minHoldMs && currentStepIndex < flow.lastIndex) {
                currentStepIndex++
                stepStartTime = System.currentTimeMillis()
            }
        } else {
            // reset if wrong phase
            currentStepIndex = 0
            stepStartTime = System.currentTimeMillis()
        }

        val step = flow[currentStepIndex]

        return step.state to step.cue
    }

    private fun getFlow(pose: YogaPose): List<PoseFlowStep> {

        return when (pose.id) {

            "forward_fold" -> listOf(
                PoseFlowStep(CoachState.SETUP, "站直，準備前彎"),
                PoseFlowStep(CoachState.MOVEMENT, "慢慢從髖部往前折"),
                PoseFlowStep(CoachState.HOLD, "保持呼吸，放鬆背部"),
                PoseFlowStep(CoachState.TRANSITION, "慢慢回到站姿")
            )

            else -> listOf(
                PoseFlowStep(CoachState.HOLD, "維持姿勢")
            )
        }
    }
}
