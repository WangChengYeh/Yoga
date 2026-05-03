package com.yogaflow.ui

import android.view.View
import android.widget.Button
import android.widget.TextView
import com.yogaflow.pose.CameraFramingResult
import com.yogaflow.pose.CameraFramingStatus
import com.yogaflow.pose.ViewOrientationResult
import com.yogaflow.pose.ViewOrientationStatus

/**
 * Owns camera onboarding state for the class start gate.
 *
 * This keeps camera readiness, stable-ready timing, auto-start gating,
 * and setup panel text outside MainActivity.
 */
class CameraSetupController(
    private val panel: View,
    private val status: TextView,
    private val coachText: TextView,
    private val startButton: Button,
    private val autoStartEnabled: Boolean,
    private val autoStartStableMs: Long,
    private val nowMs: () -> Long = { System.currentTimeMillis() }
) {
    var cameraReady: Boolean = false
        private set

    private var cameraReadySince: Long = 0L
    private var autoStartedCurrentSetup: Boolean = false

    fun reset(message: String = "請先完成相機設定。") {
        cameraReady = false
        cameraReadySince = 0L
        autoStartedCurrentSetup = false

        panel.visibility = View.VISIBLE
        status.text = "Checking body framing..."
        coachText.text = message
        startButton.isEnabled = false
        startButton.alpha = 0.45f
    }

    fun update(ready: Boolean, framingMessage: String, orientationMessage: String) {
        val now = nowMs()
        if (ready) {
            if (!cameraReady) cameraReadySince = now
        } else {
            cameraReadySince = 0L
            autoStartedCurrentSetup = false
        }

        cameraReady = ready
        panel.visibility = View.VISIBLE
        startButton.isEnabled = ready
        startButton.alpha = if (ready) 1.0f else 0.45f

        if (ready) {
            val stableFor = now - cameraReadySince
            val remaining = ((autoStartStableMs - stableFor).coerceAtLeast(0L) / 1000.0)
            status.text = if (stableFor >= autoStartStableMs) {
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
            status.text = "Not Ready\n$message"
            coachText.text = "請先完成相機設定。"
        }
    }

    fun markStarted() {
        autoStartedCurrentSetup = true
        cameraReadySince = 0L
        panel.visibility = View.GONE
    }

    fun clearReadySince() {
        cameraReadySince = 0L
    }

    fun shouldAutoStart(isIdle: Boolean): Boolean {
        if (!autoStartEnabled || !cameraReady || autoStartedCurrentSetup || !isIdle || cameraReadySince == 0L) {
            return false
        }
        return nowMs() - cameraReadySince >= autoStartStableMs
    }

    fun cue(framing: CameraFramingResult, orientation: ViewOrientationResult): String {
        return when {
            framing.status != CameraFramingStatus.GOOD -> framing.message
            orientation.status != ViewOrientationStatus.GOOD -> orientation.message
            else -> ""
        }
    }
}
