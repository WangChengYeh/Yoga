package com.yogaflow.ui

import android.animation.ObjectAnimator
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.ProgressBar
import android.widget.TextView

/**
 * Owns UI rendering for class screen: flow name, progress, countdown animation.
 */
class ClassUiController(
    private val flowName: TextView,
    private val progressText: TextView,
    private val countdownText: TextView,
    private val progressBar: ProgressBar
) {

    fun render(
        flowLabel: String,
        step: Int,
        total: Int,
        countdown: String,
        animated: Boolean
    ) {
        flowName.text = flowLabel
        progressText.text = "Step $step/$total"

        val progress = ((step.toFloat() / total.toFloat()) * 100).toInt().coerceIn(0, 100)
        if (animated) animateProgress(progress) else progressBar.progress = progress

        updateCountdown(countdown)
    }

    private fun animateProgress(progress: Int) {
        ObjectAnimator.ofInt(progressBar, "progress", progressBar.progress, progress).apply {
            duration = 350L
            interpolator = DecelerateInterpolator()
            start()
        }
    }

    private var lastCountdown = ""

    private fun updateCountdown(text: String) {
        if (text == lastCountdown) return
        countdownText.text = text
        lastCountdown = text

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
}
