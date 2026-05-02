package com.yogaflow.llm

import android.content.Context
import android.util.Log

class LlmCoach(context: Context) {

    fun generate(poseText: String): String {
        // TODO: replace with real MediaPipe LLM
        val prompt = PromptBuilder.buildCoachPrompt(poseText)

        Log.d("LLM", "Prompt: $prompt")

        // fallback mock (important for now)
        return when {
            poseText.contains("knee") -> "膝蓋稍微伸直一點，但不要鎖死"
            poseText.contains("Forward") -> "從髖部往前，不要駝背"
            else -> "很好，維持呼吸"
        }
    }
}
