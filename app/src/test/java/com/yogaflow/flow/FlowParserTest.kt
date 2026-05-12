package com.yogaflow.flow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowParserTest {

    @Test
    fun parse_mountainWarmupFlow_returnsValidFlowShape() {
        val text = File("src/main/assets/flows/01_mountain_warmup.flow.json").readText()

        val flow = FlowParser.parse(text)

        assertFalse(flow.id.isBlank())
        assertTrue(flow.steps.isNotEmpty())
        flow.steps.forEach { step ->
            assertFalse(step.cue.isBlank())
            assertTrue(step.durationMs > 0)
        }
    }

    @Test
    fun parse_allPackagedFlows_returnsValidFlowShapes() {
        val flowFiles = File("src/main/assets/flows")
            .listFiles { file -> file.extension == "json" && file.name.endsWith(".flow.json") }
            ?.sortedBy { it.name }
            .orEmpty()

        assertTrue("Expected packaged flow assets", flowFiles.isNotEmpty())
        flowFiles.forEach { file ->
            val flow = FlowParser.parse(file.readText())

            assertFalse("Flow id is blank in ${file.name}", flow.id.isBlank())
            assertTrue("No steps in ${file.name}", flow.steps.isNotEmpty())
            flow.steps.forEach { step ->
                assertFalse("Blank cue in ${file.name}", step.cue.isBlank())
                assertTrue("Invalid duration in ${file.name}", step.durationMs > 0)
            }
        }
    }
    @Test
    fun parse_avatarActionField_mapsIntoStep() {
        val text = """
            {
              "flow": {
                "id": "test_flow",
                "name": "Test",
                "pose": "mountain",
                "language": "zh-TW",
                "level": "beginner"
              },
              "steps": [
                {
                  "state": "HOLD",
                  "durationMs": 1500,
                  "cue": "測試",
                  "detect": "mountain_hold",
                  "avatar_action": "hold_mountain"
                }
              ],
              "end": {"cue": "done"}
            }
        """.trimIndent()

        val flow = FlowParser.parse(text)

        assertTrue(flow.steps.first().avatarAction == "hold_mountain")
    }

}
