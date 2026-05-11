package com.yogaflow.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class GodotScriptAssetSyncTest {

    @Test
    fun godotScripts_areMirroredIntoAppAssets() {
        val repoRoot = File("..").canonicalFile
        val godotScripts = File(repoRoot, "godot/scripts")
            .listFiles { file -> file.extension == "gd" }
            ?.sortedBy { it.name }
            .orEmpty()

        assertTrue("Expected Godot script sources", godotScripts.isNotEmpty())
        godotScripts.forEach { godotScript ->
            val assetScript = File(repoRoot, "app/src/main/assets/scripts/${godotScript.name}")
            assertTrue("Missing app asset script ${assetScript.path}", assetScript.isFile)
            assertEquals(
                "Script source differs for ${godotScript.name}",
                godotScript.readText(),
                assetScript.readText()
            )
        }
    }
}
