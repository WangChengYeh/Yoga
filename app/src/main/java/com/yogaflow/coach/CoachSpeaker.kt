package com.yogaflow.coach

import android.speech.tts.TextToSpeech

class CoachSpeaker(
    private val tts: TextToSpeech,
    private val minIntervalMs: Long = 5000L
) : CoachSpeechSink {
    private var ready = false
    private var pendingPhrase: String = ""
    private var lastPhrase: String = ""
    private var lastSpokenAt: Long = 0L

    fun setReady(isReady: Boolean) {
        ready = isReady
        if (ready && pendingPhrase.isNotBlank()) {
            val phrase = pendingPhrase
            pendingPhrase = ""
            speakPhrase(phrase)
        }
    }

    override fun speakIfNeeded(text: String) {
        val phrase = toSpeechPhrase(text)
        val now = System.currentTimeMillis()

        if (phrase.isBlank()) return
        if (phrase == lastPhrase && now - lastSpokenAt < minIntervalMs) return
        if (now - lastSpokenAt < minIntervalMs) return

        speakPhrase(phrase)
    }

    private fun toSpeechPhrase(text: String): String {
        return text.lineSequence().firstOrNull()?.trim().orEmpty()
    }

    private fun speakPhrase(phrase: String) {
        if (!ready) {
            pendingPhrase = phrase
            return
        }

        val now = System.currentTimeMillis()
        val result = tts.speak(phrase, TextToSpeech.QUEUE_FLUSH, null, "coach-${now}")
        if (result == TextToSpeech.SUCCESS) {
            lastPhrase = phrase
            lastSpokenAt = now
        } else {
            pendingPhrase = phrase
        }
    }
}
