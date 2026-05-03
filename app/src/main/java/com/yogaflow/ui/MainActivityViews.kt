package com.yogaflow.ui

import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.view.PreviewView
import com.yogaflow.R
import com.yogaflow.pose.PoseOverlayView

class MainActivityViews(activity: AppCompatActivity) {
    val homeView: View = activity.findViewById(R.id.homeView)
    val classView: View = activity.findViewById(R.id.classView)
    val previewView: PreviewView = activity.findViewById(R.id.previewView)
    val overlayView: PoseOverlayView = activity.findViewById(R.id.overlayView)

    val cameraSetupPanel: View = activity.findViewById(R.id.cameraSetupPanel)
    val cameraSetupStatus: TextView = activity.findViewById(R.id.cameraSetupStatus)

    val debugText: TextView = activity.findViewById(R.id.debugText)
    val coachText: TextView = activity.findViewById(R.id.coachText)
    val flowName: TextView = activity.findViewById(R.id.flowName)
    val progressText: TextView = activity.findViewById(R.id.progressText)
    val countdownText: TextView = activity.findViewById(R.id.countdownText)
    val llmStatus: TextView = activity.findViewById(R.id.llmStatus)
    val progressBar: ProgressBar = activity.findViewById(R.id.progressBar)

    val squatThresholdLabel: TextView = activity.findViewById(R.id.squatThresholdLabel)
    val bridgeThresholdLabel: TextView = activity.findViewById(R.id.bridgeThresholdLabel)
    val squatThresholdSeekBar: SeekBar = activity.findViewById(R.id.squatThresholdSeekBar)
    val bridgeThresholdSeekBar: SeekBar = activity.findViewById(R.id.bridgeThresholdSeekBar)

    val startClassButton: Button = activity.findViewById(R.id.startClassButton)
    val startStretchButton: Button = activity.findViewById(R.id.startStretchButton)
    val startRecoveryButton: Button = activity.findViewById(R.id.startRecoveryButton)
    val startButton: Button = activity.findViewById(R.id.startButton)
    val pauseButton: Button = activity.findViewById(R.id.pauseButton)
    val restartButton: Button = activity.findViewById(R.id.restartButton)
}
