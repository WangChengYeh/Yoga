package com.yogaflow.ui

import android.view.View

/**
 * Pure UI renderer.
 *
 * No pose logic, no flow logic, no tuning logic.
 * Just maps a state object into Android Views.
 */
class MainScreenRenderer(
    private val views: MainActivityViews
) {

    fun render(state: UiState) {
        views.coachText.text = state.coachText
        views.flowName.text = state.flowName
        views.progressText.text = state.progressText
        views.countdownText.text = state.countdownText
        views.llmStatus.text = state.llmStatus

        views.cameraSetupPanel.visibility =
            if (state.showCameraSetup) View.VISIBLE else View.GONE

        views.cameraSetupStatus.text = state.cameraSetupText

        views.startButton.isEnabled = state.startEnabled
        views.startButton.alpha = if (state.startEnabled) 1f else 0.45f

        if (state.debugText.isBlank()) {
            views.debugText.visibility = View.GONE
        } else {
            views.debugText.visibility = View.VISIBLE
            views.debugText.text = state.debugText
        }
    }
}

/**
 * Minimal UI contract for phase-1 refactor.
 *
 * This will later be produced by YogaSessionController.
 */
data class UiState(
    val coachText: String = "",
    val flowName: String = "",
    val progressText: String = "",
    val countdownText: String = "",
    val llmStatus: String = "",
    val showCameraSetup: Boolean = false,
    val cameraSetupText: String = "",
    val startEnabled: Boolean = false,
    val debugText: String = ""
)
