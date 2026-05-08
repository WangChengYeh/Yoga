package com.yogaflow.llm

import com.yogaflow.coach.CoachState
import com.yogaflow.yoga.YogaPoseCatalog
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptBuilderTest {

    @Test
    fun buildCoachPrompt_includesPoseStateAndRawCue() {
        val cases = listOf(
            Triple(YogaPoseCatalog.poses[0], CoachState.SETUP, "把重量平均放在雙腳。"),
            Triple(YogaPoseCatalog.poses[1], CoachState.HOLD, "保持肩膀穩定貼地。"),
            Triple(YogaPoseCatalog.poses[2], CoachState.CORRECTION, "讓脊椎往上延伸。")
        )

        cases.forEach { (pose, state, rawCue) ->
            val prompt = PromptBuilder.buildCoachPrompt(pose, state, rawCue)

            assertTrue(prompt.contains(pose.displayName))
            assertTrue(prompt.contains(state.toString()))
            assertTrue(prompt.contains(rawCue))
            assertFalse(prompt.contains("JSON"))
            assertFalse(prompt.contains("Markdown"))
            assertFalse(prompt.contains("°"))
            assertFalse(prompt.contains("degrees"))
            assertTrue(prompt.endsWith("請輸出一句私人教練語句。"))
        }
    }
}
