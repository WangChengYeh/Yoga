package com.yogaflow.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()
    private var framingStatus: CameraFramingStatus? = null

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val framingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 6f
        style = Paint.Style.STROKE
    }

    private val connections = listOf(
        11 to 12,
        11 to 13,
        13 to 15,
        12 to 14,
        14 to 16,
        11 to 23,
        12 to 24,
        23 to 24,
        23 to 25,
        25 to 27,
        24 to 26,
        26 to 28,
        27 to 31,
        28 to 32,
        0 to 11,
        0 to 12
    )

    fun setLandmarks(newLandmarks: List<NormalizedLandmark>) {
        landmarks = newLandmarks
        invalidate()
    }

    fun setFramingStatus(status: CameraFramingStatus?) {
        framingStatus = status
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (landmarks.isNotEmpty()) {
            drawSkeleton(canvas)
        }

        drawFramingBox(canvas)
    }

    private fun drawSkeleton(canvas: Canvas) {
        for ((startIndex, endIndex) in connections) {
            if (startIndex < landmarks.size && endIndex < landmarks.size) {
                val start = landmarks[startIndex]
                val end = landmarks[endIndex]
                canvas.drawLine(
                    start.x() * width,
                    start.y() * height,
                    end.x() * width,
                    end.y() * height,
                    linePaint
                )
            }
        }

        landmarks.forEach { point ->
            canvas.drawCircle(point.x() * width, point.y() * height, 6f, pointPaint)
        }
    }

    private fun drawFramingBox(canvas: Canvas) {
        framingPaint.color = when (framingStatus) {
            CameraFramingStatus.GOOD -> Color.rgb(0x4C, 0xAF, 0x50)
            CameraFramingStatus.TOO_CLOSE,
            CameraFramingStatus.TOP_CUT,
            CameraFramingStatus.BOTTOM_CUT -> Color.rgb(0xF4, 0x43, 0x36)
            CameraFramingStatus.TOO_FAR,
            CameraFramingStatus.TOO_LEFT,
            CameraFramingStatus.TOO_RIGHT -> Color.rgb(0xFF, 0xC1, 0x07)
            CameraFramingStatus.UNKNOWN,
            null -> return
        }
        canvas.drawRect(
            width * 0.12f,
            height * 0.03f,
            width * 0.88f,
            height * 0.97f,
            framingPaint
        )
    }
}
