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
            ?.let { flattenRuntimeParams(it) }
            .orEmpty()

        return YogaFlowStep(
            state = CoachState.valueOf(step.getString("state")),
            durationMs = step.getLong("durationMs"),
            cue = step.getString("cue"),
            detect = DetectKey.fromJsonKey(step.getString("detect")),
            correction = step.optString("correction", ""),
            params = params
        )
    }

    private fun flattenRuntimeParams(runtime: JSONObject): Map<String, Double> {
        val params = mutableMapOf<String, Double>()

        if (runtime.has("stabilityMs")) {
            params["stability.ms"] = runtime.getDouble("stabilityMs")
        }
        if (runtime.has("emaAlpha")) {
            params["ema.alpha"] = runtime.getDouble("emaAlpha")
        }
        if (runtime.has("deadbandDegrees")) {
            params["deadband.degrees"] = runtime.getDouble("deadbandDegrees")
        }

        val angles = runtime.optJSONObject("angles") ?: return params
        val joints = angles.keys()
        while (joints.hasNext()) {
            val joint = joints.next()
            val phaseConfig = angles.getJSONObject(joint)
            val phases = phaseConfig.keys()
            while (phases.hasNext()) {
                val phase = phases.next()
                val range = phaseConfig.getJSONObject(phase)
                if (range.has("min")) {
                    params["angle.$joint.$phase.min"] = range.getDouble("min")
                }
                if (range.has("max")) {
                    params["angle.$joint.$phase.max"] = range.getDouble("max")
                }
            }
        }

        return params
    }
}
