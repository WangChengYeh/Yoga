// truncated for brevity
// ONLY showing key changes

// 1. add request id
private var coachRequestId = 0L

// 2. update flowEngine call
val event = flowEngine.update(currentFlow, mapping.state, mapping.matched)

// 3. protect LLM response
private fun speakCoachCue(state: CoachState, cue: String) {
    if (cue.isBlank()) return
    if (!shouldEmitCoach(cue)) return

    val pose = currentPose
    val requestId = ++coachRequestId
    val flowId = currentFlow.id
    val step = flowEngine.currentStepNumber()

    coachExecutor.execute {
        val generated = llmCoach.generate(pose, state, cue)
        val isFallback = generated == cue
        val spokenText = CoachPhrasePolisher.polish(generated)

        runOnUiThread {
            if (requestId != coachRequestId) return@runOnUiThread
            if (flowId != currentFlow.id) return@runOnUiThread
            if (step != flowEngine.currentStepNumber()) return@runOnUiThread

            llmStatus.text = if (isFallback) "LLM: OFF" else "LLM: ON"
            coachText.text = spokenText
            speaker.speakIfNeeded(spokenText)
        }
    }
}
