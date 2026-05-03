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
