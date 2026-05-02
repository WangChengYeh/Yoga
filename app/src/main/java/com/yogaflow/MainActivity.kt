package com.yogaflow

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.yogaflow.coach.CoachPhrasePolisher
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class SessionState {
        IDLE,
        RUNNING,
        PAUSED,
        COMPLETED
    }

    private lateinit var homeView: View
    private lateinit var classView: View
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coachText: TextView
    private lateinit var flowName: TextView
    private lateinit var progressText: TextView
    private lateinit var countdownText: TextView
    private lateinit var llmStatus: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var startClassButton: Button
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var restartButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var poseHelper: PoseHelper
    private lateinit var tts: TextToSpeech
    private lateinit var speaker: CoachSpeaker
    private lateinit var llmCoach: LlmCoach

    private val stateMachine = PoseStateMachine()
    private val flowEngine = PoseFlowEngine()

    private lateinit var currentFlow: YogaFlow
    private lateinit var currentPose: YogaPose
    private var sessionState = SessionState.IDLE
    private var lastCountdownSpoken: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        initRuntime()
        setupButtons()
        showHome()

        if (checkSelfPermission(android.Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST)
        }
    }

    private fun bindViews() {
        homeView = findViewById(R.id.homeView)
        classView = findViewById(R.id.classView)
        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        coachText = findViewById(R.id.coachText)
        flowName = findViewById(R.id.flowName)
        progressText = findViewById(R.id.progressText)
        countdownText = findViewById(R.id.countdownText)
        llmStatus = findViewById(R.id.llmStatus)
        progressBar = findViewById(R.id.progressBar)
        startClassButton = findViewById(R.id.startClassButton)
        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)
        restartButton = findViewById(R.id.restartButton)
    }

    private fun initRuntime() {
        currentFlow = FlowLoader.loadFromAssets(this, BEGINNER_FLOW_ASSET)
        currentPose = YogaPoseCatalog.poses.firstOrNull { it.id == currentFlow.pose }
            ?: YogaPoseCatalog.poses.first()

        poseHelper = PoseHelper(this)
        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        updateCourseUi()

        if (!poseHelper.isReady) {
            coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
        }

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                if (sessionState != SessionState.RUNNING) {
                    updateCourseUi()
                    return@runOnUiThread
                }

                val (detectedState, _) = stateMachine.update(currentPose, landmarks)
                val (flowState, flowCue) = flowEngine.update(currentFlow, detectedState)
                val generated = llmCoach.generate(currentPose, flowState, flowCue)
                val isFallback = generated == flowCue
                val displayText = if (isFallback) "(fallback) $generated" else generated
                val polished = CoachPhrasePolisher.polish(displayText)

                llmStatus.text = if (isFallback) "LLM: OFF" else "LLM: ON"
                coachText.text = polished
                speaker.speakIfNeeded(polished)

                updateCourseUi()
                speakCountdownIfNeeded()
            }
        }
    }

    private fun setupButtons() {
        startClassButton.setOnClickListener {
            showClass()
            resetClass()
        }

        startButton.setOnClickListener {
            sessionState = SessionState.RUNNING
            coachText.text = "開始練習，跟著我的節奏。"
            speaker.speakIfNeeded("開始練習，跟著我的節奏。")
        }

        pauseButton.setOnClickListener {
            sessionState = SessionState.PAUSED
            coachText.text = "已暫停。準備好後按 Start 繼續。"
        }

        restartButton.setOnClickListener {
            resetClass()
            coachText.text = "已重新開始，按 Start 開始課程。"
        }
    }

    private fun showHome() {
        homeView.visibility = View.VISIBLE
        classView.visibility = View.GONE
    }

    private fun showClass() {
        homeView.visibility = View.GONE
        classView.visibility = View.VISIBLE
    }

    private fun resetClass() {
        sessionState = SessionState.IDLE
        flowEngine.reset()
        lastCountdownSpoken = -1L
        updateCourseUi()
    }

    private fun updateCourseUi() {
        flowName.text = currentFlow.name
        val step = flowEngine.currentStepNumber()
        val total = flowEngine.totalSteps(currentFlow)
        progressText.text = "Step $step / $total"
        progressBar.progress = ((step.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
        countdownText.text = when (sessionState) {
            SessionState.IDLE -> "Ready"
            SessionState.PAUSED -> "Paused"
            SessionState.COMPLETED -> "Done"
            SessionState.RUNNING -> flowEngine.remainingSeconds(currentFlow).toString()
        }
    }

    private fun speakCountdownIfNeeded() {
        val seconds = flowEngine.remainingSeconds(currentFlow)
        if (seconds in 1..3 && seconds != lastCountdownSpoken) {
            speaker.speakIfNeeded(seconds.toString())
            lastCountdownSpoken = seconds
        }
    }

    override fun onInit(status: Int) {
        tts.language = Locale.TAIWAN
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.shutdown()
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

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val BEGINNER_FLOW_ASSET = "flows/02_forward_fold_main.flow.txt"
    }
}
