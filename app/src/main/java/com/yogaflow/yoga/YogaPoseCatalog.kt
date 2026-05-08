package com.yogaflow.yoga

object YogaPoseCatalog {

    val poses = listOf(
        YogaPose(
            id = "forward_fold",
            displayName = "Forward Fold",
            category = "Standing",
            setupCue = "雙腳打開，膝蓋伸直",
            correctionFocus = "髖部折疊"
        ),
        YogaPose(
            id = "twist",
            displayName = "Supine Twist",
            category = "Cooldown",
            setupCue = "躺平，雙肩貼地，準備扭轉",
            correctionFocus = "肩膀貼地與安全扭轉"
        ),
        YogaPose(
            id = "mountain",
            displayName = "Mountain Pose",
            category = "Standing",
            setupCue = "雙腳併攏，站直",
            correctionFocus = "脊椎延伸"
        ),
        YogaPose(
            id = "squat",
            displayName = "Yoga Squat",
            category = "Balance",
            setupCue = "下蹲，腳跟穩定",
            correctionFocus = "膝蓋方向"
        ),
        YogaPose(
            id = "bridge",
            displayName = "Bridge Pose",
            category = "Backbend",
            setupCue = "躺下，雙腳踩地，準備抬起臀部",
            correctionFocus = "骨盆抬起與腰部舒適"
        ),
        YogaPose(
            id = "warrior_2",
            displayName = "Warrior II",
            category = "Standing",
            setupCue = "雙腳打開，前腳朝前，後腳外開",
            correctionFocus = "前膝方向與手臂延伸"
        ),
        YogaPose(
            id = "downward_dog",
            displayName = "Downward Dog",
            category = "Inversion",
            setupCue = "雙手推地，臀部向上，形成倒 V",
            correctionFocus = "腿後側延伸與手臂穩定"
        )
    )
}
