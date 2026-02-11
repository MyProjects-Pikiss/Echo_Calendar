package com.echo.echocalendar.ui.demo

import android.util.Log
import com.echo.echocalendar.BuildConfig
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

interface AiApiGateway {
    suspend fun interpretInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion?
    suspend fun interpretSearch(transcript: String): AiSearchSuggestion?
    suspend fun refineField(
        transcript: String,
        field: DraftField,
        currentValue: String,
        selectedDate: LocalDate
    ): AiRefineSuggestion?
}

class HttpAiApiGateway : AiApiGateway {
    override suspend fun interpretInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion? {
        val request = JSONObject()
            .put("mode", AiMode.Input.value)
            .put("transcript", transcript)
            .put("selectedDate", selectedDate.toString())
        val json = postJson("/ai/input-interpret", request) ?: return null
        if (json.optString("mode") != AiMode.Input.value) return null
        val missingRequired = json.optJSONArray("missingRequired").toStringList()
        return AiInputSuggestion(
            date = LocalDate.parse(json.optString("date", selectedDate.toString())),
            summary = json.optString("summary"),
            timeText = json.optString("time", ""),
            categoryId = json.optString("categoryId", ""),
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
        if (query.isBlank()) return null
        return AiSearchSuggestion(
            query = query,
            dateFrom = json.optString("dateFrom", null),
            dateTo = json.optString("dateTo", null),
            categoryIds = json.optJSONArray("categoryIds").toStringList()
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
        return AiRefineSuggestion(
            field = responseField,
            value = value,
            missingRequired = json.optJSONArray("missingRequired").toStringList()
        )
    }

    private suspend fun postJson(path: String, body: JSONObject): JSONObject? = withContext(Dispatchers.IO) {
        val baseUrl = BuildConfig.AI_API_BASE_URL.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            Log.i("HttpAiApiGateway", "AI_API_BASE_URL is empty. Falling back to local interpreter.")
            throw IllegalStateException("AI API base URL is not configured")
        }
        val url = URL(baseUrl + path)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = BuildConfig.AI_API_TIMEOUT_MS
            readTimeout = BuildConfig.AI_API_TIMEOUT_MS
            doInput = true
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            val apiKey = BuildConfig.AI_API_KEY.trim()
            if (apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
        }
        return@withContext try {
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(body.toString())
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (responseCode !in 200..299) {
                throw IllegalStateException("AI API request failed ($responseCode): ${responseText.take(200)}")
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

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index).trim()
            if (value.isNotBlank()) add(value)
        }
    }
}
