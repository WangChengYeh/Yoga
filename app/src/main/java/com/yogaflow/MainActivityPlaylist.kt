package com.yogaflow

import com.yogaflow.flow.FlowLoader
import com.yogaflow.flow.YogaFlow

internal fun MainActivity.loadDiscoveredPlaylist(openClassView: Boolean = true) {
    val flows = runCatching { FlowLoader.loadAllFromAssets(this) }
        .onFailure { coachText.text = "課程載入失敗，請確認 assets/flows。" }
        .getOrDefault(emptyList())
    applyPlaylist(flows, openClassView)
}

internal fun MainActivity.loadPlaylist(paths: List<String>, openClassView: Boolean = true) {
    val flows = paths.mapNotNull { path ->
        runCatching { FlowLoader.loadFromAssets(this, path) }
            .onFailure { coachText.text = "課程載入失敗：$path" }
            .getOrNull()
    }
    applyPlaylist(flows, openClassView)
}

internal fun MainActivity.applyPlaylist(flows: List<YogaFlow>, openClassView: Boolean) {
    if (flows.isEmpty()) {
        coachText.text = "No yoga flows found in assets/flows."
        return
    }

    playlist.setPlaylist(flows)
    val flow = playlist.current()
    if (flow == null) {
        coachText.text = "No yoga flows found in assets/flows."
        return
    }

    currentFlow = flow
    currentPose = resolvePose(currentFlow)
    flowEngine.reset()
    detectionMapperSession.resetAll()
    latestSuggestion = null
    resetToCameraSetup("請先完成相機設定。")
    updateRuntimeTuningControls()

    if (openClassView) showClass()
    updateUi(animated = false)
}

internal fun MainActivity.restartCurrentPlaylist() {
    playlist.reset()
    val flow = playlist.current()
    if (flow == null) {
        sessionState = SessionState.IDLE
        coachText.text = "找不到課程流程，請確認 assets/flows。"
        updateUi(animated = false)
        return
    }

    flowEngine.reset()
    detectionMapperSession.resetAll()
    latestSuggestion = null
    currentFlow = flow
    currentPose = resolvePose(currentFlow)
    resetToCameraSetup("已重新開始。請先完成相機設定。")
    updateRuntimeTuningControls()
    updateUi(animated = false)
}

internal fun MainActivity.resetToCameraSetup(message: String) {
    resetCameraSetupController()
    val bypassReady = cameraSetupEnabled && cameraSetupDisabledForDevelopment
    sessionState = SessionState.IDLE
    cameraReady = bypassReady
    cameraReadySince = if (bypassReady) System.currentTimeMillis() else 0L
    autoStartedCurrentSetup = bypassReady
    startButton.isEnabled = bypassReady
    startButton.alpha = if (bypassReady) 1f else 0.45f
    startButton.visibility = android.view.View.VISIBLE
    beginSessionButton.isEnabled = bypassReady
    beginSessionButton.alpha = if (bypassReady) 1f else 0.45f
    cameraSetupPanel.visibility = if (cameraSetupEnabled && !bypassReady) {
        android.view.View.VISIBLE
    } else {
        android.view.View.GONE
    }
    updateVirtualCoachFromCurrentStep()
    cameraSetupStatus.text = if (bypassReady) {
        "Development: camera setup bypassed"
    } else {
        "Checking body framing..."
    }
    lastCountdownText = ""
    coachText.text = if (!cameraSetupEnabled) {
        "Camera setup is off. Tap Camera to enable."
    } else if (bypassReady) {
        "Development: camera setup bypassed."
    } else {
        message
    }
}
