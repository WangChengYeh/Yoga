package com.yogaflow.avatar

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.pose.PoseDetectionResult

object AvatarPositioning {

    private const val NOSE_INDEX = 0
    private const val LEFT_SHOULDER_INDEX = 11
    private const val RIGHT_SHOULDER_INDEX = 12
    private const val MIN_VISIBILITY = 0.5f

    fun oppositeSide(frame: PoseDetectionResult?): String {
        val landmarks = frame?.imageLandmarks ?: return "demo_area"
        val screenX = noseX(landmarks)
            ?: shoulderMidpointX(landmarks)
            ?: return "demo_area"
        return if (screenX < 0.5f) "right_side" else "left_side"
    }

    private fun noseX(landmarks: List<NormalizedLandmark>): Float? {
        val nose = landmarks.getOrNull(NOSE_INDEX) ?: return null
        if (!visibleEnough(nose)) return null
        return nose.x().takeIf { !it.isNaN() }
    }

    private fun shoulderMidpointX(landmarks: List<NormalizedLandmark>): Float? {
        val left = landmarks.getOrNull(LEFT_SHOULDER_INDEX) ?: return null
        val right = landmarks.getOrNull(RIGHT_SHOULDER_INDEX) ?: return null
        if (!visibleEnough(left) || !visibleEnough(right)) return null
        val lx = left.x().takeIf { !it.isNaN() } ?: return null
        val rx = right.x().takeIf { !it.isNaN() } ?: return null
        return (lx + rx) / 2f
    }

    private fun visibleEnough(landmark: NormalizedLandmark): Boolean {
        return landmark.visibility().orElse(0f) >= MIN_VISIBILITY
    }
}
