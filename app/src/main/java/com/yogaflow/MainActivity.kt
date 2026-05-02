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
import androidx.camera.view.PreviewView
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
    private lateinit var previewView: PreviewView
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else if (requestCode == CAMERA_PERMISSION_REQUEST) {
            coachText.text = "Camera permission is required to start pose coaching."
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
        cameraPipeline = CameraPosePipeline(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            poseHelper = poseHelper,
            cameraExecutor = cameraExecutor
        )

        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        if (!poseHelper.isReady) {
            coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
        }

        poseHelper.onResult = { landmarks ->
            runOnUiThread {
                overlayView.setLandmarks(landmarks)

                if (sessionState != SessionState.RUNNING) {
                    updateUi(animated = false)
                    return@runOnUiThread
                }

                val (detectedState, _) = stateMachine.update(currentPose, landmarks)
                val (flowState, flowCue) = flowEngine.update(currentFlow, detectedState)

                if (flowEngine.isLastStep(currentFlow)
                    && flowEngine.remainingSeconds(currentFlow) == 0L
                    && flowEngine.isCurrentStepSatisfied(currentFlow, detectedState)
                ) {
                    advanceFlowOrComplete()
                    updateUi(animated = true)
                    return@runOnUiThread
                }

                val generated = llmCoach.generate(currentPose, flowState, flowCue)
                val isFallback = generated == flowCue
                val displayText = if (isFallback) "(fallback) $generated" else generated
                val polished = CoachPhrasePolisher.polish(displayText)

                llmStatus.text = if (isFallback) "LLM: OFF" else "LLM: ON"
                coachText.text = polished
                speaker.speakIfNeeded(polished)

                updateUi(animated = true)
            }
        }
    }

    private fun setupButtons() {
        startClassButton.setOnClickListener {
            loadDiscoveredPlaylist(openClassView = true)
        }

        startStretchButton.setOnClickListener {
            loadPlaylist(listOf("flows/02_forward_fold_main.flow.txt"), openClassView = true)
        }

        startRecoveryButton.setOnClickListener {
            loadPlaylist(listOf("flows/03_twist_cooldown.flow.txt"), openClassView = true)
        }

        startButton.setOnClickListener {
            sessionState = SessionState.RUNNING
            coachText.text = "開始練習，跟著我的節奏。"
            speaker.speakIfNeeded("開始練習，跟著我的節奏。")
            updateUi(animated = true)
        }

        pauseButton.setOnClickListener {
            sessionState = SessionState.PAUSED
            coachText.text = "已暫停。準備好後按 Start 繼續。"
            updateUi(animated = false)
        }

        restartButton.setOnClickListener {
            restartCurrentPlaylist()
        }
    }

    private fun loadDiscoveredPlaylist(openClassView: Boolean = true) {
        val flows = FlowLoader.loadAllFromAssets(this)
        applyPlaylist(flows, openClassView)
    }

    private fun loadPlaylist(paths: List<String>, openClassView: Boolean = true) {
        val flows = paths.map { FlowLoader.loadFromAssets(this, it) }
        applyPlaylist(flows, openClassView)
    }

    private fun applyPlaylist(flows: List<YogaFlow>, openClassView: Boolean) {
        if (flows.isEmpty()) {
            coachText.text = "No yoga flows found in assets/flows."
            return
        }

        playlist.setPlaylist(flows)
        currentFlow = playlist.current()!!
        currentPose = resolvePose(currentFlow)
        flowEngine.reset()
        sessionState = SessionState.IDLE
        lastCountdownText = ""
        coachText.text = "按 Start 開始課程。"
        llmStatus.text = "LLM: OFF"

        if (openClassView) showClass()
        updateUi(animated = false)
    }

    private fun restartCurrentPlaylist() {
        playlist.reset()
        flowEngine.reset()
        currentFlow = playlist.current()!!
        currentPose = resolvePose(currentFlow)
        sessionState = SessionState.IDLE
        lastCountdownText = ""
        coachText.text = "已重新開始，按 Start 開始課程。"
        updateUi(animated = false)
    }

    private fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose }
            ?: YogaPoseCatalog.poses.first()
    }

    private fun advanceFlowOrComplete() {
        val next = playlist.moveNext()
        if (next != null) {
            currentFlow = next
            currentPose = resolvePose(currentFlow)
            flowEngine.reset()
            animateFlowTransition()
            speaker.speakIfNeeded("下一個動作")
        } else {
            sessionState = SessionState.COMPLETED
            coachText.text = "課程完成，很好。"
            speaker.speakIfNeeded("課程完成，很好。")
        }
    }

    private fun updateUi(animated: Boolean) {
        if (!::currentFlow.isInitialized) return

        flowName.text = "Flow ${playlist.currentNumber()}/${playlist.total()} · ${currentFlow.name}"

        val step = flowEngine.currentStepNumber()
        val total = flowEngine.totalSteps(currentFlow)
        progressText.text = "Step $step/$total"

        val progress = ((step.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
        if (animated) animateProgress(progress) else progressBar.progress = progress

        val countdown = when (sessionState) {
            SessionState.IDLE -> "Ready"
            SessionState.PAUSED -> "Paused"
            SessionState.COMPLETED -> "Done"
            SessionState.RUNNING -> flowEngine.remainingSeconds(currentFlow).toString()
        }
        updateCountdown(countdown)
    }

    private fun animateProgress(progress: Int) {
        ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, progress).apply {
            duration = 350L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private fun updateCountdown(text: String) {
        if (text == lastCountdownText) return

        countdownText.text = text
        lastCountdownText = text

        if (sessionState == SessionState.RUNNING) {
            val number = text.toIntOrNull()
            if (number != null && number in 1..3) {
                speaker.speakIfNeeded(number.toString())
            }
        }

        countdownText.scaleX = 1.35f
        countdownText.scaleY = 1.35f
        countdownText.alpha = 0.4f
        countdownText.animate()
            .scaleX(1f)
            .scaleY(1f)
            .alpha(1f)
            .setDuration(250L)
            .start()
    }

    private fun animateFlowTransition() {
        flowName.alpha = 0f
        flowName.translationY = -20f
        flowName.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(400L)
            .start()
    }

    private fun showHome() {
        homeView.visibility = View.VISIBLE
        classView.visibility = View.GONE
    }

    private fun showClass() {
        homeView.visibility = View.GONE
        classView.visibility = View.VISIBLE
    }

    private fun startCamera() {
        cameraPipeline.start()
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.TAIWAN
        }
    }

    override fun onDestroy() {
        if (::cameraPipeline.isInitialized) {
            cameraPipeline.stop()
        }
        cameraExecutor.shutdown()
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
    }
}
