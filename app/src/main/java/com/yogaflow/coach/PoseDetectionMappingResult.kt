package com.yogaflow.coach

data class PoseDetectionMappingResult(
    val matched: Boolean,
    val state: CoachState,
    val cue: String,
    val reason: String = ""
)
