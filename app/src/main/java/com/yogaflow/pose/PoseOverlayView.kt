package com.yogaflow.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.coach.PoseStateMachine
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

class PoseOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var landmarks: List<NormalizedLandmark> = emptyList()
    private var imageWidth: Int = 0
    private var imageHeight: Int = 0
    private var framingStatus: CameraFramingStatus? = null
    private var jointStatus: Map<Int, Boolean> = emptyMap()
    private var jointCorrections: Map<Int, PoseStateMachine.JointCorrection> = emptyMap()

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

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0xFF, 0xC1, 0x07) // amber, drawn above skeleton
        strokeWidth = 6f
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private val arrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(220, 0xFF, 0xC1, 0x07)
        style = Paint.Style.FILL
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

    fun setLandmarks(newLandmarks: List<NormalizedLandmark>, imageWidth: Int, imageHeight: Int) {
        landmarks = newLandmarks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        invalidate()
    }

    fun setFramingStatus(status: CameraFramingStatus?) {
        framingStatus = status
        invalidate()
    }

    fun setJointStatus(status: Map<Int, Boolean>) {
        jointStatus = status
        invalidate()
    }

    fun setJointCorrections(corrections: Map<Int, PoseStateMachine.JointCorrection>) {
        jointCorrections = corrections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (landmarks.isNotEmpty()) {
            drawSkeleton(canvas)
            if (jointCorrections.isNotEmpty()) drawCorrectionArrows(canvas)
        }

        drawFramingBox(canvas)
    }

    private fun drawSkeleton(canvas: Canvas) {
        val hasValidImageSize = imageWidth > 0 && imageHeight > 0
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val scaledWidth: Float
        val scaledHeight: Float
        val offsetX: Float
        val offsetY: Float
        if (hasValidImageSize) {
            val scaleX = viewWidth / imageWidth.toFloat()
            val scaleY = viewHeight / imageHeight.toFloat()
            val scale = maxOf(scaleX, scaleY)
            scaledWidth = imageWidth.toFloat() * scale
            scaledHeight = imageHeight.toFloat() * scale
            offsetX = (viewWidth - scaledWidth) / 2f
            offsetY = (viewHeight - scaledHeight) / 2f
        } else {
            scaledWidth = viewWidth
            scaledHeight = viewHeight
            offsetX = 0f
            offsetY = 0f
        }

        for ((startIndex, endIndex) in connections) {
            if (startIndex < landmarks.size && endIndex < landmarks.size) {
                val start = landmarks[startIndex]
                val end = landmarks[endIndex]
                canvas.drawLine(
                    start.x() * scaledWidth + offsetX,
                    start.y() * scaledHeight + offsetY,
                    end.x() * scaledWidth + offsetX,
                    end.y() * scaledHeight + offsetY,
                    linePaint
                )
            }
        }

        landmarks.forEachIndexed { index, point ->
            pointPaint.color = when (jointStatus[index]) {
                true -> Color.rgb(0x4C, 0xAF, 0x50)
                false -> Color.rgb(0xF4, 0x43, 0x36)
                null -> Color.WHITE
            }
            canvas.drawCircle(
                point.x() * scaledWidth + offsetX,
                point.y() * scaledHeight + offsetY,
                6f,
                pointPaint
            )
        }
    }

    /**
     * For each deviated joint, draws a bisector arrow showing which way to move.
     * Positive deviation (angle too large) → arrow points inward along bisector (close the angle).
     * Negative deviation (angle too small) → arrow points outward (open the angle).
     */
    private fun drawCorrectionArrows(canvas: Canvas) {
        val hasValidImageSize = imageWidth > 0 && imageHeight > 0
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        val scaledWidth: Float
        val scaledHeight: Float
        val offsetX: Float
        val offsetY: Float
        if (hasValidImageSize) {
            val scaleX = viewWidth / imageWidth.toFloat()
            val scaleY = viewHeight / imageHeight.toFloat()
            val scale = maxOf(scaleX, scaleY)
            scaledWidth = imageWidth.toFloat() * scale
            scaledHeight = imageHeight.toFloat() * scale
            offsetX = (viewWidth - scaledWidth) / 2f
            offsetY = (viewHeight - scaledHeight) / 2f
        } else {
            scaledWidth = viewWidth
            scaledHeight = viewHeight
            offsetX = 0f
            offsetY = 0f
        }

        val density = resources.displayMetrics.density
        val maxArrowLen = 70f * density
        val minArrowLen = 22f * density
        val headSize = 14f * density

        for ((_, correction) in jointCorrections) {
            if (correction.a >= landmarks.size || correction.b >= landmarks.size || correction.c >= landmarks.size) continue
            val lA = landmarks[correction.a]
            val lB = landmarks[correction.b]
            val lC = landmarks[correction.c]

            val bx = lB.x() * scaledWidth + offsetX
            val by = lB.y() * scaledHeight + offsetY

            // Vectors from B toward A and C
            val bax = lA.x() * scaledWidth + offsetX - bx
            val bay = lA.y() * scaledHeight + offsetY - by
            val bcx = lC.x() * scaledWidth + offsetX - bx
            val bcy = lC.y() * scaledHeight + offsetY - by

            val lenBA = hypot(bax, bay)
            val lenBC = hypot(bcx, bcy)
            if (lenBA < 1f || lenBC < 1f) continue

            // Normalised bisector = average of unit vectors BA and BC
            var bisX = bax / lenBA + bcx / lenBC
            var bisY = bay / lenBA + bcy / lenBC
            val bisLen = hypot(bisX, bisY)
            if (bisLen < 0.001f) continue
            bisX /= bisLen
            bisY /= bisLen

            // Positive deviation → close angle → arrow along bisector (inward)
            // Negative deviation → open angle → arrow opposite bisector (outward)
            val sign = if (correction.deviationDeg > 0f) 1f else -1f
            val arrowLen = min(maxArrowLen, minArrowLen + abs(correction.deviationDeg) / 45f * maxArrowLen)

            val dx = sign * bisX * arrowLen
            val dy = sign * bisY * arrowLen
            val tipX = bx + dx
            val tipY = by + dy

            canvas.drawLine(bx, by, tipX, tipY, arrowPaint)

            // Arrowhead: two lines back from tip at ±30°
            val angle = atan2(dy, dx)
            val path = Path()
            path.moveTo(tipX, tipY)
            path.lineTo(
                tipX - headSize * cos(angle - 0.5f),
                tipY - headSize * sin(angle - 0.5f)
            )
            path.lineTo(
                tipX - headSize * cos(angle + 0.5f),
                tipY - headSize * sin(angle + 0.5f)
            )
            path.close()
            canvas.drawPath(path, arrowHeadPaint)
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
