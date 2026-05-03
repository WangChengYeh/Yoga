package com.yogaflow.flow

import kotlin.math.roundToLong

/**
 * Auto tuning advisor for JSON DSL v2 runtime params.
 *
 * This component observes numeric fail reasons and produces suggestions only.
 * It never mutates Flow JSON and never applies RuntimeOverrideStore changes by itself.
 */
data class AutoTuningSample(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val metric: String,
    val actual: Double,
    val operator: String,
    val boundName: String,
    val expected: Double,
    val timestampMs: Long = System.currentTimeMillis()
)

data class AutoTuningSuggestion(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val path: String,
    val metric: String,
    val boundName: String,
    val currentValue: Double,
    val suggestedValue: Double,
    val sampleCount: Int,
    val reason: String
) {
    val confidence: String
        get() = when {
            sampleCount >= 15 -> "high"
            sampleCount >= 8 -> "medium"
            else -> "low"
        }

    val label: String
        get() = "$metric.$boundName ${currentValue.fmt()} → ${suggestedValue.fmt()} ($sampleCount samples, $confidence)"
}

class AutoTuningAdvisor(
    private val minSamples: Int = DEFAULT_MIN_SAMPLES,
    private val maxSamplesPerScope: Int = DEFAULT_MAX_SAMPLES_PER_SCOPE,
    private val staleAfterMs: Long = DEFAULT_STALE_AFTER_MS
) {
    private val samples = mutableListOf<AutoTuningSample>()

    fun observe(sample: AutoTuningSample) {
        samples.add(sample)
        prune()
    }

    fun observeReason(flowId: String, stepIndex: Int, detect: DetectKey, reason: String): Boolean {
        val sample = parseReason(flowId, stepIndex, detect, reason) ?: return false
        observe(sample)
        return true
    }

    fun suggestionsFor(flowId: String, stepIndex: Int, detect: DetectKey): List<AutoTuningSuggestion> {
        prune()
        return samples
            .filter { it.flowId == flowId && it.stepIndex == stepIndex && it.detect == detect }
            .groupBy { SampleGroupKey(it.metric, it.boundName, it.operator) }
            .values
            .mapNotNull { scopedSamples -> buildSuggestion(flowId, stepIndex, detect, scopedSamples) }
    }

    fun clear() {
        samples.clear()
    }

    private fun buildSuggestion(
        flowId: String,
        stepIndex: Int,
        detect: DetectKey,
        scopedSamples: List<AutoTuningSample>
    ): AutoTuningSuggestion? {
        if (scopedSamples.size < minSamples) return null

        val latest = scopedSamples.last()
        val path = overridePathFor(detect, latest.metric, latest.boundName) ?: return null
        val avgActual = scopedSamples.map { it.actual }.average()
        val suggested = avgActual.roundToNearestHalfDegree()

        if (kotlin.math.abs(suggested - latest.expected) < MIN_SUGGESTION_DELTA) return null

        return AutoTuningSuggestion(
            flowId = flowId,
            stepIndex = stepIndex,
            detect = detect,
            path = path,
            metric = latest.metric,
            boundName = latest.boundName,
            currentValue = latest.expected,
            suggestedValue = suggested,
            sampleCount = scopedSamples.size,
            reason = "${latest.metric}.${latest.boundName}: recent average actual=${avgActual.fmt()} from ${scopedSamples.size} failed samples"
        )
    }

    private fun overridePathFor(detect: DetectKey, metric: String, boundName: String): String? {
        if (boundName !in setOf("min", "max")) return null
        val phase = when (detect) {
            DetectKey.READY_FORWARD_FOLD -> "ready"
            DetectKey.TALL_SPINE_SETUP,
            DetectKey.SQUAT_SETUP -> "setup"
            DetectKey.HIP_HINGE -> "hinge"
            DetectKey.CONTROLLED_FORWARD_FOLD -> "fold"
            DetectKey.FORWARD_HOLD,
            DetectKey.TWIST_HOLD,
            DetectKey.SQUAT_HOLD,
            DetectKey.BRIDGE_HOLD -> "hold"
            DetectKey.RETURN_STANDING,
            DetectKey.SQUAT_RETURN -> "return"
            DetectKey.NEUTRAL_FINISH -> "neutral"
            DetectKey.STABLE_BASE,
            DetectKey.RETURN_CENTER -> "center"
            DetectKey.TWIST_START -> "start"
            DetectKey.SQUAT_DESCENT -> "descent"
            DetectKey.BRIDGE_LIFT -> "lift"
            DetectKey.BRIDGE_SETUP,
            DetectKey.BRIDGE_RETURN,
            DetectKey.STANDING_CENTERED,
            DetectKey.SPINE_LENGTHENED,
            DetectKey.MOUNTAIN_HOLD,
            DetectKey.READY_FOR_NEXT_POSE -> return null
        }
        val joint = when (metric) {
            "knee", "hip", "twist" -> metric
            else -> return null
        }
        return "runtime.angles.$joint.$phase.$boundName"
    }

    private fun prune() {
        val now = System.currentTimeMillis()
        samples.removeAll { now - it.timestampMs > staleAfterMs }

        val overflowGroups = samples.groupBy { ScopeKey(it.flowId, it.stepIndex, it.detect, it.metric, it.boundName, it.operator) }
        samples.clear()
        overflowGroups.values.forEach { group ->
            samples.addAll(group.takeLast(maxSamplesPerScope))
        }
    }

    private data class ScopeKey(
        val flowId: String,
        val stepIndex: Int,
        val detect: DetectKey,
        val metric: String,
        val boundName: String,
        val operator: String
    )

    private data class SampleGroupKey(
        val metric: String,
        val boundName: String,
        val operator: String
    )

    companion object {
        private const val DEFAULT_MIN_SAMPLES = 5
        private const val DEFAULT_MAX_SAMPLES_PER_SCOPE = 30
        private const val DEFAULT_STALE_AFTER_MS = 30_000L
        private const val MIN_SUGGESTION_DELTA = 0.5

        private val NUMERIC_REASON = Regex(
            pattern = "^([A-Za-z_][A-Za-z0-9_]*)=([-+]?[0-9]*\\.?[0-9]+)\\s*([<>])\\s*(min|max|required)=([-+]?[0-9]*\\.?[0-9]+)(?:ms)?$"
        )

        fun parseReason(flowId: String, stepIndex: Int, detect: DetectKey, reason: String): AutoTuningSample? {
            val match = NUMERIC_REASON.matchEntire(reason.trim()) ?: return null
            val metric = match.groupValues[1]
            val actual = match.groupValues[2].toDoubleOrNull() ?: return null
            val operator = match.groupValues[3]
            val boundName = match.groupValues[4]
            val expected = match.groupValues[5].toDoubleOrNull() ?: return null

            if (metric == "stableFor" || boundName == "required") return null

            return AutoTuningSample(
                flowId = flowId,
                stepIndex = stepIndex,
                detect = detect,
                metric = metric,
                actual = actual,
                operator = operator,
                boundName = boundName,
                expected = expected
            )
        }
    }
}

private fun Double.roundToNearestHalfDegree(): Double {
    return (this * 2.0).roundToLong() / 2.0
}

private fun Double.fmt(): String = "%.1f".format(this)
