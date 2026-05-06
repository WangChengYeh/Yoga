package com.yogaflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.coach.AvatarCommand
import com.yogaflow.coach.CoachCueController
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.CoachVisualState
import com.yogaflow.coach.DetectionMapperSession
import com.yogaflow.coach.GodotAvatarBridge
import com.yogaflow.coach.PoseCoachFrame
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseMetrics
import com.yogaflow.coach.PoseStateMachine
// import com.yogaflow.coach.VirtualCoachView
import com.yogaflow.flow.AutoTuningAdvisor
import com.yogaflow.flow.AutoTuningSuggestion
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.RuntimeOverrideKey
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.flow.YogaFlow
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.session.CameraSetupController
import com.yogaflow.session.LiveCoachSessionController
import com.yogaflow.session.SessionRecorder
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import org.godotengine.godot.Godot
import org.godotengine.godot.GodotHost
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

class MainActivity : AppCompatActivity(), GodotHost {

    lateinit var homeView: View
    lateinit var classView: View
    lateinit var previewView: PreviewView
    lateinit var overlayView: PoseOverlayView
    lateinit var virtualCoachView: View
    lateinit var cameraSetupPanel: View
    lateinit var thresholdPanel: View
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
    lateinit var sessionRecordButton: Button
    lateinit var debugToggleButton: Button
    lateinit var sessionRecordStatus: TextView
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
    var debugViewEnabled = false
    var cameraSetupDisabledForDevelopment = false
    var lastCountdownText = ""
    var latestSuggestion: AutoTuningSuggestion? = null

    private lateinit var poseHelper: PoseHelper
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var coachExecutor: ExecutorService
    private lateinit var tts: TextToSpeech
    private lateinit var coachCueController: CoachCueController
    lateinit var cameraSetupController: CameraSetupController
    private lateinit var liveCoachSessionController: LiveCoachSessionController
    private lateinit var sessionRecorder: SessionRecorder
    private lateinit var godotAvatarBridge: GodotAvatarBridge
    private var pendingTtsReady: Boolean? = null
    private val stateMachine = PoseStateMachine()
    private val autoTuningAdvisor = AutoTuningAdvisor()

    override fun getActivity(): Activity = this

    override fun getGodot(): Godot = Godot.getInstance(this)

    override fun getCommandLine(): List<String> = emptyList()

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
        sessionRecorder = SessionRecorder(this)
        godotAvatarBridge = GodotAvatarBridge()
        loadDevelopmentSettings()
        applyDevelopmentIntentFlags(intent)
        loadThresholdPreferencesMain()

        cameraExecutor = Executors.newSingleThreadExecutor()
        coachExecutor = Executors.newSingleThreadExecutor()
        tts = TextToSpeech(this) { status ->
            val isReady = status == TextToSpeech.SUCCESS && configureTts()
            pendingTtsReady = isReady
            if (::speaker.isInitialized) speaker.setReady(isReady)
        }
        speaker = CoachSpeaker(tts)
        pendingTtsReady?.let { speaker.setReady(it) }
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
            minCueIntervalMs = 5000L,
            sameCueIntervalMs = 5000L,
            onDisplay = { displayText, llmEnabled ->
                coachText.text = displayText
                llmStatus.text = if (llmEnabled) "LLM: ON" else "LLM: OFF"
                recordSessionCue(null, displayText, "display")
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
                updateVirtualCoachFromCurrentStep()
            },
            onUpdateSetupPanel = { ready, framingMessage, orientationMessage ->
                cameraSetupPanel.visibility = View.VISIBLE
                updateVirtualCoachFromCurrentStep()
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
        applyDebugViewEnabled(false)
        bindActions()
        loadDiscoveredPlaylist(openClassView = false)
        requestCameraIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyDevelopmentIntentFlags(intent)
        if (isCurrentFlowInitialized()) {
            resetToCameraSetup(
                if (cameraSetupDisabledForDevelopment) {
                    "Development: camera setup bypassed."
                } else {
                    "請先完成相機設定。"
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        getGodot().onResume(this)
    }

    override fun onPause() {
        super.onPause()
        getGodot().onPause(this)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        getGodot().onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        saveThresholdPreferencesMain()
        godotAvatarBridge.close()
        cameraPipeline.stop()
        cameraExecutor.shutdown()
        coachExecutor.shutdown()
        tts.shutdown()
        super.onDestroy()
    }

    fun isCurrentFlowInitialized(): Boolean = ::currentFlow.isInitialized && ::currentPose.isInitialized

    private fun configureTts(): Boolean {
        tts.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
        )
        val locale = supportedTtsLocales().firstOrNull { locale ->
            when (tts.isLanguageAvailable(locale)) {
                TextToSpeech.LANG_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_AVAILABLE,
                TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
                else -> false
            }
        } ?: return false

        return when (tts.setLanguage(locale)) {
            TextToSpeech.LANG_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_AVAILABLE,
            TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> true
            else -> false
        }
    }

    private fun supportedTtsLocales(): List<Locale> {
        return listOf(
            Locale.TRADITIONAL_CHINESE,
            Locale.TAIWAN,
            Locale("zh", "TW"),
            Locale.CHINESE,
            Locale.getDefault(),
            Locale.US
        ).distinct()
    }

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
        sessionRecordButton.setOnClickListener { toggleSessionRecording() }
        debugToggleButton.setOnClickListener { applyDebugViewEnabled(!debugViewEnabled) }
    }

    private fun applyDebugViewEnabled(enabled: Boolean) {
        debugViewEnabled = enabled
        val visibility = if (enabled) View.VISIBLE else View.GONE
        debugText.visibility = visibility
        thresholdPanel.visibility = visibility
        debugToggleButton.text = if (enabled) "Hide" else "Debug"
    }

    private fun loadDevelopmentSettings() {
        cameraSetupDisabledForDevelopment = isDebuggableBuild() &&
            getSharedPreferences(DEV_PREFS, MODE_PRIVATE)
                .getBoolean(PREF_DISABLE_CAMERA_SETUP, false)
    }

    private fun applyDevelopmentIntentFlags(intent: Intent?) {
        if (!isDebuggableBuild() || intent == null) return
        when {
            intent.getBooleanExtra(EXTRA_DISABLE_CAMERA_SETUP, false) -> setDevelopmentCameraSetupDisabled(true)
            intent.getBooleanExtra(EXTRA_ENABLE_CAMERA_SETUP, false) -> setDevelopmentCameraSetupDisabled(false)
        }
    }

    private fun isDebuggableBuild(): Boolean {
        return applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    }

    private fun setDevelopmentCameraSetupDisabled(disabled: Boolean) {
        cameraSetupDisabledForDevelopment = disabled
        getSharedPreferences(DEV_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_DISABLE_CAMERA_SETUP, disabled)
            .apply()
    }

    internal fun requestCameraIfNeeded() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun handlePoseFrame(frame: PoseDetectionResult) {
        overlayView.setLandmarks(frame.imageLandmarks)
        if (!isCurrentFlowInitialized()) {
            virtualCoachView.visibility = View.GONE
            return
        }
        updateVirtualCoachBounds(frame.imageLandmarks)
        updateVirtualCoachFromCurrentStep()

        if (cameraSetupDisabledForDevelopment && sessionState != SessionState.COMPLETED) {
            bypassCameraSetupForDevelopment()
            liveCoachSessionController.handleReadyPoseFrame(frame, currentFlow, currentPose)
            return
        }

        if (cameraSetupController.handleFrame(frame)) return

        liveCoachSessionController.handleReadyPoseFrame(frame, currentFlow, currentPose)
    }

    private fun beginRunningSession() {
        if (!cameraReady) return
        sessionState = SessionState.RUNNING
        cameraSetupPanel.visibility = View.GONE
        virtualCoachView.visibility = View.VISIBLE
        godotAvatarBridge.connect()
        coachCueController.reset()
        updateUi(animated = false)
    }

    private fun bypassCameraSetupForDevelopment() {
        if (sessionState == SessionState.RUNNING) return
        cameraReady = true
        cameraReadySince = System.currentTimeMillis()
        autoStartedCurrentSetup = true
        startButton.isEnabled = true
        startButton.alpha = 1f
        cameraSetupPanel.visibility = View.GONE
        sessionState = SessionState.RUNNING
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
                updateVirtualCoachFromCurrentStep()
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
        recordSessionCue(CoachState.HOLD, text, "flow_completed")
        val next = playlist.moveNext()
        if (next == null) {
            sessionState = SessionState.COMPLETED
            coachText.text = text
            virtualCoachView.visibility = View.GONE
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
            "devCameraSetupDisabled=$cameraSetupDisabledForDevelopment",
            "landmarks=${frame.imageLandmarks.size} size=${frame.imageWidth}x${frame.imageHeight}",
            runtimeSummary,
            overrideSummary,
            failReason,
            suggestionSummary
        ).filter { it.isNotBlank() }.joinToString("\n")
        if (isCurrentFlowInitialized()) {
            val poseCoachFrame = buildPoseCoachFrame(frame, detect, state, matched, failReason)
            godotAvatarBridge.send(poseCoachFrame)
            updateVirtualCoach(poseCoachFrame)
            sessionRecorder.recordFrame(
                frame = frame,
                flowId = currentFlow.id,
                stepNumber = flowEngine.currentStepNumber(),
                detect = detect,
                state = state,
                matched = matched,
                runtimeSummary = runtimeSummary,
                overrideSummary = overrideSummary,
                failReason = failReason,
                suggestionSummary = suggestionSummary
            )
            updateSessionRecordStatus()
        }
    }

    private fun updateVirtualCoach(detect: String, state: CoachState) {
        val shouldShowCoach = detect.isNotBlank() &&
            state != CoachState.SETUP &&
            sessionState != SessionState.COMPLETED
        virtualCoachView.visibility = if (shouldShowCoach) View.VISIBLE else View.GONE
    }

    private fun updateVirtualCoach(frame: PoseCoachFrame) {
        val shouldShowCoach = frame.stepId.isNotBlank() &&
            frame.avatar.action.isNotBlank() &&
            sessionState != SessionState.COMPLETED
        virtualCoachView.visibility = if (shouldShowCoach) View.VISIBLE else View.GONE
    }

    private fun buildPoseCoachFrame(
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean,
        failReason: String
    ): PoseCoachFrame {
        val step = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        val coachState = if (matched && state != CoachState.CORRECTION) "ok" else "needs_correction"
        val avatarCommand = buildAvatarCommand(detect, state, matched, failReason)
        return PoseCoachFrame(
            timestampMs = System.currentTimeMillis(),
            stepId = step?.detect?.jsonKey ?: detect,
            phase = state.name.lowercase(Locale.US),
            pose = buildPoseMetrics(frame),
            coach = CoachVisualState(
                state = coachState,
                error = avatarCommand.highlight?.let { "${it}_alignment" },
                message = coachText.text.toString(),
                severity = severityFor(state, matched, failReason)
            ),
            avatar = avatarCommand
        )
    }

    private fun buildPoseMetrics(frame: PoseDetectionResult): PoseMetrics {
        val leftKnee = PoseGeometry.angleDegreesOrNull(frame, 23, 25, 27)
        val rightKnee = PoseGeometry.angleDegreesOrNull(frame, 24, 26, 28)
        val leftHip = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 25)
        val rightHip = PoseGeometry.angleDegreesOrNull(frame, 12, 24, 26)
        val hip = listOfNotNull(leftHip, rightHip).averageOrNull()
        val spine = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 24)
        return PoseMetrics(
            leftKneeAngle = leftKnee,
            rightKneeAngle = rightKnee,
            hipAngle = hip,
            spineAngle = spine,
            ankleDistanceRatio = ankleDistanceRatio(frame)
        )
    }

    private fun buildAvatarCommand(
        detect: String,
        state: CoachState,
        matched: Boolean,
        failReason: String
    ): AvatarCommand {
        val highlight = highlightFor(detect, failReason)
        val action = when {
            state == CoachState.CORRECTION && highlight == "knees" -> "correct_knees"
            state == CoachState.CORRECTION && highlight == "hips" -> "correct_hips"
            state == CoachState.CORRECTION && highlight == "spine" -> "correct_spine"
            state == CoachState.CORRECTION -> "correct_alignment"
            detect.contains("forward_fold") || currentPose.id == "forward_fold" -> "hold_forward_fold"
            currentPose.id == "squat" -> "hold_squat"
            currentPose.id == "twist" -> "hold_twist"
            currentPose.id == "bridge" -> "hold_bridge"
            state == CoachState.TRANSITION -> "controlled_transition"
            else -> "hold_mountain"
        }
        val emotion = when {
            state == CoachState.CORRECTION || !matched -> "focused"
            state == CoachState.MOVEMENT || state == CoachState.TRANSITION -> "attentive"
            else -> "calm"
        }
        return AvatarCommand(action = action, emotion = emotion, highlight = highlight)
    }

    private fun severityFor(state: CoachState, matched: Boolean, failReason: String): Int {
        return when {
            matched && state != CoachState.CORRECTION -> 0
            failReason.contains("<") || failReason.contains(">") -> 2
            state == CoachState.CORRECTION -> 2
            else -> 1
        }
    }

    private fun highlightFor(detect: String, failReason: String): String? {
        val source = "$detect $failReason".lowercase(Locale.US)
        return when {
            source.contains("knee") -> "knees"
            source.contains("hip") -> "hips"
            source.contains("spine") || source.contains("back") -> "spine"
            source.contains("shoulder") -> "shoulders"
            source.contains("ankle") || source.contains("foot") -> "feet"
            source.contains("twist") -> "shoulders"
            else -> null
        }
    }

    private fun ankleDistanceRatio(frame: PoseDetectionResult): Double? {
        val leftAnkle = frame.imageLandmarks.getOrNull(27) ?: return null
        val rightAnkle = frame.imageLandmarks.getOrNull(28) ?: return null
        val leftHip = frame.imageLandmarks.getOrNull(23) ?: return null
        val rightHip = frame.imageLandmarks.getOrNull(24) ?: return null
        val ankleDistance = distance(leftAnkle.x(), leftAnkle.y(), rightAnkle.x(), rightAnkle.y())
        val hipDistance = distance(leftHip.x(), leftHip.y(), rightHip.x(), rightHip.y()).takeIf { it > 0.001 } ?: return null
        return ankleDistance / hipDistance
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Double {
        val dx = ax - bx
        val dy = ay - by
        return sqrt((dx * dx + dy * dy).toDouble())
    }

    private fun List<Double>.averageOrNull(): Double? {
        return if (isEmpty()) null else average()
    }

    private fun updateVirtualCoachBounds(landmarks: List<NormalizedLandmark>) {
        val visibleBody = VIRTUAL_COACH_SCALE_INDICES.mapNotNull { index ->
            landmarks.getOrNull(index)?.takeIf { it.x() in 0f..1f && it.y() in 0f..1f }
        }
        if (visibleBody.isEmpty() || classView.width == 0 || classView.height == 0) return

        val minY = visibleBody.minOf { it.y() } * classView.height
        val maxY = visibleBody.maxOf { it.y() } * classView.height
        val centerY = (minY + maxY) / 2f
        val bottomReserved = dp(96)
        val maxCoachHeight = (classView.height * VIRTUAL_COACH_MAX_HEIGHT_FRACTION).toInt()
            .coerceAtMost((classView.height - bottomReserved - dp(8)).coerceAtLeast(dp(180)))
        val minCoachHeight = dp(320).coerceAtMost(maxCoachHeight)
        val targetHeight = ((maxY - minY) * VIRTUAL_COACH_HEIGHT_SCALE)
            .toInt()
            .coerceIn(minCoachHeight, maxCoachHeight)
        val targetWidth = (targetHeight * VIRTUAL_COACH_ASPECT_RATIO).toInt()
        val maxTop = (classView.height - bottomReserved - targetHeight).coerceAtLeast(0)
        val topMargin = (centerY - targetHeight / 2f)
            .toInt()
            .coerceIn(dp(8), maxTop)

        val params = (virtualCoachView.layoutParams as FrameLayout.LayoutParams).apply {
            width = targetWidth
            height = targetHeight
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            this.topMargin = topMargin
            marginEnd = dp(8)
        }
        virtualCoachView.layoutParams = params
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun updateVirtualCoachFromCurrentStep() {
        if (!isCurrentFlowInitialized()) {
            virtualCoachView.visibility = View.GONE
            return
        }
        val step = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        if (step == null || sessionState == SessionState.COMPLETED) {
            virtualCoachView.visibility = View.GONE
            return
        }
        updateVirtualCoach(step.detect.jsonKey, step.state)
    }

    private fun toggleSessionRecording() {
        if (sessionRecorder.isRecording) {
            val file = sessionRecorder.stopAndSave()
            sessionRecordButton.text = "Record"
            if (file == null) {
                sessionRecordStatus.visibility = View.GONE
            } else {
                sessionRecordStatus.visibility = View.VISIBLE
                sessionRecordStatus.text = "Saved ${sessionRecorder.eventCount} events\n${file.absolutePath}"
            }
        } else {
            sessionRecorder.start()
            sessionRecordButton.text = "Stop"
            updateSessionRecordStatus()
        }
    }

    private fun updateSessionRecordStatus() {
        if (!sessionRecorder.isRecording) return
        sessionRecordStatus.visibility = View.VISIBLE
        sessionRecordStatus.text = "Recording session events: ${sessionRecorder.eventCount}"
    }

    private fun recordSessionCue(state: CoachState?, cue: String, source: String) {
        sessionRecorder.recordCue(
            flowId = if (isCurrentFlowInitialized()) currentFlow.id else null,
            stepNumber = if (isCurrentFlowInitialized()) flowEngine.currentStepNumber() else null,
            state = state,
            cue = cue,
            source = source
        )
        updateSessionRecordStatus()
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
        private const val CAMERA_AUTO_START_STABLE_MS = 600L
        private const val VIRTUAL_COACH_ASPECT_RATIO = 0.72f
        private const val VIRTUAL_COACH_HEIGHT_SCALE = 1.35f
        private const val VIRTUAL_COACH_MAX_HEIGHT_FRACTION = 0.68f
        private const val DEV_PREFS = "development"
        private const val PREF_DISABLE_CAMERA_SETUP = "disableCameraSetup"
        private const val EXTRA_DISABLE_CAMERA_SETUP = "devDisableCameraSetup"
        private const val EXTRA_ENABLE_CAMERA_SETUP = "devEnableCameraSetup"
        private val VIRTUAL_COACH_SCALE_INDICES = listOf(0, 11, 12, 23, 24, 25, 26, 27, 28)
    }
}
