package com.yogaflow.avatar

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachAvatarSceneTest {

    @Test
    fun coachAvatarScene_wiresRealFemaleCoachGlb() {
        val sceneFile = locateSceneFile()

        assertTrue("Scene file not found: ${sceneFile.absolutePath}", sceneFile.exists())

        val content = sceneFile.readText()

        assertTrue("Expected female_yoga_coach.glb in scene", content.contains("female_yoga_coach.glb"))
        assertTrue("Expected instantiated female_yoga_coach node", content.contains("[node name=\"female_yoga_coach\""))

        assertTrue("Scene should not reference placeholder.glb", !content.contains("placeholder.glb"))
        assertTrue("Scene should not reference cube.glb", !content.contains("cube.glb"))
    }

    private fun locateSceneFile(): File {
        val userDir = requireNotNull(System.getProperty("user.dir")) { "user.dir is not set" }
        var current: File? = File(userDir).absoluteFile
        while (current != null) {
            val candidate = File(current, "godot/scenes/CoachAvatar.tscn")
            if (candidate.exists()) {
                return candidate
            }
            current = current.parentFile
        }
        return File(File(userDir).absoluteFile, "godot/scenes/CoachAvatar.tscn")
    }
}
