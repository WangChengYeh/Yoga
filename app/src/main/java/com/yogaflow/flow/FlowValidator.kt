package com.yogaflow.flow

object FlowValidator {

    fun validate(flow: YogaFlow): YogaFlow {
        flow.steps.forEachIndexed { index, step ->
            validateStep(flow, index + 1, step)
        }
        return flow
    }

    private fun validateStep(flow: YogaFlow, stepNumber: Int, step: YogaFlowStep) {
        val missing = requiredParamsFor(step.detect, step.params)
        if (missing.isNotEmpty()) {
            error(
                "Invalid YogaFlow DSL v2 config.\n" +
                    "flow=${flow.id}\n" +
                    "step=$stepNumber\n" +
                    "detect=${step.detect.jsonKey}\n" +
                    "Missing required runtime params:\n" +
                    missing.joinToString(separator = "\n") { "- $it" }
            )
        }
    }

    private fun requiredParamsFor(detect: DetectKey, params: RuntimeParams): List<String> {
        val missing = mutableListOf<String>()

        fun require(value: Double?, key: String) {
            if (value == null) missing.add(key)
        }

        fun require(value: Long?, key: String) {
            if (value == null) missing.add(key)
        }

        fun requireStrictRuntimeControls() {
            require(params.stabilityMs, "runtime.stabilityMs")
            require(params.emaAlpha, "runtime.emaAlpha")
            require(params.deadbandDegrees, "runtime.deadbandDegrees")
        }

        when (detect) {
            DetectKey.READY_FORWARD_FOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.ready.min, "runtime.angles.knee.ready.min")
                require(params.angles.hip.ready.min, "runtime.angles.hip.ready.min")
            }
            DetectKey.TALL_SPINE_SETUP -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.setup.min, "runtime.angles.knee.setup.min")
                require(params.angles.hip.setup.min, "runtime.angles.hip.setup.min")
            }
            DetectKey.HIP_HINGE -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.hinge.min, "runtime.angles.knee.hinge.min")
                require(params.angles.hip.hinge.max, "runtime.angles.hip.hinge.max")
            }
            DetectKey.CONTROLLED_FORWARD_FOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.fold.min, "runtime.angles.knee.fold.min")
                require(params.angles.hip.fold.min, "runtime.angles.hip.fold.min")
                require(params.angles.hip.fold.max, "runtime.angles.hip.fold.max")
            }
            DetectKey.FORWARD_HOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.hold.min, "runtime.angles.knee.hold.min")
                require(params.angles.hip.hold.min, "runtime.angles.hip.hold.min")
                require(params.angles.hip.hold.max, "runtime.angles.hip.hold.max")
            }
            DetectKey.RETURN_STANDING -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.returnPhase.min, "runtime.angles.knee.return.min")
                require(params.angles.hip.returnPhase.min, "runtime.angles.hip.return.min")
            }
            DetectKey.NEUTRAL_FINISH -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.neutral.min, "runtime.angles.knee.neutral.min")
                require(params.angles.hip.neutral.min, "runtime.angles.hip.neutral.min")
            }
            DetectKey.STABLE_BASE, DetectKey.RETURN_CENTER -> {
                requireStrictRuntimeControls()
                require(params.angles.twist.center.max, "runtime.angles.twist.center.max")
            }
            DetectKey.TWIST_START -> {
                requireStrictRuntimeControls()
                require(params.angles.twist.start.min, "runtime.angles.twist.start.min")
                require(params.angles.twist.start.max, "runtime.angles.twist.start.max")
            }
            DetectKey.TWIST_HOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.twist.hold.min, "runtime.angles.twist.hold.min")
                require(params.angles.twist.hold.max, "runtime.angles.twist.hold.max")
            }
            DetectKey.SQUAT_SETUP -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.setup.min, "runtime.angles.knee.setup.min")
            }
            DetectKey.SQUAT_DESCENT -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.descent.min, "runtime.angles.knee.descent.min")
                require(params.angles.knee.descent.max, "runtime.angles.knee.descent.max")
            }
            DetectKey.SQUAT_HOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.hold.min, "runtime.angles.knee.hold.min")
                require(params.angles.knee.hold.max, "runtime.angles.knee.hold.max")
            }
            DetectKey.SQUAT_RETURN -> {
                requireStrictRuntimeControls()
                require(params.angles.knee.returnPhase.min, "runtime.angles.knee.return.min")
            }
            DetectKey.BRIDGE_LIFT -> {
                requireStrictRuntimeControls()
                require(params.angles.hip.lift.min, "runtime.angles.hip.lift.min")
                require(params.angles.hip.lift.max, "runtime.angles.hip.lift.max")
            }
            DetectKey.BRIDGE_HOLD -> {
                requireStrictRuntimeControls()
                require(params.angles.hip.hold.min, "runtime.angles.hip.hold.min")
                require(params.angles.hip.hold.max, "runtime.angles.hip.hold.max")
            }
            DetectKey.BRIDGE_SETUP,
            DetectKey.BRIDGE_RETURN,
            DetectKey.STANDING_CENTERED,
            DetectKey.SPINE_LENGTHENED,
            DetectKey.MOUNTAIN_HOLD,
            DetectKey.READY_FOR_NEXT_POSE -> Unit
        }

        return missing
    }
}
