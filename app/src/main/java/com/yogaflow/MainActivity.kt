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
import com.yogaflow.coach.*
import com.yogaflow.flow.*
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.*
import com.yogaflow.yoga.*
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class SessionState { IDLE, RUNNING, PAUSED, COMPLETED }

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
    private val playlist = FlowPlaylistEngine()

    private lateinit var currentFlow: YogaFlow
    private lateinit var currentPose: YogaPose
    private var sessionState = SessionState.IDLE

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
            ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.CAMERA), 100)
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
        val flows = listOf(
            FlowLoader.loadFromAssets(this, "flows/01_mountain_warmup.flow.txt"),
            FlowLoader.loadFromAssets(this, "flows/02_forward_fold_main.flow.txt"),
            FlowLoader.loadFromAssets(this, "flows/03_twist_cooldown.flow.txt")
        )

        playlist.setPlaylist(flows)
        currentFlow = playlist.current()!!
        currentPose = YogaPoseCatalog.poses.first { it.id == currentFlow.pose }

        poseHelper = PoseHelper(this)
        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        updateUi()

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                if (sessionState != SessionState.RUNNING) {
                    updateUi()
                    return@runOnUiThread
                }

                val (detectedState, _) = stateMachine.update(currentPose, landmarks)
                val (flowState, flowCue) = flowEngine.update(currentFlow, detectedState)

                if (flowEngine.isLastStep(currentFlow) && flowEngine.remainingSeconds(currentFlow) == 0L) {
                    val next = playlist.moveNext()
                    if (next != null) {
                        currentFlow = next
                        flowEngine.reset()
                        currentPose = YogaPoseCatalog.poses.first { it.id == currentFlow.pose }
                        speaker.speakIfNeeded("下一個動作")
                    } else {
                        sessionState = SessionState.COMPLETED
                        speaker.speakIfNeeded("課程完成")
                    }
                }

                val generated = llmCoach.generate(currentPose, flowState, flowCue)
                val polished = CoachPhrasePolisher.polish(generated)

                coachText.text = polished
                speaker.speakIfNeeded(polished)

                updateUi()
            }
        }
    }

    private fun updateUi() {
        flowName.text = "Flow ${playlist.currentNumber()}/${playlist.total()} · ${currentFlow.name}"
        val step = flowEngine.currentStepNumber()
        val total = flowEngine.totalSteps(currentFlow)
        progressText.text = "Step $step/$total"
        progressBar.progress = ((step.toFloat() / total) * 100).toInt().coerceIn(0, 100)
        countdownText.text = when (sessionState) {
            SessionState.IDLE -> "Ready"
            SessionState.PAUSED -> "Paused"
            SessionState.COMPLETED -> "Done"
            SessionState.RUNNING -> flowEngine.remainingSeconds(currentFlow).toString()
        }
    }

    private fun setupButtons() {
        startClassButton.setOnClickListener {
            showClass()
            sessionState = SessionState.IDLE
            updateUi()
        }

        startButton.setOnClickListener {
            sessionState = SessionState.RUNNING
            updateUi()
        }

        pauseButton.setOnClickListener {
            sessionState = SessionState.PAUSED
            updateUi()
        }

        restartButton.setOnClickListener {
            playlist.reset()
            flowEngine.reset()
            currentFlow = playlist.current()!!
            currentPose = YogaPoseCatalog.poses.first { it.id == currentFlow.pose }
            sessionState = SessionState.IDLE
            updateUi()
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

    override fun onInit(status: Int) {
        tts.language = Locale.TAIWAN
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        tts.shutdown()
    }

    private fun startCamera() {
        val providerFuture = ProcessCameraProvider.getInstance(this)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val analyzer = ImageAnalysis.Builder().build().also {
                it.setAnalyzer(cameraExecutor) { image ->
                    poseHelper.detect(image)
                    image.close()
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analyzer)
        }, mainExecutor)
    }
}
