package com.yogaflow.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()

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

    private val framingBoxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0, 255, 0)
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }

    private val framingBoxFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 0, 255, 0)
        style = Paint.Style.FILL
    }

    private val framingGuidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(180, 255, 255, 255)
        strokeWidth = 2f
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

        drawFramingGuide(canvas)
        drawBodyFramingBox(canvas)
        drawSkeleton(canvas)
    }

    private fun drawFramingGuide(canvas: Canvas) {
        val guide = RectF(
            width * GUIDE_LEFT,
            height * GUIDE_TOP,
            width * GUIDE_RIGHT,
            height * GUIDE_BOTTOM
        )
        canvas.drawRoundRect(guide, GUIDE_CORNER_RADIUS, GUIDE_CORNER_RADIUS, framingGuidePaint)
    }

    private fun drawBodyFramingBox(canvas: Canvas) {
        val visibleLandmarks = landmarks.filter { point ->
            point.x() in 0f..1f && point.y() in 0f..1f
        }
        if (visibleLandmarks.isEmpty()) return

        val minX = visibleLandmarks.minOf { it.x() } * width
        val maxX = visibleLandmarks.maxOf { it.x() } * width
        val minY = visibleLandmarks.minOf { it.y() } * height
        val maxY = visibleLandmarks.maxOf { it.y() } * height

        val paddingX = width * BODY_BOX_PADDING_X
        val paddingY = height * BODY_BOX_PADDING_Y
        val bodyBox = RectF(
            (minX - paddingX).coerceAtLeast(0f),
            (minY - paddingY).coerceAtLeast(0f),
            (maxX + paddingX).coerceAtMost(width.toFloat()),
            (maxY + paddingY).coerceAtMost(height.toFloat())
        )

        canvas.drawRoundRect(bodyBox, BODY_BOX_CORNER_RADIUS, BODY_BOX_CORNER_RADIUS, framingBoxFillPaint)
        canvas.drawRoundRect(bodyBox, BODY_BOX_CORNER_RADIUS, BODY_BOX_CORNER_RADIUS, framingBoxPaint)
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

    companion object {
        private const val GUIDE_LEFT = 0.12f
        private const val GUIDE_TOP = 0.08f
        private const val GUIDE_RIGHT = 0.88f
        private const val GUIDE_BOTTOM = 0.92f
        private const val GUIDE_CORNER_RADIUS = 28f
        private const val BODY_BOX_PADDING_X = 0.035f
        private const val BODY_BOX_PADDING_Y = 0.035f
        private const val BODY_BOX_CORNER_RADIUS = 24f
    }
}
