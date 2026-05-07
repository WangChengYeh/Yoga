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
}
