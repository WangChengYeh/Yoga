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
import com.yogaflow.coach.BridgeDetectionMapper
import com.yogaflow.coach.CoachPhrasePolisher
import com.yogaflow.coach.CoachSpeaker
import com.yogaflow.coach.CoachState
import com.yogaflow.coach.ForwardFoldDetectionMapper
import com.yogaflow.coach.PoseDetectionRouter
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.PoseStateMachine
import com.yogaflow.coach.SquatDetectionMapper
import com.yogaflow.coach.TwistDetectionMapper
import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.FlowPlaylistEngine
import com.yogaflow.flow.YogaFlow
import com.yogaflow.llm.LlmCoach
import com.yogaflow.pose.CameraFramingCoach
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.CameraPosePipeline
import com.yogaflow.pose.DebugPoseInfo
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.pose.PoseHelper
import com.yogaflow.pose.PoseOverlayView
import com.yogaflow.pose.ViewOrientation
import com.yogaflow.pose.ViewOrientationStatus
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog
import kotlin.math.abs
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private enum class SessionState { IDLE, RUNNING, PAUSED, COMPLETED }

    private lateinit var homeView: View
    private lateinit var classView: View
    private lateinit var previewView: PreviewView
    private lateinit var overlayView: PoseOverlayView
    private lateinit var debugText: TextView
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
    private val coachExecutor = Executors.newSingleThreadExecutor()

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
    private var lastCoachCue = ""
    private var lastCoachAt = 0L
    private var coachRequestId = 0L

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
        debugText = findViewById(R.id.debugText)
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
            cameraExecutor = cameraExecutor,
            onError = {
                runOnUiThread {
                    coachText.text = "Camera 啟動失敗，請確認權限或重新開啟 App。"
                }
            }
        )

        tts = TextToSpeech(this, this)
        speaker = CoachSpeaker(tts)
        llmCoach = LlmCoach(this)

        if (!poseHelper.isReady) {
            coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
        }

        poseHelper.onResult = { frame ->
            runOnUiThread {
                handlePoseFrame(frame)
            }
        }
    }

    private fun handlePoseFrame(frame: PoseDetectionResult) {
        overlayView.setLandmarks(frame.imageLandmarks)

        val framing = CameraFramingCoach.analyze(frame)
        val orientation = ViewOrientation.analyze(frame)

        if (sessionState != SessionState.RUNNING) {
            val setupCue = cameraSetupCue(framing, orientation)
            if (setupCue.isNotBlank()) {
                coachText.text = setupCue
            }
            updateDebugOverlay(frame, detect = "camera_setup", state = CoachState.SETUP, matched = false)
            updateUi(animated = false)
            return
        }

        val allowPoseCoaching = framing.status == CameraFramingStatus.GOOD &&
            orientation.status == ViewOrientationStatus.GOOD

        if (!allowPoseCoaching) {
            val setupCue = cameraSetupCue(framing, orientation)
            speakCoachCue(CoachState.CORRECTION, setupCue)
            updateDebugOverlay(frame, detect = "camera_setup", state = CoachState.CORRECTION, matched = false)
            updateUi(animated = false)
            return
        }

        val currentStep = currentFlow.steps.getOrNull(flowEngine.currentStepNumber() - 1)
        if (currentStep == null) {
            completeCurrentFlow(currentFlow.endCue.ifBlank { "課程完成，很好。" })
            updateDebugOverlay(frame, detect = "flow_complete", state = CoachState.HOLD, matched = true)
            updateUi(animated = true)
            return
        }

        val mapping = PoseDetectionRouter.evaluate(
            poseId = currentPose.id,
            detect = currentStep.detect,
            frame = frame,
            fallback = stateMachine,
            currentPose = currentPose
        )
        val event = flowEngine.update(currentFlow, mapping.state, mapping.matched)
        updateDebugOverlay(frame, detect = currentStep.detect, state = mapping.state, matched = mapping.matched)

        when (event) {
            is PoseFlowEngine.FlowEvent.Cue -> {
                val cue = if (mapping.matched) event.text else mapping.cue
                speakCoachCue(mapping.state, cue)
            }

            is PoseFlowEngine.FlowEvent.StepCompleted -> {
                animateFlowTransition()
                speakCoachCue(event.state, event.text)
            }

            is PoseFlowEngine.FlowEvent.FlowCompleted -> {
                completeCurrentFlow(event.text)
            }
        }

        updateUi(animated = true)
    }

    private fun updateDebugOverlay(
        frame: PoseDetectionResult,
        detect: String,
        state: CoachState,
        matched: Boolean
    ) {
        if (!::debugText.isInitialized) return

        val leftKnee = PoseGeometry.angle(frame, 23, 25, 27).toNullableDegrees()
        val rightKnee = PoseGeometry.angle(frame, 24, 26, 28).toNullableDegrees()
        val leftHip = PoseGeometry.angle(frame, 11, 23, 25).toNullableDegrees()
        val rightHip = PoseGeometry.angle(frame, 12, 24, 26).toNullableDegrees()

        val torsoTwist = if (leftHip != null && rightHip != null) {
            abs(leftHip - rightHip)
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
            torsoTwistEstimate = torsoTwist
        )

        debugText.text = debugInfo.toDisplayText()
    }

    private fun PoseGeometry.AngleResult.toNullableDegrees(): Double? {
        return if (confidence == PoseGeometry.Confidence.INVALID) null else degrees
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

    private fun cameraSetupCue(
        framing: com.yogaflow.pose.CameraFramingResult,
        orientation: com.yogaflow.pose.ViewOrientationResult
    ): String {
        return when {
            framing.status != CameraFramingStatus.GOOD -> framing.message
            orientation.status != ViewOrientationStatus.GOOD -> orientation.message
            else -> ""
        }
    }

    private fun shouldEmitCoach(cue: String): Boolean {
        val now = System.currentTimeMillis()
        if (cue == lastCoachCue && now - lastCoachAt < SAME_CUE_INTERVAL_MS) return false
        if (now - lastCoachAt < MIN_CUE_INTERVAL_MS) return false

        lastCoachCue = cue
        lastCoachAt = now
        return true
    }

    private fun speakCoachCue(state: CoachState, cue: String) {
        if (cue.isBlank()) return
        if (!shouldEmitCoach(cue)) return

        val pose = currentPose
        val requestId = ++coachRequestId
        val flowId = currentFlow.id
        val step = flowEngine.currentStepNumber()

        coachExecutor.execute {
            val generated = llmCoach.generate(pose, state, cue)
            val isFallback = generated == cue
            val spokenText = CoachPhrasePolisher.polish(generated)
            val displayText = if (isFallback) "(fallback) $spokenText" else spokenText

            runOnUiThread {
                if (requestId != coachRequestId) return@runOnUiThread
                if (flowId != currentFlow.id) return@runOnUiThread
                if (step != flowEngine.currentStepNumber()) return@runOnUiThread

                llmStatus.text = if (isFallback) "LLM: OFF" else "LLM: ON"
                coachText.text = displayText
                speaker.speakIfNeeded(spokenText)
            }
        }
    }

    private fun speakRawCue(cue: String) {
        if (cue.isBlank()) return
        speaker.speakIfNeeded(cue)
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
            coachRequestId++
            coachText.text = "已暫停。準備好後按 Start 繼續。"
            updateUi(animated = false)
        }

        restartButton.setOnClickListener {
            restartCurrentPlaylist()
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
        sessionState = SessionState.IDLE
        lastCountdownText = ""
        lastCoachCue = ""
        lastCoachAt = 0L
        coachRequestId++
        coachText.text = "按 Start 開始課程。"
        llmStatus.text = "LLM: OFF"

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
        currentFlow = flow
        currentPose = resolvePose(currentFlow)
        sessionState = SessionState.IDLE
        lastCountdownText = ""
        lastCoachCue = ""
        lastCoachAt = 0L
        coachRequestId++
        coachText.text = "已重新開始，按 Start 開始課程。"
        updateUi(animated = false)
    }

    private fun resetDetectionMappers() {
        ForwardFoldDetectionMapper.reset()
        TwistDetectionMapper.reset()
        SquatDetectionMapper.reset()
        BridgeDetectionMapper.reset()
    }

    private fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose }
            ?: YogaPoseCatalog.poses.first()
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
            val result = tts.setLanguage(Locale.TAIWAN)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                coachText.text = "TTS 中文語音不可用，請安裝語音資料。"
            }
        }
    }

    override fun onDestroy() {
        if (::cameraPipeline.isInitialized) {
            cameraPipeline.stop()
        }
        cameraExecutor.shutdown()
        coachExecutor.shutdown()
        if (::tts.isInitialized) {
            tts.shutdown()
        }
        super.onDestroy()
    }

    companion object {
        private const val CAMERA_PERMISSION_REQUEST = 100
        private const val MIN_CUE_INTERVAL_MS = 1200L
        private const val SAME_CUE_INTERVAL_MS = 2500L
    }
}
