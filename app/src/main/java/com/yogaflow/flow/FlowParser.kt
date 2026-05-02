package com.yogaflow.flow

import com.yogaflow.coach.CoachState

object FlowParser {

    private enum class Section {
        NONE,
        FLOW,
        STEP,
        END
    }

    fun parse(text: String): YogaFlow {
        val lines = text.lines().map { it.trim() }

        val metadata = mutableMapOf<String, String>()
        val steps = mutableListOf<YogaFlowStep>()
        var currentStep = mutableMapOf<String, String>()
        var section = Section.NONE
        var endCue = ""

        fun flushStep() {
            if (currentStep.isNotEmpty()) {
                steps.add(buildStep(currentStep))
                currentStep = mutableMapOf()
            }
        }

        for (line in lines) {
            if (line.isBlank() || line.startsWith("#")) continue

            when {
                line == "[FLOW]" -> {
                    flushStep()
                    section = Section.FLOW
                }

                line.startsWith("[STEP") -> {
                    flushStep()
                    section = Section.STEP
                }

                line == "[END]" -> {
                    flushStep()
                    section = Section.END
                }

                line.contains("=") -> {
                    val (key, parsedValue) = parseKeyValue(line)
                    when (section) {
                        Section.FLOW -> metadata[key] = parsedValue
                        Section.STEP -> currentStep[key] = parsedValue
                        Section.END -> if (key == "cue") endCue = parsedValue
                        Section.NONE -> Unit
                    }
                }
            }
        }

        flushStep()

        return YogaFlow(
            id = metadata["id"].orEmpty(),
            name = metadata["name"].orEmpty(),
            pose = metadata["pose"].orEmpty(),
            language = metadata["language"].orEmpty(),
            level = metadata["level"].orEmpty(),
            steps = steps,
            endCue = endCue
        )
    }

    private fun buildStep(map: Map<String, String>): YogaFlowStep {
        return YogaFlowStep(
            state = CoachState.valueOf(map["state"] ?: "HOLD"),
            durationMs = map["duration_ms"]?.toLongOrNull() ?: 2000,
            cue = map["cue"].orEmpty(),
            detect = map["detect"].orEmpty(),
            correction = map["correction"].orEmpty()
        )
    }

    private fun parseKeyValue(line: String): Pair<String, String> {
        val parts = line.split("=", limit = 2)
        return parts[0].trim() to parts.getOrElse(1) { "" }.trim()
    }
}
