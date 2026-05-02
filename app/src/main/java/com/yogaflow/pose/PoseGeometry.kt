package com.yogaflow.pose

import kotlin.math.acos
import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.min

object PoseGeometry {

    enum class Confidence {
        HIGH_3D,
        LOW_2D_FALLBACK,
        INVALID
    }

    data class Angle(
        val degrees: Double,
        val confidence: Confidence
    )

    fun angle(frame: PoseDetectionResult, a: Int, b: Int, c: Int): Angle {
        return if (frame.worldLandmarks.size > maxOf(a, b, c)) {
            angle3D(frame, a, b, c)
        } else if (frame.imageLandmarks.size > maxOf(a, b, c)) {
            angle2D(frame, a, b, c)
        } else {
            Angle(0.0, Confidence.INVALID)
        }
    }

    fun angleDegreesOrNull(frame: PoseDetectionResult, a: Int, b: Int, c: Int): Double? {
        val result = angle(frame, a, b, c)
        return if (result.confidence == Confidence.INVALID) null else result.degrees
    }

    private fun angle3D(frame: PoseDetectionResult, a: Int, b: Int, c: Int): Angle {
        val l = frame.worldLandmarks

        val ab = floatArrayOf(
            l[a].x() - l[b].x(),
            l[a].y() - l[b].y(),
            l[a].z() - l[b].z()
        )
        val cb = floatArrayOf(
            l[c].x() - l[b].x(),
            l[c].y() - l[b].y(),
            l[c].z() - l[b].z()
        )

        val dot = ab[0]*cb[0] + ab[1]*cb[1] + ab[2]*cb[2]
        val magA = sqrt((ab[0]*ab[0] + ab[1]*ab[1] + ab[2]*ab[2]).toDouble())
        val magB = sqrt((cb[0]*cb[0] + cb[1]*cb[1] + cb[2]*cb[2]).toDouble())

        if (magA == 0.0 || magB == 0.0) {
            return Angle(0.0, Confidence.INVALID)
        }

        val cos = (dot / (magA * magB)).coerceIn(-1.0, 1.0)
        return Angle(Math.toDegrees(acos(cos)), Confidence.HIGH_3D)
    }

    private fun angle2D(frame: PoseDetectionResult, a: Int, b: Int, c: Int): Angle {
        val l = frame.imageLandmarks

        val scaleX = frame.imageWidth.toFloat().takeIf { it > 0 } ?: 1f
        val scaleY = frame.imageHeight.toFloat().takeIf { it > 0 } ?: 1f

        val ab = floatArrayOf(
            (l[a].x() - l[b].x()) * scaleX,
            (l[a].y() - l[b].y()) * scaleY
        )
        val cb = floatArrayOf(
            (l[c].x() - l[b].x()) * scaleX,
            (l[c].y() - l[b].y()) * scaleY
        )

        val dot = ab[0]*cb[0] + ab[1]*cb[1]
        val magA = sqrt((ab[0]*ab[0] + ab[1]*ab[1]).toDouble())
        val magB = sqrt((cb[0]*cb[0] + cb[1]*cb[1]).toDouble())

        if (magA == 0.0 || magB == 0.0) {
            return Angle(0.0, Confidence.INVALID)
        }

        val cos = (dot / (magA * magB)).coerceIn(-1.0, 1.0)
        return Angle(Math.toDegrees(acos(cos)), Confidence.LOW_2D_FALLBACK)
    }
}
