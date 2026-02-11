package com.echo.echocalendar.ui.demo

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantInterpreterTest {
    @Test
    fun suggestInput_extractsTimeAndBody() {
        val suggestion = AiAssistantInterpreter.suggestInput(
            transcript = "내일 9시 30분 팀 미팅 준비",
            selectedDate = LocalDate.of(2026, 1, 1)
        )

        assertEquals("09:30", suggestion.timeText)
        assertTrue(suggestion.body.contains("팀 미팅"))
    }

    @Test
    fun suggestSearchQuery_removesSearchVerbs() {
        val suggestion = AiAssistantInterpreter.suggestSearchQuery("회의 일정 검색 찾아줘")

        assertEquals("회의 일정", suggestion.query)
    }
}
