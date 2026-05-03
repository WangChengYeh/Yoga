package com.yogaflow.runtime

import android.os.Handler
import android.os.Looper
import com.yogaflow.coach.CoachPhrasePolisher
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.CoachState
import com.yogaflow.llm.LlmCoach
import com.yogaflow.yoga.YogaPose
import java.util.concurrent.Executor

/**
 * Handles:
 * - cue throttling
 * - async LLM generation
 * - TTS dispatch
 * - UI callback
 */
class CoachRuntimeEngine(
    private val llmCoach: LlmCoach,
    private val speaker: CoachSpeaker,
    private val executor: Executor,
    private val getCurrentPose: () -> YogaPose,
    private val getFlowId: () -> String,
    private val getStep: () -> Int,
    private val updateUi: (text: String, llmOn: Boolean) -> Unit
) {

    private var lastCue = ""
    private var lastAt = 0L
    private var requestId = 0L

    private val mainHandler = Handler(Looper.getMainLooper())

    fun emit(state: CoachState, cue: String) {
        if (cue.isBlank()) return
        if (!shouldEmit(cue)) return

        val pose = getCurrentPose()
        val flowId = getFlowId()
        val step = getStep()
        val id = ++requestId

        executor.execute {
            val generated = llmCoach.generate(pose, state, cue)
            val isFallback = generated == cue

            val spoken = CoachPhrasePolisher.polish(generated)
            val display = if (isFallback) "(fallback) $spoken" else spoken

            mainHandler.post {
                if (id != requestId) return@post
                if (flowId != getFlowId()) return@post
                if (step != getStep()) return@post

                updateUi(display, !isFallback)
                speaker.speakIfNeeded(spoken)
            }
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

    companion object {
        private const val MIN_CUE_INTERVAL_MS = 1200L
        private const val SAME_CUE_INTERVAL_MS = 2500L
    }
}
