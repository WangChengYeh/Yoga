package com.yogaflow.coach

import android.speech.tts.TextToSpeech

class CoachSpeaker(
    private val tts: TextToSpeech,
    private val minIntervalMs: Long = 3500L
) {
    private var lastPhrase: String = ""
    private var lastSpokenAt: Long = 0L

    fun speakIfNeeded(text: String) {
        val phrase = toSpeechPhrase(text)
        val now = System.currentTimeMillis()

        if (phrase.isBlank()) return
        if (phrase == lastPhrase && now - lastSpokenAt < minIntervalMs) return
        if (now - lastSpokenAt < minIntervalMs) return

        tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "coach-${now}")
        lastPhrase = phrase
        lastSpokenAt = now
    }

    private fun toSpeechPhrase(text: String): String {
        return text.lineSequence().firstOrNull()?.trim().orEmpty()
    }
}
