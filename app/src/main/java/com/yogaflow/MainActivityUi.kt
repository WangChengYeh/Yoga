package com.yogaflow

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator

internal enum class SessionState { IDLE, RUNNING, PAUSED, COMPLETED }

internal fun MainActivity.updateUi(animated: Boolean) {
    if (!isCurrentFlowInitialized()) return
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

internal fun MainActivity.animateProgress(progress: Int) {
    ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, progress).apply {
        duration = 350L
        interpolator = DecelerateInterpolator()
        start()
    }
}

internal fun MainActivity.updateCountdown(text: String) {
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

internal fun MainActivity.animateFlowTransition() {
    flowName.alpha = 0f
    flowName.translationY = -20f
    flowName.animate().alpha(1f).translationY(0f).setDuration(400L).start()
}

internal fun MainActivity.showHome() {
    homeView.visibility = View.VISIBLE
    classView.visibility = View.GONE
}

internal fun MainActivity.showClass() {
    homeView.visibility = View.GONE
    classView.visibility = View.VISIBLE
}

internal fun MainActivity.startCamera() {
    cameraPipeline.start()
}
