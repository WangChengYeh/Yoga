package com.yogaflow.llm

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.yogaflow.coach.CoachCueGenerator
import com.yogaflow.coach.CoachState
import com.yogaflow.yoga.YogaPose
import java.io.File

class LlmCoach(
    context: Context,
    private val interactionDb: LlmInteractionDb? = null
) : CoachCueGenerator {

    private var llm: LlmInference? = null

    init {
        try {
            if (File(LlmConfig.DEFAULT_MODEL_PATH).isFile) {
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(LlmConfig.DEFAULT_MODEL_PATH)
                    .setMaxTopK(LlmConfig.MAX_TOP_K)
                    .build()

                llm = LlmInference.createFromOptions(context, options)
                Log.d("LLM", "Gemma loaded")
            } else {
                Log.d("LLM", "LLM model not found, fallback mode")
            }

        } catch (e: Exception) {
            Log.e("LLM", "LLM init failed, fallback mode", e)
        }
    }

    override fun generate(pose: YogaPose, state: CoachState, raw: String): String {
        val prompt = PromptBuilder.buildCoachPrompt(pose, state, raw)
        val startMs = System.currentTimeMillis()

        val (response, isFallback) = try {
            // TODO(#5): Use generateResponseAsync when callers can consume async results.
            val llmResponse = llm?.generateResponse(prompt)
            if (llmResponse == null) {
                Pair(raw, true)
            } else {
                Pair(llmResponse, false)
            }
        } catch (e: Exception) {
            Log.e("LLM", "generate failed", e)
            Pair(raw, true)
        }
        val elapsedMs = System.currentTimeMillis() - startMs
        interactionDb?.log(pose, state, raw, prompt, response, isFallback, elapsedMs)
        return response
    }
}
