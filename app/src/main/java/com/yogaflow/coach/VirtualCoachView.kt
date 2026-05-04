package com.yogaflow.coach

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class VirtualCoachView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class CoachSkin(
        val id: String,
        val bodyColor: Int,
        val outfitColor: Int,
        val accentColor: Int,
        val hairColor: Int,
        val silhouetteScale: Float
    )

    private data class Joint(val x: Float, val y: Float)

    private data class CoachPose(
        val head: Joint,
        val neck: Joint,
        val leftShoulder: Joint,
        val rightShoulder: Joint,
        val leftElbow: Joint,
        val rightElbow: Joint,
        val leftHand: Joint,
        val rightHand: Joint,
        val leftHip: Joint,
        val rightHip: Joint,
        val leftKnee: Joint,
        val rightKnee: Joint,
        val leftFoot: Joint,
        val rightFoot: Joint
    )

    private val prefs = context.getSharedPreferences("virtual_coach", Context.MODE_PRIVATE)

    private val skins = listOf(
        CoachSkin("calm_blue", Color.rgb(198, 143, 105), Color.rgb(40, 103, 185), Color.rgb(167, 219, 255), Color.rgb(42, 34, 30), 1.0f),
        CoachSkin("forest_green", Color.rgb(150, 93, 68), Color.rgb(34, 128, 86), Color.rgb(189, 229, 164), Color.rgb(30, 27, 23), 1.04f),
        CoachSkin("warm_coral", Color.rgb(116, 70, 52), Color.rgb(196, 75, 72), Color.rgb(255, 211, 143), Color.rgb(24, 22, 20), 0.98f),
        CoachSkin("mono_focus", Color.rgb(169, 177, 187), Color.rgb(58, 68, 82), Color.rgb(232, 238, 246), Color.rgb(42, 48, 56), 1.02f)
    )

    private var skinIndex = skins.indexOfFirst { it.id == prefs.getString(PREF_SKIN_ID, skins.first().id) }
        .coerceAtLeast(0)
    private var poseId = "mountain"
    private var detect = ""
    private var coachState = CoachState.SETUP
    private var avatarCommand = AvatarCommand("hold_mountain", "calm", null)

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        style = Paint.Style.STROKE
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 0, 0, 0)
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    init {
        alpha = 0.88f
        isClickable = true
        contentDescription = "Virtual coach"
        setOnClickListener { cycleSkin() }
    }

    fun setGuide(
        nextPoseId: String,
        nextDetect: String,
        nextState: CoachState,
        nextAvatarCommand: AvatarCommand = avatarCommand
    ) {
        if (
            poseId == nextPoseId &&
            detect == nextDetect &&
            coachState == nextState &&
            avatarCommand == nextAvatarCommand
        ) return
        poseId = nextPoseId
        detect = nextDetect
        coachState = nextState
        avatarCommand = nextAvatarCommand
        invalidate()
    }

    fun setPoseCoachFrame(frame: PoseCoachFrame) {
        setGuide(
            nextPoseId = poseIdForAction(frame.avatar.action),
            nextDetect = frame.avatar.action,
            nextState = stateForPhase(frame.phase),
            nextAvatarCommand = frame.avatar
        )
        invalidate()
    }

    private fun cycleSkin() {
        skinIndex = (skinIndex + 1) % skins.size
        prefs.edit().putString(PREF_SKIN_ID, skins[skinIndex].id).apply()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val avatarScale = skins[skinIndex].silhouetteScale
        val frame = RectF(
            width * 0.01f,
            height * 0.01f,
            width * 0.99f,
            height * 0.99f
        )

        drawBackdrop(canvas, frame)
        drawCoach(canvas, frame, avatarScale)
        if (visibility == VISIBLE) postInvalidateDelayed(80L)
    }

    private fun drawBackdrop(canvas: Canvas, frame: RectF) {
        fillPaint.shader = LinearGradient(
            frame.left,
            frame.top,
            frame.right,
            frame.bottom,
            Color.argb(0, 9, 12, 16),
            Color.argb(0, 245, 248, 252),
            Shader.TileMode.CLAMP
        )
        val radius = frame.width() * 0.08f
        canvas.drawRoundRect(frame, radius, radius, fillPaint)
        fillPaint.shader = null

        outlinePaint.color = Color.argb(0, 255, 255, 255)
        outlinePaint.strokeWidth = frame.width() * 0.006f
        canvas.drawRoundRect(frame, radius, radius, outlinePaint)
    }

    private fun drawCoach(canvas: Canvas, frame: RectF, avatarScale: Float) {
        val skin = skins[skinIndex]
        val pose = applyBreathing(poseFor(poseId, detect, coachState))
        val scaleFrame = scaledFrame(frame, avatarScale)
        val limbWidth = scaleFrame.width() * 0.062f
        val armWidth = scaleFrame.width() * 0.045f

        drawShadow(canvas, scaleFrame)

        drawSegment(canvas, scaleFrame, pose.leftHip, pose.leftKnee, limbWidth, skin.outfitColor)
        drawSegment(canvas, scaleFrame, pose.leftKnee, pose.leftFoot, limbWidth * 0.92f, skin.outfitColor)
        drawSegment(canvas, scaleFrame, pose.rightHip, pose.rightKnee, limbWidth, skin.outfitColor)
        drawSegment(canvas, scaleFrame, pose.rightKnee, pose.rightFoot, limbWidth * 0.92f, skin.outfitColor)

        drawTorso(canvas, scaleFrame, pose, skin)

        drawSegment(canvas, scaleFrame, pose.leftShoulder, pose.leftElbow, armWidth, skin.bodyColor)
        drawSegment(canvas, scaleFrame, pose.leftElbow, pose.leftHand, armWidth, skin.bodyColor)
        drawSegment(canvas, scaleFrame, pose.rightShoulder, pose.rightElbow, armWidth, skin.bodyColor)
        drawSegment(canvas, scaleFrame, pose.rightElbow, pose.rightHand, armWidth, skin.bodyColor)

        drawHead(canvas, scaleFrame, pose.head, skin)
        drawJointHighlights(canvas, scaleFrame, pose, skin)
        drawCorrectionHighlight(canvas, scaleFrame, pose, skin)
    }

    private fun scaledFrame(frame: RectF, scale: Float): RectF {
        val insetX = frame.width() * (1f - scale).coerceAtMost(0.12f) / 2f
        val insetY = frame.height() * (1f - scale).coerceAtMost(0.08f) / 2f
        return RectF(frame.left + insetX, frame.top + insetY, frame.right - insetX, frame.bottom - insetY)
    }

    private fun drawShadow(canvas: Canvas, frame: RectF) {
        fillPaint.color = Color.argb(80, 0, 0, 0)
        fillPaint.shader = null
        val base = RectF(
            frame.left + frame.width() * 0.20f,
            frame.top + frame.height() * 0.91f,
            frame.right - frame.width() * 0.20f,
            frame.top + frame.height() * 0.97f
        )
        canvas.drawOval(base, fillPaint)
    }

    private fun drawSegment(canvas: Canvas, frame: RectF, start: Joint, end: Joint, width: Float, color: Int) {
        val sx = x(frame, start)
        val sy = y(frame, start)
        val ex = x(frame, end)
        val ey = y(frame, end)

        strokePaint.shader = null
        strokePaint.strokeWidth = width * 1.18f
        strokePaint.color = Color.argb(90, 0, 0, 0)
        canvas.drawLine(sx + width * 0.22f, sy + width * 0.28f, ex + width * 0.22f, ey + width * 0.28f, strokePaint)

        strokePaint.strokeWidth = width
        strokePaint.shader = LinearGradient(sx, sy, ex, ey, lighten(color), darken(color), Shader.TileMode.CLAMP)
        canvas.drawLine(sx, sy, ex, ey, strokePaint)
        strokePaint.shader = null
    }

    private fun drawTorso(canvas: Canvas, frame: RectF, pose: CoachPose, skin: CoachSkin) {
        val path = Path().apply {
            moveTo(x(frame, pose.leftShoulder), y(frame, pose.leftShoulder))
            lineTo(x(frame, pose.rightShoulder), y(frame, pose.rightShoulder))
            lineTo(x(frame, pose.rightHip), y(frame, pose.rightHip))
            lineTo(x(frame, pose.leftHip), y(frame, pose.leftHip))
            close()
        }
        fillPaint.shader = LinearGradient(
            x(frame, pose.leftShoulder),
            y(frame, pose.leftShoulder),
            x(frame, pose.rightHip),
            y(frame, pose.rightHip),
            lighten(skin.outfitColor),
            darken(skin.outfitColor),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(path, fillPaint)
        fillPaint.shader = null

        val waist = Path().apply {
            moveTo(x(frame, pose.leftHip), y(frame, pose.leftHip))
            lineTo(x(frame, pose.rightHip), y(frame, pose.rightHip))
            lineTo(x(frame, pose.rightHip) - frame.width() * 0.026f, y(frame, pose.rightHip) + frame.height() * 0.055f)
            lineTo(x(frame, pose.leftHip) + frame.width() * 0.026f, y(frame, pose.leftHip) + frame.height() * 0.055f)
            close()
        }
        fillPaint.color = darken(skin.outfitColor)
        canvas.drawPath(waist, fillPaint)

        outlinePaint.color = Color.argb(100, 0, 0, 0)
        outlinePaint.strokeWidth = frame.width() * 0.012f
        canvas.drawPath(path, outlinePaint)

        strokePaint.strokeWidth = frame.width() * 0.014f
        strokePaint.color = skin.accentColor
        canvas.drawLine(x(frame, pose.neck), y(frame, pose.neck), x(frame, midpoint(pose.leftHip, pose.rightHip)), y(frame, midpoint(pose.leftHip, pose.rightHip)), strokePaint)
    }

    private fun drawHead(canvas: Canvas, frame: RectF, head: Joint, skin: CoachSkin) {
        val cx = x(frame, head)
        val cy = y(frame, head)
        val radius = frame.width() * 0.054f
        fillPaint.shader = RadialGradient(cx - radius * 0.35f, cy - radius * 0.4f, radius * 1.25f, lighten(skin.bodyColor), darken(skin.bodyColor), Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, fillPaint)
        fillPaint.shader = null

        fillPaint.color = skin.hairColor
        val hair = RectF(cx - radius * 0.9f, cy - radius * 1.0f, cx + radius * 0.9f, cy - radius * 0.15f)
        canvas.drawArc(hair, 185f, 170f, false, fillPaint)
    }

    private fun drawJointHighlights(canvas: Canvas, frame: RectF, pose: CoachPose, skin: CoachSkin) {
        fillPaint.shader = null
        fillPaint.color = Color.argb(205, Color.red(skin.accentColor), Color.green(skin.accentColor), Color.blue(skin.accentColor))
        val radius = frame.width() * 0.018f
        listOf(pose.leftShoulder, pose.rightShoulder, pose.leftHip, pose.rightHip, pose.leftKnee, pose.rightKnee).forEach {
            canvas.drawCircle(x(frame, it), y(frame, it), radius, fillPaint)
        }
    }

    private fun drawCorrectionHighlight(canvas: Canvas, frame: RectF, pose: CoachPose, skin: CoachSkin) {
        val joints = when (avatarCommand.highlight) {
            "knees" -> listOf(pose.leftKnee, pose.rightKnee)
            "hips" -> listOf(pose.leftHip, pose.rightHip)
            "spine" -> listOf(pose.neck, midpoint(pose.leftHip, pose.rightHip))
            "shoulders" -> listOf(pose.leftShoulder, pose.rightShoulder)
            "feet" -> listOf(pose.leftFoot, pose.rightFoot)
            else -> return
        }
        val pulse = ((sin(SystemClock.uptimeMillis() / 180.0) + 1.0) * 0.5).toFloat()
        outlinePaint.color = Color.argb((150 + pulse * 80).toInt(), Color.red(skin.accentColor), Color.green(skin.accentColor), Color.blue(skin.accentColor))
        outlinePaint.strokeWidth = frame.width() * 0.02f
        joints.forEach { joint ->
            val radius = frame.width() * (0.055f + pulse * 0.02f)
            canvas.drawCircle(x(frame, joint), y(frame, joint), radius, outlinePaint)
        }
    }

    private fun applyBreathing(pose: CoachPose): CoachPose {
        if (coachState != CoachState.HOLD && avatarCommand.emotion != "calm") return pose
        val breath = sin(SystemClock.uptimeMillis() / 850.0).toFloat() * 0.008f
        return pose.copy(
            neck = pose.neck.copy(y = pose.neck.y - breath),
            leftShoulder = pose.leftShoulder.copy(y = pose.leftShoulder.y - breath),
            rightShoulder = pose.rightShoulder.copy(y = pose.rightShoulder.y - breath),
            leftElbow = pose.leftElbow.copy(y = pose.leftElbow.y - breath * 0.6f),
            rightElbow = pose.rightElbow.copy(y = pose.rightElbow.y - breath * 0.6f)
        )
    }

    private fun poseIdForAction(action: String): String {
        return when {
            action.contains("forward_fold") || action.contains("knees") -> "forward_fold"
            action.contains("squat") -> "squat"
            action.contains("twist") -> "twist"
            action.contains("bridge") -> "bridge"
            else -> "mountain"
        }
    }

    private fun stateForPhase(phase: String): CoachState {
        return CoachState.values().firstOrNull { it.name.equals(phase, ignoreCase = true) } ?: CoachState.HOLD
    }

    private fun poseFor(poseId: String, detect: String, state: CoachState): CoachPose {
        return when (poseId) {
            "forward_fold" -> forwardFoldPose(detect, state)
            "twist" -> twistPose(detect, state)
            "squat" -> squatPose(detect, state)
            "bridge" -> bridgePose(detect, state)
            else -> mountainPose()
        }
    }

    private fun mountainPose(): CoachPose = CoachPose(
        head = Joint(0.50f, 0.12f),
        neck = Joint(0.50f, 0.21f),
        leftShoulder = Joint(0.36f, 0.27f),
        rightShoulder = Joint(0.64f, 0.27f),
        leftElbow = Joint(0.31f, 0.47f),
        rightElbow = Joint(0.69f, 0.47f),
        leftHand = Joint(0.32f, 0.66f),
        rightHand = Joint(0.68f, 0.66f),
        leftHip = Joint(0.43f, 0.57f),
        rightHip = Joint(0.57f, 0.57f),
        leftKnee = Joint(0.42f, 0.76f),
        rightKnee = Joint(0.58f, 0.76f),
        leftFoot = Joint(0.39f, 0.95f),
        rightFoot = Joint(0.61f, 0.95f)
    )

    private fun forwardFoldPose(detect: String, state: CoachState): CoachPose {
        val depth = when {
            detect == "return_standing" || state == CoachState.TRANSITION -> 0.35f
            detect.contains("hold") || state == CoachState.HOLD -> 1.0f
            state == CoachState.MOVEMENT -> 0.74f
            else -> 0.18f
        }
        val shoulderY = lerp(0.33f, 0.67f, depth)
        val shoulderXOffset = lerp(0.10f, 0.03f, depth)
        val headY = lerp(0.19f, 0.62f, depth)
        return mountainPose().copy(
            head = Joint(0.50f, headY),
            neck = Joint(0.50f, shoulderY - 0.04f),
            leftShoulder = Joint(0.50f - shoulderXOffset, shoulderY),
            rightShoulder = Joint(0.50f + shoulderXOffset, shoulderY),
            leftElbow = Joint(0.42f, lerp(0.49f, 0.76f, depth)),
            rightElbow = Joint(0.58f, lerp(0.49f, 0.76f, depth)),
            leftHand = Joint(0.37f, lerp(0.62f, 0.88f, depth)),
            rightHand = Joint(0.63f, lerp(0.62f, 0.88f, depth)),
            leftHip = Joint(0.43f, 0.58f),
            rightHip = Joint(0.57f, 0.58f)
        )
    }

    private fun twistPose(detect: String, state: CoachState): CoachPose {
        val twist = if (detect == "stable_base" || state == CoachState.SETUP || state == CoachState.TRANSITION) 0f else 1f
        return mountainPose().copy(
            leftShoulder = Joint(lerp(0.40f, 0.33f, twist), 0.33f),
            rightShoulder = Joint(lerp(0.60f, 0.66f, twist), 0.31f),
            leftElbow = Joint(lerp(0.36f, 0.30f, twist), 0.48f),
            rightElbow = Joint(lerp(0.64f, 0.70f, twist), 0.45f),
            leftHand = Joint(lerp(0.36f, 0.42f, twist), 0.62f),
            rightHand = Joint(lerp(0.64f, 0.58f, twist), 0.60f)
        )
    }

    private fun squatPose(detect: String, state: CoachState): CoachPose {
        val depth = if (detect.contains("hold") || state == CoachState.HOLD) 1f else if (state == CoachState.MOVEMENT) 0.72f else 0.12f
        return mountainPose().copy(
            head = Joint(0.50f, lerp(0.18f, 0.31f, depth)),
            neck = Joint(0.50f, lerp(0.28f, 0.41f, depth)),
            leftShoulder = Joint(0.40f, lerp(0.32f, 0.45f, depth)),
            rightShoulder = Joint(0.60f, lerp(0.32f, 0.45f, depth)),
            leftElbow = Joint(0.34f, lerp(0.48f, 0.56f, depth)),
            rightElbow = Joint(0.66f, lerp(0.48f, 0.56f, depth)),
            leftHand = Joint(0.31f, lerp(0.62f, 0.68f, depth)),
            rightHand = Joint(0.69f, lerp(0.62f, 0.68f, depth)),
            leftHip = Joint(0.42f, lerp(0.58f, 0.66f, depth)),
            rightHip = Joint(0.58f, lerp(0.58f, 0.66f, depth)),
            leftKnee = Joint(lerp(0.43f, 0.31f, depth), lerp(0.76f, 0.78f, depth)),
            rightKnee = Joint(lerp(0.57f, 0.69f, depth), lerp(0.76f, 0.78f, depth)),
            leftFoot = Joint(0.32f, 0.92f),
            rightFoot = Joint(0.68f, 0.92f)
        )
    }

    private fun bridgePose(detect: String, state: CoachState): CoachPose {
        val lift = if (detect.contains("hold") || state == CoachState.HOLD) 1f else if (state == CoachState.MOVEMENT) 0.62f else 0.12f
        val hipY = lerp(0.70f, 0.52f, lift)
        return CoachPose(
            head = Joint(0.24f, 0.67f),
            neck = Joint(0.31f, 0.65f),
            leftShoulder = Joint(0.32f, 0.68f),
            rightShoulder = Joint(0.43f, 0.66f),
            leftElbow = Joint(0.30f, 0.78f),
            rightElbow = Joint(0.46f, 0.76f),
            leftHand = Joint(0.24f, 0.82f),
            rightHand = Joint(0.52f, 0.82f),
            leftHip = Joint(0.56f, hipY),
            rightHip = Joint(0.66f, hipY + 0.02f),
            leftKnee = Joint(0.72f, 0.72f),
            rightKnee = Joint(0.80f, 0.73f),
            leftFoot = Joint(0.78f, 0.89f),
            rightFoot = Joint(0.88f, 0.89f)
        )
    }

    private fun x(frame: RectF, joint: Joint): Float = frame.left + joint.x.coerceIn(0f, 1f) * frame.width()
    private fun y(frame: RectF, joint: Joint): Float = frame.top + joint.y.coerceIn(0f, 1f) * frame.height()
    private fun midpoint(a: Joint, b: Joint): Joint = Joint((a.x + b.x) / 2f, (a.y + b.y) / 2f)
    private fun lerp(start: Float, end: Float, amount: Float): Float = start + (end - start) * amount.coerceIn(0f, 1f)

    private fun lighten(color: Int): Int = adjust(color, 1.22f)
    private fun darken(color: Int): Int = adjust(color, 0.72f)

    private fun adjust(color: Int, factor: Float): Int {
        return Color.rgb(
            (Color.red(color) * factor).toInt().coerceIn(0, 255),
            (Color.green(color) * factor).toInt().coerceIn(0, 255),
            (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        )
    }

    private companion object {
        const val PREF_SKIN_ID = "virtualCoach.skinId"
    }
}
