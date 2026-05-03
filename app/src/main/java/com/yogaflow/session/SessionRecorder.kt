package com.yogaflow.session

import android.content.Context
import com.yogaflow.coach.CoachState
import com.yogaflow.pose.PoseDetectionResult
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionRecorder(private val context: Context) {
    private val events = mutableListOf<String>()
    private var recording = false
    private var startedAt = 0L
    private var lastFrameAt = 0L
    private var lastSavedFile: File? = null

    val isRecording: Boolean
        get() = recording

    val eventCount: Int
        get() = events.size

    fun start() {
        events.clear()
        recording = true
        startedAt = System.currentTimeMillis()
        lastFrameAt = 0L
        lastSavedFile = null
        recordEvent("recording_start")
    }

    fun stopAndSave(): File? {
        if (!recording) return lastSavedFile
        recordEvent("recording_stop")
        recording = false

        val dir = File(context.getExternalFilesDir(null), "session-recordings")
        dir.mkdirs()
        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(startedAt))
        val file = File(dir, "yogaflow-session-$timestamp.jsonl")
        file.writeText(events.joinToString(separator = "\n", postfix = "\n"))
        lastSavedFile = file
        return file
    }

    fun recordFrame(
        frame: PoseDetectionResult,
        flowId: String,
        stepNumber: Int,
        detect: String,
        state: CoachState,
        matched: Boolean,
        runtimeSummary: String,
        overrideSummary: String,
        failReason: String,
        suggestionSummary: String
    ) {
        if (!recording) return
        val now = System.currentTimeMillis()
        if (now - lastFrameAt < FRAME_SAMPLE_INTERVAL_MS) return
        lastFrameAt = now

        recordEvent(
            "frame",
            mapOf(
                "flowId" to flowId,
                "step" to stepNumber,
                "detect" to detect,
                "state" to state.name,
                "matched" to matched,
                "landmarks" to frame.imageLandmarks.size,
                "imageWidth" to frame.imageWidth,
                "imageHeight" to frame.imageHeight,
                "mirrored" to frame.isMirrored,
                "runtime" to runtimeSummary,
                "overrides" to overrideSummary,
                "failReason" to failReason,
                "suggestion" to suggestionSummary
            )
        )
    }

    fun recordCue(
        flowId: String?,
        stepNumber: Int?,
        state: CoachState?,
        cue: String,
        source: String
    ) {
        if (!recording || cue.isBlank()) return
        recordEvent(
            "cue",
            mapOf(
                "flowId" to flowId.orEmpty(),
                "step" to (stepNumber ?: 0),
                "state" to state?.name.orEmpty(),
                "source" to source,
                "text" to cue
            )
        )
    }

    private fun recordEvent(type: String, values: Map<String, Any?> = emptyMap()) {
        val now = System.currentTimeMillis()
        val json = JSONObject()
            .put("type", type)
            .put("timeMs", now)
            .put("elapsedMs", if (startedAt == 0L) 0L else now - startedAt)
        values.forEach { (key, value) -> json.put(key, value) }
        events.add(json.toString())
        if (events.size > MAX_EVENTS) events.removeAt(0)
    }

    private companion object {
        const val FRAME_SAMPLE_INTERVAL_MS = 250L
        const val MAX_EVENTS = 20000
    }
}
