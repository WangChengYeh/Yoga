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
                "failExplanation" to explainFailReason(failReason),
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

    private fun explainFailReason(reason: String): String {
        if (reason.isBlank()) return ""
        val match = FAIL_REASON_PATTERN.matchEntire(reason) ?: return reason
        val metric = match.groupValues[1]
        val observed = match.groupValues[2]
        val comparison = match.groupValues[3]
        val boundName = match.groupValues[4]
        val required = match.groupValues[5]
        val metricText = when (metric) {
            "knee" -> "knee angle (膝蓋角度)"
            "hip" -> "hip/body angle (髖部/身體角度)"
            "twist" -> "torso twist angle (軀幹扭轉角度)"
            "stableFor" -> "time held steady (穩定維持時間)"
            else -> metric
        }
        val unit = if (metric == "stableFor" || boundName == "required") "ms" else "degrees"
        val requirementText = when (comparison) {
            "<" -> "at least"
            ">" -> "at most"
            else -> boundName
        }
        return "Observed $metricText was $observed $unit; this step requires $requirementText $required $unit."
    }

    private companion object {
        const val FRAME_SAMPLE_INTERVAL_MS = 250L
        const val MAX_EVENTS = 20000
        val FAIL_REASON_PATTERN = Regex("""([A-Za-z]+)=(-?\d+(?:\.\d+)?)\s*([<>])\s*([A-Za-z]+)=(-?\d+(?:\.\d+)?)""")
    }
}
