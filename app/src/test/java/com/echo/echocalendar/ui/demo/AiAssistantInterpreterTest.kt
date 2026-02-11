package com.echo.echocalendar.ui.demo

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
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

    @Test
    fun refineField_prefersExtractedTime() {
        val refined = AiAssistantInterpreter.refineField(
            field = DraftField.Time,
            transcript = "3시 15분으로 바꿔줘",
            currentValue = "09:00",
            selectedDate = LocalDate.of(2026, 1, 1)
        )

        assertEquals("03:15", refined.value)
    }

    @Test
    fun assistantService_fallsBackWhenApiUnavailable() = runBlocking {
        val service = AiAssistantService(
            apiGateway = object : AiApiGateway {
                override suspend fun interpretInput(transcript: String, selectedDate: LocalDate) = null
                override suspend fun interpretSearch(transcript: String) = null
                override suspend fun refineField(
                    transcript: String,
                    field: DraftField,
                    currentValue: String,
                    selectedDate: LocalDate
                ) = null
            }
        )

        val suggestion = service.suggestSearch("회의 검색")
        assertEquals("회의", suggestion.query)
    }
}
