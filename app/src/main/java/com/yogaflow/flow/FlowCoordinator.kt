package com.yogaflow.flow

import android.content.Context
import com.yogaflow.coach.BridgeDetectionMapper
import com.yogaflow.coach.ForwardFoldDetectionMapper
import com.yogaflow.coach.PoseFlowEngine
import com.yogaflow.coach.SquatDetectionMapper
import com.yogaflow.coach.TwistDetectionMapper
import com.yogaflow.yoga.YogaPose
import com.yogaflow.yoga.YogaPoseCatalog

/**
 * Owns playlist loading, current flow/pose selection, playlist restart,
 * and detection mapper reset.
 *
 * UI code should call this coordinator and then render the returned state.
 */
class FlowCoordinator(
    private val context: Context,
    private val playlist: FlowPlaylistEngine,
    private val flowEngine: PoseFlowEngine
) {
    data class FlowState(
        val flow: YogaFlow,
        val pose: YogaPose
    )

    fun loadDiscovered(): FlowState? {
        val flows = FlowLoader.loadAllFromAssets(context)
        return applyPlaylist(flows)
    }

    fun load(paths: List<String>): FlowState? {
        val flows = paths.mapNotNull { path ->
            runCatching { FlowLoader.loadFromAssets(context, path) }.getOrNull()
        }
        return applyPlaylist(flows)
    }

    fun restart(): FlowState? {
        playlist.reset()
        val flow = playlist.current() ?: return null
        return activate(flow)
    }

    fun next(): FlowState? {
        val flow = playlist.moveNext() ?: return null
        return activate(flow)
    }

    fun resetDetectionMappers() {
        ForwardFoldDetectionMapper.reset()
        TwistDetectionMapper.reset()
        SquatDetectionMapper.reset()
        BridgeDetectionMapper.reset()
    }

    fun resolvePose(flow: YogaFlow): YogaPose {
        return YogaPoseCatalog.poses.firstOrNull { it.id == flow.pose } ?: YogaPoseCatalog.poses.first()
    }

    private fun applyPlaylist(flows: List<YogaFlow>): FlowState? {
        if (flows.isEmpty()) return null
        playlist.setPlaylist(flows)
        val flow = playlist.current() ?: return null
        return activate(flow)
    }

    private fun activate(flow: YogaFlow): FlowState {
        val pose = resolvePose(flow)
        flowEngine.reset()
        resetDetectionMappers()
        return FlowState(flow, pose)
    }
}
