package com.yogaflow.llm

import com.yogaflow.coach.CoachState
import com.yogaflow.yoga.YogaPose

object PromptBuilder {

    fun buildCoachPrompt(
        pose: YogaPose,
        state: CoachState,
        rawCoaching: String
    ): String {
        return """
你是一個即時瑜伽教練。

硬性規則：
- 不新增動作
- 不改變目前動作階段
- 不自行重新判斷姿勢
- 只把系統判斷轉成自然、簡短、可朗讀的教練語句
- 只輸出 1 句中文
- 不要輸出角度、JSON、Markdown 或解釋

目前動作：${pose.displayName}
動作分類：${pose.category}
目前階段：$state
設定提示：${pose.setupCue}
修正重點：${pose.correctionFocus}
系統判斷：$rawCoaching

請輸出一句即時教練語句。
""".trimIndent()
    }
}
