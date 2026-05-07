package com.yogaflow.coach

import org.junit.Assert.assertFalse
import org.junit.Test

class CoachPhrasePolisherTest {

    @Test
    fun polish_nonEmptyInput_returnsNonEmptyString() {
        assertFalse(CoachPhrasePolisher.polish("Keep breathing").isBlank())
    }

    @Test
    fun polish_doesNotAddAngleOrDegreeSymbols() {
        val polished = CoachPhrasePolisher.polish("保持呼吸")

        assertFalse(polished.contains("°"))
        assertFalse(polished.contains("度"))
        assertFalse(polished.contains("degrees", ignoreCase = true))
    }

    @Test
    fun polish_doesNotAddMarkdown() {
        val polished = CoachPhrasePolisher.polish("Relax shoulders")

        assertFalse(polished.contains("**"))
        assertFalse(polished.contains("#"))
        assertFalse(polished.contains("- "))
        assertFalse(polished.contains("```"))
    }

    @Test
    fun polish_handlesChineseAndAsciiInputWithoutThrowing() {
        val polished = CoachPhrasePolisher.polish("準備 start with steady breath")

        assertFalse(polished.isBlank())
    }
}
