package com.yogaflow.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MixamoBoneNamesTest {

    @Test
    fun names_containsEveryAvatarBone() {
        val missing = AvatarBone.values().filterNot { MixamoBoneNames.names.containsKey(it) }
        assertTrue("Missing mappings for bones: $missing", missing.isEmpty())
        assertEquals(AvatarBone.values().size, MixamoBoneNames.names.size)
    }

    @Test
    fun names_hasNoDuplicateTargetNames() {
        val values = MixamoBoneNames.names.values.toList()
        assertEquals(values.size, values.toSet().size)
    }

    @Test
    fun names_containsKeyMediapipeMapperBones() {
        val required = setOf(
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

        val present = MixamoBoneNames.names.values.toSet()
        assertTrue(
            "Missing required mapper bone names: ${required - present}",
            present.containsAll(required)
        )
    }
}
