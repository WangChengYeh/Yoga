package com.yogaflow

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.yogaflow.coach.CoachCueController
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.DetectionMapperSession
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.AutoTuningSuggestion
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.RuntimeOverrideKey
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.flow.YogaFlow
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.session.CameraSetupController
import com.yogaflow.session.LiveCoachSessionController
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    lateinit var homeView: View
    lateinit var classView: View
    lateinit var previewView: PreviewView
    lateinit var overlayView: PoseOverlayView
    lateinit var cameraSetupPanel: View
    lateinit var cameraSetupStatus: TextView
    lateinit var debugText: TextView
    lateinit var coachText: TextView
    lateinit var flowName: TextView
    lateinit var progressText: TextView
    lateinit var countdownText: TextView
    lateinit var llmStatus: TextView
    lateinit var progressBar: ProgressBar
    lateinit var squatThresholdLabel: TextView
    lateinit var bridgeThresholdLabel: TextView
    lateinit var squatThresholdSeekBar: SeekBar
    lateinit var bridgeThresholdSeekBar: SeekBar
    lateinit var startClassButton: Button
    lateinit var startStretchButton: Button
    lateinit var startRecoveryButton: Button
    lateinit var startButton: Button
    lateinit var pauseButton: Button
    lateinit var restartButton: Button
    private lateinit var applySuggestionButton: Button

    lateinit var cameraPipeline: CameraPosePipeline
    lateinit var speaker: CoachSpeaker

    val playlist = FlowPlaylistEngine()
    val flowEngine = PoseFlowEngine()
    val detectionMapperSession = DetectionMapperSession()
    val runtimeOverrideStore = RuntimeOverrideStore()

    lateinit var currentFlow: YogaFlow
    lateinit var currentPose: YogaPose

    var sessionState = SessionState.IDLE
    var cameraReady = false
    var cameraReadySince = 0L
    var autoStartedCurrentSetup = false
    var suppressTuningCallbacks = false
    var lastCountdownText = ""
    var latestSuggestion: AutoTuningSuggestion? = null

    private lateinit var poseHelper: PoseHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var coachExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private lateinit var coachCueController: CoachCueController
    lateinit var cameraSetupController: CameraSetupController
    private lateinit var liveCoachSessionController: LiveCoachSessionController
    private val stateMachine = PoseStateMachine()
    private val autoTuningAdvisor = AutoTuningAdvisor()

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else coachText.text = "Camera permission is required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViewsMain()
        applySuggestionButton = findViewById(R.id.applySuggestionButton)
        loadThresholdPreferencesMain()

        cameraExecutor = Executors.newSingleThreadExecutor()
        coachExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) tts.language = Locale.TRADITIONAL_CHINESE
        }
        speaker = CoachSpeaker(tts)
        poseHelper = PoseHelper(this)
        poseHelper.onResult = { frame -> runOnUiThread { handlePoseFrame(frame) } }
        cameraPipeline = CameraPosePipeline(this, this, previewView, poseHelper, cameraExecutor) {
            runOnUiThread { coachText.text = "Camera start failed: ${it.message.orEmpty()}" }
        }

        coachCueController = CoachCueController(
            llmCoach = com.yogaflow.llm.LlmCoach(this),
            speaker = speaker,
            executor = coachExecutor,
            uiExecutor = { runnable -> runOnUiThread(runnable) },
            minCueIntervalMs = 1200L,
            sameCueIntervalMs = 3500L,
            onDisplay = { displayText, llmEnabled ->
                coachText.text = displayText
                llmStatus.text = if (llmEnabled) "LLM: ON" else "LLM: OFF"
            },
            isRequestCurrent = { flowId, step ->
                isCurrentFlowInitialized() &&
                    currentFlow.id == flowId && flowEngine.currentStepNumber() == step
            }
        )

        cameraSetupController = CameraSetupController(
            autoStartEnabled = true,
            autoStartStableMs = CAMERA_AUTO_START_STABLE_MS,
            getSessionState = { sessionState },
            onReadyChanged = { ready, readySince, autoStarted ->
                cameraReady = ready
                cameraReadySince = readySince
                autoStartedCurrentSetup = autoStarted
                startButton.isEnabled = ready
                startButton.alpha = if (ready) 1f else 0.45f
            },
            setSetupPanelVisible = { visible ->
                cameraSetupPanel.visibility = if (visible) View.VISIBLE else View.GONE
            },
            onUpdateSetupPanel = { ready, framingMessage, orientationMessage ->
                cameraSetupPanel.visibility = View.VISIBLE
                cameraSetupStatus.text = if (ready) {
                    "Ready"
                } else {
                    listOf(framingMessage, orientationMessage).firstOrNull { it.isNotBlank() }.orEmpty()
                }
            },
            onAutoStartReady = ::beginRunningSession,
            onSpeakCoachCue = ::speakCoachCue,
            onUpdateDebugOverlay = { frame, detect, state, matched ->
                updateDebugOverlay(frame, detect, state, matched)
            },
            onUpdateUi = ::updateUi
        )

        liveCoachSessionController = LiveCoachSessionController(
            stateMachine = stateMachine,
            flowEngine = flowEngine,
            poseDetectionRouter = detectionMapperSession.poseDetectionRouter,
            runtimeOverrideStore = runtimeOverrideStore,
            autoTuningAdvisor = autoTuningAdvisor,
            onFlowCompleted = ::onFlowCompleted,
            onUpdateRuntimeTuningControls = ::updateRuntimeTuningControls,
            onUpdateDebugOverlay = ::updateDebugOverlay,
            onSpeakCoachCue = ::speakCoachCue,
            onAnimateFlowTransition = ::animateFlowTransition,
            onUpdateUi = ::updateUi,
            buildRuntimeSummary = ::buildRuntimeSummary,
            buildOverrideSummary = ::buildOverrideSummary,
            buildSuggestionSummary = ::buildSuggestionSummary
        )

        setupThresholdControlsMain()
        bindActions()
        loadDiscoveredPlaylist(openClassView = false)
        requestCameraIfNeeded()
    }

    override fun onDestroy() {
        saveThresholdPreferencesMain()
        cameraPipeline.stop()
        cameraExecutor.shutdown()
        coachExecutor.shutdown()
        tts.shutdown()
        super.onDestroy()
    }

    fun isCurrentFlowInitialized(): Boolean = ::currentFlow.isInitialized && ::currentPose.isInitialized

    fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose }
            ?: YogaPoseCatalog.poses.first()
    }

    fun resetCameraSetupController() {
        if (::cameraSetupController.isInitialized) {
            cameraSetupController.reset()
        }
    }

    fun updateRuntimeTuningControls() {
        val bindings = computeCurrentTuningBindings()
        val labels = listOf(squatThresholdLabel, bridgeThresholdLabel)
        val seekBars = listOf(squatThresholdSeekBar, bridgeThresholdSeekBar)

        suppressTuningCallbacks = true
        labels.zip(seekBars).forEachIndexed { index, (label, seekBar) ->
            val binding = bindings.getOrNull(index)
            if (binding == null) {
                label.text = "Tuning: -"
                seekBar.progress = 0
            } else {
                val value = runtimeOverrideStore.valueFor(binding.key) ?: binding.param.value
                label.text = "${binding.param.label}: ${formatTuningValue(value, binding.param)}"
                seekBar.progress = valueToSliderProgress(value, binding.param, TUNING_SLIDER_MAX)
            }
        }
        suppressTuningCallbacks = false
    }

    private fun bindActions() {
        startClassButton.setOnClickListener { loadDiscoveredPlaylist() }
        startStretchButton.setOnClickListener { loadPlaylist(listOf("flows/02_forward_fold_main.flow.json")) }
        startRecoveryButton.setOnClickListener { loadPlaylist(listOf("flows/03_twist_cooldown.flow.json")) }
        startButton.setOnClickListener {
            if (cameraReady) beginRunningSession()
        }
        pauseButton.setOnClickListener {
            togglePause()
        }
        restartButton.setOnClickListener { restartCurrentPlaylist() }
        applySuggestionButton.setOnClickListener { applyLatestSuggestion() }
    }

    private fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun handlePoseFrame(frame: PoseDetectionResult) {
        overlayView.setLandmarks(frame.imageLandmarks)
        if (!isCurrentFlowInitialized()) return

        if (cameraSetupController.handleFrame(frame)) return

        liveCoachSessionController.handleReadyPoseFrame(frame, currentFlow, currentPose)
    }

    private fun beginRunningSession() {
        if (!cameraReady) return
        sessionState = SessionState.RUNNING
        cameraSetupPanel.visibility = View.GONE
        coachCueController.reset()
        updateUi(animated = false)
    }

    private fun togglePause() {
        when (sessionState) {
            SessionState.RUNNING -> {
                cameraSetupController.reset()
                sessionState = SessionState.PAUSED
                cameraReady = false
                cameraReadySince = 0L
                autoStartedCurrentSetup = false
                startButton.isEnabled = false
                startButton.alpha = 0.45f
                cameraSetupPanel.visibility = View.VISIBLE
                cameraSetupStatus.text = "Checking body framing..."
                updateUi(animated = false)
            }
            SessionState.PAUSED -> {
                if (cameraReady) {
                    beginRunningSession()
                } else {
                    coachText.text = "請先重新完成相機設定。"
                    updateUi(animated = false)
                }
            }
            SessionState.IDLE,
            SessionState.COMPLETED -> Unit
        }
    }

    private fun onFlowCompleted(text: String) {
        val next = playlist.moveNext()
        if (next == null) {
            sessionState = SessionState.COMPLETED
            coachText.text = text
            updateUi(animated = true)
            return
        }

        currentFlow = next
        currentPose = resolvePose(next)
        flowEngine.reset()
        detectionMapperSession.resetAll()
        resetToCameraSetup(text)
        updateUi(animated = true)
    }

    private fun speakCoachCue(state: CoachState, cue: String) {
        if (!isCurrentFlowInitialized()) return
        coachCueController.speak(currentPose, currentFlow.id, flowEngine.currentStepNumber(), state, cue)
    }

    private fun updateDebugOverlay(
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean,
        runtimeSummary: String = "",
        overrideSummary: String = "",
        failReason: String = "",
        suggestionSummary: String = ""
    ) {
        debugText.text = listOf(
            "detect=$detect state=$state matched=$matched",
            "landmarks=${frame.imageLandmarks.size} size=${frame.imageWidth}x${frame.imageHeight}",
            runtimeSummary,
            overrideSummary,
            failReason,
            suggestionSummary
        ).filter { it.isNotBlank() }.joinToString("\n")
    }

    private fun buildRuntimeSummary(params: RuntimeParams): String {
        return "runtime stability=${params.stabilityMs ?: "-"} ema=${params.emaAlpha ?: "-"} deadband=${params.deadbandDegrees ?: "-"}"
    }

    private fun buildOverrideSummary(): String {
        val count = computeCurrentTuningBindings().count { runtimeOverrideStore.valueFor(it.key) != null }
        return if (count == 0) "" else "overrides=$count"
    }

    private fun buildSuggestionSummary(flowId: String, stepIndex: Int, detect: com.yogaflow.flow.DetectKey): String {
        latestSuggestion = autoTuningAdvisor.suggestionsFor(flowId, stepIndex, detect).firstOrNull()
        return latestSuggestion?.label.orEmpty()
    }

    private fun applyLatestSuggestion() {
        val suggestion = latestSuggestion ?: return
        runtimeOverrideStore.set(
            RuntimeOverrideKey(suggestion.flowId, suggestion.stepIndex, suggestion.detect, suggestion.path),
            suggestion.suggestedValue
        )
        updateRuntimeTuningControls()
    }

    companion object {
        const val TUNING_SLIDER_MAX = 1000
        private const val CAMERA_AUTO_START_STABLE_MS = 1200L
    }
}
