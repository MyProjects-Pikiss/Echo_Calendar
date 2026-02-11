package com.echo.echocalendar.ui.demo

import android.util.Log
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import org.json.JSONException

class AiAssistantService(
    private val apiGateway: AiApiGateway
) {
    suspend fun suggestInput(transcript: String, selectedDate: LocalDate): AiSuggestionResult<AiInputSuggestion> {
        val remote = runCatching {
            apiGateway.interpretInput(transcript, selectedDate)
        }
        val remoteValue = remote.getOrNull()
        if (remoteValue != null) {
            logRemoteSuccess("input")
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }

        val reason = normalizeFallbackReason(remote.exceptionOrNull())
        logRemoteFailure("input", reason)
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.suggestInput(transcript, selectedDate),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = reason
        )
    }

    suspend fun suggestSearch(transcript: String): AiSuggestionResult<AiSearchSuggestion> {
        val remote = runCatching {
            apiGateway.interpretSearch(transcript)
        }
        val remoteValue = remote.getOrNull()
        if (remoteValue != null) {
            logRemoteSuccess("search")
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }

        val reason = normalizeFallbackReason(remote.exceptionOrNull())
        logRemoteFailure("search", reason)
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.suggestSearchQuery(transcript),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = reason
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
            logRemoteSuccess("refine.${field.value}")
            return AiSuggestionResult(
                suggestion = remoteValue,
                source = AiSuggestionSource.Remote
            )
        }

        val reason = normalizeFallbackReason(remote.exceptionOrNull())
        logRemoteFailure("refine.${field.value}", reason)
        return AiSuggestionResult(
            suggestion = AiAssistantInterpreter.refineField(
                field = field,
                transcript = transcript,
                currentValue = currentValue,
                selectedDate = selectedDate
            ),
            source = AiSuggestionSource.LocalFallback,
            fallbackReason = reason
        )
    }

    private fun logRemoteSuccess(action: String) {
        Log.i(TAG, "remote_success action=$action")
    }

    private fun logRemoteFailure(action: String, reason: String) {
        Log.w(TAG, "remote_failure_fallback action=$action reason=$reason")
    }

    private fun normalizeFallbackReason(error: Throwable?): String {
        if (error == null) return "remote_empty_response"
        return when (error) {
            is SocketTimeoutException,
            is InterruptedIOException -> "timeout"
            is JSONException -> "json_parse"
            else -> {
                val message = error.message.orEmpty()
                when {
                    "empty body" in message -> "empty_body"
                    "failed (" in message -> "http_error"
                    "mode" in message -> "mode_mismatch"
                    else -> "gateway_failure"
                }
            }
        }
    }

    companion object {
        private const val TAG = "AiAssistantService"
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
