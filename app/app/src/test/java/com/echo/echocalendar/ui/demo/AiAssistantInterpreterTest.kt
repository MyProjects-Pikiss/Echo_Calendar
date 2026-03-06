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

        assertEquals(AiCrudIntent.Create, suggestion.intent)
        assertEquals("09:30", suggestion.timeText)
        assertTrue(suggestion.body.contains("팀 미팅"))
    }

    @Test
    fun suggestInput_detectsDeleteIntent() {
        val suggestion = AiAssistantInterpreter.suggestInput(
            transcript = "팀 미팅 삭제해줘",
            selectedDate = LocalDate.of(2026, 1, 1)
        )

        assertEquals(AiCrudIntent.Delete, suggestion.intent)
    }

    @Test
    fun suggestInput_detectsUpdateIntent() {
        val suggestion = AiAssistantInterpreter.suggestInput(
            transcript = "팀 미팅 시간 3시로 수정해줘",
            selectedDate = LocalDate.of(2026, 1, 1)
        )

        assertEquals(AiCrudIntent.Update, suggestion.intent)
    }

    @Test
    fun suggestSearchQuery_removesSearchVerbs() {
        val suggestion = AiAssistantInterpreter.suggestSearchQuery("회의 일정 검색 찾아줘")

        assertEquals(AiSearchStrategy.Keyword, suggestion.strategy)
        assertEquals("회의 일정", suggestion.query)
    }

    @Test
    fun suggestSearchQuery_extractsLabelFilters() {
        val suggestion = AiAssistantInterpreter.suggestSearchQuery("라벨: 병원, 정기 #검진 검색")

        assertEquals(AiSearchStrategy.Label, suggestion.strategy)
        assertEquals("", suggestion.query)
        assertEquals(listOf("병원", "정기", "검진"), suggestion.labelNames)
    }

    @Test
    fun suggestSearchQuery_extractsDateRangeAndSortOrder() {
        val suggestion = AiAssistantInterpreter.suggestSearchQuery("2026-01-02부터 2026-01-20까지 기록 오래된순으로 찾아줘")

        assertEquals(AiSearchStrategy.DateRange, suggestion.strategy)
        assertEquals("기록", suggestion.query)
        assertEquals("2026-01-02", suggestion.dateFrom)
        assertEquals("2026-01-20", suggestion.dateTo)
        assertEquals("asc", suggestion.sortOrder)
    }

    @Test
    fun suggestSearchQuery_allHistoryMeansAllEvents() {
        val suggestion = AiAssistantInterpreter.suggestSearchQuery("여태까지 한 모든 기록 찾아줘")

        assertEquals(AiSearchStrategy.AllEvents, suggestion.strategy)
        assertEquals("*", suggestion.query)
        assertEquals(null, suggestion.dateFrom)
        assertEquals(null, suggestion.dateTo)
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
    fun assistantService_throwsWhenApiUnavailable() = runBlocking {
        val service = AiAssistantService(
            apiGateway = object : AiApiGateway {
                override suspend fun checkAppUpdate(currentVersionCode: Int) = null
                override suspend fun interpretInput(transcript: String, selectedDate: LocalDate) = null
                override suspend fun interpretSearch(transcript: String) = null
                override suspend fun interpretModify(
                    transcript: String,
                    selectedDate: LocalDate,
                    currentSummary: String,
                    currentTime: String,
                    currentCategoryId: String,
                    currentPlaceText: String,
                    currentBody: String,
                    currentLabels: List<String>
                ) = null
                override suspend fun refineField(
                    transcript: String,
                    field: DraftField,
                    currentValue: String,
                    selectedDate: LocalDate
                ) = null
                override suspend fun usageSignup(username: String, password: String) = null
                override suspend fun usageLogin(username: String, password: String) = null
                override suspend fun myUsage(accessToken: String) = null
            }
        )

        try {
            service.suggestSearch("회의 검색")
            throw AssertionError("AiRemoteException expected")
        } catch (error: AiRemoteException) {
            assertTrue(error.userMessage.isNotBlank())
        }
    }
}
