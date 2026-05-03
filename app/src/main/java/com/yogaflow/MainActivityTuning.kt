package com.yogaflow

import com.yogaflow.flow.RuntimeOverrideKey
import com.yogaflow.flow.TunableRuntimeParam
import com.yogaflow.flow.TunableRuntimeParamExtractor

internal data class RuntimeTuningBinding(
    val key: RuntimeOverrideKey,
    val param: TunableRuntimeParam
)

internal fun MainActivity.computeCurrentTuningBindings(): List<RuntimeTuningBinding> {
    if (!isCurrentFlowInitialized()) return emptyList()
    val stepIndex = flowEngine.currentStepNumber() - 1
    val step = currentFlow.steps.getOrNull(stepIndex) ?: return emptyList()
    return TunableRuntimeParamExtractor.extract(step.params).map { param ->
        RuntimeTuningBinding(
            key = RuntimeOverrideKey(
                flowId = currentFlow.id,
                stepIndex = stepIndex,
                detect = step.detect,
                path = param.path
            ),
            param = param
        )
    }
}

internal fun valueToSliderProgress(value: Double, param: TunableRuntimeParam, sliderMax: Int): Int {
    val clamped = value.coerceIn(param.min, param.max)
    val ratio = if (param.max == param.min) 0.0 else (clamped - param.min) / (param.max - param.min)
    return (ratio * sliderMax).toInt().coerceIn(0, sliderMax)
}

internal fun sliderProgressToValue(progress: Int, param: TunableRuntimeParam, sliderMax: Int): Double {
    val ratio = progress.coerceIn(0, sliderMax).toDouble() / sliderMax.toDouble()
    val raw = param.min + ratio * (param.max - param.min)
    return if (param.isInteger) raw.toLong().toDouble() else raw
}

internal fun formatTuningValue(value: Double, param: TunableRuntimeParam): String {
    return if (param.isInteger) value.toLong().toString() else "%.2f".format(value)
}
