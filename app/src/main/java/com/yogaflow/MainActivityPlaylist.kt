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
    llmStatus.text = "LLM: OFF"
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
    sessionState = SessionState.IDLE
    cameraReady = cameraSetupDisabledForDevelopment
    cameraReadySince = if (cameraSetupDisabledForDevelopment) System.currentTimeMillis() else 0L
    autoStartedCurrentSetup = cameraSetupDisabledForDevelopment
    startButton.isEnabled = cameraSetupDisabledForDevelopment
    startButton.alpha = if (cameraSetupDisabledForDevelopment) 1f else 0.45f
    cameraSetupPanel.visibility = if (cameraSetupDisabledForDevelopment) {
        android.view.View.GONE
    } else {
        android.view.View.VISIBLE
    }
    updateVirtualCoachFromCurrentStep()
    cameraSetupStatus.text = if (cameraSetupDisabledForDevelopment) {
        "Development: camera setup bypassed"
    } else {
        "Checking body framing..."
    }
    lastCountdownText = ""
    coachText.text = if (cameraSetupDisabledForDevelopment) {
        "Development: camera setup bypassed."
    } else {
        message
    }
}
