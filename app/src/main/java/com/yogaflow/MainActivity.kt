package com.yogaflow

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.yoga.YogaPoseCatalog
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coachText: TextView
    private lateinit var flowSelector: Spinner

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var poseHelper: PoseHelper
    private lateinit var tts: TextToSpeech
    private lateinit var speaker: CoachSpeaker
    private lateinit var llmCoach: LlmCoach

    private val stateMachine = PoseStateMachine()
    private val flowEngine = PoseFlowEngine()

    private lateinit var currentFlow: YogaFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        coachText = findViewById(R.id.coachText)
        flowSelector = findViewById(R.id.poseSelector)

        poseHelper = PoseHelper(this)
        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        setupFlowSelector()

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                val pose = YogaPoseCatalog.poses.first()
                val (state, raw) = stateMachine.update(pose, landmarks)

                val (flowState, flowCue) = flowEngine.update(currentFlow, state)

                val coaching = llmCoach.generate(pose, flowState, flowCue)

                coachText.text = coaching
                speaker.speakIfNeeded(coaching)
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

    private fun setupFlowSelector() {
        val flows = listOf("flows/demo_forward_fold.flow.txt")

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, flows)
        flowSelector.adapter = adapter

        flowSelector.setSelection(0)
        currentFlow = FlowLoader.loadFromAssets(this, flows[0])

        flowSelector.setOnItemSelectedListener { _, _, position, _ ->
            currentFlow = FlowLoader.loadFromAssets(this, flows[position])
            flowEngine.reset()
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
