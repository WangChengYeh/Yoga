package com.yogaflow.pose

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.MediaImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.ImageProcessingOptions
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
        if (!isReady) return
        val image = imageProxy.image ?: return

        val mpImage = MediaImageBuilder(image).build()

        val rotation = imageProxy.imageInfo.rotationDegrees
        val processingOptions = ImageProcessingOptions.builder()
            .setRotationDegrees(rotation)
            .build()

        frameTimestampMs += FRAME_TIMESTAMP_STEP_MS
        detector?.detectAsync(mpImage, processingOptions, frameTimestampMs)
    }

    companion object {
        private const val FRAME_TIMESTAMP_STEP_MS = 33L
    }
}
