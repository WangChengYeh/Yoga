package com.yogaflow.coach

import com.yogaflow.flow.FlowParser
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.yoga.YogaPoseCatalog
import org.junit.Test
import java.io.File

class PoseDetectionRouterTest {

    @Test
    fun evaluate_allPackagedFlowSteps_doesNotThrowForCatalogPoseIds() {
        val router = PoseDetectionRouter()
        val fallback = PoseStateMachine()
        val emptyFrame = PoseDetectionResult(imageLandmarks = emptyList())
        val posesById = YogaPoseCatalog.poses.associateBy { it.id }
        val flowFiles = File("src/main/assets/flows")
            .listFiles { file -> file.extension == "json" && file.name.endsWith(".flow.json") }
            ?.sortedBy { it.name }
            .orEmpty()

        flowFiles.forEach { file ->
            val flow = FlowParser.parse(file.readText())
            val pose = posesById[flow.pose]
                ?: error("Flow ${flow.id} uses pose ${flow.pose}, but it is missing from YogaPoseCatalog")

            flow.steps.forEachIndexed { index, step ->
                runCatching {
                    router.evaluate(
                        poseId = flow.pose,
                        detect = step.detect,
                        params = step.params,
                        frame = emptyFrame,
                        fallback = fallback,
                        currentPose = pose,
                        expectedState = step.state
                    )
                }.getOrElse { error("Router failed for ${file.name} step ${index + 1}: ${it.message}") }
            }
        }
    }
}
