package com.yogaflow.integration

import com.yogaflow.coach.CoachState
import com.yogaflow.coach.PoseDetectionRouter
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.FlowParser
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.flow.YogaFlow
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.session.LiveCoachSessionController
import com.yogaflow.yoga.YogaPoseCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowRuntimeIntegrationTest {

    @Test
    fun poseFlowEngine_completesEveryPackagedFlowWithMatchingStepStates() {
        packagedFlows().forEach { originalFlow ->
            val flow = originalFlow.withImmediateSteps()
            val engine = PoseFlowEngine()
            var completed: PoseFlowEngine.FlowEvent.FlowCompleted? = null

            repeat(flow.steps.size + 1) {
                val step = flow.steps[engine.currentStepNumber() - 1]
                val event = engine.update(flow, detectedState = step.state, matched = true)
                if (event is PoseFlowEngine.FlowEvent.FlowCompleted) {
                    completed = event
                    return@repeat
                }
            }

            assertEquals(
                "Flow ${flow.id} should complete with its configured end cue",
                flow.endCue,
                completed?.text
            )
            assertEquals("Flow ${flow.id} should have no remaining time", 0L, engine.remainingSeconds(flow))
            assertEquals(
                "Flow ${flow.id} should finish on its last step number",
                flow.steps.size,
                engine.currentStepNumber()
            )
        }
    }

    @Test
    fun liveCoachSessionController_withInvalidPoseFrameEmitsCorrectionCueAndDebugState() {
        val flow = packagedFlows().first { it.pose == "forward_fold" }
        val pose = YogaPoseCatalog.poses.first { it.id == flow.pose }
        val spoken = mutableListOf<Pair<CoachState, String>>()
        val debugUpdates = mutableListOf<DebugUpdate>()
        var uiUpdateCount = 0

        val controller = LiveCoachSessionController(
            stateMachine = PoseStateMachine(),
            flowEngine = PoseFlowEngine(),
            poseDetectionRouter = PoseDetectionRouter(),
            runtimeOverrideStore = RuntimeOverrideStore(),
            autoTuningAdvisor = AutoTuningAdvisor(),
            onFlowCompleted = { error("Flow should not complete from an invalid first frame: $it") },
            onUpdateRuntimeTuningControls = {},
            onUpdateDebugOverlay = { _, detect, state, matched, _, _, failReason, _ ->
                debugUpdates.add(DebugUpdate(detect, state, matched, failReason))
            },
            onSpeakCoachCue = { state, cue -> spoken.add(state to cue) },
            onAnimateFlowTransition = { error("Invalid first frame should not animate a transition") },
            onUpdateUi = { _ -> uiUpdateCount++ },
            buildRuntimeSummary = { params -> runtimeSummary(params) },
            buildOverrideSummary = { "" },
            buildSuggestionSummary = { _, _, _ -> "" }
        )

        controller.handleReadyPoseFrame(
            frame = PoseDetectionResult(imageLandmarks = emptyList(), worldLandmarks = emptyList()),
            currentFlow = flow,
            currentPose = pose
        )

        assertEquals("Controller should emit one coach cue", 1, spoken.size)
        assertEquals("Invalid frame should emit correction state", CoachState.CORRECTION, spoken.single().first)
        assertTrue("Correction cue should explain missing landmarks", spoken.single().second.contains("看不清楚"))

        assertEquals("Controller should emit one debug update", 1, debugUpdates.size)
        assertEquals(flow.steps.first().detect.jsonKey, debugUpdates.single().detect)
        assertEquals(CoachState.CORRECTION, debugUpdates.single().state)
        assertFalse("Invalid frame should not be marked as matched", debugUpdates.single().matched)
        assertEquals("required landmarks invalid", debugUpdates.single().failReason)
        assertEquals("UI should update once after frame handling", 1, uiUpdateCount)
    }

    private fun packagedFlows(): List<YogaFlow> {
        return File("src/main/assets/flows")
            .listFiles { file -> file.extension == "json" && file.name.endsWith(".flow.json") }
            ?.sortedBy { it.name }
            .orEmpty()
            .map { FlowParser.parse(it.readText()) }
    }

    private fun YogaFlow.withImmediateSteps(): YogaFlow {
        return copy(steps = steps.map { step -> step.copy(durationMs = 0L) })
    }

    private fun runtimeSummary(params: RuntimeParams): String {
        return listOfNotNull(
            params.stabilityMs?.let { "stabilityMs=$it" },
            params.emaAlpha?.let { "emaAlpha=$it" },
            params.deadbandDegrees?.let { "deadbandDegrees=$it" }
        ).joinToString(",")
    }

    private data class DebugUpdate(
        val detect: String,
        val state: CoachState,
        val matched: Boolean,
        val failReason: String
    )
}
