package com.yogaflow.coach

import com.yogaflow.llm.LlmCoach
import com.yogaflow.yoga.YogaPose
import java.util.concurrent.Executor

class CoachCueController(
    private val llmCoach: LlmCoach,
    private val speaker: CoachSpeaker,
    private val executor: Executor,
    private val uiExecutor: (Runnable) -> Unit,
    private val minCueIntervalMs: Long,
    private val sameCueIntervalMs: Long,
    private val onDisplay: (displayText: String, llmEnabled: Boolean) -> Unit,
    private val isRequestCurrent: (requestId: Long, flowId: String, step: Int) -> Boolean
) {
    private var lastCoachCue = ""
    private var lastCoachAt = 0L
    private var requestId = 0L

    fun reset() {
        lastCoachCue = ""
        lastCoachAt = 0L
        requestId++
    }

    fun cancelPending() {
        requestId++
    }

    fun speak(pose: YogaPose, flowId: String, step: Int, state: CoachState, cue: String) {
        if (cue.isBlank() || !shouldEmit(cue)) return
        val currentRequestId = ++requestId

        executor.execute {
            val generated = llmCoach.generate(pose, state, cue)
            val isFallback = generated == cue
            val spokenText = CoachPhrasePolisher.polish(generated)
            val displayText = if (isFallback) "(fallback) $spokenText" else spokenText

            uiExecutor(Runnable {
                if (!isRequestCurrent(currentRequestId, flowId, step)) return@Runnable
                onDisplay(displayText, !isFallback)
                speaker.speakIfNeeded(spokenText)
            })
        }
    }

    fun speakRaw(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
    }

    private fun shouldEmit(cue: String): Boolean {
        val now = System.currentTimeMillis()
        if (cue == lastCoachCue && now - lastCoachAt < sameCueIntervalMs) return false
        if (now - lastCoachAt < minCueIntervalMs) return false
        lastCoachCue = cue
        lastCoachAt = now
        return true
    }
}
