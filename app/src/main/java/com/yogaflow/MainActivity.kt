package com.yogaflow

import android.animation.ObjectAnimator
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import com.yogaflow.coach.BridgeDetectionMapper
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.ForwardFoldDetectionMapper
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.coach.SquatDetectionMapper
import com.yogaflow.coach.ThresholdConfig
import com.yogaflow.coach.TwistDetectionMapper
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.AutoTuningSuggestion
import com.yogaflow.flow.DetectKey
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.RuntimeOverrideKey
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.flow.TunableRuntimeParam
import com.yogaflow.flow.TunableRuntimeParamExtractor
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.CameraFramingResult
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.DebugPoseInfo
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.pose.ViewOrientationResult
import com.yogaflow.pose.ViewOrientationStatus
import com.yogaflow.runtime.CoachRuntimeEngine
import com.yogaflow.runtime.PoseFrameRuntimeHandler
import com.yogaflow.runtime.SessionState
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var homeView: View
    private lateinit var classView: View
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var cameraSetupPanel: View
    private lateinit var cameraSetupStatus: TextView
    private lateinit var debugText: TextView
    private lateinit var coachText: TextView
    private lateinit var flowName: TextView
    private lateinit var progressText: TextView
    private lateinit var countdownText: TextView
    private lateinit var llmStatus: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var squatThresholdLabel: TextView
    private lateinit var bridgeThresholdLabel: TextView
    private lateinit var squatThresholdSeekBar: SeekBar
    private lateinit var bridgeThresholdSeekBar: SeekBar

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
    private lateinit var coachEngine: CoachRuntimeEngine
    private lateinit var frameHandler: PoseFrameRuntimeHandler

    private val stateMachine = PoseStateMachine()
    private val flowEngine = PoseFlowEngine()
    private val playlist = FlowPlaylistEngine()
    private val runtimeOverrideStore = RuntimeOverrideStore()
    private val autoTuningAdvisor = AutoTuningAdvisor()
    private var latestSuggestion: AutoTuningSuggestion? = null
    private var suppressTuningCallbacks = false

    private lateinit var currentFlow: YogaFlow
    private lateinit var currentPose: YogaPose
    private var sessionState = SessionState.IDLE
    private var cameraReady = false
    private var cameraReadySince = 0L
    private var autoStartedCurrentSetup = false
    private var lastCountdownText = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadThresholdPreferences()
        initRuntime()
        setupThresholdControls()
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
        cameraSetupPanel = findViewById(R.id.cameraSetupPanel)
        cameraSetupStatus = findViewById(R.id.cameraSetupStatus)
        debugText = findViewById(R.id.debugText)
        coachText = findViewById(R.id.coachText)
        flowName = findViewById(R.id.flowName)
        progressText = findViewById(R.id.progressText)
        countdownText = findViewById(R.id.countdownText)
        llmStatus = findViewById(R.id.llmStatus)
        progressBar = findViewById(R.id.progressBar)
        squatThresholdLabel = findViewById(R.id.squatThresholdLabel)
        bridgeThresholdLabel = findViewById(R.id.bridgeThresholdLabel)
        squatThresholdSeekBar = findViewById(R.id.squatThresholdSeekBar)
        bridgeThresholdSeekBar = findViewById(R.id.bridgeThresholdSeekBar)
        startClassButton = findViewById(R.id.startClassButton)
        startStretchButton = findViewById(R.id.startStretchButton)
        startRecoveryButton = findViewById(R.id.startRecoveryButton)
        startButton = findViewById(R.id.startButton)
        pauseButton = findViewById(R.id.pauseButton)
        restartButton = findViewById(R.id.restartButton)
    }

    private fun setupThresholdControls() {
        squatThresholdSeekBar.max = TUNING_SLIDER_MAX
        bridgeThresholdSeekBar.max = TUNING_SLIDER_MAX
        squatThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListener(0))
        bridgeThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListener(1))
        updateRuntimeTuningControls()
    }

    private fun runtimeTuningListener(index: Int): SeekBar.OnSeekBarChangeListener {
        return object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser || suppressTuningCallbacks) return
                val binding = currentTuningBindings().getOrNull(index) ?: return
                val value = sliderProgressToValue(progress, binding.param)
                runtimeOverrideStore.set(binding.key, value)
                updateRuntimeTuningControls()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        }
    }

    private data class RuntimeTuningBinding(
        val key: RuntimeOverrideKey,
        val param: TunableRuntimeParam
    )

    private fun currentTuningBindings(): List<RuntimeTuningBinding> {
        if (!::currentFlow.isInitialized) return emptyList()
        val stepIndex = flowEngine.currentStepNumber() - 1
        val step = currentFlow.steps.getOrNull(stepIndex) ?: return emptyList()
        return TunableRuntimeParamExtractor.extract(step.params).map { param ->
            RuntimeTuningBinding(
                key = RuntimeOverrideKey(
                    flowId = currentFlow.id,
                    stepIndex = stepIndex,
                    detect = step.detect,
                    path = param.path
                ),
                param = param
            )
        }
    }

    private fun updateRuntimeTuningControls() {
        val bindings = currentTuningBindings()
        applyRuntimeTuningBinding(squatThresholdLabel, squatThresholdSeekBar, bindings.getOrNull(0))
        applyRuntimeTuningBinding(bridgeThresholdLabel, bridgeThresholdSeekBar, bindings.getOrNull(1))
    }

    private fun applyRuntimeTuningBinding(label: TextView, seekBar: SeekBar, binding: RuntimeTuningBinding?) {
        suppressTuningCallbacks = true
        if (binding == null) {
            label.text = "DSL tuning: no param"
            seekBar.isEnabled = false
            seekBar.alpha = 0.35f
            seekBar.progress = 0
        } else {
            val value = runtimeOverrideStore.valueFor(binding.key) ?: binding.param.value
            label.text = "${binding.param.label}: ${formatTuningValue(value, binding.param)}"
            seekBar.isEnabled = true
            seekBar.alpha = 1.0f
            seekBar.progress = valueToSliderProgress(value, binding.param)
        }
        suppressTuningCallbacks = false
    }

    private fun valueToSliderProgress(value: Double, param: TunableRuntimeParam): Int {
        val clamped = value.coerceIn(param.min, param.max)
        val ratio = if (param.max == param.min) 0.0 else (clamped - param.min) / (param.max - param.min)
        return (ratio * TUNING_SLIDER_MAX).toInt().coerceIn(0, TUNING_SLIDER_MAX)
    }

    private fun sliderProgressToValue(progress: Int, param: TunableRuntimeParam): Double {
        val ratio = progress.coerceIn(0, TUNING_SLIDER_MAX).toDouble() / TUNING_SLIDER_MAX.toDouble()
        val raw = param.min + ratio * (param.max - param.min)
        return if (param.isInteger) raw.toLong().toDouble() else raw
    }

    private fun formatTuningValue(value: Double, param: TunableRuntimeParam): String {
        return if (param.isInteger) value.toLong().toString() else "%.2f".format(value)
    }

    private fun loadThresholdPreferences() {
        val prefs = getSharedPreferences(THRESHOLD_PREFS, MODE_PRIVATE)
        val squat = prefs.getFloat(KEY_SQUAT_HOLD_KNEE_MAX, ThresholdConfig.squatHoldKneeMaxDegrees.toFloat()).toDouble()
        val bridge = prefs.getFloat(KEY_BRIDGE_LIFT_HIP_MAX, ThresholdConfig.bridgeLiftHipMaxDegrees.toFloat()).toDouble()
        ThresholdConfig.squatHoldKneeMaxDegrees = clampThreshold(squat, SQUAT_THRESHOLD_MIN, SQUAT_THRESHOLD_RANGE)
        ThresholdConfig.bridgeLiftHipMaxDegrees = clampThreshold(bridge, BRIDGE_THRESHOLD_MIN, BRIDGE_THRESHOLD_RANGE)
    }

    private fun clampThreshold(value: Double, min: Double, range: Int): Double {
        return value.coerceIn(min, min + range)
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

        coachEngine = CoachRuntimeEngine(
            llmCoach = llmCoach,
            speaker = speaker,
            getCurrentPose = { currentPose },
            getFlowId = { currentFlow.id },
            getStep = { flowEngine.currentStepNumber() },
            updateUi = { text, llmOn ->
                llmStatus.text = if (llmOn) "LLM: ON" else "LLM: OFF"
                coachText.text = text
            }
        )

        frameHandler = PoseFrameRuntimeHandler(
            overlayView = overlayView,
            cameraSetupPanel = cameraSetupPanel,
            flowEngine = flowEngine,
            stateMachine = stateMachine,
            runtimeOverrideStore = runtimeOverrideStore,
            autoTuningAdvisor = autoTuningAdvisor,
            getSessionState = { sessionState },
            setSessionState = { sessionState = it },
            getCurrentFlow = { currentFlow },
            setCurrentFlow = { currentFlow = it },
            getCurrentPose = { currentPose },
            setCurrentPose = { currentPose = it },
            isCurrentPoseReady = { ::currentPose.isInitialized },
            updateCameraSetupPanel = ::updateCameraSetupPanel,
            maybeAutoStartClass = ::maybeAutoStartClass,
            cameraSetupCue = ::cameraSetupCue,
            updateDebugOverlay = ::updateDebugOverlay,
            updateRuntimeTuningControls = ::updateRuntimeTuningControls,
            updateUi = ::updateUi,
            speakCoachCue = { state, cue -> coachEngine.emit(state, cue) },
            completeCurrentFlow = ::completeCurrentFlow,
            animateFlowTransition = ::animateFlowTransition,
            buildRuntimeSummary = ::buildRuntimeSummary,
            buildOverrideSummary = ::buildOverrideSummary,
            buildSuggestionSummary = ::buildSuggestionSummary
        )

        if (!poseHelper.isReady) {
            coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
        }

        poseHelper.onResult = { frame ->
            runOnUiThread { frameHandler.handle(frame) }
        }
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
            cameraSetupStatus.text =
                if (stableFor >= CAMERA_AUTO_START_STABLE_MS) {
                    "Ready ✔\nStarting class automatically..."
                } else {
                    "Ready ✔\nHold still. Auto-start in %.1fs.".format(remaining)
                }
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

    private fun maybeAutoStartClass(): Boolean {
        if (!AUTO_START_ENABLED || !cameraReady || autoStartedCurrentSetup || sessionState != SessionState.IDLE || cameraReadySince == 0L) return false
        if (System.currentTimeMillis() - cameraReadySince < CAMERA_AUTO_START_STABLE_MS) return false
        autoStartedCurrentSetup = true
        startRunningClass(auto = true)
        return sessionState == SessionState.RUNNING
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
        val torsoTwist = if (leftShoulderHipKnee != null && rightShoulderHipKnee != null) {
            abs(leftShoulderHipKnee - rightShoulderHipKnee)
        } else {
            null
        }

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
        return currentTuningBindings()
            .mapNotNull { binding ->
                runtimeOverrideStore.valueFor(binding.key)?.let { value ->
                    "${binding.param.label}=${formatTuningValue(value, binding.param)}"
                }
            }
            .take(4)
            .joinToString(" ")
    }

    private fun buildSuggestionSummary(flowId: String, stepIndex: Int, detect: DetectKey): String {
        val suggestions = autoTuningAdvisor.suggestionsFor(flowId, stepIndex, detect)
        latestSuggestion = suggestions.firstOrNull()
        return suggestions.take(2).joinToString(" | ") { it.label }
    }

    private fun applyLatestSuggestion(): Boolean {
        val suggestion = latestSuggestion ?: return false
        val binding = currentTuningBindings().firstOrNull {
            it.param.label.startsWith("${suggestion.metric}.") && it.param.label.endsWith(".${suggestion.boundName}")
        } ?: return false
        runtimeOverrideStore.set(binding.key, suggestion.suggestedValue)
        updateRuntimeTuningControls()
        coachText.text = "已套用建議：${suggestion.label}"
        return true
    }

    private fun Double?.fmt1(): String = this?.let { "%.1f".format(it) } ?: "--"
    private fun Double?.fmt2(): String = this?.let { "%.2f".format(it) } ?: "--"

    private fun completeCurrentFlow(cue: String) {
        val next = playlist.moveNext()
        if (next != null) {
            currentFlow = next
            currentPose = resolvePose(currentFlow)
            flowEngine.reset()
            resetDetectionMappers()
            animateFlowTransition()
            speakRawCue("下一個動作")
        } else {
            sessionState = SessionState.COMPLETED
            coachText.text = cue
            speakRawCue(cue)
        }
    }

    private fun cameraSetupCue(framing: CameraFramingResult, orientation: ViewOrientationResult): String {
        return when {
            framing.status != CameraFramingStatus.GOOD -> framing.message
            orientation.status != ViewOrientationStatus.GOOD -> orientation.message
            else -> ""
        }
    }

    private fun speakRawCue(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
    }

    private fun setupButtons() {
        startClassButton.setOnClickListener { loadDiscoveredPlaylist(openClassView = true) }
        startStretchButton.setOnClickListener { loadPlaylist(listOf("flows/02_forward_fold_main.flow.json"), openClassView = true) }
        startRecoveryButton.setOnClickListener { loadPlaylist(listOf("flows/03_twist_cooldown.flow.json"), openClassView = true) }
        startButton.setOnClickListener { startRunningClass(auto = false) }
        pauseButton.setOnClickListener {
            sessionState = SessionState.PAUSED
            coachText.text = "已暫停。準備好後按 Start 繼續。"
            updateUi(animated = false)
        }
        restartButton.setOnClickListener { restartCurrentPlaylist() }
        restartButton.setOnLongClickListener {
            if (!applyLatestSuggestion()) coachText.text = "目前沒有可套用的調參建議。"
            true
        }
    }

    private fun loadDiscoveredPlaylist(openClassView: Boolean = true) {
        val flows = runCatching { FlowLoader.loadAllFromAssets(this) }
            .onFailure { coachText.text = "課程載入失敗，請確認 assets/flows。" }
            .getOrDefault(emptyList())
        applyPlaylist(flows, openClassView)
    }

    private fun loadPlaylist(paths: List<String>, openClassView: Boolean = true) {
        val flows = paths.mapNotNull { path ->
            runCatching { FlowLoader.loadFromAssets(this, path) }
                .onFailure { coachText.text = "課程載入失敗：$path" }
                .getOrNull()
        }
        applyPlaylist(flows, openClassView)
    }

    private fun applyPlaylist(flows: List<YogaFlow>, openClassView: Boolean) {
        if (flows.isEmpty()) {
            coachText.text = "No yoga flows found in assets/flows."
            return
        }

        playlist.setPlaylist(flows)
        val flow = playlist.current()
        if (flow == null) {
            coachText.text = "No yoga flows found in assets/flows."
            return
        }

        currentFlow = flow
        currentPose = resolvePose(currentFlow)
        flowEngine.reset()
        resetDetectionMappers()
        latestSuggestion = null
        sessionState = SessionState.IDLE
        cameraReady = false
        cameraReadySince = 0L
        autoStartedCurrentSetup = false
        startButton.isEnabled = false
        startButton.alpha = 0.45f
        cameraSetupPanel.visibility = View.VISIBLE
        cameraSetupStatus.text = "Checking body framing..."
        lastCountdownText = ""
        coachText.text = "請先完成相機設定。"
        llmStatus.text = "LLM: OFF"
        updateRuntimeTuningControls()

        if (openClassView) showClass()
        updateUi(animated = false)
    }

    private fun restartCurrentPlaylist() {
        playlist.reset()
        val flow = playlist.current()
        if (flow == null) {
            sessionState = SessionState.IDLE
            coachText.text = "找不到課程流程，請確認 assets/flows。"
            updateUi(animated = false)
            return
        }

        flowEngine.reset()
        resetDetectionMappers()
        latestSuggestion = null
        currentFlow = flow
        currentPose = resolvePose(currentFlow)
        sessionState = SessionState.IDLE
        cameraReady = false
        cameraReadySince = 0L
        autoStartedCurrentSetup = false
        startButton.isEnabled = false
        startButton.alpha = 0.45f
        cameraSetupPanel.visibility = View.VISIBLE
        cameraSetupStatus.text = "Checking body framing..."
        lastCountdownText = ""
        coachText.text = "已重新開始。請先完成相機設定。"
        updateRuntimeTuningControls()
        updateUi(animated = false)
    }

    private fun resetDetectionMappers() {
        ForwardFoldDetectionMapper.reset()
        TwistDetectionMapper.reset()
        SquatDetectionMapper.reset()
        BridgeDetectionMapper.reset()
    }

    private fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose } ?: YogaPoseCatalog.poses.first()
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
            SessionState.IDLE -> if (cameraReady) "Ready" else "Setup"
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
            if (number != null && number in 1..3) speaker.speakIfNeeded(number.toString())
        }
        countdownText.scaleX = 1.35f
        countdownText.scaleY = 1.35f
        countdownText.alpha = 0.4f
        countdownText.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(250L).start()
    }

    private fun animateFlowTransition() {
        flowName.alpha = 0f
        flowName.translationY = -20f
        flowName.animate().alpha(1f).translationY(0f).setDuration(400L).start()
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
            val result = tts.setLanguage(Locale.TAIWAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                coachText.text = "TTS 中文語音不可用，請安裝語音資料。"
            }
        }
    }

    override fun onDestroy() {
        if (::cameraPipeline.isInitialized) cameraPipeline.stop()
        cameraExecutor.shutdown()
        if (::tts.isInitialized) tts.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val DEBUG_OVERLAY_ENABLED = true
        private const val AUTO_START_ENABLED = true
        private const val CAMERA_AUTO_START_STABLE_MS = 1500L
        private const val SQUAT_THRESHOLD_MIN = 80.0
        private const val BRIDGE_THRESHOLD_MIN = 120.0
        private const val SQUAT_THRESHOLD_RANGE = 70
        private const val BRIDGE_THRESHOLD_RANGE = 70
        private const val THRESHOLD_PREFS = "threshold_prefs"
        private const val KEY_SQUAT_HOLD_KNEE_MAX = "squat_hold_knee_max"
        private const val KEY_BRIDGE_LIFT_HIP_MAX = "bridge_lift_hip_max"
        private const val TUNING_SLIDER_MAX = 1000
    }
}
