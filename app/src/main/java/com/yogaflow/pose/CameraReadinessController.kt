package com.yogaflow.pose

/**
 * Owns camera setup readiness state.
 *
 * This keeps framing / orientation checks, stable-ready timing, and auto-start
 * gating out of MainActivity and out of the flow engine.
 */
class CameraReadinessController(
    private val autoStartEnabled: Boolean = true,
    private val autoStartStableMs: Long = 1500L,
    private val nowProvider: () -> Long = { System.currentTimeMillis() }
) {
    private var ready: Boolean = false
    private var readySince: Long = 0L
    private var autoStartedCurrentSetup: Boolean = false

    fun analyze(frame: PoseDetectionResult, sessionIdle: Boolean): CameraReadinessResult {
        val framing = CameraFramingCoach.analyze(frame)
        val orientation = ViewOrientation.analyze(frame)
        val isReady = framing.status == CameraFramingStatus.GOOD &&
            orientation.status == ViewOrientationStatus.GOOD

        updateReadyState(isReady)

        return CameraReadinessResult(
            ready = isReady,
            stableForMs = stableForMs(),
            setupMessage = buildSetupMessage(isReady, framing.message, orientation.message),
            coachCue = buildCoachCue(isReady, framing, orientation),
            shouldAutoStart = shouldAutoStart(sessionIdle)
        )
    }

    fun resetAutoStart() {
        autoStartedCurrentSetup = false
    }

    fun resetReadyTimer() {
        ready = false
        readySince = 0L
        autoStartedCurrentSetup = false
    }

    fun markAutoStarted() {
        autoStartedCurrentSetup = true
    }

    private fun updateReadyState(isReady: Boolean) {
        val now = nowProvider()
        if (isReady) {
            if (!ready) readySince = now
        } else {
            readySince = 0L
            autoStartedCurrentSetup = false
        }
        ready = isReady
    }

    private fun stableForMs(): Long {
        return if (ready && readySince > 0L) nowProvider() - readySince else 0L
    }

    private fun shouldAutoStart(sessionIdle: Boolean): Boolean {
        if (!autoStartEnabled) return false
        if (!sessionIdle) return false
        if (!ready) return false
        if (readySince == 0L) return false
        if (autoStartedCurrentSetup) return false
        return stableForMs() >= autoStartStableMs
    }

    private fun buildSetupMessage(
        isReady: Boolean,
        framingMessage: String,
        orientationMessage: String
    ): String {
        return if (isReady) {
            val remaining = ((autoStartStableMs - stableForMs()).coerceAtLeast(0L) / 1000.0)
            if (stableForMs() >= autoStartStableMs) {
                "Ready ✔\nStarting class automatically..."
            } else {
                "Ready ✔\nHold still. Auto-start in %.1fs.".format(remaining)
            }
        } else {
            val message = when {
                framingMessage.isNotBlank() -> framingMessage
                orientationMessage.isNotBlank() -> orientationMessage
                else -> "Adjust your position until your full body is visible."
            }
            "Not Ready\n$message"
        }
    }

    private fun buildCoachCue(
        isReady: Boolean,
        framing: CameraFramingResult,
        orientation: ViewOrientationResult
    ): String {
        return when {
            isReady -> "準備好了，請穩住，系統會自動開始。"
            framing.status != CameraFramingStatus.GOOD -> framing.message
            orientation.status != ViewOrientationStatus.GOOD -> orientation.message
            else -> "請先完成相機設定。"
        }
    }
}

data class CameraReadinessResult(
    val ready: Boolean,
    val stableForMs: Long,
    val setupMessage: String,
    val coachCue: String,
    val shouldAutoStart: Boolean
)
