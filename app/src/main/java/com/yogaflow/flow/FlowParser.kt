package com.yogaflow.flow

import com.yogaflow.coach.CoachState

object FlowParser {

    fun parse(text: String): YogaFlow {

        val lines = text.lines().map { it.trim() }

        var id = ""
        var name = ""
        var pose = ""
        var language = ""
        var level = ""

        val steps = mutableListOf<YogaFlowStep>()
        var currentStep: MutableMap<String, String> = mutableMapOf()
        var endCue = ""

        for (line in lines) {

            if (line.startsWith("id =")) id = value(line)
            if (line.startsWith("name =")) name = value(line)
            if (line.startsWith("pose =")) pose = value(line)
            if (line.startsWith("language =")) language = value(line)
            if (line.startsWith("level =")) level = value(line)

            if (line.startsWith("[STEP")) {
                if (currentStep.isNotEmpty()) {
                    steps.add(buildStep(currentStep))
                    currentStep = mutableMapOf()
                }
            }

            if (line.contains("=")) {
                val (k, v) = line.split("=", limit = 2)
                currentStep[k.trim()] = v.trim()
            }

            if (line.startsWith("[END]")) {
                currentStep = mutableMapOf()
            }

            if (line.startsWith("cue =") && lines.contains("[END]")) {
                endCue = value(line)
            }
        }

        if (currentStep.isNotEmpty()) {
            steps.add(buildStep(currentStep))
        }

        return YogaFlow(id, name, pose, language, level, steps, endCue)
    }

    private fun buildStep(map: Map<String, String>): YogaFlowStep {
        return YogaFlowStep(
            state = CoachState.valueOf(map["state"] ?: "HOLD"),
            durationMs = map["duration_ms"]?.toLongOrNull() ?: 2000,
            cue = map["cue"] ?: "",
            detect = map["detect"] ?: "",
            correction = map["correction"] ?: ""
        )
    }

    private fun value(line: String): String {
        return line.substringAfter("=").trim()
    }
}
