package com.yogaflow.flow

object FlowValidator {

    data class RequiredParam(
        val key: String,
        val reason: String
    )

    fun validate(flow: YogaFlow): YogaFlow {
        flow.steps.forEachIndexed { index, step ->
            validateStep(flow, index + 1, step)
        }
        return flow
    }

    private fun validateStep(flow: YogaFlow, stepNumber: Int, step: YogaFlowStep) {
        val requiredParams = requiredParamsFor(step.detect)
        val missing = requiredParams.filterNot { requirement ->
            step.params.containsKey(requirement.key)
        }

        if (missing.isNotEmpty()) {
            val details = missing.joinToString(separator = "\n") { requirement ->
                "- ${requirement.key}: ${requirement.reason}"
            }
            error(
                "Invalid YogaFlow DSL v2 config.\n" +
                    "flow=${flow.id}\n" +
                    "step=$stepNumber\n" +
                    "detect=${step.detect}\n" +
                    "Missing required runtime params:\n$details"
            )
        }
    }

    private fun requiredParamsFor(detect: String): List<RequiredParam> {
        return when (detect) {
            "ready_forward_fold" -> listOf(
                RequiredParam("angle.knee.ready.min", "required to confirm straight-leg ready posture"),
                RequiredParam("angle.hip.ready.min", "required to confirm upright ready posture")
            )

            "tall_spine_setup" -> listOf(
                RequiredParam("angle.knee.setup.min", "required to keep knees long during setup"),
                RequiredParam("angle.hip.setup.min", "required to keep torso tall during setup")
            )

            "hip_hinge" -> listOf(
                RequiredParam("angle.knee.hinge.min", "required to prevent excessive knee bend during hinge"),
                RequiredParam("angle.hip.hinge.max", "required to detect the start of hip hinge")
            )

            "controlled_forward_fold" -> listOf(
                RequiredParam("angle.knee.fold.min", "required to keep legs long during fold"),
                RequiredParam("angle.hip.fold.min", "required to prevent unsafe over-folding"),
                RequiredParam("angle.hip.fold.max", "required to detect enough forward fold depth")
            )

            "forward_hold" -> listOf(
                RequiredParam("angle.knee.hold.min", "required to keep knees stable during hold"),
                RequiredParam("angle.hip.hold.min", "required to prevent unsafe over-folding during hold"),
                RequiredParam("angle.hip.hold.max", "required to detect sufficient hold depth")
            )

            "return_standing" -> listOf(
                RequiredParam("angle.knee.return.min", "required to keep knees stable during return"),
                RequiredParam("angle.hip.return.min", "required to confirm return toward standing")
            )

            "neutral_finish" -> listOf(
                RequiredParam("angle.knee.neutral.min", "required to finish with stable legs"),
                RequiredParam("angle.hip.neutral.min", "required to finish in upright posture")
            )

            "twist_start" -> listOf(
                RequiredParam("angle.twist.start.min", "required to detect twist start"),
                RequiredParam("angle.twist.start.max", "required to prevent over-twisting")
            )

            "twist_hold" -> listOf(
                RequiredParam("angle.twist.hold.min", "required to detect enough twist depth"),
                RequiredParam("angle.twist.hold.max", "required to prevent over-twisting during hold")
            )

            "stable_base", "return_center" -> listOf(
                RequiredParam("angle.twist.center.max", "required to confirm centered torso")
            )

            "squat_setup" -> listOf(
                RequiredParam("angle.knee.setup.min", "required to confirm standing squat setup")
            )

            "squat_descent" -> listOf(
                RequiredParam("angle.knee.descent.min", "required to prevent excessive squat depth"),
                RequiredParam("angle.knee.descent.max", "required to detect descent progress")
            )

            "squat_hold" -> listOf(
                RequiredParam("angle.knee.hold.min", "required to prevent excessive squat depth during hold"),
                RequiredParam("angle.knee.hold.max", "required to detect sufficient squat depth during hold")
            )

            "squat_return" -> listOf(
                RequiredParam("angle.knee.return.min", "required to confirm return to standing")
            )

            "bridge_lift" -> listOf(
                RequiredParam("angle.hip.lift.min", "required to prevent unsafe bridge over-lift"),
                RequiredParam("angle.hip.lift.max", "required to detect bridge lift progress")
            )

            "bridge_hold" -> listOf(
                RequiredParam("angle.hip.hold.min", "required to prevent unsafe bridge over-lift during hold"),
                RequiredParam("angle.hip.hold.max", "required to detect sufficient bridge height during hold")
            )

            else -> emptyList()
        }
    }
}
