// Demo polish additions
// inside onCreate after init

if (!poseHelper.isReady) {
    coachText.text = "Pose model not found. Please add pose_landmarker_lite.task to assets."
}

// LLM fallback indicator handled inline

// inside onResult
val coaching = llmCoach.generate(pose, flowState, flowCue)

val finalText = if (coaching == flowCue) {
    "(fallback) $coaching"
} else {
    coaching
}

coachText.text = finalText
speaker.speakIfNeeded(finalText)
