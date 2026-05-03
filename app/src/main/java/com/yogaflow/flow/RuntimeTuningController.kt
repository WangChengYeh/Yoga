package com.yogaflow.flow

/**
 * Owns runtime override + auto tuning coordination.
 *
 * MainActivity should not directly merge RuntimeOverrideStore, observe numeric
 * fail reasons, or format tuning summaries after this controller is wired.
 */
class RuntimeTuningController(
    private val overrideStore: RuntimeOverrideStore = RuntimeOverrideStore(),
    private val advisor: AutoTuningAdvisor = AutoTuningAdvisor(),
    private val bindingProvider: () -> List<RuntimeTuningBinding> = { emptyList() },
    private val valueFormatter: (Double, TunableRuntimeParam) -> String = { value, _ -> "%.1f".format(value) }
) {
    private var latestSuggestion: AutoTuningSuggestion? = null

    fun effectiveParams(
        flowId: String,
        stepIndex: Int,
        detect: DetectKey,
        baseParams: RuntimeParams
    ): RuntimeParams {
        val overrides = overrideStore.overridesFor(flowId, stepIndex, detect)
        return RuntimeOverrideMerger.apply(baseParams, overrides)
    }

    fun observeFailReason(
        flowId: String,
        stepIndex: Int,
        detect: DetectKey,
        reason: String
    ) {
        if (reason.isBlank()) return
        advisor.observeReason(flowId, stepIndex, detect, reason)
    }

    fun buildDebugSummary(
        flowId: String,
        stepIndex: Int,
        detect: DetectKey,
        effectiveParams: RuntimeParams
    ): RuntimeTuningDebugSummary {
        val suggestions = advisor.suggestionsFor(flowId, stepIndex, detect)
        latestSuggestion = suggestions.firstOrNull()

        return RuntimeTuningDebugSummary(
            runtimeSummary = buildRuntimeSummary(effectiveParams),
            overrideSummary = buildOverrideSummary(),
            suggestionSummary = suggestions.take(2).joinToString(" | ") { it.label }
        )
    }

    fun applyLatestSuggestion(): RuntimeTuningApplyResult {
        val suggestion = latestSuggestion
            ?: return RuntimeTuningApplyResult(false, "目前沒有可套用的調參建議。")

        val binding = bindingProvider().firstOrNull {
            it.param.label.startsWith("${suggestion.metric}.") &&
                it.param.label.endsWith(".${suggestion.boundName}")
        } ?: return RuntimeTuningApplyResult(false, "目前找不到對應的調參控制。")

        overrideStore.set(binding.key, suggestion.suggestedValue)
        return RuntimeTuningApplyResult(true, "已套用建議：${suggestion.label}")
    }

    fun overrideStore(): RuntimeOverrideStore = overrideStore

    private fun buildRuntimeSummary(params: RuntimeParams): String {
        val controls = "stab=${params.stabilityMs ?: "--"} " +
            "ema=${params.emaAlpha.fmt2()} " +
            "dead=${params.deadbandDegrees.fmt1()}"

        val activeAngles = TunableRuntimeParamExtractor.extract(params)
            .filter { it.path.startsWith("runtime.angles") }
            .take(3)
            .joinToString(" ") { "${it.label}=${valueFormatter(it.value, it)}" }

        return listOf(controls, activeAngles)
            .filter { it.isNotBlank() }
            .joinToString(" | ")
    }

    private fun buildOverrideSummary(): String {
        return bindingProvider()
            .mapNotNull { binding ->
                overrideStore.valueFor(binding.key)?.let { value ->
                    "${binding.param.label}=${valueFormatter(value, binding.param)}"
                }
            }
            .take(4)
            .joinToString(" ")
    }

    private fun Double?.fmt1(): String = this?.let { "%.1f".format(it) } ?: "--"

    private fun Double?.fmt2(): String = this?.let { "%.2f".format(it) } ?: "--"
}

data class RuntimeTuningBinding(
    val key: RuntimeOverrideKey,
    val param: TunableRuntimeParam
)

data class RuntimeTuningDebugSummary(
    val runtimeSummary: String = "",
    val overrideSummary: String = "",
    val suggestionSummary: String = ""
)

data class RuntimeTuningApplyResult(
    val applied: Boolean,
    val message: String
)
