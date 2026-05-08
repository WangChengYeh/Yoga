package com.yogaflow.coach

import com.yogaflow.pose.PoseDetectionResult
import com.yogaflow.pose.PoseGeometry
import com.yogaflow.yoga.YogaPose

class PoseStateMachine {

    fun getJointStatus(pose: YogaPose, frame: PoseDetectionResult): Map<Int, Boolean> {
        return when (pose.id) {
            "forward_fold" -> {
                buildMap {
                    val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                    if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(25, leftKnee.degrees >= 150.0)
                    }

                    val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                    if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(26, rightKnee.degrees >= 150.0)
                    }

                    val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
                    if (leftHip.confidence != PoseGeometry.Confidence.INVALID) {
                        put(23, leftHip.degrees <= 140.0)
                    }

                    val rightHip = PoseGeometry.angle(frame, 12, 24, 26)
                    if (rightHip.confidence != PoseGeometry.Confidence.INVALID) {
                        put(24, rightHip.degrees <= 140.0)
                    }
                }
            }

            "squat" -> {
                buildMap {
                    val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                    if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(25, leftKnee.degrees in 120.0..160.0)
                    }

                    val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                    if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                        put(26, rightKnee.degrees in 120.0..160.0)
                    }
                }
            }

            "warrior_2" -> buildMap {
                val frontKnee = PoseGeometry.angle(frame, 23, 25, 27)
                if (frontKnee.confidence != PoseGeometry.Confidence.INVALID) {
                    put(25, frontKnee.degrees in 80.0..110.0)
                }
                val leftArm = PoseGeometry.angle(frame, 11, 13, 15)
                if (leftArm.confidence != PoseGeometry.Confidence.INVALID) {
                    put(13, leftArm.degrees >= 150.0)
                }
                val rightArm = PoseGeometry.angle(frame, 12, 14, 16)
                if (rightArm.confidence != PoseGeometry.Confidence.INVALID) {
                    put(14, rightArm.degrees >= 150.0)
                }
            }

            "downward_dog" -> buildMap {
                val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                    put(25, leftKnee.degrees >= 150.0)
                }
                val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                    put(26, rightKnee.degrees >= 150.0)
                }
                val leftElbow = PoseGeometry.angle(frame, 11, 13, 15)
                if (leftElbow.confidence != PoseGeometry.Confidence.INVALID) {
                    put(13, leftElbow.degrees >= 155.0)
                }
                val rightElbow = PoseGeometry.angle(frame, 12, 14, 16)
                if (rightElbow.confidence != PoseGeometry.Confidence.INVALID) {
                    put(14, rightElbow.degrees >= 155.0)
                }
            }

            "bridge" -> buildMap {
                val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
                if (leftHip.confidence != PoseGeometry.Confidence.INVALID) {
                    put(23, leftHip.degrees <= 120.0)
                }
                val rightHip = PoseGeometry.angle(frame, 12, 24, 26)
                if (rightHip.confidence != PoseGeometry.Confidence.INVALID) {
                    put(24, rightHip.degrees <= 120.0)
                }
                val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                if (leftKnee.confidence != PoseGeometry.Confidence.INVALID) {
                    put(25, leftKnee.degrees in 80.0..120.0)
                }
                val rightKnee = PoseGeometry.angle(frame, 24, 26, 28)
                if (rightKnee.confidence != PoseGeometry.Confidence.INVALID) {
                    put(26, rightKnee.degrees in 80.0..120.0)
                }
            }

            else -> emptyMap()
        }
    }

    fun update(pose: YogaPose, frame: PoseDetectionResult): Pair<CoachState, String> {
        return when (pose.id) {
            "forward_fold" -> {
                val knee = PoseGeometry.angle(frame, 23, 25, 27)
                val hip = PoseGeometry.angle(frame, 11, 23, 25)

                if (knee.confidence == PoseGeometry.Confidence.INVALID || hip.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "我目前看不清楚你的膝蓋和髖部角度，請讓全身進入畫面。"
                } else {
                    handleForwardFold(knee.degrees, hip.degrees, knee.confidence)
                }
            }

            "squat" -> {
                val knee = PoseGeometry.angle(frame, 23, 25, 27)

                if (knee.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "我目前看不清楚你的膝蓋角度，請讓雙腿進入畫面。"
                } else {
                    handleSquat(knee.degrees, knee.confidence)
                }
            }

            "warrior_2" -> {
                val frontKnee = PoseGeometry.angle(frame, 23, 25, 27)
                val leftArm = PoseGeometry.angle(frame, 11, 13, 15)
                if (frontKnee.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "請讓前腿進入畫面，我需要看到你的膝蓋。"
                } else {
                    val prefix = confidencePrefix(frontKnee.confidence)
                    when {
                        frontKnee.degrees > 110 -> CoachState.MOVEMENT to "${prefix}前膝再往下彎，讓小腿垂直地面。"
                        frontKnee.degrees < 80 -> CoachState.CORRECTION to "${prefix}前膝不要超過腳尖，稍微抬高一點。"
                        leftArm.confidence != PoseGeometry.Confidence.INVALID && leftArm.degrees < 150 ->
                            CoachState.CORRECTION to "${prefix}手臂打直，向兩側平舉延伸。"
                        else -> CoachState.HOLD to "${prefix}很好，保持穩定，眼看前方。"
                    }
                }
            }

            "downward_dog" -> {
                val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                val leftElbow = PoseGeometry.angle(frame, 11, 13, 15)
                if (leftKnee.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "請讓雙腿進入畫面，確認倒 V 姿勢。"
                } else {
                    val prefix = confidencePrefix(leftKnee.confidence)
                    when {
                        leftElbow.confidence != PoseGeometry.Confidence.INVALID && leftElbow.degrees < 155 ->
                            CoachState.CORRECTION to "${prefix}手臂打直，手掌穩定推地。"
                        leftKnee.degrees < 150 -> CoachState.MOVEMENT to "${prefix}膝蓋再伸直，腿後側持續延伸。"
                        else -> CoachState.HOLD to "${prefix}很好，維持倒 V，均勻呼吸。"
                    }
                }
            }

            "bridge" -> {
                val leftHip = PoseGeometry.angle(frame, 11, 23, 25)
                val leftKnee = PoseGeometry.angle(frame, 23, 25, 27)
                if (leftHip.confidence == PoseGeometry.Confidence.INVALID) {
                    CoachState.CORRECTION to "請讓全身進入畫面，確認臀部與雙腿可見。"
                } else {
                    val prefix = confidencePrefix(leftHip.confidence)
                    when {
                        leftHip.degrees > 120 -> CoachState.MOVEMENT to "${prefix}骨盆繼續往上推，臀部離地更高。"
                        leftKnee.confidence != PoseGeometry.Confidence.INVALID && leftKnee.degrees > 120 ->
                            CoachState.CORRECTION to "${prefix}腳跟往臀部靠近，膝蓋彎曲更多。"
                        leftKnee.confidence != PoseGeometry.Confidence.INVALID && leftKnee.degrees < 80 ->
                            CoachState.CORRECTION to "${prefix}腳跟稍微往外移，膝蓋角度調整一下。"
                        else -> CoachState.HOLD to "${prefix}臀橋很好，持續夾緊臀部。"
                    }
                }
            }

            else -> CoachState.HOLD to "維持姿勢"
        }
    }

    private fun handleForwardFold(
        knee: Double,
        hip: Double,
        confidence: PoseGeometry.Confidence
    ): Pair<CoachState, String> {
        val prefix = confidencePrefix(confidence)
        return when {
            knee < 150 -> CoachState.CORRECTION to "${prefix}膝蓋再伸直一點"
            hip > 140 -> CoachState.MOVEMENT to "${prefix}從髖部往前折"
            else -> CoachState.HOLD to "${prefix}很好，保持呼吸"
        }
    }

    private fun handleSquat(
        knee: Double,
        confidence: PoseGeometry.Confidence
    ): Pair<CoachState, String> {
        val prefix = confidencePrefix(confidence)
        return when {
            knee > 160 -> CoachState.MOVEMENT to "${prefix}再往下蹲"
            knee < 120 -> CoachState.CORRECTION to "${prefix}不要蹲太低，穩住"
            else -> CoachState.HOLD to "${prefix}穩住這個位置"
        }
    }

    private fun confidencePrefix(confidence: PoseGeometry.Confidence): String {
        return when (confidence) {
            PoseGeometry.Confidence.HIGH_3D -> ""
            PoseGeometry.Confidence.LOW_2D_FALLBACK -> "我先用畫面估算，"
            PoseGeometry.Confidence.INVALID -> ""
        }
    }
}
