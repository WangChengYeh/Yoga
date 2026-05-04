package com.yogaflow.coach

import android.util.Log

class GodotAvatarBridge(
    private val minIntervalMs: Long = 200L,
    private val sender: (String) -> Unit = { Log.d(TAG, it) }
) {
    private var lastSentMs = 0L

    fun send(frame: PoseCoachFrame, force: Boolean = false) {
        val now = frame.timestampMs
        if (!force && now - lastSentMs < minIntervalMs) return
        lastSentMs = now
        sender(frame.toJson().toString())
    }

    private companion object {
        const val TAG = "GodotAvatarBridge"
    }
}
