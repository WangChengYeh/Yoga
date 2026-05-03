package com.yogaflow.flow

import com.yogaflow.coach.CoachState
import org.json.JSONObject

object FlowParser {

    fun parse(text: String): YogaFlow {
        val root = FlowJsonValidator.validate(JSONObject(text))

        val flow = root.getJSONObject("flow")
        val stepsArray = root.getJSONArray("steps")
        val steps = mutableListOf<YogaFlowStep>()

        for (i in 0 until stepsArray.length()) {
            steps.add(buildStep(stepsArray.getJSONObject(i)))
        }

        val yogaFlow = YogaFlow(
            id = flow.getString("id"),
            name = flow.getString("name"),
            pose = flow.getString("pose"),
            language = flow.getString("language"),
            level = flow.getString("level"),
            steps = steps,
            endCue = root.getJSONObject("end").getString("cue")
        )

        return FlowValidator.validate(yogaFlow)
    }

    private fun buildStep(step: JSONObject): YogaFlowStep {
        val params = step.optJSONObject("runtime")
            ?.let { parseRuntimeParams(it) }
            ?: RuntimeParams.EMPTY

        return YogaFlowStep(
            state = CoachState.valueOf(step.getString("state")),
            durationMs = step.getLong("durationMs"),
            cue = step.getString("cue"),
            detect = DetectKey.fromJsonKey(step.getString("detect")),
            correction = step.optString("correction", ""),
            params = params
        )
    }

    private fun parseRuntimeParams(runtime: JSONObject): RuntimeParams {
        return RuntimeParams(
            stabilityMs = if (runtime.has("stabilityMs")) runtime.getLong("stabilityMs") else null,
            emaAlpha = if (runtime.has("emaAlpha")) runtime.getDouble("emaAlpha") else null,
            deadbandDegrees = if (runtime.has("deadbandDegrees")) runtime.getDouble("deadbandDegrees") else null,
            angles = parseAngleParams(runtime.optJSONObject("angles"))
        )
    }

    private fun parseAngleParams(angles: JSONObject?): AngleParams {
        if (angles == null) return AngleParams()
        return AngleParams(
            knee = parsePhaseAngleParams(angles.optJSONObject("knee")),
            hip = parsePhaseAngleParams(angles.optJSONObject("hip")),
            twist = parsePhaseAngleParams(angles.optJSONObject("twist"))
        )
    }

    private fun parsePhaseAngleParams(phaseConfig: JSONObject?): PhaseAngleParams {
        if (phaseConfig == null) return PhaseAngleParams()
        return PhaseAngleParams(
            ready = parseRange(phaseConfig.optJSONObject("ready")),
            setup = parseRange(phaseConfig.optJSONObject("setup")),
            hinge = parseRange(phaseConfig.optJSONObject("hinge")),
            fold = parseRange(phaseConfig.optJSONObject("fold")),
            hold = parseRange(phaseConfig.optJSONObject("hold")),
            returnPhase = parseRange(phaseConfig.optJSONObject("return")),
            neutral = parseRange(phaseConfig.optJSONObject("neutral")),
            start = parseRange(phaseConfig.optJSONObject("start")),
            center = parseRange(phaseConfig.optJSONObject("center")),
            descent = parseRange(phaseConfig.optJSONObject("descent")),
            lift = parseRange(phaseConfig.optJSONObject("lift"))
        )
    }

    private fun parseRange(range: JSONObject?): AngleRange {
        if (range == null) return AngleRange()
        return AngleRange(
            min = if (range.has("min")) range.getDouble("min") else null,
            max = if (range.has("max")) range.getDouble("max") else null
        )
    }
}
