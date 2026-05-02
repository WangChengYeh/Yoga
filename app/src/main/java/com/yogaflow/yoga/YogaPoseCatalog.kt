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
        )
    )
}
