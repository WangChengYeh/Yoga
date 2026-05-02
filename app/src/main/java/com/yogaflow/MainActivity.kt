package com.yogaflow

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PostureAnalyzer
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.coach.CoachSpeaker
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coachText: TextView

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private lateinit var poseHelper: PoseHelper
    private lateinit var tts: TextToSpeech
    private lateinit var speaker: CoachSpeaker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        coachText = findViewById(R.id.coachText)

        poseHelper = PoseHelper(this)
        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                val result = PostureAnalyzer.analyze(landmarks)
                coachText.text = result

                speaker.speakIfNeeded(result)
            }
        }

        if (checkSelfPermission(android.Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this,
                arrayOf(android.Manifest.permission.CAMERA), 100)
        }
    }

    override fun onInit(status: Int) {
        tts.language = Locale.US
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val analyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor) { imageProxy ->
                    poseHelper.detect(imageProxy)
                    imageProxy.close()
                }
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analyzer
            )

        }, mainExecutor)
    }
}
