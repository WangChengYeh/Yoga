package com.yogaflow.pose

import kotlin.math.abs

enum class CameraFramingStatus {
    GOOD,
    TOO_CLOSE,
    TOO_FAR,
    TOO_LEFT,
    TOO_RIGHT,
    TOP_CUT,
    BOTTOM_CUT,
    UNKNOWN
}

data class CameraFramingResult(
    val status: CameraFramingStatus,
    val message: String,
    val score: Double
)

object CameraFramingCoach {

    fun analyze(frame: PoseDetectionResult): CameraFramingResult {
        val landmarks = frame.imageLandmarks
        if (landmarks.isEmpty()) {
            return unknown("請讓全身進入畫面。")
        }

        val visible = landmarks.filter { it.visibility().orElse(0f) >= MIN_VISIBILITY }
        if (visible.size < MIN_VISIBLE_LANDMARKS) {
            return unknown("我看不清楚全身，請站到鏡頭前方。")
        }

        val minX = visible.minOf { it.x().toDouble() }
        val maxX = visible.maxOf { it.x().toDouble() }
        val minY = visible.minOf { it.y().toDouble() }
        val maxY = visible.maxOf { it.y().toDouble() }

        val centerX = (minX + maxX) / 2.0
        val bodyWidth = maxX - minX
        val bodyHeight = maxY - minY

        return when {
            minY < TOP_MARGIN -> CameraFramingResult(
                CameraFramingStatus.TOP_CUT,
                "頭部太靠近畫面上緣，請退後一點。",
                minY
            )
            maxY > BOTTOM_MARGIN -> CameraFramingResult(
                CameraFramingStatus.BOTTOM_CUT,
                "腳部太靠近畫面下緣，請退後一點讓全身入鏡。",
                maxY
            )
            bodyHeight > TOO_CLOSE_HEIGHT -> CameraFramingResult(
                CameraFramingStatus.TOO_CLOSE,
                "你離鏡頭太近了，請退後一步。",
                bodyHeight
            )
            bodyHeight < TOO_FAR_HEIGHT -> CameraFramingResult(
                CameraFramingStatus.TOO_FAR,
                "你離鏡頭太遠了，請往前一步。",
                bodyHeight
            )
            centerX < LEFT_CENTER_LIMIT -> CameraFramingResult(
                CameraFramingStatus.TOO_LEFT,
                "你太靠畫面左邊了，請往右移一點。",
                centerX
            )
            centerX > RIGHT_CENTER_LIMIT -> CameraFramingResult(
                CameraFramingStatus.TOO_RIGHT,
                "你太靠畫面右邊了，請往左移一點。",
                centerX
            )
            bodyWidth < MIN_BODY_WIDTH -> CameraFramingResult(
                CameraFramingStatus.TOO_FAR,
                "身體在畫面中太小，請往前一點。",
                bodyWidth
            )
            abs(centerX - 0.5) <= CENTER_TOLERANCE -> CameraFramingResult(
                CameraFramingStatus.GOOD,
                "位置很好，保持全身在畫面中央。",
                bodyHeight
            )
            else -> CameraFramingResult(
                CameraFramingStatus.GOOD,
                "位置可以，準備開始。",
                bodyHeight
            )
        }
    }

    private fun unknown(message: String): CameraFramingResult {
        return CameraFramingResult(CameraFramingStatus.UNKNOWN, message, 0.0)
    }

    private const val MIN_VISIBILITY = 0.45f
    private const val MIN_VISIBLE_LANDMARKS = 16

    private const val TOP_MARGIN = 0.03
    private const val BOTTOM_MARGIN = 0.97

    private const val TOO_CLOSE_HEIGHT = 0.96
    private const val TOO_FAR_HEIGHT = 0.30
    private const val MIN_BODY_WIDTH = 0.10

    private const val LEFT_CENTER_LIMIT = 0.28
    private const val RIGHT_CENTER_LIMIT = 0.72
    private const val CENTER_TOLERANCE = 0.22
}
