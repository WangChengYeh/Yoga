package com.yogaflow.avatar

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.pose.PoseDetectionResult
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Optional

class AvatarPositioningTest {

    @Test
    fun oppositeSide_nullFrame_returnsDemoArea() {
        assertEquals("demo_area", AvatarPositioning.oppositeSide(null))
    }

    @Test
    fun oppositeSide_emptyLandmarks_returnsDemoArea() {
        val frame = PoseDetectionResult(imageLandmarks = emptyList(), worldLandmarks = emptyList())
        assertEquals("demo_area", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_userOnLeft_avatarGoesRight() {
        val frame = framePosingNoseAt(0.25f)
        assertEquals("right_side", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_userOnRight_avatarGoesLeft() {
        val frame = framePosingNoseAt(0.75f)
        assertEquals("left_side", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_userAtCenter_picksLeftSide() {
        // Boundary case: screenX == 0.5 → strictly-less comparison picks left_side.
        // Documenting current behaviour.
        val frame = framePosingNoseAt(0.5f)
        assertEquals("left_side", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_noseInvisible_fallsBackToShoulderMidpoint() {
        val landmarks = MutableList(33) { lowConfidence(0.5f, 0.5f) }
        landmarks[0] = lowConfidence(0.5f, 0.4f)    // nose visible=0.1 → ignored
        landmarks[11] = visible(0.20f, 0.4f)        // left shoulder
        landmarks[12] = visible(0.30f, 0.4f)        // right shoulder, midpoint=0.25
        val frame = PoseDetectionResult(imageLandmarks = landmarks, worldLandmarks = emptyList())

        // Shoulder midpoint 0.25 < 0.5 → avatar goes right.
        assertEquals("right_side", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_allLandmarksLowConfidence_returnsDemoArea() {
        val landmarks = MutableList(33) { lowConfidence(0.25f, 0.4f) }
        val frame = PoseDetectionResult(imageLandmarks = landmarks, worldLandmarks = emptyList())
        assertEquals("demo_area", AvatarPositioning.oppositeSide(frame))
    }

    @Test
    fun oppositeSide_noseNaN_fallsBackToShoulderMidpoint() {
        val landmarks = MutableList(33) { visible(0.5f, 0.4f) }
        landmarks[0] = NormalizedLandmark.create(Float.NaN, 0.4f, 0f, Optional.of(1.0f), Optional.of(1.0f))
        landmarks[11] = visible(0.70f, 0.4f)
        landmarks[12] = visible(0.90f, 0.4f)        // midpoint=0.80
        val frame = PoseDetectionResult(imageLandmarks = landmarks, worldLandmarks = emptyList())

        // Shoulder midpoint 0.80 ≥ 0.5 → avatar goes left.
        assertEquals("left_side", AvatarPositioning.oppositeSide(frame))
    }

    private fun framePosingNoseAt(x: Float): PoseDetectionResult {
        val landmarks = MutableList(33) { visible(0.5f, 0.5f) }
        landmarks[0] = visible(x, 0.4f)
        return PoseDetectionResult(imageLandmarks = landmarks, worldLandmarks = emptyList())
    }

    private fun visible(x: Float, y: Float): NormalizedLandmark =
        NormalizedLandmark.create(x, y, 0f, Optional.of(1.0f), Optional.of(1.0f))

    private fun lowConfidence(x: Float, y: Float): NormalizedLandmark =
        NormalizedLandmark.create(x, y, 0f, Optional.of(0.1f), Optional.of(0.1f))
}
