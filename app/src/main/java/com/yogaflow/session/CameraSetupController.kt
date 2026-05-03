package com.yogaflow.session

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
    private val getCameraReady: () -> Boolean,
    private val getCameraReadySince: () -> Long,
    private val getAutoStarted: () -> Boolean,
    private val setSetupPanelVisible: (Boolean) -> Unit,
    private val onUpdateSetupPanel: (ready: Boolean, framingMessage: String, orientationMessage: String) -> Unit,
    private val onMaybeAutoStart: () -> Unit,
    private val onSpeakCoachCue: (CoachState, String) -> Unit,
    private val onUpdateDebugOverlay: (frame: PoseDetectionResult, detect: String, state: CoachState, matched: Boolean) -> Unit,
    private val onUpdateUi: (Boolean) -> Unit
) {
    fun handleFrame(frame: PoseDetectionResult): Boolean {
        val framing = CameraFramingCoach.analyze(frame)
        val orientation = ViewOrientation.analyze(frame)
        val ready = framing.status == CameraFramingStatus.GOOD && orientation.status == ViewOrientationStatus.GOOD

        when (getSessionState()) {
            SessionState.IDLE -> {
                onUpdateSetupPanel(ready, framing.message, orientation.message)
                onMaybeAutoStart()
                onUpdateDebugOverlay(frame, "camera_setup", CoachState.SETUP, ready)
                onUpdateUi(false)
                return true
            }
            SessionState.PAUSED -> {
                setSetupPanelVisible(false)
                onUpdateDebugOverlay(frame, "paused", CoachState.SETUP, ready)
                onUpdateUi(false)
                return true
            }
            SessionState.COMPLETED -> {
                setSetupPanelVisible(false)
                onUpdateDebugOverlay(frame, "completed", CoachState.HOLD, true)
                onUpdateUi(false)
                return true
            }
            SessionState.RUNNING -> Unit
        }

        setSetupPanelVisible(false)

        if (!ready) {
            val setupCue = cameraSetupCue(framing, orientation)
            onSpeakCoachCue(CoachState.CORRECTION, setupCue)
            onUpdateDebugOverlay(frame, "camera_setup", CoachState.CORRECTION, false)
            onUpdateUi(false)
            return true
        }

        return false
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
