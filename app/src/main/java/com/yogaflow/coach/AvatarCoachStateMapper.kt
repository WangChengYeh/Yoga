package com.yogaflow.coach

import com.yogaflow.avatar.AvatarIntent

class AvatarCoachStateMapper {
    fun map(
        detect: String,
        state: CoachState,
        matched: Boolean,
        failReason: String,
        poseId: String?,
        overridePosition: Pair<Float, Float>? = null
    ): AvatarIntent {
        val highlight = highlightFor(detect, failReason)
        val action = when {
            state == CoachState.CORRECTION && highlight == "knees" -> "correct_knees"
            state == CoachState.CORRECTION && highlight == "hips" -> "correct_hips"
            state == CoachState.CORRECTION && highlight == "spine" -> "correct_spine"
            state == CoachState.CORRECTION -> "correct_alignment"
            detect.contains("forward_fold") || poseId == "forward_fold" -> "hold_forward_fold"
            poseId == "squat" -> "hold_squat"
            poseId == "twist" -> "hold_twist"
            poseId == "bridge" -> "hold_bridge"
            state == CoachState.TRANSITION -> "controlled_transition"
            else -> "hold_mountain"
        }
        val emotion = when {
            state == CoachState.CORRECTION || !matched -> "focused"
            state == CoachState.MOVEMENT || state == CoachState.TRANSITION -> "attentive"
            else -> "calm"
        }
        return AvatarIntent(
            action = action,
            emotion = emotion,
            highlight = highlight,
            position = positionFor(state, highlight),
            facing = "user",
            scale = 1.0f,
            overridePosition = overridePosition
        )
    }

    private fun positionFor(state: CoachState, highlight: String?): String {
        if (state == CoachState.CORRECTION) {
            return when (highlight) {
                "knees" -> "near_knees"
                "hips" -> "near_hips"
                "spine" -> "near_spine"
                else -> "center"
            }
        }
        return "demo_area"
    }

    private fun highlightFor(detect: String, failReason: String): String? {
        val source = "$detect $failReason".lowercase()
        return when {
            source.contains("knee") -> "knees"
            source.contains("hip") -> "hips"
            source.contains("spine") || source.contains("back") -> "spine"
            source.contains("shoulder") -> "shoulders"
            source.contains("ankle") || source.contains("foot") -> "feet"
            source.contains("twist") -> "shoulders"
            else -> null
        }
    }
}
