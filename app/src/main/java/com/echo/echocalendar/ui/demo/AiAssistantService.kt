package com.echo.echocalendar.ui.demo

import java.time.LocalDate

class AiAssistantService(
    private val apiGateway: AiApiGateway
) {
    suspend fun suggestInput(transcript: String, selectedDate: LocalDate): AiSuggestionResult<AiInputSuggestion> {
        val remote = runCatching {
            apiGateway.interpretInput(transcript, selectedDate)
        }
        val remoteValue = remote.getOrNull()
        if (remoteValue != null) {
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.suggestInput(transcript, selectedDate),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = remote.exceptionOrNull()?.message
        )
    }

    suspend fun suggestSearch(transcript: String): AiSuggestionResult<AiSearchSuggestion> {
        val remote = runCatching {
            apiGateway.interpretSearch(transcript)
        }
        val remoteValue = remote.getOrNull()
        if (remoteValue != null) {
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.suggestSearchQuery(transcript),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = remote.exceptionOrNull()?.message
        )
    }

    suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiSuggestionResult<AiRefineSuggestion> {
        val remote = runCatching {
            apiGateway.refineField(transcript, field, currentValue, selectedDate)
        }
        val remoteValue = remote.getOrNull()
        if (remoteValue != null) {
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.refineField(
                field = field,
                transcript = transcript,
                currentValue = currentValue,
                selectedDate = selectedDate
            ),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = remote.exceptionOrNull()?.message
        )
    }
}

data class AiSuggestionResult<T>(
    val suggestion: T,
    val source: AiSuggestionSource,
    val fallbackReason: String? = null
)

enum class AiSuggestionSource {
    Remote,
    LocalFallback
}
