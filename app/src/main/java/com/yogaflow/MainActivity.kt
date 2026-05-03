package com.yogaflow

import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.yogaflow.coach.CoachCueController
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.AutoTuningSuggestion
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.session.CameraSetupController
import com.yogaflow.session.LiveCoachSessionController
import com.yogaflow.yoga.YogaPose
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    internal lateinit var homeView: View
    internal lateinit var classView: View
    internal lateinit var previewView: PreviewView
    internal lateinit var overlayView: PoseOverlayView
    internal lateinit var cameraSetupPanel: View
    internal lateinit var cameraSetupStatus: TextView
    internal lateinit var debugText: TextView
    internal lateinit var coachText: TextView
    internal lateinit var flowName: TextView
    internal lateinit var progressText: TextView
    internal lateinit var countdownText: TextView
    internal lateinit var llmStatus: TextView
    internal lateinit var progressBar: ProgressBar
    internal lateinit var squatThresholdLabel: TextView
    internal lateinit var bridgeThresholdLabel: TextView
    internal lateinit var squatThresholdSeekBar: SeekBar
    internal lateinit var bridgeThresholdSeekBar: SeekBar

    internal lateinit var startClassButton: Button
    internal lateinit var startStretchButton: Button
    internal lateinit var startRecoveryButton: Button
    internal lateinit var startButton: Button
    internal lateinit var pauseButton: Button
    internal lateinit var restartButton: Button

    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private val coachExecutor = Executors.newSingleThreadExecutor()

    private lateinit var poseHelper: PoseHelper
    internal lateinit var cameraPipeline: CameraPosePipeline
    private lateinit var tts: TextToSpeech
    internal lateinit var speaker: CoachSpeaker
    private lateinit var llmCoach: LlmCoach
    private lateinit var coachCueController: CoachCueController
    private lateinit var liveCoachSessionController: LiveCoachSessionController
    private lateinit var cameraSetupController: CameraSetupController

    private val stateMachine = PoseStateMachine()
    internal val flowEngine = PoseFlowEngine()
    internal val playlist = FlowPlaylistEngine()
    internal val runtimeOverrideStore = RuntimeOverrideStore()
    private val autoTuningAdvisor = AutoTuningAdvisor()
    internal var latestSuggestion: AutoTuningSuggestion? = null
    internal var suppressTuningCallbacks = false

    internal lateinit var currentFlow: YogaFlow
    internal lateinit var currentPose: YogaPose
    internal var sessionState = SessionState.IDLE
    internal var cameraReady = false
    internal var cameraReadySince = 0L
    internal var autoStartedCurrentSetup = false
    internal var lastCountdownText = ""
    internal var lastCoachCue = ""
    internal var lastCoachAt = 0L
    internal var coachRequestId = 0L

    internal fun isCurrentFlowInitialized(): Boolean = ::currentFlow.isInitialized

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViewsMain()
        loadThresholdPreferencesMain()
        initRuntime()
        setupThresholdControlsMain()
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


    private fun initRuntime() {
        loadDiscoveredPlaylist(openClassView = false)

        poseHelper = PoseHelper(this)
        cameraPipeline = CameraPosePipeline(
            context = this,
            lifecycleOwner = this,
            previewView = previewView,
            poseHelper = poseHelper,
            cameraExecutor = cameraExecutor,
            onError = { runOnUiThread { coachText.text = "Camera 啟動失敗，請確認權限或重新開啟 App。" } }
        )

        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)
        coachCueController = CoachCueController(
            llmCoach = llmCoach,
            speaker = speaker,
            executor = coachExecutor,
            uiExecutor = { runOnUiThread(it) },
            minCueIntervalMs = MIN_CUE_INTERVAL_MS,
            sameCueIntervalMs = SAME_CUE_INTERVAL_MS,
            onDisplay = { text, llmOn ->
                llmStatus.text = if (llmOn) "LLM: ON" else "LLM: OFF"
                coachText.text = text
            },
            isRequestCurrent = { requestId, flowId, step ->
                requestId == coachRequestId &&
                    flowId == currentFlow.id &&
                    step == flowEngine.currentStepNumber()
            }
        )
        liveCoachSessionController = LiveCoachSessionController(
            stateMachine = stateMachine,
            flowEngine = flowEngine,
            runtimeOverrideStore = runtimeOverrideStore,
            autoTuningAdvisor = autoTuningAdvisor,
            onFlowCompleted = { completeCurrentFlow(it) },
            onUpdateRuntimeTuningControls = { updateRuntimeTuningControls() },
            onUpdateDebugOverlay = { frame, detect, state, matched, runtimeSummary, overrideSummary, failReason, suggestionSummary ->
                updateDebugOverlay(frame, detect, state, matched, runtimeSummary, overrideSummary, failReason, suggestionSummary)
            },
            onSpeakCoachCue = { state, cue -> speakCoachCue(state, cue) },
            onAnimateFlowTransition = { animateFlowTransition() },
            onUpdateUi = { updateUi(it) },
            buildRuntimeSummary = { buildRuntimeSummary(it) },
            buildOverrideSummary = { buildOverrideSummary() },
            buildSuggestionSummary = { flowId, stepIndex, detect -> buildSuggestionSummary(flowId, stepIndex, detect) }
        )
        cameraSetupController = CameraSetupController(
            autoStartEnabled = AUTO_START_ENABLED,
            autoStartStableMs = CAMERA_AUTO_START_STABLE_MS,
            getSessionState = { sessionState },
            onReadyChanged = { ready, readySince, autoStarted ->
                cameraReady = ready
                cameraReadySince = readySince
                autoStartedCurrentSetup = autoStarted
            },
            getCameraReady = { cameraReady },
            getCameraReadySince = { cameraReadySince },
            getAutoStarted = { autoStartedCurrentSetup },
            setSetupPanelVisible = { visible -> cameraSetupPanel.visibility = if (visible) View.VISIBLE else View.GONE },
            onUpdateSetupPanel = { ready, framingMessage, orientationMessage ->
                updateCameraSetupPanel(ready, framingMessage, orientationMessage)
            },
            onMaybeAutoStart = { maybeAutoStartClass() },
            onSpeakCoachCue = { state, cue -> speakCoachCue(state, cue) },
            onUpdateDebugOverlay = { frame, detect, state, matched ->
                updateDebugOverlay(frame, detect, state, matched)
            },
            onUpdateUi = { updateUi(it) }
        )

        if (!poseHelper.isReady) {
            coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
        }

        poseHelper.onResult = { frame -> runOnUiThread { handlePoseFrame(frame) } }
    }

    private fun handlePoseFrame(frame: PoseDetectionResult) {
        overlayView.setLandmarks(frame.imageLandmarks)

        if (cameraSetupController.handleFrame(frame)) return

        liveCoachSessionController.handleReadyPoseFrame(
            frame = frame,
            currentFlow = currentFlow,
            currentPose = currentPose
        )
    }

    private fun updateCameraSetupPanel(ready: Boolean, framingMessage: String, orientationMessage: String) {
        val now = System.currentTimeMillis()
        if (ready) {
            if (!cameraReady) cameraReadySince = now
        } else {
            cameraReadySince = 0L
            autoStartedCurrentSetup = false
        }

        cameraReady = ready
        cameraSetupPanel.visibility = View.VISIBLE
        startButton.isEnabled = ready
        startButton.alpha = if (ready) 1.0f else 0.45f

        if (ready) {
            val stableFor = now - cameraReadySince
            val remaining = ((CAMERA_AUTO_START_STABLE_MS - stableFor).coerceAtLeast(0L) / 1000.0)
            cameraSetupStatus.text = if (stableFor >= CAMERA_AUTO_START_STABLE_MS) "Ready ✔\nStarting class automatically..." else "Ready ✔\nHold still. Auto-start in %.1fs.".format(remaining)
            coachText.text = "準備好了，請穩住，系統會自動開始。"
        } else {
            val message = when {
                framingMessage.isNotBlank() -> framingMessage
                orientationMessage.isNotBlank() -> orientationMessage
                else -> "Adjust your position until your full body is visible."
            }
            cameraSetupStatus.text = "Not Ready\n$message"
            coachText.text = "請先完成相機設定。"
        }
    }

    private fun maybeAutoStartClass() {
        if (!AUTO_START_ENABLED || !cameraReady || autoStartedCurrentSetup || sessionState != SessionState.IDLE || cameraReadySince == 0L) return
        if (System.currentTimeMillis() - cameraReadySince < CAMERA_AUTO_START_STABLE_MS) return
        autoStartedCurrentSetup = true
        startRunningClass(auto = true)
    }

    private fun startRunningClass(auto: Boolean) {
        if (!cameraReady) {
            coachText.text = "請先完成相機設定。"
            return
        }
        sessionState = SessionState.RUNNING
        cameraReadySince = 0L
        cameraSetupPanel.visibility = View.GONE
        coachText.text = if (auto) "相機設定完成，自動開始練習。" else "開始練習，跟著我的節奏。"
        speaker.speakIfNeeded(if (auto) "相機設定完成，開始練習。" else "開始練習，跟著我的節奏。")
        updateRuntimeTuningControls()
        updateUi(animated = true)
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
        if (!::debugText.isInitialized) return
        if (!DEBUG_OVERLAY_ENABLED) {
            debugText.visibility = View.GONE
            return
        }

        debugText.visibility = View.VISIBLE
        val leftKnee = PoseGeometry.angleDegreesOrNull(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angleDegreesOrNull(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angleDegreesOrNull(frame, 12, 24, 26)
        val leftShoulderHipKnee = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 25)
        val rightShoulderHipKnee = PoseGeometry.angleDegreesOrNull(frame, 12, 24, 26)
        val torsoTwist = if (leftShoulderHipKnee != null && rightShoulderHipKnee != null) abs(leftShoulderHipKnee - rightShoulderHipKnee) else null

        val debugInfo = DebugPoseInfo(
            poseId = if (::currentPose.isInitialized) currentPose.id else "none",
            detect = detect,
            state = state,
            matched = matched,
            leftKneeAngle = leftKnee,
            rightKneeAngle = rightKnee,
            leftHipAngle = leftHip,
            rightHipAngle = rightHip,
            torsoTwistEstimate = torsoTwist,
            effectiveRuntimeSummary = runtimeSummary,
            overrideSummary = overrideSummary,
            failReason = failReason,
            tuningSuggestionSummary = suggestionSummary
        )
        debugText.text = debugInfo.toDisplayText()
    }

    private fun buildRuntimeSummary(params: RuntimeParams): String {
        val controls = "stab=${params.stabilityMs ?: "--"} ema=${params.emaAlpha.fmt2()} dead=${params.deadbandDegrees.fmt1()}"
        val activeAngles = TunableRuntimeParamExtractor.extract(params)
            .filter { it.path.startsWith("runtime.angles") }
            .take(3)
            .joinToString(" ") { "${it.label}=${formatTuningValue(it.value, it)}" }
        return listOf(controls, activeAngles).filter { it.isNotBlank() }.joinToString(" | ")
    }

    private fun buildOverrideSummary(): String {
        return computeCurrentTuningBindings()
            .mapNotNull { binding ->
                runtimeOverrideStore.valueFor(binding.key)?.let { value ->
                    "${binding.param.label}=${formatTuningValue(value, binding.param)}"
                }
            }
            .take(4)
            .joinToString(" ")
    }

    private fun buildSuggestionSummary(flowId: String, stepIndex: Int, detect: com.yogaflow.flow.DetectKey): String {
        val suggestions = autoTuningAdvisor.suggestionsFor(flowId, stepIndex, detect)
        latestSuggestion = suggestions.firstOrNull()
        return suggestions.take(2).joinToString(" | ") { it.label }
    }

    private fun applyLatestSuggestion(): Boolean {
        val suggestion = latestSuggestion ?: return false
        val binding = computeCurrentTuningBindings().firstOrNull {
            it.param.label.startsWith("${suggestion.metric}.") && it.param.label.endsWith(".${suggestion.boundName}")
        } ?: return false
        runtimeOverrideStore.set(binding.key, suggestion.suggestedValue)
        updateRuntimeTuningControls()
        coachText.text = "已套用建議：${suggestion.label}"
        return true
    }

    private fun Double?.fmt1(): String {
        return this?.let { "%.1f".format(it) } ?: "--"
    }

    private fun Double?.fmt2(): String {
        return this?.let { "%.2f".format(it) } ?: "--"
    }

    private fun completeCurrentFlow(cue: String) {
        val next = playlist.moveNext()
        if (next != null) {
            currentFlow = next
            currentPose = resolvePose(currentFlow)
            flowEngine.reset()
            resetDetectionMappers()
            coachRequestId++
            animateFlowTransition()
            speakRawCue("下一個動作")
        } else {
            sessionState = SessionState.COMPLETED
            coachText.text = cue
            speakRawCue(cue)
        }
    }

    private fun speakCoachCue(state: CoachState, cue: String) {
        if (!::coachCueController.isInitialized) return
        coachCueController.speak(
            pose = currentPose,
            flowId = currentFlow.id,
            step = flowEngine.currentStepNumber(),
            state = state,
            cue = cue
        )
    }

    private fun speakRawCue(cue: String) {
        if (!::coachCueController.isInitialized) return
        coachCueController.speakRaw(cue)
    }

    private fun setupButtons() {
        startClassButton.setOnClickListener { loadDiscoveredPlaylist(openClassView = true) }
        startStretchButton.setOnClickListener { loadPlaylist(listOf("flows/02_forward_fold_main.flow.json"), openClassView = true) }
        startRecoveryButton.setOnClickListener { loadPlaylist(listOf("flows/03_twist_cooldown.flow.json"), openClassView = true) }
        startButton.setOnClickListener { startRunningClass(auto = false) }
        pauseButton.setOnClickListener {
            sessionState = SessionState.PAUSED
            coachRequestId++
            coachText.text = "已暫停。準備好後按 Start 繼續。"
            updateUi(animated = false)
        }
        restartButton.setOnClickListener { restartCurrentPlaylist() }
        restartButton.setOnLongClickListener {
            if (!applyLatestSuggestion()) coachText.text = "目前沒有可套用的調參建議。"
            true
        }
    }

    

    internal fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose } ?: YogaPoseCatalog.poses.first()
    }

    

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.TAIWAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                coachText.text = "TTS 中文語音不可用，請安裝語音資料。"
            }
        }
    }

    override fun onDestroy() {
        if (::cameraPipeline.isInitialized) cameraPipeline.stop()
        cameraExecutor.shutdown()
        coachExecutor.shutdown()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val MIN_CUE_INTERVAL_MS = 1200L
        private const val SAME_CUE_INTERVAL_MS = 2500L
        private const val DEBUG_OVERLAY_ENABLED = true
        private const val AUTO_START_ENABLED = true
        private const val CAMERA_AUTO_START_STABLE_MS = 1500L
        private const val SQUAT_THRESHOLD_MIN = 80.0
        private const val BRIDGE_THRESHOLD_MIN = 120.0
        private const val SQUAT_THRESHOLD_RANGE = 70
        private const val BRIDGE_THRESHOLD_RANGE = 70
        private const val THRESHOLD_PREFS = "threshold_prefs"
        private const val KEY_SQUAT_HOLD_KNEE_MAX = "squat_hold_knee_max"
        internal const val TUNING_SLIDER_MAX = 1000
    }
}
