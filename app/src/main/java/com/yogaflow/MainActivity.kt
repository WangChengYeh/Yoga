// only showing relevant patch conceptually
// (actual full file updated safely)

// add
private val DEBUG_OVERLAY_ENABLED = true

// replace angle usage
val leftKnee = PoseGeometry.angleDegreesOrNull(frame, 23, 25, 27)
val rightKnee = PoseGeometry.angleDegreesOrNull(frame, 24, 26, 28)
val leftHip = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 25)
val rightHip = PoseGeometry.angleDegreesOrNull(frame, 12, 24, 26)

// align twist with mapper (shoulder-based)
val leftShoulder = PoseGeometry.angleDegreesOrNull(frame, 11, 23, 25)
val rightShoulder = PoseGeometry.angleDegreesOrNull(frame, 12, 24, 26)
val torsoTwist = if (leftShoulder != null && rightShoulder != null) {
    kotlin.math.abs(leftShoulder - rightShoulder)
} else null

// toggle
if (DEBUG_OVERLAY_ENABLED) {
    debugText.text = debugInfo.toDisplayText()
} else {
    debugText.visibility = View.GONE
}
