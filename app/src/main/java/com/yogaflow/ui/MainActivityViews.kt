package com.yogaflow.ui

import android.app.Activity
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.camera.view.PreviewView
import com.yogaflow.R
import com.yogaflow.pose.PoseOverlayView

/**
 * Centralized view binding for MainActivity.
 *
 * This keeps Android view lookup separate from yoga session, camera, coach,
 * tuning, and debug logic. MainActivity can gradually depend on this holder
 * instead of owning many individual lateinit view fields.
 */
data class MainActivityViews(
    val homeView: View,
    val classView: View,
    val previewView: PreviewView,
    val overlayView: PoseOverlayView,
    val cameraSetupPanel: View,
    val cameraSetupStatus: TextView,
    val debugText: TextView,
    val coachText: TextView,
    val flowName: TextView,
    val progressText: TextView,
    val countdownText: TextView,
    val llmStatus: TextView,
    val progressBar: ProgressBar,
    val squatThresholdLabel: TextView,
    val bridgeThresholdLabel: TextView,
    val squatThresholdSeekBar: SeekBar,
    val bridgeThresholdSeekBar: SeekBar,
    val startClassButton: Button,
    val startStretchButton: Button,
    val startRecoveryButton: Button,
    val startButton: Button,
    val pauseButton: Button,
    val restartButton: Button,
    val applySuggestionButton: Button
) {
    companion object {
        fun bind(activity: Activity): MainActivityViews = MainActivityViews(
            homeView = activity.findViewById(R.id.homeView),
            classView = activity.findViewById(R.id.classView),
            previewView = activity.findViewById(R.id.previewView),
            overlayView = activity.findViewById(R.id.overlayView),
            cameraSetupPanel = activity.findViewById(R.id.cameraSetupPanel),
            cameraSetupStatus = activity.findViewById(R.id.cameraSetupStatus),
            debugText = activity.findViewById(R.id.debugText),
            coachText = activity.findViewById(R.id.coachText),
            flowName = activity.findViewById(R.id.flowName),
            progressText = activity.findViewById(R.id.progressText),
            countdownText = activity.findViewById(R.id.countdownText),
            llmStatus = activity.findViewById(R.id.llmStatus),
            progressBar = activity.findViewById(R.id.progressBar),
            squatThresholdLabel = activity.findViewById(R.id.squatThresholdLabel),
            bridgeThresholdLabel = activity.findViewById(R.id.bridgeThresholdLabel),
            squatThresholdSeekBar = activity.findViewById(R.id.squatThresholdSeekBar),
            bridgeThresholdSeekBar = activity.findViewById(R.id.bridgeThresholdSeekBar),
            startClassButton = activity.findViewById(R.id.startClassButton),
            startStretchButton = activity.findViewById(R.id.startStretchButton),
            startRecoveryButton = activity.findViewById(R.id.startRecoveryButton),
            startButton = activity.findViewById(R.id.startButton),
            pauseButton = activity.findViewById(R.id.pauseButton),
            restartButton = activity.findViewById(R.id.restartButton),
            applySuggestionButton = activity.findViewById(R.id.applySuggestionButton)
        )
    }
}
