package com.yogaflow.pose

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.acos
import kotlin.math.pow
import kotlin.math.sqrt

object PostureAnalyzer {

    fun analyze(landmarks: List<NormalizedLandmark>): String {
        if (landmarks.size < 33) return "Pose not detected"

        val leftHip = landmarks[23]
        val leftKnee = landmarks[25]
        val leftAnkle = landmarks[27]
        val leftShoulder = landmarks[11]

        val rightHip = landmarks[24]
        val rightKnee = landmarks[26]
        val rightAnkle = landmarks[28]
        val rightShoulder = landmarks[12]

        val leftKneeAngle = angle(leftHip, leftKnee, leftAnkle)
        val rightKneeAngle = angle(rightHip, rightKnee, rightAnkle)
        val leftHipAngle = angle(leftShoulder, leftHip, leftKnee)
        val rightHipAngle = angle(rightShoulder, rightHip, rightKnee)

        val avgKnee = (leftKneeAngle + rightKneeAngle) / 2.0
        val avgHip = (leftHipAngle + rightHipAngle) / 2.0

        return when {
            avgKnee > 160 && avgHip > 150 -> "Standing: knees straight"
            avgKnee > 150 && avgHip < 120 -> "Forward fold: hinge from hips"
            avgKnee < 145 -> "Correction: straighten your knees"
            else -> "Hold steady: adjust slowly"
        } + "\nKnee: ${avgKnee.toInt()}°, Hip: ${avgHip.toInt()}°"
    }

    private fun angle(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val ab = vector(b, a)
        val cb = vector(b, c)
        val dot = ab[0] * cb[0] + ab[1] * cb[1] + ab[2] * cb[2]
        val magAb = magnitude(ab)
        val magCb = magnitude(cb)
        if (magAb == 0.0 || magCb == 0.0) return 0.0
        val cosine = (dot / (magAb * magCb)).coerceIn(-1.0, 1.0)
        return Math.toDegrees(acos(cosine))
    }

    private fun vector(from: NormalizedLandmark, to: NormalizedLandmark): DoubleArray {
        return doubleArrayOf(
            (to.x() - from.x()).toDouble(),
            (to.y() - from.y()).toDouble(),
            (to.z() - from.z()).toDouble()
        )
    }

    private fun magnitude(v: DoubleArray): Double {
        return sqrt(v[0].pow(2) + v[1].pow(2) + v[2].pow(2))
    }
}
