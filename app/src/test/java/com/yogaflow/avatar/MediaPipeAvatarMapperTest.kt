package com.yogaflow.avatar

import com.google.mediapipe.tasks.components.containers.Landmark
import com.yogaflow.pose.PoseDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPipeAvatarMapperTest {

    @Test
    fun map_emptyLandmarks_returnsEmptyRigFrame() {
        val frame = PoseDetectionResult(imageLandmarks = emptyList(), worldLandmarks = emptyList())

        val rigFrame = MediaPipeAvatarMapper.map(frame)

        assertTrue(rigFrame.bones.isEmpty())
    }

    @Test
    fun map_partialLandmarks_returnsEmptyRigFrame() {
        val sizes = listOf(1, 28)

        for (size in sizes) {
            val frame = PoseDetectionResult(
                imageLandmarks = emptyList(),
                worldLandmarks = landmarks(size)
            )

            val rigFrame = MediaPipeAvatarMapper.map(frame)

            assertTrue("Expected empty bones for landmark size=$size", rigFrame.bones.isEmpty())
        }
    }

    @Test
    fun map_exactlyMinimumLandmarks_emitsNineBones() {
        val frame = PoseDetectionResult(
            imageLandmarks = emptyList(),
            worldLandmarks = landmarks(29)
        )

        val rigFrame = MediaPipeAvatarMapper.map(frame)

        assertEquals(9, rigFrame.bones.size)

        val boneNames = rigFrame.bones.map { it.bone }.toSet()
        val expectedNames = setOf(
            "Spine",
            "LeftArm",
            "LeftForeArm",
            "RightArm",
            "RightForeArm",
            "LeftUpLeg",
            "LeftLeg",
            "RightUpLeg",
            "RightLeg"
        )
        assertEquals(expectedNames, boneNames)
    }

    @Test
    fun map_zeroBetweenSameLandmarks_doesNotProduceNaN() {
        val points = MutableList(29) { index ->
            val v = (index + 1).toFloat()
            Triple(v, v * 2f, v * 3f)
        }

        points[13] = points[11]
        points[15] = points[13]
        points[26] = points[24]
        points[28] = points[26]

        val frame = PoseDetectionResult(
            imageLandmarks = emptyList(),
            worldLandmarks = points.map { (x, y, z) -> Landmark.create(x, y, z) }
        )

        val rigFrame = MediaPipeAvatarMapper.map(frame)

        assertEquals(9, rigFrame.bones.size)
        assertTrue(rigFrame.bones.none { it.direction.x.isNaN() })
        assertTrue(rigFrame.bones.none { it.direction.y.isNaN() })
        assertTrue(rigFrame.bones.none { it.direction.z.isNaN() })
    }

    private fun landmarks(count: Int): List<Landmark> {
        return (0 until count).map { index ->
            val v = (index + 1).toFloat()
            Landmark.create(v, v * 2f, v * 3f)
        }
    }
}
