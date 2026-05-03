package com.yogaflow

import com.yogaflow.coach.BridgeDetectionMapper
import com.yogaflow.coach.ForwardFoldDetectionMapper
import com.yogaflow.coach.SquatDetectionMapper
import com.yogaflow.coach.TwistDetectionMapper
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
    resetDetectionMappers()
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
    resetDetectionMappers()
    latestSuggestion = null
    currentFlow = flow
    currentPose = resolvePose(currentFlow)
    resetToCameraSetup("已重新開始。請先完成相機設定。")
    updateRuntimeTuningControls()
    updateUi(animated = false)
}

internal fun MainActivity.resetToCameraSetup(message: String) {
    sessionState = SessionState.IDLE
    cameraReady = false
    cameraReadySince = 0L
    autoStartedCurrentSetup = false
    startButton.isEnabled = false
    startButton.alpha = 0.45f
    cameraSetupPanel.visibility = android.view.View.VISIBLE
    cameraSetupStatus.text = "Checking body framing..."
    lastCountdownText = ""
    lastCoachCue = ""
    lastCoachAt = 0L
    coachRequestId++
    coachText.text = message
}

internal fun MainActivity.resetDetectionMappers() {
    ForwardFoldDetectionMapper.reset()
    TwistDetectionMapper.reset()
    SquatDetectionMapper.reset()
    BridgeDetectionMapper.reset()
}
