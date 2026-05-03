package com.yogaflow.session

import com.yogaflow.coach.CoachCommand
import com.yogaflow.flow.RuntimeTuningDebugSummary
import com.yogaflow.ui.UiState

/**
 * Output of one pose-frame tick after session processing.
 *
 * MainActivity should eventually only:
 * 1. update landmark overlay
 * 2. renderer.render(result.uiState)
 * 3. coachController.emit(result.coachCommand)
 * 4. run result.sideEffect if needed
 */
data class YogaSessionFrameResult(
    val uiState: UiState = UiState(),
    val coachCommand: CoachCommand? = null,
    val debug: YogaSessionDebug = YogaSessionDebug(),
    val tuning: RuntimeTuningDebugSummary = RuntimeTuningDebugSummary(),
    val sideEffect: YogaSessionSideEffect = YogaSessionSideEffect.None
)

data class YogaSessionDebug(
    val detect: String = "",
    val matched: Boolean = false,
    val failReason: String = ""
)

sealed class YogaSessionSideEffect {
    data object None : YogaSessionSideEffect()
    data object AutoStartClass : YogaSessionSideEffect()
    data object AnimateFlowTransition : YogaSessionSideEffect()
    data class CompleteFlow(val cue: String) : YogaSessionSideEffect()
}
