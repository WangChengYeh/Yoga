package com.yogaflow.flow

/**
 * Typed runtime parameters parsed from JSON Flow DSL v2.
 *
 * This replaces Map<String, Double> so detection logic no longer depends on
 * string keys such as "angle.hip.hold.min".
 */
data class RuntimeParams(
    val stabilityMs: Long? = null,
    val emaAlpha: Double? = null,
    val deadbandDegrees: Double? = null,
    val angles: AngleParams = AngleParams()
) {
    companion object {
        val EMPTY = RuntimeParams()
    }
}

data class AngleParams(
    val knee: PhaseAngleParams = PhaseAngleParams(),
    val hip: PhaseAngleParams = PhaseAngleParams(),
    val twist: PhaseAngleParams = PhaseAngleParams()
)

data class PhaseAngleParams(
    val ready: AngleRange = AngleRange(),
    val setup: AngleRange = AngleRange(),
    val hinge: AngleRange = AngleRange(),
    val fold: AngleRange = AngleRange(),
    val hold: AngleRange = AngleRange(),
    val returnPhase: AngleRange = AngleRange(),
    val neutral: AngleRange = AngleRange(),
    val start: AngleRange = AngleRange(),
    val center: AngleRange = AngleRange(),
    val descent: AngleRange = AngleRange(),
    val lift: AngleRange = AngleRange()
) {
    fun phase(phase: AnglePhase): AngleRange {
        return when (phase) {
            AnglePhase.READY -> ready
            AnglePhase.SETUP -> setup
            AnglePhase.HINGE -> hinge
            AnglePhase.FOLD -> fold
            AnglePhase.HOLD -> hold
            AnglePhase.RETURN -> returnPhase
            AnglePhase.NEUTRAL -> neutral
            AnglePhase.START -> start
            AnglePhase.CENTER -> center
            AnglePhase.DESCENT -> descent
            AnglePhase.LIFT -> lift
        }
    }
}

data class AngleRange(
    val min: Double? = null,
    val max: Double? = null
)

enum class AnglePhase(val jsonKey: String) {
    READY("ready"),
    SETUP("setup"),
    HINGE("hinge"),
    FOLD("fold"),
    HOLD("hold"),
    RETURN("return"),
    NEUTRAL("neutral"),
    START("start"),
    CENTER("center"),
    DESCENT("descent"),
    LIFT("lift");

    companion object {
        fun fromJsonKey(value: String): AnglePhase {
            return entries.firstOrNull { it.jsonKey == value }
                ?: error("Invalid angle phase '$value'")
        }
    }
}
