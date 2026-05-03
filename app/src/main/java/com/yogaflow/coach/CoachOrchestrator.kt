package com.yogaflow.coach

import android.os.Handler
import android.os.Looper
import com.yogaflow.llm.LlmCoach
import com.yogaflow.yoga.YogaPose
import java.util.concurrent.Executor

/**
 * Owns coach cue throttling, LLM generation, TTS dispatch, and stale-response protection.
 *
 * MainActivity should provide current flow/step identity and UI callbacks, while this class
 * decides when a cue should be emitted and ensures old async LLM responses do not update
 * the current UI after the flow has moved on.
 */
class CoachOrchestrator(
    private val llmCoach: LlmCoach,
    private val speaker: CoachSpeaker,
    private val executor: Executor,
    private val getCurrentPose: () -> YogaPose,
    private val getFlowId: () -> String,
    private val getStepNumber: () -> Int,
    private val updateCoachUi: (displayText: String, llmEnabled: Boolean) -> Unit,
    private val minCueIntervalMs: Long = DEFAULT_MIN_CUE_INTERVAL_MS,
    private val sameCueIntervalMs: Long = DEFAULT_SAME_CUE_INTERVAL_MS
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastCue = ""
    private var lastCueAt = 0L
    private var requestId = 0L

    fun reset() {
        lastCue = ""
        lastCueAt = 0L
        requestId++
    }

    fun emit(state: CoachState, cue: String) {
        if (cue.isBlank() || !shouldEmit(cue)) return

        val pose = getCurrentPose()
        val flowId = getFlowId()
        val stepNumber = getStepNumber()
        val id = ++requestId

        executor.execute {
            val generated = llmCoach.generate(pose, state, cue)
            val isFallback = generated == cue
            val spokenText = CoachPhrasePolisher.polish(generated)
            val displayText = if (isFallback) "(fallback) $spokenText" else spokenText

            mainHandler.post {
                if (id != requestId) return@post
                if (flowId != getFlowId()) return@post
                if (stepNumber != getStepNumber()) return@post

                updateCoachUi(displayText, !isFallback)
                speaker.speakIfNeeded(spokenText)
            }
        }
    }

    fun speakRaw(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
    }

    private fun shouldEmit(cue: String): Boolean {
        val now = System.currentTimeMillis()
        if (cue == lastCue && now - lastCueAt < sameCueIntervalMs) return false
        if (now - lastCueAt < minCueIntervalMs) return false

        lastCue = cue
        lastCueAt = now
        return true
    }

    companion object {
        const val DEFAULT_MIN_CUE_INTERVAL_MS = 1200L
        const val DEFAULT_SAME_CUE_INTERVAL_MS = 2500L
    }
}
