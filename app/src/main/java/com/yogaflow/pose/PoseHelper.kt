package com.yogaflow.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseHelper(context: Context) {

    private var detector: PoseLandmarker? = null
    private var frameTimestampMs: Long = 0L
    private var lastWidth: Int = 0
    private var lastHeight: Int = 0

    var isReady: Boolean = false
        private set

    var isMirrored: Boolean = false

    var onResult: ((PoseDetectionResult) -> Unit)? = null

    init {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: PoseLandmarkerResult, _ ->
                    val imagePoints = result.landmarks().firstOrNull()
                    if (imagePoints == null || imagePoints.isEmpty()) return@setResultListener

                    val worldPoints = result.worldLandmarks().firstOrNull() ?: emptyList()

                    val finalImagePoints = if (isMirrored) {
                        imagePoints.map {
                            NormalizedLandmark.create(1f - it.x(), it.y(), it.z(), it.visibility(), it.presence())
                        }
                    } else {
                        imagePoints
                    }

                    val finalWorldPoints = if (isMirrored) {
                        worldPoints.map {
                            Landmark.create(-it.x(), it.y(), it.z(), it.visibility(), it.presence())
                        }
                    } else {
                        worldPoints
                    }

                    onResult?.invoke(
                        PoseDetectionResult(
                            imageLandmarks = finalImagePoints,
                            worldLandmarks = finalWorldPoints,
                            imageWidth = lastWidth,
                            imageHeight = lastHeight,
                            isMirrored = isMirrored
                        )
                    )
                }
                .build()

            detector = PoseLandmarker.createFromOptions(context, options)
            isReady = true

        } catch (e: Exception) {
            Log.e("Pose", "init error", e)
            isReady = false
        }
    }

    fun detect(imageProxy: ImageProxy) {
        if (!isReady) {
            imageProxy.close()
            return
        }

        try {
            val bitmapBuffer = Bitmap.createBitmap(
                imageProxy.width,
                imageProxy.height,
                Bitmap.Config.ARGB_8888
            )

            val buffer = imageProxy.planes[0].buffer
            buffer.rewind()
            bitmapBuffer.copyPixelsFromBuffer(buffer)

            val matrix = Matrix().apply {
                postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
            }

            val rotatedBitmap = Bitmap.createBitmap(
                bitmapBuffer,
                0,
                0,
                bitmapBuffer.width,
                bitmapBuffer.height,
                matrix,
                true
            )

            lastWidth = rotatedBitmap.width
            lastHeight = rotatedBitmap.height

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()
            frameTimestampMs = maxOf(frameTimestampMs + 1, SystemClock.uptimeMillis())
            detector?.detectAsync(mpImage, frameTimestampMs)

        } finally {
            imageProxy.close()
        }
    }
}
