package com.yogaflow.coach

import com.yogaflow.yoga.YogaPose
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.Executor

class CoachCueControllerTest {

    @Test
    fun speak_firstCueAlwaysEmits() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 1_000L, sameCueIntervalMs = 2_000L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")

        assertEquals(listOf("Stand tall"), speaker.spoken)
    }

    @Test
    fun speak_sameCueWithinMinCueInterval_isSuppressed() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 1_000L, sameCueIntervalMs = 2_000L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")
        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")

        assertEquals(1, speaker.spoken.size)
    }

    @Test
    fun speak_differentCueWithinMinCueInterval_isSuppressed() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 1_000L, sameCueIntervalMs = 2_000L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")
        controller.speak(testPose, flowId, step, CoachState.SETUP, "Relax shoulders")

        assertEquals(listOf("Stand tall"), speaker.spoken)
    }

    @Test
    fun speak_sameCueAfterSameCueInterval_emitsAgain() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 0L, sameCueIntervalMs = 20L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")
        Thread.sleep(30L)
        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")

        assertEquals(listOf("Stand tall", "Stand tall"), speaker.spoken)
    }

    @Test
    fun speak_higherSeverityCue_overridesSuppression() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 1_000L, sameCueIntervalMs = 2_000L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall", severity = 0)
        controller.speak(testPose, flowId, step, CoachState.CORRECTION, "Protect your knee", severity = 1)

        assertEquals(listOf("Stand tall", "Protect your knee"), speaker.spoken)
    }

    @Test
    fun reset_clearsStateSoNextCueEmitsImmediately() {
        val speaker = RecordingSpeaker()
        val controller = controller(speaker, minCueIntervalMs = 1_000L, sameCueIntervalMs = 2_000L)

        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")
        controller.reset()
        controller.speak(testPose, flowId, step, CoachState.SETUP, "Stand tall")

        assertEquals(listOf("Stand tall", "Stand tall"), speaker.spoken)
    }

    private fun controller(
        speaker: RecordingSpeaker,
        minCueIntervalMs: Long,
        sameCueIntervalMs: Long
    ): CoachCueController {
        return CoachCueController(
            llmCoach = EchoCueGenerator,
            speaker = speaker,
            executor = Executor { it.run() },
            uiExecutor = { it.run() },
            minCueIntervalMs = minCueIntervalMs,
            sameCueIntervalMs = sameCueIntervalMs,
            onDisplay = { _, _ -> },
            isRequestCurrent = { _, _ -> true }
        )
    }

    private object EchoCueGenerator : CoachCueGenerator {
        override fun generate(pose: YogaPose, state: CoachState, raw: String): String = raw
    }

    private class RecordingSpeaker : CoachSpeechSink {
        val spoken = mutableListOf<String>()

        override fun speakIfNeeded(text: String) {
            spoken.add(text)
        }
    }

    private companion object {
        const val flowId = "flow"
        const val step = 1

        val testPose = YogaPose(
            id = "test",
            displayName = "Test Pose",
            category = "Standing",
            setupCue = "Set up",
            correctionFocus = "Alignment"
        )
    }
}
