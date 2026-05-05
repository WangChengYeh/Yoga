package com.yogaflow.avatar

import com.yogaflow.pose.PoseDetectionResult
import kotlin.math.sqrt

object MediaPipeAvatarMapper {

    private const val L_SHOULDER = 11
    private const val R_SHOULDER = 12
    private const val L_ELBOW = 13
    private const val R_ELBOW = 14
    private const val L_WRIST = 15
    private const val R_WRIST = 16
    private const val L_HIP = 23
    private const val R_HIP = 24
    private const val L_KNEE = 25
    private const val R_KNEE = 26
    private const val L_ANKLE = 27
    private const val R_ANKLE = 28
    private const val MIN_REQUIRED_LANDMARKS = R_ANKLE + 1

    fun map(frame: PoseDetectionResult): AvatarRigFrame {
        val l = frame.worldLandmarks
        if (l.size < MIN_REQUIRED_LANDMARKS) {
            return AvatarRigFrame(System.currentTimeMillis(), emptyList())
        }

        fun vec(a: Int, b: Int): Vec3 {
            return Vec3(
                (l[b].x() - l[a].x()).toFloat(),
                (l[b].y() - l[a].y()).toFloat(),
                (l[b].z() - l[a].z()).toFloat()
            )
        }

        fun normalize(v: Vec3): Vec3 {
            val len = sqrt(v.x*v.x + v.y*v.y + v.z*v.z)
            if (len == 0f) return v
            return Vec3(v.x/len, v.y/len, v.z/len)
        }

        val bones = mutableListOf<BoneRotation>()

        bones.add(BoneRotation("Spine", normalize(vec(L_HIP, L_SHOULDER)), 1f))

        bones.add(BoneRotation("LeftArm", normalize(vec(L_SHOULDER, L_ELBOW)), 1f))
        bones.add(BoneRotation("LeftForeArm", normalize(vec(L_ELBOW, L_WRIST)), 1f))

        bones.add(BoneRotation("RightArm", normalize(vec(R_SHOULDER, R_ELBOW)), 1f))
        bones.add(BoneRotation("RightForeArm", normalize(vec(R_ELBOW, R_WRIST)), 1f))

        bones.add(BoneRotation("LeftUpLeg", normalize(vec(L_HIP, L_KNEE)), 1f))
        bones.add(BoneRotation("LeftLeg", normalize(vec(L_KNEE, L_ANKLE)), 1f))

        bones.add(BoneRotation("RightUpLeg", normalize(vec(R_HIP, R_KNEE)), 1f))
        bones.add(BoneRotation("RightLeg", normalize(vec(R_KNEE, R_ANKLE)), 1f))

        return AvatarRigFrame(System.currentTimeMillis(), bones)
    }
}
