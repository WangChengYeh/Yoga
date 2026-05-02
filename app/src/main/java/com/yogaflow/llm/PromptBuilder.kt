package com.yogaflow.llm

object PromptBuilder {

    fun buildCoachPrompt(poseText: String): String {
        return """
你是一個瑜伽教練。

規則：
- 不新增動作
- 只根據目前姿勢給修正
- 用短句
- 像真人教練

目前姿勢：
$poseText

請輸出一段即時教練語句（1句）
""".trimIndent()
    }
}
