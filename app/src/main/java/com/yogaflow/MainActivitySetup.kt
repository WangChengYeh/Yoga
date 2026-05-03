package com.yogaflow

import android.content.Context
import android.widget.SeekBar
import com.yogaflow.coach.ThresholdConfig

internal fun MainActivity.bindViewsMain() {
    homeView = findViewById(R.id.homeView)
    classView = findViewById(R.id.classView)
    previewView = findViewById(R.id.previewView)
    overlayView = findViewById(R.id.overlayView)
    cameraSetupPanel = findViewById(R.id.cameraSetupPanel)
    cameraSetupStatus = findViewById(R.id.cameraSetupStatus)
    debugText = findViewById(R.id.debugText)
    coachText = findViewById(R.id.coachText)
    flowName = findViewById(R.id.flowName)
    progressText = findViewById(R.id.progressText)
    countdownText = findViewById(R.id.countdownText)
    llmStatus = findViewById(R.id.llmStatus)
    progressBar = findViewById(R.id.progressBar)
    squatThresholdLabel = findViewById(R.id.squatThresholdLabel)
    bridgeThresholdLabel = findViewById(R.id.bridgeThresholdLabel)
    squatThresholdSeekBar = findViewById(R.id.squatThresholdSeekBar)
    bridgeThresholdSeekBar = findViewById(R.id.bridgeThresholdSeekBar)
    startClassButton = findViewById(R.id.startClassButton)
    startStretchButton = findViewById(R.id.startStretchButton)
    startRecoveryButton = findViewById(R.id.startRecoveryButton)
    startButton = findViewById(R.id.startButton)
    pauseButton = findViewById(R.id.pauseButton)
    restartButton = findViewById(R.id.restartButton)
}

internal fun MainActivity.setupThresholdControlsMain() {
    squatThresholdSeekBar.max = MainActivity.TUNING_SLIDER_MAX
    bridgeThresholdSeekBar.max = MainActivity.TUNING_SLIDER_MAX
    squatThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListenerMain(0))
    bridgeThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListenerMain(1))
    updateRuntimeTuningControls()
}

internal fun MainActivity.runtimeTuningListenerMain(index: Int): SeekBar.OnSeekBarChangeListener {
    return object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (!fromUser || suppressTuningCallbacks) return
            val binding = computeCurrentTuningBindings().getOrNull(index) ?: return
            val value = sliderProgressToValue(progress, binding.param, MainActivity.TUNING_SLIDER_MAX)
            runtimeOverrideStore.set(binding.key, value)
            updateRuntimeTuningControls()
        }

        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }
}

internal fun MainActivity.loadThresholdPreferencesMain() {
    val prefs = getSharedPreferences("threshold_prefs", Context.MODE_PRIVATE)
    val squat = prefs.getFloat("squat_hold_knee_max", ThresholdConfig.squatHoldKneeMaxDegrees.toFloat()).toDouble()
    val bridge = prefs.getFloat("bridge_lift_hip_max", ThresholdConfig.bridgeLiftHipMaxDegrees.toFloat()).toDouble()
    ThresholdConfig.squatHoldKneeMaxDegrees = clampThresholdMain(squat, 80.0, 70)
    ThresholdConfig.bridgeLiftHipMaxDegrees = clampThresholdMain(bridge, 120.0, 70)
}

internal fun MainActivity.saveThresholdPreferencesMain() {
    getSharedPreferences("threshold_prefs", Context.MODE_PRIVATE)
        .edit()
        .putFloat("squat_hold_knee_max", ThresholdConfig.squatHoldKneeMaxDegrees.toFloat())
        .putFloat("bridge_lift_hip_max", ThresholdConfig.bridgeLiftHipMaxDegrees.toFloat())
        .apply()
}

internal fun MainActivity.thresholdProgressMain(value: Double, min: Double, maxProgress: Int): Int {
    return (value - min).toInt().coerceIn(0, maxProgress)
}

internal fun MainActivity.clampThresholdMain(value: Double, min: Double, range: Int): Double {
    return value.coerceIn(min, min + range)
}
