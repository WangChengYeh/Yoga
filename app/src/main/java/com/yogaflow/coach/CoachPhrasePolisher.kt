package com.yogaflow.coach

object CoachPhrasePolisher {

    fun polish(text: String): String {
        val clean = text
            .replace("(fallback)", "")
            .trim()

        return when {
            clean.contains("準備") -> "準備好了，先穩定身體，跟著我的節奏開始。"
            clean.contains("髖部") || clean.contains("前折") -> "很好，吐氣時從髖部慢慢往前折，不要急。"
            clean.contains("保持") || clean.contains("呼吸") -> "維持在這裡，放鬆肩膀，慢慢呼吸。"
            clean.contains("回到") || clean.contains("回來") -> "現在慢慢回來，保持穩定，不要突然起身。"
            clean.contains("完成") -> "很好，這一段完成了，準備進入下一個動作。"
            else -> clean.ifBlank { "很好，維持呼吸。" }
        }
    }
}
