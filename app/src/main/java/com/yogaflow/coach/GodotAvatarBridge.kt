package com.yogaflow.coach

import android.util.Log
import com.yogaflow.avatar.AvatarRigFrame
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class GodotAvatarBridge(
    private val minIntervalMs: Long = 200L
) {
    private var lastSentMs = 0L
    private var webSocket: WebSocket? = null
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    init {
        connect()
    }

    private fun connect() {
        val request = Request.Builder().url("ws://127.0.0.1:9090").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Log.d(TAG, "Connected to Godot WebSocket")
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Log.e(TAG, "Godot WebSocket Error: ${t.message}")
            }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "Godot WebSocket Closed")
            }
        })
    }

    fun send(frame: PoseCoachFrame, force: Boolean = false) {
        val now = frame.timestampMs
        if (!force && now - lastSentMs < minIntervalMs) return
        lastSentMs = now
        val jsonString = frame.toJson().toString()
        webSocket?.send(jsonString)
        // Log.d(TAG, "Sent PoseCoachFrame: $jsonString")
    }

    fun sendRig(frame: AvatarRigFrame, force: Boolean = false) {
        val now = frame.timestampMs
        if (!force && now - lastSentMs < minIntervalMs) return
        lastSentMs = now
        val jsonString = frame.toJson().toString()
        webSocket?.send(jsonString)
    }
    
    fun close() {
        webSocket?.close(1000, "App closing")
        client.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val TAG = "GodotAvatarBridge"
    }
}
