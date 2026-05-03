package com.yogaflow.coach

import com.yogaflow.yoga.YogaPose

/**
 * Commands emitted by session / flow logic and consumed by CoachCueController.
 *
 * The session layer decides what should be said. The coach layer decides how
 * to throttle, polish, optionally LLM-generate, display, and speak it.
 */
sealed class CoachCommand {
    data class Generate(
        val pose: YogaPose,
        val state: CoachState,
        val cue: String,
        val flowId: String,
        val stepNumber: Int
    ) : CoachCommand()

    data class Raw(
        val cue: String
    ) : CoachCommand()
}

data class CoachDisplayState(
    val text: String,
    val llmEnabled: Boolean
) {
    val llmStatus: String = if (llmEnabled) "LLM: ON" else "LLM: OFF"
}
