package com.yogaflow.pose

import kotlin.math.abs

enum class ViewOrientationStatus {
    GOOD,
    OFF_AXIS,
    TOO_ROTATED,
    UNKNOWN
}

data class ViewOrientationResult(
    val status: ViewOrientationStatus,
    val message: String,
    val score: Double
)

object ViewOrientation {
    fun analyze(frame: PoseDetectionResult): ViewOrientationResult {
        if (frame.worldLandmarks.size <= 24) {
            return ViewOrientationResult(
                ViewOrientationStatus.UNKNOWN,
                "請讓肩膀和骨盆都進入畫面。",
                0.0
            )
        }

        val l = frame.worldLandmarks
        val depth = (abs(l[11].z() - l[12].z()) + abs(l[23].z() - l[24].z())) / 2.0
        val width = maxOf((abs(l[11].x() - l[12].x()) + abs(l[23].x() - l[24].x())) / 2.0, 0.05)
        val score = depth / width

        return when {
            score < 0.35 -> ViewOrientationResult(ViewOrientationStatus.GOOD, "鏡頭角度很好，維持現在方向。", score)
            score < 0.75 -> ViewOrientationResult(ViewOrientationStatus.OFF_AXIS, "身體有一點轉開，請更正面面對鏡頭。", score)
            else -> ViewOrientationResult(ViewOrientationStatus.TOO_ROTATED, "請轉回來，讓肩膀和骨盆更正面面對鏡頭。", score)
        }
    }
}
