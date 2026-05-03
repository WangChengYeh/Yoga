package com.yogaflow.flow

/**
 * User runtime override layer for JSON DSL v2.
 *
 * Flow JSON remains the source of truth. User tuning is applied as a separate
 * runtime layer and never mutates packaged flow assets.
 */
data class UserRuntimeOverrides(
    val stabilityMs: Long? = null,
    val emaAlpha: Double? = null,
    val deadbandDegrees: Double? = null,
    val angles: AngleParams = AngleParams()
) {
    companion object {
        val EMPTY = UserRuntimeOverrides()
    }
}

data class RuntimeOverrideKey(
    val flowId: String,
    val stepIndex: Int,
    val detect: DetectKey,
    val path: String
)

data class TunableRuntimeParam(
    val label: String,
    val path: String,
    val value: Double,
    val min: Double,
    val max: Double,
    val isInteger: Boolean = false
)

class RuntimeOverrideStore {
    private val values = mutableMapOf<RuntimeOverrideKey, Double>()

    fun set(key: RuntimeOverrideKey, value: Double) {
        values[key] = value
    }

    fun clear(key: RuntimeOverrideKey) {
        values.remove(key)
    }

    fun clearFlow(flowId: String) {
        values.keys.filter { it.flowId == flowId }.forEach { values.remove(it) }
    }

    fun clearAll() {
        values.clear()
    }

    fun valueFor(key: RuntimeOverrideKey): Double? = values[key]

    fun overridesFor(flowId: String, stepIndex: Int, detect: DetectKey): UserRuntimeOverrides {
        val scopedValues = values.filterKeys {
            it.flowId == flowId && it.stepIndex == stepIndex && it.detect == detect
        }
        if (scopedValues.isEmpty()) return UserRuntimeOverrides.EMPTY

        var result = UserRuntimeOverrides.EMPTY
        scopedValues.forEach { (key, value) ->
            result = RuntimeOverridePathWriter.write(result, key.path, value)
        }
        return result
    }
}

object RuntimeOverrideMerger {

    fun apply(base: RuntimeParams, overrides: UserRuntimeOverrides): RuntimeParams {
        return RuntimeParams(
            stabilityMs = overrides.stabilityMs ?: base.stabilityMs,
            emaAlpha = overrides.emaAlpha ?: base.emaAlpha,
            deadbandDegrees = overrides.deadbandDegrees ?: base.deadbandDegrees,
            angles = mergeAngles(base.angles, overrides.angles)
        )
    }

    private fun mergeAngles(base: AngleParams, overrides: AngleParams): AngleParams {
        return AngleParams(
            knee = mergePhaseAngles(base.knee, overrides.knee),
            hip = mergePhaseAngles(base.hip, overrides.hip),
            twist = mergePhaseAngles(base.twist, overrides.twist)
        )
    }

    private fun mergePhaseAngles(base: PhaseAngleParams, overrides: PhaseAngleParams): PhaseAngleParams {
        return PhaseAngleParams(
            ready = mergeRange(base.ready, overrides.ready),
            setup = mergeRange(base.setup, overrides.setup),
            hinge = mergeRange(base.hinge, overrides.hinge),
            fold = mergeRange(base.fold, overrides.fold),
            hold = mergeRange(base.hold, overrides.hold),
            returnPhase = mergeRange(base.returnPhase, overrides.returnPhase),
            neutral = mergeRange(base.neutral, overrides.neutral),
            start = mergeRange(base.start, overrides.start),
            center = mergeRange(base.center, overrides.center),
            descent = mergeRange(base.descent, overrides.descent),
            lift = mergeRange(base.lift, overrides.lift)
        )
    }

    private fun mergeRange(base: AngleRange, overrides: AngleRange): AngleRange {
        return AngleRange(
            min = overrides.min ?: base.min,
            max = overrides.max ?: base.max
        )
    }
}

object TunableRuntimeParamExtractor {

    fun extract(params: RuntimeParams): List<TunableRuntimeParam> {
        val result = mutableListOf<TunableRuntimeParam>()

        params.stabilityMs?.let {
            result.add(
                TunableRuntimeParam(
                    label = "stabilityMs",
                    path = "runtime.stabilityMs",
                    value = it.toDouble(),
                    min = 0.0,
                    max = 5000.0,
                    isInteger = true
                )
            )
        }
        params.emaAlpha?.let {
            result.add(
                TunableRuntimeParam(
                    label = "emaAlpha",
                    path = "runtime.emaAlpha",
                    value = it,
                    min = 0.01,
                    max = 1.0
                )
            )
        }
        params.deadbandDegrees?.let {
            result.add(
                TunableRuntimeParam(
                    label = "deadbandDegrees",
                    path = "runtime.deadbandDegrees",
                    value = it,
                    min = 0.0,
                    max = 20.0
                )
            )
        }

        collectJoint(result, "knee", params.angles.knee)
        collectJoint(result, "hip", params.angles.hip)
        collectJoint(result, "twist", params.angles.twist)

        return result
    }

    private fun collectJoint(result: MutableList<TunableRuntimeParam>, joint: String, phases: PhaseAngleParams) {
        collectRange(result, joint, AnglePhase.READY, phases.ready)
        collectRange(result, joint, AnglePhase.SETUP, phases.setup)
        collectRange(result, joint, AnglePhase.HINGE, phases.hinge)
        collectRange(result, joint, AnglePhase.FOLD, phases.fold)
        collectRange(result, joint, AnglePhase.HOLD, phases.hold)
        collectRange(result, joint, AnglePhase.RETURN, phases.returnPhase)
        collectRange(result, joint, AnglePhase.NEUTRAL, phases.neutral)
        collectRange(result, joint, AnglePhase.START, phases.start)
        collectRange(result, joint, AnglePhase.CENTER, phases.center)
        collectRange(result, joint, AnglePhase.DESCENT, phases.descent)
        collectRange(result, joint, AnglePhase.LIFT, phases.lift)
    }

    private fun collectRange(result: MutableList<TunableRuntimeParam>, joint: String, phase: AnglePhase, range: AngleRange) {
        range.min?.let {
            val path = "runtime.angles.$joint.${phase.jsonKey}.min"
            result.add(TunableRuntimeParam(path.removePrefix("runtime.angles."), path, it, 0.0, 180.0))
        }
        range.max?.let {
            val path = "runtime.angles.$joint.${phase.jsonKey}.max"
            result.add(TunableRuntimeParam(path.removePrefix("runtime.angles."), path, it, 0.0, 180.0))
        }
    }
}

private object RuntimeOverridePathWriter {

    fun write(base: UserRuntimeOverrides, path: String, value: Double): UserRuntimeOverrides {
        return when (path) {
            "runtime.stabilityMs" -> base.copy(stabilityMs = value.toLong())
            "runtime.emaAlpha" -> base.copy(emaAlpha = value)
            "runtime.deadbandDegrees" -> base.copy(deadbandDegrees = value)
            else -> writeAngle(base, path, value)
        }
    }

    private fun writeAngle(base: UserRuntimeOverrides, path: String, value: Double): UserRuntimeOverrides {
        val parts = path.split(".")
        require(parts.size == 5 && parts[0] == "runtime" && parts[1] == "angles") {
            "Unsupported runtime override path '$path'"
        }

        val joint = parts[2]
        val phase = AnglePhase.fromJsonKey(parts[3])
        val bound = parts[4]

        val angles = when (joint) {
            "knee" -> base.angles.copy(knee = writePhase(base.angles.knee, phase, bound, value))
            "hip" -> base.angles.copy(hip = writePhase(base.angles.hip, phase, bound, value))
            "twist" -> base.angles.copy(twist = writePhase(base.angles.twist, phase, bound, value))
            else -> error("Unsupported angle joint '$joint'")
        }
        return base.copy(angles = angles)
    }

    private fun writePhase(base: PhaseAngleParams, phase: AnglePhase, bound: String, value: Double): PhaseAngleParams {
        return when (phase) {
            AnglePhase.READY -> base.copy(ready = writeRange(base.ready, bound, value))
            AnglePhase.SETUP -> base.copy(setup = writeRange(base.setup, bound, value))
            AnglePhase.HINGE -> base.copy(hinge = writeRange(base.hinge, bound, value))
            AnglePhase.FOLD -> base.copy(fold = writeRange(base.fold, bound, value))
            AnglePhase.HOLD -> base.copy(hold = writeRange(base.hold, bound, value))
            AnglePhase.RETURN -> base.copy(returnPhase = writeRange(base.returnPhase, bound, value))
            AnglePhase.NEUTRAL -> base.copy(neutral = writeRange(base.neutral, bound, value))
            AnglePhase.START -> base.copy(start = writeRange(base.start, bound, value))
            AnglePhase.CENTER -> base.copy(center = writeRange(base.center, bound, value))
            AnglePhase.DESCENT -> base.copy(descent = writeRange(base.descent, bound, value))
            AnglePhase.LIFT -> base.copy(lift = writeRange(base.lift, bound, value))
        }
    }

    private fun writeRange(base: AngleRange, bound: String, value: Double): AngleRange {
        return when (bound) {
            "min" -> base.copy(min = value)
            "max" -> base.copy(max = value)
            else -> error("Unsupported angle bound '$bound'")
        }
    }
}
