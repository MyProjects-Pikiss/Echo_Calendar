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
        return requestRemote("input") {
            apiGateway.interpretInput(transcript, selectedDate)
        }
    }

    suspend fun suggestSearch(transcript: String): AiSuggestionResult<AiSearchSuggestion> {
        return requestRemote("search") {
            apiGateway.interpretSearch(transcript)
        }
    }

    suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiSuggestionResult<AiRefineSuggestion> {
        return requestRemote("refine.${field.value}") {
            apiGateway.refineField(transcript, field, currentValue, selectedDate)
        }
    }

    suspend fun suggestModifyPatch(
        transcript: String,
        selectedDate: LocalDate,
        currentSummary: String,
        currentTimeText: String,
        currentCategoryId: String,
        currentPlaceText: String,
        currentBody: String,
        currentLabelsText: String,
        currentRawText: String?
    ): AiSuggestionResult<AiModifyPatch> {
        val currentLabels = currentLabelsText.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        return requestRemote("modify") {
            apiGateway.interpretModify(
                transcript = transcript,
                selectedDate = selectedDate,
                currentSummary = currentSummary,
                currentTime = currentTimeText,
                currentCategoryId = currentCategoryId,
                currentPlaceText = currentPlaceText,
                currentBody = currentBody,
                currentLabels = currentLabels
            )
        }
    }

    suspend fun loginUsage(username: String, password: String): String {
        val remote = runCatching { apiGateway.usageLogin(username, password) }
        val token = remote.getOrNull()
        if (!token.isNullOrBlank()) return token
        val failure = remote.exceptionOrNull()
        val reason = normalizeFailureReason(failure)
        logRemoteFailure("usage.login", reason)
        throw AiRemoteException(
            userMessage = toUserMessage("usage.login", reason, failure),
            reason = reason,
            cause = failure
        )
    }

    suspend fun signupUsage(username: String, password: String): String {
        val remote = runCatching { apiGateway.usageSignup(username, password) }
        val token = remote.getOrNull()
        if (!token.isNullOrBlank()) return token
        val failure = remote.exceptionOrNull()
        val reason = normalizeFailureReason(failure)
        logRemoteFailure("usage.signup", reason)
        throw AiRemoteException(
            userMessage = toUserMessage("usage.signup", reason, failure),
            reason = reason,
            cause = failure
        )
    }

    suspend fun fetchMyUsage(accessToken: String): UsageMySummary {
        val remote = runCatching { apiGateway.myUsage(accessToken) }
        val summary = remote.getOrNull()
        if (summary != null) return summary
        val failure = remote.exceptionOrNull()
        val reason = normalizeFailureReason(failure)
        logRemoteFailure("usage.me", reason)
        throw AiRemoteException(
            userMessage = toUserMessage("usage.me", reason, failure),
            reason = reason,
            cause = failure
        )
    }

    private fun logRemoteSuccess(action: String) {
        safeLog { Log.i(TAG, "remote_success action=$action") }
    }

    private fun logRemoteFailure(action: String, reason: String) {
        safeLog { Log.w(TAG, "remote_failure action=$action reason=$reason") }
    }

    private inline fun safeLog(block: () -> Unit) {
        runCatching(block)
    }

    private suspend fun <T> requestRemote(action: String, call: suspend () -> T?): AiSuggestionResult<T> {
        val remote = runCatching { call() }
        val value = remote.getOrNull()
        if (value != null) {
            logRemoteSuccess(action)
            return AiSuggestionResult(
                suggestion = value,
                source = AiSuggestionSource.Remote
            )
        }
        val failure = remote.exceptionOrNull()
        val reason = normalizeFailureReason(failure)
        logRemoteFailure(action, reason)
        throw AiRemoteException(
            userMessage = toUserMessage(action, reason, failure),
            reason = reason,
            cause = failure
        )
    }

    private fun normalizeFailureReason(error: Throwable?): String {
        if (error == null) return "remote_empty_response"
        return when (error) {
            is AiApiException -> when {
                error.statusCode >= 500 -> "http_5xx"
                error.statusCode >= 400 -> "http_4xx"
                else -> "http_error"
            }
            is SocketTimeoutException,
            is InterruptedIOException -> "timeout"
            is JSONException -> "json_parse"
            else -> {
                val message = error.message.orEmpty().lowercase()
                when {
                    "base url is not configured" in message -> "url_not_configured"
                    "url is invalid" in message -> "invalid_url"
                    "must use https" in message -> "https_required"
                    "empty body" in message -> "empty_body"
                    "failed (" in message -> {
                        val code = Regex("""\((\d{3})\)""")
                            .find(message)
                            ?.groupValues
                            ?.getOrNull(1)
                            ?.toIntOrNull()
                        when {
                            code == null -> "http_error"
                            code >= 500 -> "http_5xx"
                            else -> "http_4xx"
                        }
                    }
                    "mode" in message -> "mode_mismatch"
                    else -> "gateway_failure"
                }
            }
        }
    }

    private fun toUserMessage(action: String, reason: String, error: Throwable?): String {
        val isAuthAction = action == "usage.login" || action == "usage.signup" || action == "usage.me"
        if (error is AiApiException) {
            val serverMessage = error.serverMessage?.trim().orEmpty()
            if (serverMessage.isNotBlank()) return serverMessage
        }
        return when (reason) {
            "timeout" -> if (isAuthAction) {
                "로그인 서버 응답이 지연되고 있어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
            } else {
                "AI 서버 응답이 지연되고 있어요. 인터넷 연결을 확인하고 다시 시도해 주세요."
            }
            "url_not_configured", "invalid_url", "https_required" -> if (isAuthAction) {
                "로그인 서버 주소 설정에 문제가 있어요. 관리자 설정을 확인해 주세요."
            } else {
                "AI 서버 주소 설정에 문제가 있어요. 관리자 설정을 확인해 주세요."
            }
            "http_4xx" -> if (isAuthAction) {
                "요청이 거절되었어요. 계정 정보를 확인해 주세요."
            } else {
                "요청이 거절되었어요. 계정 정보 또는 서버 설정을 확인해 주세요."
            }
            "http_5xx", "http_error", "gateway_failure", "empty_body", "json_parse", "mode_mismatch", "remote_empty_response" ->
                if (isAuthAction) {
                    "로그인 서버 연결에 실패했어요. 잠시 후 다시 시도해 주세요."
                } else {
                    "AI 서버 연결에 실패했어요. 잠시 후 다시 시도해 주세요."
                }
            else -> if (isAuthAction) {
                "로그인 기능을 사용할 수 없어요. 인터넷 연결 또는 서버 상태를 확인해 주세요."
            } else {
                "AI 기능을 사용할 수 없어요. 인터넷 연결 또는 서버 상태를 확인해 주세요."
            }
        }
    }

    companion object {
        private const val TAG = "AiAssistantService"
    }
}

data class AiSuggestionResult<T>(
    val suggestion: T,
    val source: AiSuggestionSource
)

enum class AiSuggestionSource {
    Remote
}

class AiRemoteException(
    val userMessage: String,
    val reason: String,
    cause: Throwable? = null
) : Exception(reason, cause)
