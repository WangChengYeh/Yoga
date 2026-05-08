package com.yogaflow.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AvatarRigFrameTest {

    @Test
    fun toJson_emptyBones_hasRequiredEnvelopeAndEmptyBonesArray() {
        val frame = AvatarRigFrame(
            timestampMs = 1234L,
            bones = emptyList()
        )

        val json = frame.toJson()

        assertEquals("avatar_rig_frame", json.getString("type"))
        assertEquals("mixamo-style-v1", json.getString("standard"))
        assertTrue(json.has("timestampMs"))
        assertEquals(0, json.getJSONArray("bones").length())
    }

    @Test
    fun toJson_singleBone_containsBoneDirectionAndConfidenceFields() {
        val frame = AvatarRigFrame(
            timestampMs = 5678L,
            bones = listOf(
                BoneRotation(
                    bone = "LeftArm",
                    direction = Vec3(0.1f, 0.2f, 0.3f),
                    confidence = 0.95f
                )
            )
        )

        val json = frame.toJson()
        val bones = json.getJSONArray("bones")

        assertEquals(1, bones.length())
        val bone = bones.getJSONObject(0)
        assertTrue(bone.has("bone"))
        assertTrue(bone.has("direction"))
        assertTrue(bone.has("confidence"))

        val direction = bone.getJSONObject("direction")
        assertTrue(direction.has("x"))
        assertTrue(direction.has("y"))
        assertTrue(direction.has("z"))
    }

    @Test
    fun vec3_toJson_roundtrip_preservesValues() {
        val vec = Vec3(x = -1.25f, y = 2.5f, z = 0.3333f)

        val json = vec.toJson()

        assertEquals(vec.x.toDouble(), json.getDouble("x"), 1e-4)
        assertEquals(vec.y.toDouble(), json.getDouble("y"), 1e-4)
        assertEquals(vec.z.toDouble(), json.getDouble("z"), 1e-4)
    }
}
