package com.yogaflow.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()

    private val pointPaint = Paint().apply {
        strokeWidth = 8f
        style = Paint.Style.FILL
    }

    private val linePaint = Paint().apply {
        strokeWidth = 5f
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (landmarks.isEmpty()) return

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
}
