package com.yogaflow.pose

import android.content.Context
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import com.google.mediapipe.framework.image.MediaImageBuilder

class PoseHelper(context: Context) {

    private var detector: PoseLandmarker? = null
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

        } catch (e: Exception) {
            Log.e("Pose", "init error", e)
        }
    }

    fun detect(imageProxy: ImageProxy) {
        val image = imageProxy.image ?: return
        val mp = MediaImageBuilder(image).build()
        detector?.detectAsync(mp, System.currentTimeMillis())
    }
}
