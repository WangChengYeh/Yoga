package com.yogaflow.coach

import com.yogaflow.llm.LlmCoach
import com.yogaflow.yoga.YogaPose
import java.util.concurrent.Executor

interface CoachCueGenerator {
    fun generate(pose: YogaPose, state: CoachState, raw: String): String
}

interface CoachSpeechSink {
    fun speakIfNeeded(text: String)
}

class CoachCueController(
    private val llmCoach: CoachCueGenerator,
    private val speaker: CoachSpeechSink,
    private val executor: Executor,
    private val uiExecutor: (Runnable) -> Unit,
    private val minCueIntervalMs: Long,
    private val sameCueIntervalMs: Long,
    private val onDisplay: (displayText: String, llmEnabled: Boolean) -> Unit,
    private val isRequestCurrent: (flowId: String, step: Int) -> Boolean
) {
    private var lastCoachCue = ""
    private var lastCoachAt = 0L
    private var lastSeverity = 0
    private var requestId = 0L

    fun reset() {
        lastCoachCue = ""
        lastCoachAt = 0L
        lastSeverity = 0
        requestId++
    }

    fun cancelPending() {
        requestId++
    }

    fun speak(pose: YogaPose, flowId: String, step: Int, state: CoachState, cue: String, severity: Int = 0) {
        if (cue.isBlank() || !shouldEmit(cue, severity)) return
        val currentRequestId = ++requestId

        executor.execute {
            val generated = llmCoach.generate(pose, state, cue)
            val isFallback = generated == cue
            val spokenText = CoachPhrasePolisher.polish(generated)

            uiExecutor(Runnable {
                if (currentRequestId != requestId || !isRequestCurrent(flowId, step)) return@Runnable
                onDisplay(spokenText, !isFallback)
                speaker.speakIfNeeded(spokenText)
            })
        }
    }

    fun speakRaw(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
    }

    private fun shouldEmit(cue: String, severity: Int = 0): Boolean {
        val now = System.currentTimeMillis()
        val isHigherSeverity = severity > lastSeverity
        if (!isHigherSeverity && now - lastCoachAt < minCueIntervalMs) return false
        if (!isHigherSeverity && cue == lastCoachCue && now - lastCoachAt < sameCueIntervalMs) return false
        lastCoachCue = cue
        lastCoachAt = now
        lastSeverity = severity
        return true
    }
}
