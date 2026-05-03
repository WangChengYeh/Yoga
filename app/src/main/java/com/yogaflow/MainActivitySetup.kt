package com.yogaflow

import android.widget.SeekBar
import com.yogaflow.coach.ThresholdConfig

internal fun MainActivity.bindViewsImpl() {
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

internal fun MainActivity.setupThresholdControlsImpl() {
    squatThresholdSeekBar.max = MainActivity.TUNING_SLIDER_MAX
    bridgeThresholdSeekBar.max = MainActivity.TUNING_SLIDER_MAX
    squatThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListener(0))
    bridgeThresholdSeekBar.setOnSeekBarChangeListener(runtimeTuningListener(1))
    updateRuntimeTuningControls()
}

internal fun MainActivity.runtimeTuningListenerImpl(index: Int): SeekBar.OnSeekBarChangeListener {
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

internal fun MainActivity.loadThresholdPreferencesImpl() {
    val prefs = getSharedPreferences("threshold_prefs", MODE_PRIVATE)
    val squat = prefs.getFloat("squat_hold_knee_max", ThresholdConfig.squatHoldKneeMaxDegrees.toFloat()).toDouble()
    val bridge = prefs.getFloat("bridge_lift_hip_max", ThresholdConfig.bridgeLiftHipMaxDegrees.toFloat()).toDouble()
    ThresholdConfig.squatHoldKneeMaxDegrees = clampThreshold(squat, 80.0, 70)
    ThresholdConfig.bridgeLiftHipMaxDegrees = clampThreshold(bridge, 120.0, 70)
}

internal fun MainActivity.saveThresholdPreferencesImpl() {
    getSharedPreferences("threshold_prefs", MODE_PRIVATE)
        .edit()
        .putFloat("squat_hold_knee_max", ThresholdConfig.squatHoldKneeMaxDegrees.toFloat())
        .putFloat("bridge_lift_hip_max", ThresholdConfig.bridgeLiftHipMaxDegrees.toFloat())
        .apply()
}

internal fun MainActivity.thresholdProgressImpl(value: Double, min: Double, maxProgress: Int): Int {
    return (value - min).toInt().coerceIn(0, maxProgress)
}

internal fun MainActivity.clampThresholdImpl(value: Double, min: Double, range: Int): Double {
    return value.coerceIn(min, min + range)
}
