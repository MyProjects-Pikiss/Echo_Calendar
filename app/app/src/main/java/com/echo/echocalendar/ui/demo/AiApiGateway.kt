package com.echo.echocalendar.ui.demo

import android.util.Log
import com.echo.echocalendar.BuildConfig
import com.echo.echocalendar.data.local.CategoryDefaults
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AiApiGateway {
    suspend fun checkAppUpdate(currentVersionCode: Int): AppUpdateInfo?
    suspend fun interpretInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion?
    suspend fun interpretSearch(transcript: String): AiSearchSuggestion?
    suspend fun interpretModify(
        transcript: String,
        selectedDate: LocalDate,
        currentSummary: String,
        currentTime: String,
        currentCategoryId: String,
        currentPlaceText: String,
        currentBody: String,
        currentLabels: List<String>
    ): AiModifyPatch?
    suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiRefineSuggestion?
    suspend fun usageSignup(username: String, password: String): String?
    suspend fun usageLogin(username: String, password: String): String?
    suspend fun myUsage(accessToken: String): UsageMySummary?
}

class HttpAiApiGateway(
    private val usageAccessTokenProvider: (() -> String?)? = null
) : AiApiGateway {
    override suspend fun checkAppUpdate(currentVersionCode: Int): AppUpdateInfo? {
        val safeCode = maxOf(currentVersionCode, 0)
        val json = getJson("/app/version?currentVersionCode=$safeCode", bearerToken = null) ?: return null
        val latestVersionCode = json.optInt("latestVersionCode", 0)
        if (latestVersionCode <= 0) return null
        return AppUpdateInfo(
            hasUpdate = json.optBoolean("hasUpdate", false),
            required = json.optBoolean("required", false),
            latestVersionCode = latestVersionCode,
            latestVersionName = json.optString("latestVersionName", latestVersionCode.toString()),
            minSupportedVersionCode = json.optInt("minSupportedVersionCode", latestVersionCode),
            apkDownloadUrl = json.optNullableString("apkDownloadUrl")?.trim()?.takeIf { it.isNotBlank() }
        )
    }

    override suspend fun interpretInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion? {
        val request = JSONObject()
            .put("mode", AiMode.Input.value)
            .put("transcript", transcript)
            .put("selectedDate", selectedDate.toString())
        val json = postJson("/ai/input-interpret", request) ?: return null
        if (json.optString("mode") != AiMode.Input.value) return null
        val intent = AiCrudIntent.entries.firstOrNull {
            it.value == json.optString("intent", AiCrudIntent.Create.value).lowercase()
        } ?: AiCrudIntent.Create
        val missingRequired = json.optJSONArray("missingRequired").toStringList()
        return AiInputSuggestion(
            date = LocalDate.parse(json.optString("date", selectedDate.toString())),
            intent = intent,
            summary = json.optString("summary"),
            timeText = json.optString("time", ""),
            repeatYearly = if (json.has("repeatYearly") && !json.isNull("repeatYearly")) {
                json.optBoolean("repeatYearly")
            } else {
                null
            },
            categoryId = normalizeCategoryIdOrNull(json.optString("categoryId", "")) ?: "other",
            placeText = json.optString("placeText", ""),
            body = json.optString("body", transcript),
            labelsText = json.optJSONArray("labels").toStringList().joinToString(", "),
            missingRequired = missingRequired
        )
    }

    override suspend fun interpretSearch(transcript: String): AiSearchSuggestion? {
        val request = JSONObject()
            .put("mode", AiMode.Search.value)
            .put("transcript", transcript)
        val json = postJson("/ai/search-interpret", request) ?: return null
        if (json.optString("mode") != AiMode.Search.value) return null
        val query = json.optString("query").trim()
        val dateFrom = json.optNullableString("dateFrom")
        val dateTo = json.optNullableString("dateTo")
        val categoryIds = json.optJSONArray("categoryIds")
            .toStringList()
            .mapNotNull(::normalizeCategoryIdOrNull)
            .distinct()
        val labelNames = json.optJSONArray("labels")
            .toStringList()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (query.isBlank() && dateFrom.isNullOrBlank() && dateTo.isNullOrBlank() &&
            categoryIds.isEmpty() && labelNames.isEmpty()
        ) return null
        return AiSearchSuggestion(
            query = query,
            dateFrom = dateFrom,
            dateTo = dateTo,
            categoryIds = categoryIds,
            labelNames = labelNames
        )
    }

    override suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiRefineSuggestion? {
        val request = JSONObject()
            .put("mode", AiMode.Refine.value)
            .put("transcript", transcript)
            .put("field", field.value)
            .put("currentValue", currentValue)
            .put("selectedDate", selectedDate.toString())
        val json = postJson("/ai/refine-field", request) ?: return null
        if (json.optString("mode") != AiMode.Refine.value) return null
        val responseField = DraftField.entries.firstOrNull { it.value == json.optString("field") } ?: return null
        val value = json.optString("value").trim()
        if (value.isBlank()) return null
        val normalizedValue = if (responseField == DraftField.Category) {
            normalizeCategoryIdOrNull(value) ?: return null
        } else {
            value
        }
        return AiRefineSuggestion(
            field = responseField,
            value = normalizedValue,
            missingRequired = json.optJSONArray("missingRequired").toStringList()
        )
    }

    override suspend fun interpretModify(
        transcript: String,
        selectedDate: LocalDate,
        currentSummary: String,
        currentTime: String,
        currentCategoryId: String,
        currentPlaceText: String,
        currentBody: String,
        currentLabels: List<String>
    ): AiModifyPatch? {
        val request = JSONObject()
            .put("mode", AiMode.Modify.value)
            .put("transcript", transcript)
            .put("selectedDate", selectedDate.toString())
            .put("currentSummary", currentSummary)
            .put("currentTime", currentTime)
            .put("currentCategoryId", currentCategoryId)
            .put("currentPlaceText", currentPlaceText)
            .put("currentBody", currentBody)
            .put("currentLabels", JSONArray(currentLabels))
        val json = postJson("/ai/modify-interpret", request) ?: return null
        if (json.optString("mode") != AiMode.Modify.value) return null
        val summary = json.optNullableString("summary")?.trim()?.takeIf { it.isNotBlank() }
        val time = json.optNullableString("time")?.trim()?.takeIf { it.isNotBlank() }
        val category = json.optNullableString("categoryId")
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let(::normalizeCategoryIdOrNull)
        val place = json.optNullableString("placeText")?.trim()
        val body = json.optNullableString("body")?.trim()
        val labels = if (json.has("labels") && !json.isNull("labels")) {
            json.optJSONArray("labels").toStringList().joinToString(", ")
        } else {
            null
        }
        return AiModifyPatch(
            summary = summary,
            timeText = time,
            categoryId = category,
            placeText = place,
            body = body,
            labelsText = labels
        )
    }

    override suspend fun usageLogin(username: String, password: String): String? {
        val request = JSONObject()
            .put("username", username.trim())
            .put("password", password)
        val json = postJson("/auth/login", request, includeUsageToken = false) ?: return null
        return json.optString("accessToken").trim().ifBlank { null }
    }

    override suspend fun usageSignup(username: String, password: String): String? {
        val request = JSONObject()
            .put("username", username.trim())
            .put("password", password)
        val json = postJson("/auth/signup", request, includeUsageToken = false) ?: return null
        return json.optString("accessToken").trim().ifBlank { null }
    }

    override suspend fun myUsage(accessToken: String): UsageMySummary? {
        val json = getJson("/usage/me?limit=50", bearerToken = accessToken.trim()) ?: return null
        return UsageMySummary(
            username = json.optString("username", "unknown"),
            requestCount = json.optInt("requestCount", 0),
            totalTokens = json.optInt("totalTokens", 0),
            avgTokensPerRequest = json.optDouble("avgTokensPerRequest", 0.0),
            successRate = json.optDouble("successRate", 0.0)
        )
    }

    private suspend fun postJson(
        path: String,
        body: JSONObject,
        includeUsageToken: Boolean = true
    ): JSONObject? = withContext(Dispatchers.IO) {
        return@withContext requestJson(
            path = path,
            method = "POST",
            body = body,
            bearerToken = resolveBearerToken(includeUsageToken)
        )
    }

    private suspend fun getJson(path: String, bearerToken: String? = null): JSONObject? = withContext(Dispatchers.IO) {
        return@withContext requestJson(
            path = path,
            method = "GET",
            body = null,
            bearerToken = bearerToken
        )
    }

    private fun resolveDefaultBearerToken(): String? {
        val apiKey = BuildConfig.AI_API_KEY.trim()
        if (BuildConfig.AI_SEND_CLIENT_API_KEY && apiKey.isNotBlank()) {
            return apiKey
        }
        return null
    }

    private fun resolveBearerToken(includeUsageToken: Boolean): String? {
        if (includeUsageToken) {
            val usageToken = usageAccessTokenProvider?.invoke()?.trim().orEmpty()
            if (usageToken.isNotBlank()) return usageToken
        }
        return resolveDefaultBearerToken()
    }

    private fun requestJson(
        path: String,
        method: String,
        body: JSONObject?,
        bearerToken: String?
    ): JSONObject? {
        val baseUrl = BuildConfig.AI_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            Log.i("HttpAiApiGateway", "AI_API_BASE_URL is empty.")
            throw IllegalStateException("AI API base URL is not configured")
        }
        val uri = runCatching { URI(baseUrl + path) }.getOrElse {
            throw IllegalStateException("AI API URL is invalid: ${it.message}")
        }
        if (BuildConfig.AI_REQUIRE_HTTPS && !uri.scheme.equals("https", ignoreCase = true)) {
            throw IllegalStateException("AI API base URL must use https in this build type")
        }
        val url = URL(uri.toString())
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = BuildConfig.AI_API_TIMEOUT_MS
            readTimeout = BuildConfig.AI_API_TIMEOUT_MS
            doInput = true
            setRequestProperty("Content-Type", "application/json")
            if (method == "POST") {
                doOutput = true
            }
            if (!bearerToken.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $bearerToken")
            } else {
                val apiKey = BuildConfig.AI_API_KEY.trim()
                if (!BuildConfig.AI_SEND_CLIENT_API_KEY && apiKey.isNotBlank()) {
                    Log.i("HttpAiApiGateway", "Client API key is configured but disabled for this build type.")
                }
            }
        }
        return try {
            if (method == "POST" && body != null) {
                OutputStreamWriter(connection.outputStream).use { writer ->
                    writer.write(body.toString())
                }
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                val errorJson = runCatching { JSONObject(responseText) }.getOrNull()
                val serverErrorCode = errorJson
                    ?.optString("errorCode")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val serverMessage = errorJson
                    ?.optString("message")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                throw AiApiException(
                    statusCode = responseCode,
                    errorCode = serverErrorCode,
                    serverMessage = serverMessage,
                    responsePreview = responseText.take(200)
                )
            }
            if (responseText.isBlank()) {
                throw IllegalStateException("AI API returned empty body")
            }
            JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }
}

class AiApiException(
    val statusCode: Int,
    val errorCode: String?,
    val serverMessage: String?,
    responsePreview: String
) : IllegalStateException(
    buildString {
        append("AI API request failed (")
        append(statusCode)
        append(")")
        errorCode?.let {
            append(" code=")
            append(it)
        }
        if (responsePreview.isNotBlank()) {
            append(": ")
            append(responsePreview)
        }
    }
)

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}

private fun JSONObject.optNullableString(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key)
}

private fun normalizeCategoryIdOrNull(raw: String): String? {
    val normalized = raw.trim()
    if (normalized.isBlank()) return null
    val category = CategoryDefaults.categories.firstOrNull { candidate ->
        candidate.id.equals(normalized, ignoreCase = true) ||
            candidate.displayName.equals(normalized, ignoreCase = true)
    }
    return category?.id
}
