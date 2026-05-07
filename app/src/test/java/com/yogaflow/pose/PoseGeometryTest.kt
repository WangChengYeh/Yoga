package com.yogaflow.pose

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Test

class PoseGeometryTest {

    @Test
    fun angle_straightLineWorldLandmarks_returns180High3d() {
        val frame = frame3d(
            landmark(-1f, 0f, 0f),
            landmark(0f, 0f, 0f),
            landmark(1f, 0f, 0f)
        )

        val angle = PoseGeometry.angle(frame, 0, 1, 2)

        assertEquals(180.0, angle.degrees, 0.0001)
        assertEquals(PoseGeometry.Confidence.HIGH_3D, angle.confidence)
    }

    @Test
    fun angle_rightAngleWorldLandmarks_returns90High3d() {
        val frame = frame3d(
            landmark(1f, 0f, 0f),
            landmark(0f, 0f, 0f),
            landmark(0f, 1f, 0f)
        )

        val angle = PoseGeometry.angle(frame, 0, 1, 2)

        assertEquals(90.0, angle.degrees, 0.0001)
        assertEquals(PoseGeometry.Confidence.HIGH_3D, angle.confidence)
    }

    @Test
    fun angle_acuteWorldLandmarks_returns45High3d() {
        val frame = frame3d(
            landmark(1f, 0f, 0f),
            landmark(0f, 0f, 0f),
            landmark(1f, 1f, 0f)
        )

        val angle = PoseGeometry.angle(frame, 0, 1, 2)

        assertEquals(45.0, angle.degrees, 0.0001)
        assertEquals(PoseGeometry.Confidence.HIGH_3D, angle.confidence)
    }

    @Test
    fun angle_withoutWorldLandmarks_fallsBackTo2dImageLandmarks() {
        val frame = PoseDetectionResult(
            imageLandmarks = listOf(
                normalizedLandmark(1f, 0f),
                normalizedLandmark(0f, 0f),
                normalizedLandmark(0f, 1f)
            ),
            worldLandmarks = emptyList(),
            imageWidth = 100,
            imageHeight = 100
        )

        val angle = PoseGeometry.angle(frame, 0, 1, 2)

        assertEquals(90.0, angle.degrees, 0.0001)
        assertEquals(PoseGeometry.Confidence.LOW_2D_FALLBACK, angle.confidence)
    }

    @Test
    fun angle_withoutAnyLandmarks_returnsInvalid() {
        val frame = PoseDetectionResult(imageLandmarks = emptyList(), worldLandmarks = emptyList())

        val angle = PoseGeometry.angle(frame, 0, 1, 2)

        assertEquals(0.0, angle.degrees, 0.0001)
        assertEquals(PoseGeometry.Confidence.INVALID, angle.confidence)
    }

    private fun frame3d(vararg landmarks: Landmark): PoseDetectionResult {
        return PoseDetectionResult(
            imageLandmarks = emptyList(),
            worldLandmarks = landmarks.toList()
        )
    }

    private fun landmark(x: Float, y: Float, z: Float): Landmark {
        return Landmark.create(x, y, z)
    }

    private fun normalizedLandmark(x: Float, y: Float): NormalizedLandmark {
        return NormalizedLandmark.create(x, y, 0f)
    }
}
