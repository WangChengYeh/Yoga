package com.yogaflow.session

import com.yogaflow.SessionState
import com.yogaflow.coach.CoachState
import com.yogaflow.pose.CameraFramingCoach
import com.yogaflow.pose.CameraFramingResult
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.ViewOrientation
import com.yogaflow.pose.ViewOrientationResult
import com.yogaflow.pose.ViewOrientationStatus

class CameraSetupController(
    private val autoStartEnabled: Boolean,
    private val autoStartStableMs: Long,
    private val getSessionState: () -> SessionState,
    private val onReadyChanged: (ready: Boolean, readySince: Long, autoStarted: Boolean) -> Unit,
    private val setSetupPanelVisible: (Boolean) -> Unit,
    private val onUpdateSetupPanel: (ready: Boolean, framingMessage: String, orientationMessage: String) -> Unit,
    private val onAutoStartReady: () -> Unit,
    private val onSpeakCoachCue: (CoachState, String) -> Unit,
    private val onUpdateDebugOverlay: (frame: PoseDetectionResult, detect: String, state: CoachState, matched: Boolean) -> Unit,
    private val onUpdateUi: (Boolean) -> Unit
) {
    private var ready = false
    private var readySince = 0L
    private var autoStarted = false

    fun reset() {
        resetReadyState()
    }

    fun handleFrame(frame: PoseDetectionResult): Boolean {
        val framing = CameraFramingCoach.analyze(frame)
        val orientation = ViewOrientation.analyze(frame)
        val frameReady = framing.status == CameraFramingStatus.GOOD && orientation.status == ViewOrientationStatus.GOOD

        when (getSessionState()) {
            SessionState.IDLE -> {
                updateReadyState(frameReady)
                onUpdateSetupPanel(frameReady, framing.message, orientation.message)
                maybeAutoStart()
                onUpdateDebugOverlay(frame, "camera_setup", CoachState.SETUP, frameReady)
                onUpdateUi(false)
                return true
            }
            SessionState.PAUSED -> {
                updateReadyState(frameReady)
                setSetupPanelVisible(true)
                onUpdateSetupPanel(frameReady, framing.message, orientation.message)
                onUpdateDebugOverlay(frame, "paused", CoachState.SETUP, frameReady)
                onUpdateUi(false)
                return true
            }
            SessionState.COMPLETED -> {
                resetReadyState()
                setSetupPanelVisible(false)
                onUpdateDebugOverlay(frame, "completed", CoachState.HOLD, true)
                onUpdateUi(false)
                return true
            }
            SessionState.RUNNING -> Unit
        }

        resetReadyState()
        setSetupPanelVisible(false)

        if (!frameReady) {
            val setupCue = cameraSetupCue(framing, orientation)
            onSpeakCoachCue(CoachState.CORRECTION, setupCue)
            onUpdateDebugOverlay(frame, "camera_setup", CoachState.CORRECTION, false)
            onUpdateUi(false)
            return true
        }

        return false
    }

    private fun updateReadyState(nextReady: Boolean) {
        val now = System.currentTimeMillis()
        if (nextReady) {
            if (!ready) readySince = now
        } else {
            readySince = 0L
            autoStarted = false
        }
        ready = nextReady
        onReadyChanged(ready, readySince, autoStarted)
    }

    private fun resetReadyState() {
        if (!ready && readySince == 0L && !autoStarted) return
        ready = false
        readySince = 0L
        autoStarted = false
        onReadyChanged(ready, readySince, autoStarted)
    }

    private fun maybeAutoStart() {
        if (!autoStartEnabled || !ready || autoStarted || readySince == 0L) return
        if (System.currentTimeMillis() - readySince < autoStartStableMs) return
        autoStarted = true
        onReadyChanged(ready, readySince, autoStarted)
        onAutoStartReady()
    }

    private fun cameraSetupCue(
        framing: CameraFramingResult,
        orientation: ViewOrientationResult
    ): String {
        return when {
            framing.status != CameraFramingStatus.GOOD -> framing.message
            orientation.status != ViewOrientationStatus.GOOD -> orientation.message
            else -> ""
        }
    }
}
