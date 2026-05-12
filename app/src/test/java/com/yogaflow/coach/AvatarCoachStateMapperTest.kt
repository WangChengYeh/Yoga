package com.yogaflow.coach

import org.junit.Assert.assertEquals
import org.junit.Test

class AvatarCoachStateMapperTest {

    private val mapper = AvatarCoachStateMapper()

    @Test
    fun map_usesPreferredAction_whenNotCorrection() {
        val intent = mapper.map(
            detect = "mountain_hold",
            state = CoachState.HOLD,
            matched = true,
            failReason = "",
            poseId = "mountain",
            preferredAction = "hold_warrior_1"
        )

        assertEquals("hold_warrior_1", intent.action)
    }

    @Test
    fun map_ignoresPreferredAction_whenCorrection() {
        val intent = mapper.map(
            detect = "squat_hold",
            state = CoachState.CORRECTION,
            matched = false,
            failReason = "knee angle too low",
            poseId = "squat",
            preferredAction = "hold_warrior_1"
        )

        assertEquals("correct_knees", intent.action)
    }
}
