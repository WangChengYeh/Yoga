package com.yogaflow

import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.yogaflow.coach.CoachPhrasePolisher
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class SessionState { IDLE, RUNNING, PAUSED, COMPLETED }

    private lateinit var homeView: View
    private lateinit var classView: View
    private lateinit var previewView: androidx.camera.view.PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var coachText: TextView
    private lateinit var flowName: TextView
    private lateinit var progressText: TextView
    private lateinit var countdownText: TextView
    private lateinit var llmStatus: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var startClassButton: Button
    private lateinit var startStretchButton: Button
    private lateinit var startRecoveryButton: Button
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var restartButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private lateinit var poseHelper: PoseHelper
    private lateinit var cameraPipeline: CameraPosePipeline
    private lateinit var tts: TextToSpeech
    private lateinit var speaker: CoachSpeaker
    private lateinit var llmCoach: LlmCoach

    private val stateMachine = PoseStateMachine()
    private val flowEngine = PoseFlowEngine()
    private val playlist = FlowPlaylistEngine()

    private lateinit var currentFlow: YogaFlow
    private lateinit var currentPose: YogaPose
    private var sessionState = SessionState.IDLE
    private var lastCountdownText = ""

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
        startStretchButton = findViewById(R.id.startStretchButton)
        startRecoveryButton = findViewById(R.id.startRecoveryButton)
        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)
        restartButton = findViewById(R.id.restartButton)
    }

    private fun initRuntime() {
        loadDiscoveredPlaylist(openClassView = false)

        poseHelper = PoseHelper(this)
        cameraPipeline = CameraPosePipeline(this, this, previewView, poseHelper, cameraExecutor)

        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                if (sessionState != SessionState.RUNNING) {
                    updateUi(false)
                    return@runOnUiThread
                }

                val (detectedState, _) = stateMachine.update(currentPose, landmarks)
                val (flowState, flowCue) = flowEngine.update(currentFlow, detectedState)

                if (flowEngine.isLastStep(currentFlow)
                    && flowEngine.remainingSeconds(currentFlow) == 0L
                    && flowEngine.isCurrentStepSatisfied(currentFlow, detectedState)
                ) {
                    advanceFlowOrComplete()
                    updateUi(true)
                    return@runOnUiThread
                }

                val generated = llmCoach.generate(currentPose, flowState, flowCue)
                val isFallback = generated == flowCue
                val displayText = if (isFallback) "(fallback) $generated" else generated
                val polished = CoachPhrasePolisher.polish(displayText)

                llmStatus.text = if (isFallback) "LLM: OFF" else "LLM: ON"
                coachText.text = polished
                speaker.speakIfNeeded(polished)

                updateUi(true)
            }
        }
    }

    private fun startCamera() {
        cameraPipeline.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraPipeline.stop()
        cameraExecutor.shutdown()
        tts.shutdown()
    }

    override fun onInit(status: Int) {
        tts.language = Locale.TAIWAN
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}
