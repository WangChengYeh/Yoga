package com.yogaflow.flow

import com.yogaflow.coach.CoachState

data class YogaFlow(
    val id: String,
    val name: String,
    val pose: String,
    val language: String,
    val level: String,
    val steps: List<YogaFlowStep>,
    val endCue: String
)

data class YogaFlowStep(
    val state: CoachState,
    val durationMs: Long,
    val cue: String,
    val detect: String,
    val correction: String
)
