package com.yogaflow.coach

data class PoseFlowStep(
    val state: CoachState,
    val cue: String,
    val minHoldMs: Long = 1500L
)
