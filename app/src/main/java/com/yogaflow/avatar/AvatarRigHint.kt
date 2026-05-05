package com.yogaflow.avatar

data class AvatarRigHint(
    val bone: AvatarBone,
    val targetDirection: FloatArray? = null,
    val weight: Float = 1f
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AvatarRigHint

        if (bone != other.bone) return false
        if (targetDirection != null) {
            if (other.targetDirection == null) return false
            if (!targetDirection.contentEquals(other.targetDirection)) return false
        } else if (other.targetDirection != null) return false
        if (weight != other.weight) return false

        return true
    }

    override fun hashCode(): Int {
        var result = bone.hashCode()
        result = 31 * result + (targetDirection?.contentHashCode() ?: 0)
        result = 31 * result + weight.hashCode()
        return result
    }
}
