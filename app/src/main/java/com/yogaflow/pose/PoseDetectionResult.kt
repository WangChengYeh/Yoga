package com.yogaflow.pose

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

data class PoseDetectionResult(
    val imageLandmarks: List<NormalizedLandmark>,
    val worldLandmarks: List<Landmark> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val isMirrored: Boolean = false
)
