package com.yogaflow.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.LlmInferenceOptions
import com.yogaflow.coach.CoachState
import com.yogaflow.yoga.YogaPose

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

    fun generate(pose: YogaPose, state: CoachState, raw: String): String {
        val prompt = PromptBuilder.buildCoachPrompt(pose, state, raw)

        return try {
            llm?.generateResponse(prompt) ?: raw
        } catch (e: Exception) {
            Log.e("LLM", "generate failed", e)
            raw
        }
    }
}
