package com.echo.echocalendar.ui.demo

import java.time.LocalDate

class AiAssistantService(
    private val apiGateway: AiApiGateway
) {
    suspend fun suggestInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion {
        return apiGateway.interpretInput(transcript, selectedDate)
            ?: AiAssistantInterpreter.suggestInput(transcript, selectedDate)
    }

    suspend fun suggestSearch(transcript: String): AiSearchSuggestion {
        return apiGateway.interpretSearch(transcript)
            ?: AiAssistantInterpreter.suggestSearchQuery(transcript)
    }

    suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiRefineSuggestion {
        return apiGateway.refineField(transcript, field, currentValue, selectedDate)
            ?: AiAssistantInterpreter.refineField(
                field = field,
                transcript = transcript,
                currentValue = currentValue,
                selectedDate = selectedDate
            )
    }
}
