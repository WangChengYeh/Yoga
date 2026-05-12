package com.yogaflow.flow

import org.json.JSONArray
import org.json.JSONObject

object FlowJsonValidator {

    private val validStates = setOf("SETUP", "MOVEMENT", "HOLD", "TRANSITION", "CORRECTION")
    private val validJoints = setOf("knee", "hip", "twist")
    private val validPhases = setOf("ready", "setup", "hinge", "fold", "hold", "return", "neutral", "start", "center", "descent", "lift")
    private val validBounds = setOf("min", "max")
    private val validPoses = setOf("mountain", "forward_fold", "twist", "squat", "bridge", "warrior_1", "warrior_2", "downward_dog", "child_pose", "pigeon")
    private val validLevels = setOf("beginner", "intermediate", "advanced")
    private val validLanguages = setOf("zh-TW", "en-US")
    private val validDetectKeys = DetectKey.jsonKeys()

    fun validate(root: JSONObject): JSONObject {
        requireObject(root, "flow")
        requireArray(root, "steps")
        requireObject(root, "end")

        validateDefaults(root)
        validateFlow(root.getJSONObject("flow"))
        validateSteps(root.getJSONArray("steps"))
        validateEnd(root.getJSONObject("end"))

        return root
    }

    private fun validateDefaults(root: JSONObject) {
        if (!root.has("defaults")) return
        val defaults = root.optJSONObject("defaults")
            ?: error("Invalid Flow JSON: root.defaults must be an object")
        if (!defaults.has("runtime")) return
        val runtime = defaults.optJSONObject("runtime")
            ?: error("Invalid Flow JSON: root.defaults.runtime must be an object")
        validateRuntime(runtime, "root.defaults.runtime")
    }

    private fun validateFlow(flow: JSONObject) {
        requireString(flow, "id")
        requireString(flow, "name")
        requireEnum(flow, "pose", validPoses)
        requireEnum(flow, "language", validLanguages)
        requireEnum(flow, "level", validLevels)
    }

    private fun validateSteps(steps: JSONArray) {
        if (steps.length() == 0) {
            error("Invalid Flow JSON: steps must contain at least one step")
        }

        for (i in 0 until steps.length()) {
            val step = steps.optJSONObject(i)
                ?: error("Invalid Flow JSON: steps[$i] must be an object")
            validateStep(step, i + 1)
        }
    }

    private fun validateStep(step: JSONObject, stepNumber: Int) {
        val path = "steps[$stepNumber]"
        requireEnum(step, "state", validStates, path = path)
        requireNumber(step, "durationMs", min = 100.0, max = 60000.0, path = path)
        requireString(step, "cue", path = path)
        requireEnum(step, "detect", validDetectKeys, path = path)

        if (step.has("correction") && !isString(step, "correction")) {
            error("Invalid Flow JSON: $path.correction must be a string")
        }
        if (step.has("avatar_action") && !isString(step, "avatar_action")) {
            error("Invalid Flow JSON: $path.avatar_action must be a string")
        }

        if (step.has("runtime")) {
            val runtime = step.optJSONObject("runtime")
                ?: error("Invalid Flow JSON: $path.runtime must be an object")
            validateRuntime(runtime, "$path.runtime")
        }
    }

    private fun validateRuntime(runtime: JSONObject, path: String) {
        if (runtime.has("stabilityMs")) {
            requireNumber(runtime, "stabilityMs", min = 0.0, max = 5000.0, path = path)
        }
        if (runtime.has("emaAlpha")) {
            requireNumber(runtime, "emaAlpha", min = 0.01, max = 1.0, path = path)
        }
        if (runtime.has("deadbandDegrees")) {
            requireNumber(runtime, "deadbandDegrees", min = 0.0, max = 20.0, path = path)
        }
        if (runtime.has("angles")) {
            val angles = runtime.optJSONObject("angles")
                ?: error("Invalid Flow JSON: $path.angles must be an object")
            validateAngles(angles, path)
        }
    }

    private fun validateAngles(angles: JSONObject, path: String) {
        val joints = angles.keys()
        while (joints.hasNext()) {
            val joint = joints.next()
            if (joint !in validJoints) {
                error("Invalid Flow JSON: $path.angles.$joint is not a supported joint")
            }

            val phaseConfig = angles.optJSONObject(joint)
                ?: error("Invalid Flow JSON: $path.angles.$joint must be an object")

            val phases = phaseConfig.keys()
            while (phases.hasNext()) {
                val phase = phases.next()
                if (phase !in validPhases) {
                    error("Invalid Flow JSON: $path.angles.$joint.$phase is not a supported phase")
                }

                val range = phaseConfig.optJSONObject(phase)
                    ?: error("Invalid Flow JSON: $path.angles.$joint.$phase must be an object")

                if (!range.has("min") && !range.has("max")) {
                    error("Invalid Flow JSON: $path.angles.$joint.$phase must include min or max")
                }

                val bounds = range.keys()
                while (bounds.hasNext()) {
                    val bound = bounds.next()
                    if (bound !in validBounds) {
                        error("Invalid Flow JSON: $path.angles.$joint.$phase.$bound is not supported; use min or max")
                    }
                    requireNumber(range, bound, min = 0.0, max = 180.0, path = "$path.angles.$joint.$phase")
                }
            }
        }
    }

    private fun validateEnd(end: JSONObject) {
        requireString(end, "cue", path = "end")
    }

    private fun requireObject(obj: JSONObject, key: String, path: String = "root") {
        if (!obj.has(key) || obj.optJSONObject(key) == null) {
            error("Invalid Flow JSON: $path.$key must be an object")
        }
    }

    private fun requireArray(obj: JSONObject, key: String, path: String = "root") {
        if (!obj.has(key) || obj.optJSONArray(key) == null) {
            error("Invalid Flow JSON: $path.$key must be an array")
        }
    }

    private fun requireString(obj: JSONObject, key: String, path: String = "root") {
        if (!obj.has(key) || !isString(obj, key) || obj.getString(key).isBlank()) {
            error("Invalid Flow JSON: $path.$key must be a non-empty string")
        }
    }

    private fun requireEnum(obj: JSONObject, key: String, allowed: Set<String>, path: String = "root") {
        requireString(obj, key, path)
        val value = obj.getString(key)
        if (value !in allowed) {
            error("Invalid Flow JSON: $path.$key='$value' is not supported; expected one of ${allowed.joinToString()}")
        }
    }

    private fun requireNumber(obj: JSONObject, key: String, min: Double, max: Double, path: String = "root") {
        if (!obj.has(key)) {
            error("Invalid Flow JSON: $path.$key must be a number")
        }
        val value = obj.opt(key)
        if (value !is Number) {
            error("Invalid Flow JSON: $path.$key must be a number")
        }
        val number = value.toDouble()
        if (number < min || number > max) {
            error("Invalid Flow JSON: $path.$key=$number is out of range [$min, $max]")
        }
    }

    private fun isString(obj: JSONObject, key: String): Boolean {
        return obj.opt(key) is String
    }
}
