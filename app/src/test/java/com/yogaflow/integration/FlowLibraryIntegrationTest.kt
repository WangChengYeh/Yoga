package com.yogaflow.integration

import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.FlowParser
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.yoga.YogaPoseCatalog
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowLibraryIntegrationTest {

    @Test
    fun packagedFlows_areValidDslV2AndCatalogRoutable() {
        val flowFiles = packagedFlowFiles()
        val catalogPoseIds = YogaPoseCatalog.poses.map { it.id }.toSet()
        val flowIds = mutableSetOf<String>()
        val discoveredPoseIds = mutableSetOf<String>()

        assertTrue(
            "Expected packaged flow library to include the documented base set",
            flowFiles.size >= 15
        )

        flowFiles.forEach { file ->
            val root = JSONObject(file.readText())
            assertEquals("Flow ${file.name} must stay on DSL v2", "dsl-v2", root.getString("version"))

            val flow = FlowParser.parse(root.toString())
            assertTrue("Duplicate flow id ${flow.id}", flowIds.add(flow.id))
            assertTrue("Flow ${flow.id} pose ${flow.pose} missing from catalog", flow.pose in catalogPoseIds)
            assertEquals("Flow ${flow.id} should use zh-TW packaged cues", "zh-TW", flow.language)
            assertFalse("Flow ${flow.id} must include steps", flow.steps.isEmpty())
            assertFalse("Flow ${flow.id} must include an end cue", flow.endCue.isBlank())

            flow.steps.forEachIndexed { index, step ->
                assertTrue(
                    "Flow ${flow.id} step ${index + 1} detect key is not registered",
                    DetectKey.isValidJsonKey(step.detect.jsonKey)
                )
                assertFalse("Flow ${flow.id} step ${index + 1} cue is blank", step.cue.isBlank())
                assertTrue("Flow ${flow.id} step ${index + 1} duration must be positive", step.durationMs > 0)
            }
            discoveredPoseIds.add(flow.pose)
        }

        val requiredBasePoses = setOf("mountain", "forward_fold", "twist", "squat", "bridge")
        assertTrue(
            "Packaged flows must cover the base product pose set",
            discoveredPoseIds.containsAll(requiredBasePoses)
        )
    }

    @Test
    fun playlistEngine_sequencesPackagedFlowsInAssetOrder() {
        val flows = packagedFlowFiles().map { FlowParser.parse(it.readText()) }
        val playlist = FlowPlaylistEngine()

        playlist.setPlaylist(flows)

        assertFalse("Playlist should not be empty after loading packaged flows", playlist.isEmpty())
        flows.forEachIndexed { index, expectedFlow ->
            assertEquals("Current number mismatch", index + 1, playlist.currentNumber())
            assertEquals("Playlist total mismatch", flows.size, playlist.total())
            assertEquals("Unexpected current flow at index $index", expectedFlow.id, playlist.current()?.id)

            if (index < flows.lastIndex) {
                assertEquals("Unexpected next flow at index $index", flows[index + 1].id, playlist.moveNext()?.id)
            } else {
                assertTrue("Last flow should be marked as last", playlist.isLastFlow())
                assertEquals("No flow should exist after the last flow", null, playlist.moveNext())
            }
        }
    }

    private fun packagedFlowFiles(): List<File> {
        return File("src/main/assets/flows")
            .listFiles { file -> file.extension == "json" && file.name.endsWith(".flow.json") }
            ?.sortedBy { it.name }
            .orEmpty()
    }
}
