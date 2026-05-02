package com.yogaflow.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult

class PoseHelper(context: Context) {

    private var detector: PoseLandmarker? = null
    private var frameTimestampMs: Long = 0L

    var isReady: Boolean = false
        private set

    var onResult: ((List<com.google.mediapipe.tasks.components.containers.NormalizedLandmark>) -> Unit)? = null

    init {
        try {
            val base = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_lite.task")
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(base)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result: PoseLandmarkerResult, _ ->
                    if (result.landmarks().isNotEmpty()) {
                        onResult?.invoke(result.landmarks()[0])
                    }
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

            val rotation = imageProxy.imageInfo.rotationDegrees

            val matrix = Matrix().apply {
                postRotate(rotation.toFloat())
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

            val mpImage = BitmapImageBuilder(rotatedBitmap).build()

            frameTimestampMs = maxOf(frameTimestampMs + 1, SystemClock.uptimeMillis())

            detector?.detectAsync(mpImage, frameTimestampMs)

        } finally {
            imageProxy.close()
        }
    }
}
