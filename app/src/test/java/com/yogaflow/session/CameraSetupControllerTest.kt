package com.yogaflow.session

import com.google.mediapipe.tasks.components.containers.Landmark
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.yogaflow.SessionState
import com.yogaflow.coach.CoachState
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.ViewOrientationStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Optional

class CameraSetupControllerTest {

    // Capture callbacks
    private var capturedReady: Boolean? = null
    private var capturedReadySince: Long? = null
    private var capturedAutoStarted: Boolean? = null
    private var setupPanelVisible: Boolean? = null
    private var autoStartCalled = false
    private var sessionState = SessionState.IDLE

    @Before
    fun setUp() {
        capturedReady = null
        capturedReadySince = null
        capturedAutoStarted = null
        setupPanelVisible = null
        autoStartCalled = false
        sessionState = SessionState.IDLE
    }

    private fun makeController(
        state: SessionState = SessionState.IDLE,
        autoStart: Boolean = false,
        stableMs: Long = 600L
    ): CameraSetupController {
        return CameraSetupController(
            autoStartEnabled = autoStart,
            autoStartStableMs = stableMs,
            getSessionState = { sessionState },
            onReadyChanged = { ready, readySince, autoStarted ->
                capturedReady = ready
                capturedReadySince = readySince
                capturedAutoStarted = autoStarted
            },
            setSetupPanelVisible = { visible ->
                setupPanelVisible = visible
            },
            onUpdateSetupPanel = { _, _, _ -> },
            onAutoStartReady = {
                autoStartCalled = true
            },
            onSpeakCoachCue = { _, _ -> },
            onUpdateDebugOverlay = { _, _, _, _ -> },
            onUpdateUi = { _ -> },
            onUpdateFramingBox = null
        )
    }

    // ========== Fixture Helpers ==========

    /**
     * Good frame: 33 landmarks centered at x=0.5, spread across y=[0.1, 0.9], all visible (0.8).
     * Passes CameraFramingCoach: ≥16 visible, minY=0.1 ≥ 0.03, maxY=0.9 ≤ 0.97,
     * bodyHeight=0.8 between 0.30 and 0.96, bodyWidth=0.2 ≥ 0.10, centerX=0.5 in [0.28, 0.72].
     */
    private fun goodFrameLandmarks(): List<NormalizedLandmark> {
        val landmarks = MutableList(33) { i ->
            val y = 0.1f + (i / 32f) * 0.8f  // spread y from 0.1 to 0.9
            val x = 0.5f + ((-1)..(1)).random() * 0.1f  // slight x variation around 0.5
            NormalizedLandmark.create(x, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }
        return landmarks
    }

    /**
     * Bad frame: empty landmarks (should fail CameraFramingCoach).
     */
    private fun badFrameLandmarks(): List<NormalizedLandmark> = emptyList()

    /**
     * Bad frame: only 10 landmarks (below MIN_VISIBLE_LANDMARKS = 16).
     */
    private fun tooFewLandmarks(): List<NormalizedLandmark> {
        return (0..9).map { i ->
            val y = 0.1f + (i / 9f) * 0.8f
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }
    }

    /**
     * Frontal pose: ≥25 world landmarks with z ≈ 0, shoulders at x ≈ ±0.2.
     * Passes ViewOrientation: landmarks.size > 24, depth/width score < 0.35.
     */
    private fun frontalWorldLandmarks(): List<Landmark> {
        val landmarks = MutableList(33) { i ->
            when (i) {
                11 -> Landmark.create(-0.2f, 0.1f, 0f)  // left shoulder
                12 -> Landmark.create(0.2f, 0.1f, 0f)   // right shoulder
                23 -> Landmark.create(-0.15f, 0.6f, 0f) // left hip
                24 -> Landmark.create(0.15f, 0.6f, 0f)  // right hip
                else -> Landmark.create(0f, 0.3f + (i * 0.01f), 0f)
            }
        }
        return landmarks
    }

    /**
     * Non-frontal pose: world landmarks with large z rotation (rotated pose).
     * depth/width score > 0.75 → TOO_ROTATED.
     */
    private fun rotatedWorldLandmarks(): List<Landmark> {
        val landmarks = MutableList(33) { i ->
            when (i) {
                11 -> Landmark.create(-0.2f, 0.1f, 0.5f)  // large z diff for shoulders
                12 -> Landmark.create(0.2f, 0.1f, 0f)
                23 -> Landmark.create(-0.15f, 0.6f, 0.5f)
                24 -> Landmark.create(0.15f, 0.6f, 0f)
                else -> Landmark.create(0f, 0.3f + (i * 0.01f), 0f)
            }
        }
        return landmarks
    }

    // ========== Tests: IDLE State ==========

    @Test
    fun handleFrame_idleState_goodFrame_signalsReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in IDLE with good framing", result)
        assertTrue("Should signal ready=true", capturedReady == true)
        assertTrue("readySince should be set", capturedReadySince != null && capturedReadySince!! > 0)
        assertFalse("autoStarted should be false (not enabled)", capturedAutoStarted == true)
    }

    @Test
    fun handleFrame_idleState_badFrame_notReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in IDLE", result)
        assertEquals("Should signal ready=false", false, capturedReady)
        assertEquals("readySince should be 0", 0L, capturedReadySince)
    }

    @Test
    fun handleFrame_idleState_tooFewLandmarks_notReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = tooFewLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in IDLE", result)
        assertEquals("Should signal ready=false", false, capturedReady)
    }

    @Test
    fun handleFrame_idleState_goodFrame_doesNotShowSetupPanel() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        // In IDLE with good frame, setSetupPanelVisible should not be explicitly called to true.
        // The default behavior is to not show the panel when ready.
        assertFalse("Setup panel should not be shown when ready in IDLE", setupPanelVisible == true)
    }

    @Test
    fun handleFrame_idleState_rotatedPose_notReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = rotatedWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in IDLE", result)
        assertEquals("Should signal ready=false due to orientation", false, capturedReady)
    }

    // ========== Tests: RUNNING State ==========

    @Test
    fun handleFrame_runningState_goodFrame_returnsFalse() {
        sessionState = SessionState.RUNNING
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertFalse("Should pass through frame (return false) in RUNNING with good frame", result)
        assertEquals("Setup panel should not be shown", null, setupPanelVisible)
    }

    @Test
    fun handleFrame_runningState_badFrame_showsSetupPanel_returnsTrue() {
        sessionState = SessionState.RUNNING
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in RUNNING with bad frame", result)
        assertTrue("Setup panel should be shown", setupPanelVisible == true)
        assertEquals("Should signal ready=false", false, capturedReady)
    }

    @Test
    fun handleFrame_runningState_goodFrameTooBad_returnsTrue() {
        sessionState = SessionState.RUNNING
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = rotatedWorldLandmarks()  // bad orientation
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame when orientation is bad", result)
        assertTrue("Setup panel should be shown", setupPanelVisible == true)
    }

    // ========== Tests: PAUSED State ==========

    @Test
    fun handleFrame_pausedState_goodFrame_showsSetupPanel() {
        sessionState = SessionState.PAUSED
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in PAUSED", result)
        assertTrue("Setup panel should be shown in PAUSED state", setupPanelVisible == true)
        assertTrue("Should signal ready=true when framing is good", capturedReady == true)
    }

    @Test
    fun handleFrame_pausedState_badFrame_showsSetupPanel() {
        sessionState = SessionState.PAUSED
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in PAUSED", result)
        assertTrue("Setup panel should be shown", setupPanelVisible == true)
        assertEquals("Should signal ready=false", false, capturedReady)
    }

    // ========== Tests: COMPLETED State ==========

    @Test
    fun handleFrame_completedState_resetsReady() {
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        // 1. Get to ready state first
        sessionState = SessionState.IDLE
        controller.handleFrame(frame)
        assertTrue("Should be ready initially", capturedReady == true)

        // 2. Transition to COMPLETED
        sessionState = SessionState.COMPLETED
        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame in COMPLETED", result)
        assertEquals("Should reset ready to false", false, capturedReady)
        assertEquals("readySince should be reset to 0", 0L, capturedReadySince)
        assertEquals("Setup panel should be hidden", false, setupPanelVisible)
    }

    // ========== Tests: Auto-Start ==========

    @Test
    fun handleFrame_autoStartEnabled_triggersOnReadyAfterStableMs() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = true, stableMs = 0L)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        val result = controller.handleFrame(frame)

        assertTrue("Should swallow frame", result)
        assertTrue("Should signal ready=true", capturedReady == true)
        assertTrue("Should trigger autoStart (stableMs=0)", autoStartCalled)
        assertEquals("autoStarted flag should be true", true, capturedAutoStarted)
    }

    @Test
    fun handleFrame_autoStartDisabled_doesNotTrigger() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false, stableMs = 0L)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertFalse("Should not trigger autoStart when disabled", autoStartCalled)
        assertFalse("autoStarted flag should remain false", capturedAutoStarted == true)
    }

    @Test
    fun handleFrame_autoStartEnabledButStableWindowNotPassed() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = true, stableMs = 10000L)
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertFalse("Should not trigger autoStart within stable window", autoStartCalled)
        assertEquals("autoStarted flag should remain false", false, capturedAutoStarted)
    }

    @Test
    fun handleFrame_autoStartLosesReadyState_doesNotAutoStart() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = true, stableMs = 0L)

        // First frame: good
        val goodFrame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(goodFrame)
        autoStartCalled = false  // reset

        // Second frame: bad (loses ready state)
        val badFrame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(badFrame)

        assertFalse("Should not trigger autoStart after losing ready state", autoStartCalled)
        assertEquals("autoStarted should be reset to false", false, capturedAutoStarted)
    }

    // ========== Tests: Reset ==========

    @Test
    fun reset_clearsReadyState() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Get to ready state
        val frame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(frame)
        assertTrue("Should be ready after good frame", capturedReady == true)

        // Reset
        controller.reset()

        assertEquals("Should reset ready to false", false, capturedReady)
        assertEquals("readySince should be reset to 0", 0L, capturedReadySince)
        assertEquals("autoStarted should be reset to false", false, capturedAutoStarted)
    }

    @Test
    fun reset_whenAlreadyNotReady_doesNotSignal() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Start in not-ready state
        val badFrame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(badFrame)
        assertEquals("Should be not ready", false, capturedReady)

        // Clear capture
        capturedReady = null
        capturedReadySince = null
        capturedAutoStarted = null

        // Reset should not signal if already not ready
        controller.reset()

        // onReadyChanged should not be called
        assertEquals("onReadyChanged should not be called", null, capturedReady)
    }

    // ========== Tests: Ready State Transitions ==========

    @Test
    fun handleFrame_transitionFromGoodToBad_updatesReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Frame 1: good
        val goodFrame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(goodFrame)
        assertTrue("Should be ready after good frame", capturedReady == true)
        val readySinceGood = capturedReadySince

        // Frame 2: bad
        val badFrame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(badFrame)
        assertEquals("Should transition to not ready", false, capturedReady)
        assertEquals("readySince should be cleared", 0L, capturedReadySince)
    }

    @Test
    fun handleFrame_transitionFromBadToGood_updatesReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Frame 1: bad
        val badFrame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(badFrame)
        assertEquals("Should not be ready", false, capturedReady)

        // Frame 2: good
        val goodFrame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )
        controller.handleFrame(goodFrame)
        assertTrue("Should transition to ready", capturedReady == true)
        assertTrue("readySince should be set", capturedReadySince != null && capturedReadySince!! > 0)
    }

    @Test
    fun handleFrame_idleToRunningToCompleted() {
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val goodFrame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        // IDLE: good frame → ready
        controller.handleFrame(goodFrame)
        assertTrue("Should be ready in IDLE", capturedReady == true)

        // RUNNING: good frame → passes through
        sessionState = SessionState.RUNNING
        val result = controller.handleFrame(goodFrame)
        assertFalse("Should pass through in RUNNING", result)

        // COMPLETED: frame → resets ready
        sessionState = SessionState.COMPLETED
        controller.handleFrame(goodFrame)
        assertEquals("Should reset ready in COMPLETED", false, capturedReady)
        assertEquals("readySince should be 0", 0L, capturedReadySince)
    }

    // ========== Tests: Panel Visibility ==========

    @Test
    fun handleFrame_runningState_goodFrame_panelInvisible() {
        sessionState = SessionState.RUNNING
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val goodFrame = PoseDetectionResult(
            imageLandmarks = goodFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(goodFrame)

        assertEquals("Setup panel should not be explicitly set when passing through", null, setupPanelVisible)
    }

    @Test
    fun handleFrame_runningState_badFrame_panelVisible() {
        sessionState = SessionState.RUNNING
        val controller = makeController(state = SessionState.IDLE, autoStart = false)
        val badFrame = PoseDetectionResult(
            imageLandmarks = badFrameLandmarks(),
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(badFrame)

        assertTrue("Setup panel should be shown when bad frame in RUNNING", setupPanelVisible == true)
    }

    // ========== Tests: Framing Edge Cases ==========

    @Test
    fun handleFrame_landmarksWithLowVisibility_notReady() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks with visibility < 0.45
        val lowVisibilityLandmarks = (0..32).map { i ->
            val y = 0.1f + (i / 32f) * 0.8f
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.2f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = lowVisibilityLandmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready with low-visibility landmarks", false, capturedReady)
    }

    @Test
    fun handleFrame_userTooCloseToCamera() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks that span 0.98 in height (> TOO_CLOSE_HEIGHT = 0.96)
        val landmarks = (0..32).map { i ->
            val y = 0.01f + (i / 32f) * 0.98f  // spans 0.01 to 0.99
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when user is too close", false, capturedReady)
    }

    @Test
    fun handleFrame_userTooFarFromCamera() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks that span 0.20 in height (< TOO_FAR_HEIGHT = 0.30)
        val landmarks = (0..32).map { i ->
            val y = 0.4f + (i / 32f) * 0.20f  // spans 0.4 to 0.6, height = 0.20
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when user is too far", false, capturedReady)
    }

    @Test
    fun handleFrame_userOffToLeft() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks centered at x=0.15 (< LEFT_CENTER_LIMIT = 0.28)
        val landmarks = (0..32).map { i ->
            val y = 0.1f + (i / 32f) * 0.8f
            NormalizedLandmark.create(0.15f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when user is too left", false, capturedReady)
    }

    @Test
    fun handleFrame_userOffToRight() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks centered at x=0.85 (> RIGHT_CENTER_LIMIT = 0.72)
        val landmarks = (0..32).map { i ->
            val y = 0.1f + (i / 32f) * 0.8f
            NormalizedLandmark.create(0.85f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when user is too right", false, capturedReady)
    }

    @Test
    fun handleFrame_headCutOffTop() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks with minY = 0.01 (< TOP_MARGIN = 0.03)
        val landmarks = (0..32).map { i ->
            val y = 0.01f + (i / 32f) * 0.5f  // spans 0.01 to 0.51
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when head is cut off", false, capturedReady)
    }

    @Test
    fun handleFrame_feetCutOffBottom() {
        sessionState = SessionState.IDLE
        val controller = makeController(state = SessionState.IDLE, autoStart = false)

        // Create landmarks with maxY = 0.99 (> BOTTOM_MARGIN = 0.97)
        val landmarks = (0..32).map { i ->
            val y = 0.5f + (i / 32f) * 0.49f  // spans 0.5 to 0.99
            NormalizedLandmark.create(0.5f, y, 0f, Optional.of(1.0f), Optional.of(0.8f))
        }

        val frame = PoseDetectionResult(
            imageLandmarks = landmarks,
            worldLandmarks = frontalWorldLandmarks()
        )

        controller.handleFrame(frame)

        assertEquals("Should not be ready when feet are cut off", false, capturedReady)
    }
}
