package com.yogaflow.coach

import com.yogaflow.llm.LlmCoach
import java.util.concurrent.ExecutorService

/**
 * Handles:
 * - cue throttling
 * - async LLM generation
 * - fallback detection
 * - TTS speaking
 * - UI display state
 *
 * MainActivity should NOT directly talk to LLM / TTS after this.
 */
class CoachCueController(
    private val llmCoach: LlmCoach,
    private val speaker: CoachSpeaker,
    private val executor: ExecutorService,
    private val onDisplay: (CoachDisplayState) -> Unit
) {

    private var lastCue: String = ""
    private var lastAt: Long = 0L
    private var requestId: Long = 0L

    companion object {
        private const val MIN_CUE_INTERVAL_MS = 1200L
        private const val SAME_CUE_INTERVAL_MS = 2500L
    }

    fun emit(command: CoachCommand) {
        when (command) {
            is CoachCommand.Raw -> emitRaw(command.cue)
            is CoachCommand.Generate -> emitGenerated(command)
        }
    }

    fun cancelPending() {
        requestId++
    }

    private fun emitRaw(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
        onDisplay(CoachDisplayState(cue, llmEnabled = false))
    }

    private fun emitGenerated(cmd: CoachCommand.Generate) {
        if (!shouldEmit(cmd.cue)) return

        val currentRequest = ++requestId

        executor.execute {
            val generated = llmCoach.generate(cmd.pose, cmd.state, cmd.cue)
            val isFallback = generated == cmd.cue
            val spoken = CoachPhrasePolisher.polish(generated)

            if (currentRequest != requestId) return@execute

            onDisplay(
                CoachDisplayState(
                    text = if (isFallback) "(fallback) $spoken" else spoken,
                    llmEnabled = !isFallback
                )
            )

            speaker.speakIfNeeded(spoken)
        }
    }

    private fun shouldEmit(cue: String): Boolean {
        val now = System.currentTimeMillis()

        if (cue == lastCue && now - lastAt < SAME_CUE_INTERVAL_MS) return false
        if (now - lastAt < MIN_CUE_INTERVAL_MS) return false

        lastCue = cue
        lastAt = now
        return true
    }
}
