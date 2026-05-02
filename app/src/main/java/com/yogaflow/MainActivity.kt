// ONLY show diff-relevant part

// replace evaluateCurrentStep usage
val mapping = PoseDetectionRouter.evaluate(
    poseId = currentPose.id,
    detect = currentStep.detect,
    frame = frame,
    fallback = stateMachine,
    currentPose = currentPose
)

// remove old evaluateCurrentStep function
