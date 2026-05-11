package com.yogaflow

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.media.AudioAttributes
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.avatar.AvatarIntent
import com.yogaflow.coach.AvatarCoachStateMapper
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
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.RuntimeOverrideKey
import com.yogaflow.flow.RuntimeOverrideStore
import com.yogaflow.flow.RuntimeParams
import com.yogaflow.flow.YogaFlow
import com.yogaflow.db.SessionHistoryDb
import com.yogaflow.llm.LlmCoach
import com.yogaflow.llm.LlmInteractionDb
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
    lateinit var progressBar: ProgressBar
    lateinit var squatThresholdLabel: TextView
    lateinit var bridgeThresholdLabel: TextView
    lateinit var squatThresholdSeekBar: SeekBar
    lateinit var bridgeThresholdSeekBar: SeekBar
    lateinit var startClassButton: Button
    lateinit var startStretchButton: FrameLayout
    lateinit var startRecoveryButton: FrameLayout
    lateinit var startStrengthButton: FrameLayout
    lateinit var startDemoButton: Button
    lateinit var skinSelector: RadioGroup
    lateinit var beginSessionButton: Button
    lateinit var startButton: Button
    lateinit var pauseButton: Button
    lateinit var restartButton: Button
    lateinit var sessionRecordButton: Button
    lateinit var debugToggleButton: Button
    lateinit var cameraToggleButton: Button
    lateinit var moreButton: Button
    lateinit var secondaryButtonRow: View
    lateinit var sessionRecordStatus: TextView
    lateinit var sessionCompletionOverlay: View
    lateinit var completionDurationText: TextView
    lateinit var completionStepsText: TextView
    lateinit var completionCorrectionsText: TextView
    lateinit var historyOverlay: View
    lateinit var historyListView: android.widget.ListView
    lateinit var historyEmptyText: android.widget.TextView
    lateinit var historyTabList: android.widget.Button
    lateinit var historyTabStats: android.widget.Button
    lateinit var historySummaryBar: android.widget.TextView
    lateinit var statsScrollView: android.widget.ScrollView
    lateinit var statsContainer: android.widget.LinearLayout
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
    private var sessionStartTimeMs: Long = 0L
    private var sessionCorrectionCount: Int = 0
    private var sessionStepsCompleted: Int = 0
    private var selectedCourseName: String = "全課程"
    var cameraReady = false
    var cameraReadySince = 0L
    var autoStartedCurrentSetup = false
    var cameraSetupEnabled = false
    var suppressTuningCallbacks = false
    var debugViewEnabled = false
    var cameraSetupDisabledForDevelopment = false
    var isDemoMode = false
    private var avatarPositionOverride: Pair<Float, Float>? = null
    var lastCountdownText = ""
    var currentSkin: String = "classic"
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
    private lateinit var llmInteractionDb: LlmInteractionDb
    private lateinit var sessionHistoryDb: SessionHistoryDb
    private val demoHandler = Handler(Looper.getMainLooper())
    private val demoActions = listOf("hold_mountain", "hold_forward_fold", "hold_squat", "hold_twist")
    private var demoActionIndex = 0
    private var demoRunnable: Runnable? = null
    private var pendingTtsReady: Boolean? = null
    private val stateMachine = PoseStateMachine()
    private val avatarCoachStateMapper = AvatarCoachStateMapper()
    private val autoTuningAdvisor = AutoTuningAdvisor()

    override fun getActivity(): Activity = this

    override fun getGodot(): Godot = Godot.getInstance(this)

    override fun getCommandLine(): List<String> {
        Log.i("YogaFlow", "GodotHost.getCommandLine called - host wired")
        return emptyList()
    }

    private fun findSurfaceViewInHierarchy(view: android.view.View): android.view.SurfaceView? {
        if (view is android.view.SurfaceView) return view
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                val found = findSurfaceViewInHierarchy(view.getChildAt(i))
                if (found != null) return found
            }
        }
        return null
    }

    override fun onGodotSetupCompleted() {
        super.onGodotSetupCompleted()
        Log.i("YogaFlow", "Godot setup completed")
        runOnUiThread {
            android.os.Handler(mainLooper).postDelayed({
                val surfaceView = findSurfaceViewInHierarchy(virtualCoachView)
                if (surfaceView != null) {
                    surfaceView.setZOrderOnTop(true)
                    surfaceView.holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
                    Log.i("YogaFlow", "Godot SurfaceView transparent configured")
                } else {
                    Log.w("YogaFlow", "Godot SurfaceView not found")
                }
            }, 500L)
        }
    }

    override fun onGodotMainLoopStarted() {
        super.onGodotMainLoopStarted()
        Log.i("YogaFlow", "Godot main loop started - connecting bridge")
        godotAvatarBridge.connect()
        android.os.Handler(mainLooper).postDelayed({
            godotAvatarBridge.sendSkin(currentSkin)
        }, 1000L)
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startCamera() else coachText.text = "Camera permission is required."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.insetsController?.let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        bindViewsMain()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (homeView.visibility != View.VISIBLE) {
                    showHome()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
        cameraToggleButton = findViewById(R.id.cameraToggleButton)
        applySuggestionButton = findViewById(R.id.applySuggestionButton)
        sessionCompletionOverlay = findViewById(R.id.sessionCompletionOverlay)
        completionDurationText = findViewById(R.id.completionDurationText)
        completionStepsText = findViewById(R.id.completionStepsText)
        completionCorrectionsText = findViewById(R.id.completionCorrectionsText)
        findViewById<Button>(R.id.completionDoneButton).setOnClickListener { hideCompletionOverlay() }

        historyOverlay = findViewById(R.id.historyOverlay)
        historyListView = findViewById(R.id.historyListView)
        historyEmptyText = findViewById(R.id.historyEmptyText)
        historyTabList = findViewById(R.id.historyTabList)
        historyTabStats = findViewById(R.id.historyTabStats)
        historySummaryBar = findViewById(R.id.historySummaryBar)
        statsScrollView = findViewById(R.id.statsScrollView)
        statsContainer = findViewById(R.id.statsContainer)
        findViewById<Button>(R.id.historyButton).setOnClickListener { showHistoryOverlay(tab = "list") }
        findViewById<Button>(R.id.historyCloseButton).setOnClickListener { historyOverlay.visibility = View.GONE }
        historyTabList.setOnClickListener { showHistoryOverlay(tab = "list") }
        historyTabStats.setOnClickListener { showHistoryOverlay(tab = "stats") }

        sessionRecorder = SessionRecorder(this)
        godotAvatarBridge = GodotAvatarBridge()
        llmInteractionDb = LlmInteractionDb(this)
        sessionHistoryDb = SessionHistoryDb(this)
        loadDevelopmentSettings()
        applyDevelopmentIntentFlags(intent)
        loadThresholdPreferencesMain()
        loadCoachSkin()

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

        val llmCoach = LlmCoach(this, llmInteractionDb)

        coachCueController = CoachCueController(
            llmCoach = llmCoach,
            speaker = speaker,
            executor = coachExecutor,
            uiExecutor = { runnable -> runOnUiThread(runnable) },
            minCueIntervalMs = 5000L,
            sameCueIntervalMs = 8000L,
            onDisplay = { displayText, _ ->
                coachText.text = displayText
                recordSessionCue(null, displayText, "display")
            },
            isRequestCurrent = { flowId, step ->
                isCurrentFlowInitialized() &&
                    currentFlow.id == flowId && flowEngine.currentStepNumber() == step
            }
        )

        cameraSetupController = CameraSetupController(
            autoStartEnabled = false,
            autoStartStableMs = CAMERA_AUTO_START_STABLE_MS,
            getSessionState = { sessionState },
            onReadyChanged = { ready, readySince, autoStarted ->
                if (cameraSetupEnabled) {
                    cameraReady = ready
                    cameraReadySince = readySince
                    autoStartedCurrentSetup = autoStarted
                    startButton.isEnabled = ready
                    startButton.alpha = if (ready) 1f else 0.45f
                    beginSessionButton.isEnabled = ready
                    beginSessionButton.alpha = if (ready) 1f else 0.45f
                }
            },
            setSetupPanelVisible = { visible ->
                cameraSetupPanel.visibility = if (cameraSetupEnabled && visible) View.VISIBLE else View.GONE
                updateVirtualCoachFromCurrentStep()
            },
            onUpdateSetupPanel = { ready, framingMessage, orientationMessage ->
                if (cameraSetupEnabled) {
                    cameraSetupPanel.visibility = View.VISIBLE
                    updateVirtualCoachFromCurrentStep()
                    cameraSetupStatus.text = if (ready) {
                        "Ready"
                    } else {
                        listOf(framingMessage, orientationMessage).firstOrNull { it.isNotBlank() }.orEmpty()
                    }
                }
            },
            onAutoStartReady = ::beginRunningSession,
            onSpeakCoachCue = ::speakCoachCue,
            onUpdateDebugOverlay = { frame, detect, state, matched ->
                updateDebugOverlay(frame, detect, state, matched)
            },
            onUpdateUi = ::updateUi,
            onUpdateFramingBox = { status -> overlayView.setFramingStatus(status) }
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
        applyCameraSetupEnabled(false)
        bindSkinSelector()
        loadDiscoveredPlaylist(openClassView = false)
        applyCameraSetupEnabled(false)
        requestCameraIfNeeded()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i("YogaFlow", "onNewIntent received: extras=${intent.extras?.keySet()?.joinToString() ?: "none"}")
        applyDevelopmentIntentFlags(intent)
        val hasAvatarControlExtras =
            intent.hasExtra(EXTRA_AVATAR_TARGET_X) ||
            intent.hasExtra(EXTRA_AVATAR_TARGET_Y) ||
            intent.getBooleanExtra(EXTRA_AVATAR_CLEAR_OVERRIDE, false)
        if (hasAvatarControlExtras) {
            return
        }
        if (isCurrentFlowInitialized()) {
            resetToCameraSetup(
                if (cameraSetupDisabledForDevelopment) {
                    "Development: camera setup bypassed."
                } else {
                    "請先完成相機設定。"
                }
            )
            applyCameraSetupEnabled(cameraSetupEnabled)
        }
    }

    override fun onResume() {
        super.onResume()
        applyAvatarOverrideExtrasFromIntent(intent)
    }

    override fun onPause() {
        super.onPause()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
    }

    override fun onDestroy() {
        stopDemoCycle()
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
        startClassButton.setOnClickListener {
            selectedCourseName = "全課程"
            loadDiscoveredPlaylist()
        }
        startStretchButton.setOnClickListener {
            selectedCourseName = "Stretch 伸展"
            val flows = FlowLoader.loadByPose(this, "forward_fold")
            applyPlaylist(flows, openClassView = true)
        }
        startRecoveryButton.setOnClickListener {
            selectedCourseName = "Recovery 恢復"
            val flows = FlowLoader.loadByPose(this, "bridge", "twist")
            applyPlaylist(flows, openClassView = true)
        }
        startStrengthButton.setOnClickListener {
            selectedCourseName = "Strength 力量"
            val flows = FlowLoader.loadByPose(this, "squat")
            applyPlaylist(flows, openClassView = true)
        }
        startDemoButton.setOnClickListener {
            startDemoMode()
        }
        startButton.setOnClickListener {
            if (cameraReady) beginRunningSession()
        }
        beginSessionButton.setOnClickListener {
            if (cameraReady) beginRunningSession()
        }
        pauseButton.setOnClickListener {
            togglePause()
        }
        restartButton.setOnClickListener { restartCurrentPlaylist() }
        applySuggestionButton.setOnClickListener { applyLatestSuggestion() }
        sessionRecordButton.setOnClickListener { toggleSessionRecording() }
        debugToggleButton.setOnClickListener { applyDebugViewEnabled(!debugViewEnabled) }
        cameraToggleButton.setOnClickListener { applyCameraSetupEnabled(!cameraSetupEnabled) }
        moreButton.setOnClickListener {
            val isVisible = secondaryButtonRow.visibility == View.VISIBLE
            secondaryButtonRow.visibility = if (isVisible) View.GONE else View.VISIBLE
            moreButton.text = if (isVisible) "⋮" else "✕"
        }
    }

    private fun applyCameraSetupEnabled(enabled: Boolean) {
        cameraSetupEnabled = enabled
        cameraToggleButton.text = "Camera"
        if (enabled) {
            cameraSetupController.reset()
            cameraReady = false
            cameraReadySince = 0L
            autoStartedCurrentSetup = false
            startButton.isEnabled = false
            startButton.alpha = 0.45f
            beginSessionButton.isEnabled = false
            beginSessionButton.alpha = 0.45f
            cameraSetupPanel.visibility = View.VISIBLE
            updateVirtualCoachFromCurrentStep()
            cameraSetupStatus.text = "Checking body framing..."
            coachText.text = "請先完成相機設定。"
        } else {
            cameraSetupController.reset()
            cameraReady = false
            cameraReadySince = 0L
            autoStartedCurrentSetup = false
            startButton.isEnabled = false
            startButton.alpha = 0.45f
            beginSessionButton.isEnabled = false
            beginSessionButton.alpha = 0.45f
            cameraSetupPanel.visibility = View.GONE
            overlayView.setFramingStatus(null)
            updateVirtualCoachFromCurrentStep()
            if (sessionState != SessionState.RUNNING) {
                coachText.text = "Camera setup is off. Tap Camera to enable."
            }
        }
        updateUi(animated = false)
    }

    private fun bindSkinSelector() {
        val skinButtons = mapOf(
            R.id.skinClassic to "classic",
            R.id.skinNature to "nature",
            R.id.skinOcean to "ocean"
        )
        skinButtons.entries.firstOrNull { it.value == currentSkin }?.let { skinSelector.check(it.key) }
        skinSelector.setOnCheckedChangeListener { _, checkedId ->
            val skin = skinButtons[checkedId] ?: "classic"
            currentSkin = skin
            saveCoachSkin(skin)
            godotAvatarBridge.sendSkin(skin)
        }
    }

    private fun loadCoachSkin() {
        currentSkin = getSharedPreferences("coach_prefs", MODE_PRIVATE)
            .getString("coach_skin", "classic") ?: "classic"
    }

    private fun saveCoachSkin(skin: String) {
        getSharedPreferences("coach_prefs", MODE_PRIVATE)
            .edit()
            .putString("coach_skin", skin)
            .apply()
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
        if (intent == null) return
        if (!isDebuggableBuild()) {
            Log.w("YogaFlow", "applyDevelopmentIntentFlags skipped: non-debuggable build")
            return
        }
        Log.i("YogaFlow", "applyDevelopmentIntentFlags: extras=${intent.extras?.keySet()?.joinToString() ?: "none"}")
        when {
            intent.getBooleanExtra(EXTRA_DISABLE_CAMERA_SETUP, false) -> setDevelopmentCameraSetupDisabled(true)
            intent.getBooleanExtra(EXTRA_ENABLE_CAMERA_SETUP, false) -> setDevelopmentCameraSetupDisabled(false)
        }
        if (intent.getBooleanExtra(EXTRA_AVATAR_SELF_TEST, false)) {
            scheduleAvatarSelfTest()
        }
        if (intent.getBooleanExtra(EXTRA_DEMO_MODE, false)) {
            startDemoMode()
        }
        applyAvatarOverrideExtrasFromIntent(intent)
    }

    private fun applyAvatarOverrideExtrasFromIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_AVATAR_CLEAR_OVERRIDE, false)) {
            avatarPositionOverride = null
            Log.i("YogaFlow", "ADB avatar override cleared")
            sendAvatarPositionOverrideFrame(
                message = "ADB override cleared",
                overridePosition = null
            )
            intent.removeExtra(EXTRA_AVATAR_CLEAR_OVERRIDE)
        }
        val targetX = intent.getFloatExtra(EXTRA_AVATAR_TARGET_X, Float.NaN)
        val targetY = intent.getFloatExtra(EXTRA_AVATAR_TARGET_Y, Float.NaN)
        if (!targetX.isNaN() || !targetY.isNaN()) {
            val x = if (targetX.isNaN()) avatarPositionOverride?.first ?: 0f else targetX
            val y = if (targetY.isNaN()) avatarPositionOverride?.second ?: 0f else targetY
            avatarPositionOverride = Pair(x, y)
            Log.i("YogaFlow", "ADB avatar override set x=$x y=$y")
            avatarPositionOverride?.let { override ->
                sendAvatarPositionOverrideFrame(
                    message = "ADB position override",
                    overridePosition = override
                )
            }
            intent.removeExtra(EXTRA_AVATAR_TARGET_X)
            intent.removeExtra(EXTRA_AVATAR_TARGET_Y)
        }
    }

    private fun startDemoMode() {
        isDemoMode = true
        showClass()
        overlayView.visibility = View.INVISIBLE
        virtualCoachView.visibility = View.VISIBLE
        startDemoCycle()
    }

    private fun sendAvatarPositionOverrideFrame(
        message: String,
        overridePosition: Pair<Float, Float>?
    ) {
        runOnUiThread {
            virtualCoachView.visibility = View.VISIBLE
            godotAvatarBridge.send(
                PoseCoachFrame(
                    timestampMs = System.currentTimeMillis(),
                    stepId = "adb_override",
                    phase = "running",
                    pose = PoseMetrics(null, null, null, null, null),
                    coach = CoachVisualState("ok", null, message, 0),
                    avatar = AvatarIntent(
                        action = "hold_mountain",
                        emotion = "calm",
                        highlight = null,
                        position = "center",
                        facing = "user",
                        scale = 1.0f,
                        overridePosition = overridePosition
                    )
                ),
                force = true
            )
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
        overlayView.setLandmarks(frame.imageLandmarks, frame.imageWidth, frame.imageHeight)
        if (sessionState == SessionState.RUNNING && currentPose != null) {
            overlayView.setJointStatus(stateMachine.getJointStatus(currentPose!!, frame))
            overlayView.setJointCorrections(stateMachine.getJointCorrections(currentPose!!, frame))
        } else {
            overlayView.setJointStatus(emptyMap())
            overlayView.setJointCorrections(emptyMap())
        }
        if (isDemoMode) return
        if (!isCurrentFlowInitialized()) {
            virtualCoachView.visibility = View.INVISIBLE
            return
        }
        updateVirtualCoachBounds(frame.imageLandmarks)
        updateVirtualCoachFromCurrentStep()

        if (cameraSetupDisabledForDevelopment && sessionState != SessionState.COMPLETED) {
            bypassCameraSetupForDevelopment()
            liveCoachSessionController.handleReadyPoseFrame(frame, currentFlow, currentPose)
            return
        }

        if (!cameraSetupEnabled) {
            return
        }

        if (cameraSetupController.handleFrame(frame)) return

        liveCoachSessionController.handleReadyPoseFrame(frame, currentFlow, currentPose)
    }

    private fun beginRunningSession() {
        if (!cameraReady) return
        sessionState = SessionState.RUNNING
        startButton.visibility = View.VISIBLE
        cameraSetupPanel.visibility = View.GONE
        overlayView.setFramingStatus(null)
        virtualCoachView.visibility = View.VISIBLE
        coachCueController.reset()
        sessionStartTimeMs = System.currentTimeMillis()
        sessionCorrectionCount = 0
        sessionStepsCompleted = 0
        updateUi(animated = false)
    }

    private fun bypassCameraSetupForDevelopment() {
        if (sessionState == SessionState.RUNNING) return
        cameraReady = true
        cameraReadySince = System.currentTimeMillis()
        autoStartedCurrentSetup = true
        startButton.isEnabled = true
        startButton.alpha = 1f
        startButton.visibility = View.VISIBLE
        cameraSetupPanel.visibility = View.GONE
        virtualCoachView.visibility = View.VISIBLE
        sessionState = SessionState.RUNNING
        coachCueController.reset()
        sessionStartTimeMs = System.currentTimeMillis()
        sessionCorrectionCount = 0
        sessionStepsCompleted = 0
        updateUi(animated = false)
    }

    private fun scheduleAvatarSelfTest() {
        loadDiscoveredPlaylist(openClassView = true)
        val handler = android.os.Handler(mainLooper)
        val steps = listOf(
            3000L to "hold_mountain",
            5000L to "hold_forward_fold",
            7000L to "hold_squat",
            9000L to "hold_twist",
            11000L to "correct_knees",
            13000L to "walk_step_right",
            15000L to "walk_step_left",
            17000L to "walk_step_right",
            19000L to "walk_step_left",
            21000L to "hold_mountain"
        )
        for ((delay, action) in steps) {
            handler.postDelayed({
                android.util.Log.i("YogaFlow", "AvatarSelfTest: sending $action")
                virtualCoachView.visibility = android.view.View.VISIBLE
                godotAvatarBridge.send(buildSelfTestFrame(action), force = true)
            }, delay)
        }
    }

    private fun buildSelfTestFrame(action: String): PoseCoachFrame {
        return PoseCoachFrame(
            timestampMs = System.currentTimeMillis(),
            stepId = "self_test",
            phase = "running",
            pose = PoseMetrics(null, null, null, null, null),
            coach = CoachVisualState("ok", null, "Self-test: $action", 0),
            avatar = AvatarIntent(
                action = action,
                emotion = "calm",
                highlight = null,
                position = "demo_area",
                facing = "user",
                scale = 1.0f,
                overridePosition = avatarPositionOverride
            )
        )
    }

    private fun startDemoCycle() {
        stopDemoCycle()
        demoActionIndex = 0
        demoRunnable = object : Runnable {
            override fun run() {
                if (!isDemoMode) return
                val action = demoActions[demoActionIndex % demoActions.size]
                demoActionIndex += 1
                virtualCoachView.visibility = View.VISIBLE
                godotAvatarBridge.send(buildSelfTestFrame(action), force = true)
                demoHandler.postDelayed(this, 3000L)
            }
        }
        demoRunnable?.run()
    }

    internal fun stopDemoCycle() {
        demoRunnable?.let { demoHandler.removeCallbacks(it) }
        demoRunnable = null
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
                startButton.visibility = View.GONE
                beginSessionButton.isEnabled = false
                beginSessionButton.alpha = 0.45f
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
        sessionStepsCompleted += currentFlow.steps.size
        recordSessionCue(CoachState.HOLD, text, "flow_completed")
        val next = playlist.moveNext()
        if (next == null) {
            sessionState = SessionState.COMPLETED
            coachText.text = text
            virtualCoachView.visibility = View.INVISIBLE
            updateUi(animated = true)
            showCompletionOverlay()
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
        if (state == CoachState.CORRECTION) sessionCorrectionCount++
        if (!isCurrentFlowInitialized()) return
        val severity = if (state == CoachState.CORRECTION) 2 else 0
        coachCueController.speak(currentPose, currentFlow.id, flowEngine.currentStepNumber(), state, cue, severity = severity)
    }

    private fun showCompletionOverlay() {
        val elapsedMs = System.currentTimeMillis() - sessionStartTimeMs
        sessionHistoryDb.record(
            durationMs = elapsedMs,
            stepsCompleted = sessionStepsCompleted,
            correctionCount = sessionCorrectionCount,
            courseName = selectedCourseName
        )
        val minutes = (elapsedMs / 60000).toInt()
        val seconds = ((elapsedMs % 60000) / 1000).toInt()
        completionDurationText.text = "時長：%d:%02d".format(minutes, seconds)
        completionStepsText.text = "完成步驟：$sessionStepsCompleted"
        completionCorrectionsText.text = "修正提示：$sessionCorrectionCount 次"
        sessionCompletionOverlay.visibility = View.VISIBLE
    }

    private fun hideCompletionOverlay() {
        sessionCompletionOverlay.visibility = View.GONE
    }

    private fun showHistoryOverlay(tab: String = "list") {
        val entries = sessionHistoryDb.getAll()

        // Tab button highlight
        val activeColor = android.graphics.Color.parseColor("#FF5722")
        val inactiveColor = android.graphics.Color.parseColor("#444444")
        if (tab == "list") {
            historyTabList.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
            historyTabStats.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
            statsScrollView.visibility = View.GONE
            // Weekly summary bar
            val summary = sessionHistoryDb.getWeeklySummary()
            if (summary.sessionCount > 0) {
                val totalMin = (summary.totalMs / 60000).toInt()
                val timeStr = if (totalMin >= 60) "${totalMin / 60}h ${totalMin % 60}m" else "${totalMin}m"
                val streakStr = if (summary.streakDays > 0) "  ·  🔥 ${summary.streakDays} 天連續" else ""
                historySummaryBar.text = "本週 ${summary.sessionCount} 堂  ·  $timeStr$streakStr"
                historySummaryBar.visibility = View.VISIBLE
            } else {
                historySummaryBar.visibility = View.GONE
            }
            if (entries.isEmpty()) {
                historyListView.visibility = View.GONE
                historyEmptyText.visibility = View.VISIBLE
            } else {
                historyEmptyText.visibility = View.GONE
                historyListView.visibility = View.VISIBLE
                val items = entries.map { e ->
                    val date = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault())
                        .format(java.util.Date(e.tsMs))
                    val min = (e.durationMs / 60000).toInt()
                    val sec = ((e.durationMs % 60000) / 1000).toInt()
                    "${e.courseName}  $date  $min:%02d  步驟${e.stepsCompleted}  修正${e.correctionCount}次".format(sec)
                }
                historyListView.adapter = object : android.widget.ArrayAdapter<String>(
                    this, android.R.layout.simple_list_item_1, items
                ) {
                    override fun getView(position: Int, convertView: View?, parent: android.view.ViewGroup): View {
                        val view = super.getView(position, convertView, parent)
                        (view.findViewById(android.R.id.text1) as android.widget.TextView)
                            .setTextColor(android.graphics.Color.WHITE)
                        return view
                    }
                }
            }
        } else {
            historyTabList.backgroundTintList = android.content.res.ColorStateList.valueOf(inactiveColor)
            historyTabStats.backgroundTintList = android.content.res.ColorStateList.valueOf(activeColor)
            historyListView.visibility = View.GONE
            historyEmptyText.visibility = View.GONE
            historySummaryBar.visibility = View.GONE
            statsScrollView.visibility = View.VISIBLE
            populateStats(entries)
        }

        historyOverlay.visibility = View.VISIBLE
    }

    private fun populateStats(entries: List<com.yogaflow.db.SessionHistoryDb.SessionEntry>) {
        statsContainer.removeAllViews()

        val totalSessions = entries.size
        val totalMs = entries.sumOf { it.durationMs }
        val totalMin = (totalMs / 60000).toInt()
        val totalHrs = totalMin / 60
        val remMin = totalMin % 60

        val nowMs = System.currentTimeMillis()
        val weekMs = 7L * 24 * 60 * 60 * 1000
        val thisWeekCount = entries.count { nowMs - it.tsMs <= weekMs }

        val courseGroups = entries.groupBy { it.courseName.ifBlank { "全課程" } }
        val topCourse = courseGroups.maxByOrNull { it.value.size }?.key ?: "—"

        fun statRow(label: String, value: String) = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
            addView(android.widget.TextView(this@MainActivity).apply {
                text = label
                textSize = 14f
                setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                layoutParams = android.widget.LinearLayout.LayoutParams(0,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(android.widget.TextView(this@MainActivity).apply {
                text = value
                textSize = 14f
                setTextColor(android.graphics.Color.WHITE)
                gravity = android.view.Gravity.END
            })
        }

        statsContainer.addView(statRow("總練習次數", "$totalSessions 次"))
        statsContainer.addView(statRow("總練習時長",
            if (totalHrs > 0) "${totalHrs}h ${remMin}m" else "${remMin} 分鐘"))
        statsContainer.addView(statRow("本週練習", "$thisWeekCount 次"))
        statsContainer.addView(statRow("最多練習課程", topCourse))

        // Divider
        statsContainer.addView(android.view.View(this).apply {
            setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 1).apply { topMargin = 12; bottomMargin = 12 }
        })

        // Per-course bar chart
        statsContainer.addView(android.widget.TextView(this).apply {
            text = "各課程練習次數"
            textSize = 13f
            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
            setPadding(0, 0, 0, 8)
        })

        val maxCount = (courseGroups.values.maxOfOrNull { it.size } ?: 1).coerceAtLeast(1)
        val barColors = listOf("#FF5722", "#2196F3", "#4CAF50", "#FF9800", "#9C27B0")
        courseGroups.entries
            .sortedByDescending { it.value.size }
            .forEachIndexed { idx, (name, group) ->
                val barColor = android.graphics.Color.parseColor(barColors[idx % barColors.size])
                statsContainer.addView(android.widget.LinearLayout(this).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(0, 4, 0, 8)
                    // Label row
                    addView(android.widget.LinearLayout(this@MainActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        addView(android.widget.TextView(this@MainActivity).apply {
                            text = name
                            textSize = 13f
                            setTextColor(android.graphics.Color.WHITE)
                            layoutParams = android.widget.LinearLayout.LayoutParams(0,
                                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        addView(android.widget.TextView(this@MainActivity).apply {
                            text = "${group.size} 次"
                            textSize = 13f
                            setTextColor(android.graphics.Color.parseColor("#AAAAAA"))
                        })
                    })
                    // Bar
                    addView(android.widget.LinearLayout(this@MainActivity).apply {
                        orientation = android.widget.LinearLayout.HORIZONTAL
                        layoutParams = android.widget.LinearLayout.LayoutParams(
                            android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 12)
                        val filledView = android.view.View(this@MainActivity).apply {
                            setBackgroundColor(barColor)
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                group.size.toFloat())
                        }
                        val emptyView = android.view.View(this@MainActivity).apply {
                            setBackgroundColor(android.graphics.Color.parseColor("#33FFFFFF"))
                            layoutParams = android.widget.LinearLayout.LayoutParams(
                                0, android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                (maxCount - group.size).toFloat())
                        }
                        addView(filledView)
                        if (group.size < maxCount) addView(emptyView)
                    })
                })
            }
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
        virtualCoachView.visibility = if (shouldShowCoach) View.VISIBLE else View.INVISIBLE
    }

    private fun updateVirtualCoach(frame: PoseCoachFrame) {
        val shouldShowCoach = frame.stepId.isNotBlank() &&
            frame.avatar.action.isNotBlank() &&
            sessionState != SessionState.COMPLETED
        virtualCoachView.visibility = if (shouldShowCoach) View.VISIBLE else View.INVISIBLE
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
        val avatarCommand = avatarCoachStateMapper.map(
            detect = detect,
            state = state,
            matched = matched,
            failReason = failReason,
            poseId = currentPose.id,
            overridePosition = avatarPositionOverride
        )
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


    private fun severityFor(state: CoachState, matched: Boolean, failReason: String): Int {
        return when {
            matched && state != CoachState.CORRECTION -> 0
            failReason.contains("<") || failReason.contains(">") -> 2
            state == CoachState.CORRECTION -> 2
            else -> 1
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
        // Full-screen fusion: Godot handles its own layout
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    fun updateVirtualCoachFromCurrentStep() {
        if (!isCurrentFlowInitialized()) {
            virtualCoachView.visibility = View.INVISIBLE
            return
        }
        val step = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        if (step == null || sessionState == SessionState.COMPLETED) {
            virtualCoachView.visibility = View.INVISIBLE
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
        private const val EXTRA_AVATAR_SELF_TEST = "avatarSelfTest"
        private const val EXTRA_DEMO_MODE = "demoMode"
        private const val EXTRA_AVATAR_TARGET_X = "avatarTargetX"
        private const val EXTRA_AVATAR_TARGET_Y = "avatarTargetY"
        private const val EXTRA_AVATAR_CLEAR_OVERRIDE = "avatarClearOverride"
        private val VIRTUAL_COACH_SCALE_INDICES = listOf(0, 11, 12, 23, 24, 25, 26, 27, 28)
    }
}
