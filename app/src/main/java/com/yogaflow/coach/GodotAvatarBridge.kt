package com.yogaflow.coach

import android.util.Log
import com.yogaflow.avatar.AvatarRigFrame
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class GodotAvatarBridge(
    private val minIntervalMs: Long = 200L,
    private val reconnectDelayMs: Long = 500L
) {
    private var lastSentMs = 0L
    private var webSocket: WebSocket? = null
    private var connected = false
    private var closed = false
    private var reconnectScheduled = false
    private val reconnectExecutor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    @Synchronized
    internal fun connect() {
        if (closed || connected) return
        val request = Request.Builder().url("ws://127.0.0.1:9090").build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                synchronized(this@GodotAvatarBridge) {
                    connected = true
                    reconnectScheduled = false
                }
                Log.d(TAG, "Connected to Godot WebSocket")
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                synchronized(this@GodotAvatarBridge) {
                    connected = false
                    if (this@GodotAvatarBridge.webSocket === webSocket) {
                        this@GodotAvatarBridge.webSocket = null
                    }
                }
                Log.w(TAG, "Godot WebSocket unavailable: ${t.message}")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                synchronized(this@GodotAvatarBridge) {
                    connected = false
                    if (this@GodotAvatarBridge.webSocket === webSocket) {
                        this@GodotAvatarBridge.webSocket = null
                    }
                }
                Log.d(TAG, "Godot WebSocket Closed")
                scheduleReconnect()
            }
        })
    }

    fun send(frame: PoseCoachFrame, force: Boolean = false) {
        val now = frame.timestampMs
        if (!force && now - lastSentMs < minIntervalMs) return
        lastSentMs = now
        val jsonString = frame.toJson().toString()
        sendJson(jsonString)
        // Log.d(TAG, "Sent PoseCoachFrame: $jsonString")
    }

    fun sendRig(frame: AvatarRigFrame, force: Boolean = false) {
        val now = frame.timestampMs
        if (!force && now - lastSentMs < minIntervalMs) return
        lastSentMs = now
        val jsonString = frame.toJson().toString()
        sendJson(jsonString)
    }

    fun sendSkin(skin: String) {
        sendJson("""{"type":"set_skin","skin":"$skin"}""")
    }

    @Synchronized
    private fun sendJson(jsonString: String) {
        val socket = webSocket
        if (!connected || socket == null) {
            scheduleReconnect()
            return
        }
        if (!socket.send(jsonString)) {
            connected = false
            webSocket = null
            scheduleReconnect()
        }
    }

    @Synchronized
    private fun scheduleReconnect() {
        if (closed || reconnectScheduled) return
        reconnectScheduled = true
        reconnectExecutor.schedule({
            synchronized(this@GodotAvatarBridge) {
                reconnectScheduled = false
            }
            connect()
        }, reconnectDelayMs, TimeUnit.MILLISECONDS)
    }

    @Synchronized
    fun close() {
        closed = true
        reconnectScheduled = false
        connected = false
        webSocket?.close(1000, "App closing")
        webSocket = null
        reconnectExecutor.shutdownNow()
        client.dispatcher.executorService.shutdown()
    }

    private companion object {
        const val TAG = "GodotAvatarBridge"
    }
}
