package com.yogaflow.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions

class LlmCoach(context: Context) {

    private var llm: LlmInference? = null

    init {
        try {
            val options = LlmInferenceOptions.builder()
                .setModelPath(LlmConfig.DEFAULT_MODEL_PATH)
                .setMaxTopK(LlmConfig.MAX_TOP_K)
                .build()

            llm = LlmInference.createFromOptions(context, options)
            Log.d("LLM", "Gemma loaded")

        } catch (e: Exception) {
            Log.e("LLM", "LLM init failed, fallback mode", e)
        }
    }

    fun generate(poseText: String): String {
        val prompt = PromptBuilder.buildCoachPrompt(poseText)

        return try {
            llm?.generateResponse(prompt) ?: fallback(poseText)
        } catch (e: Exception) {
            Log.e("LLM", "generate failed", e)
            fallback(poseText)
        }
    }

    private fun fallback(poseText: String): String {
        return when {
            poseText.contains("knee") -> "膝蓋稍微伸直一點，但不要鎖死"
            poseText.contains("Forward") -> "從髖部往前，不要駝背"
            else -> "很好，維持呼吸"
        }
    }
}
