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
    val metric: String,
    val boundName: String,
    val currentValue: Double,
    val suggestedValue: Double,
    val sampleCount: Int,
    val reason: String
) {
    val label: String
        get() = "$metric.$boundName ${currentValue.fmt()} → ${suggestedValue.fmt()}"
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
        val avgActual = scopedSamples.map { it.actual }.average()
        val suggested = avgActual.roundToNearestHalfDegree()

        // Avoid noisy suggestions that do not actually change the configured value.
        if (kotlin.math.abs(suggested - latest.expected) < MIN_SUGGESTION_DELTA) return null

        return AutoTuningSuggestion(
            flowId = flowId,
            stepIndex = stepIndex,
            detect = detect,
            metric = latest.metric,
            boundName = latest.boundName,
            currentValue = latest.expected,
            suggestedValue = suggested,
            sampleCount = scopedSamples.size,
            reason = "${latest.metric}.${latest.boundName}: recent average actual=${avgActual.fmt()} from ${scopedSamples.size} failed samples"
        )
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

            // Stability window is useful for debug but should not tune angle thresholds.
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
