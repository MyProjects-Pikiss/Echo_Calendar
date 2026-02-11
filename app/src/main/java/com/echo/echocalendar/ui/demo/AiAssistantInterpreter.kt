package com.echo.echocalendar.ui.demo

import com.echo.echocalendar.data.local.CategoryDefaults
import java.time.LocalDate

data class AiInputSuggestion(
    val date: LocalDate,
    val summary: String,
    val timeText: String,
    val categoryId: String,
    val placeText: String,
    val body: String,
    val labelsText: String,
    val missingRequired: List<String>
)

data class AiSearchSuggestion(
    val query: String
)

object AiAssistantInterpreter {
    fun suggestInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion {
        val normalized = transcript.trim()
        val timeText = extractTimeText(normalized).orEmpty()
        val summary = extractSummary(normalized)
        val categoryId = extractCategoryId(normalized)
        val placeText = extractPlaceText(normalized).orEmpty()
        val labelsText = extractLabels(normalized).joinToString(", ")
        val missingRequired = buildList {
            if (summary.isBlank()) add("제목")
            if (timeText.isBlank()) add("시간")
            if (normalized.isBlank()) add("내용")
        }

        return AiInputSuggestion(
            date = selectedDate,
            summary = summary,
            timeText = timeText,
            categoryId = categoryId,
            placeText = placeText,
            body = normalized,
            labelsText = labelsText,
            missingRequired = missingRequired
        )
    }

    fun suggestSearchQuery(transcript: String): AiSearchSuggestion {
        val cleaned = transcript
            .replace("검색", "")
            .replace("찾아줘", "")
            .replace("찾아 줘", "")
            .replace("보여줘", "")
            .replace("보여 줘", "")
            .trim()
        return AiSearchSuggestion(query = cleaned.ifBlank { transcript.trim() })
    }

    private fun extractTimeText(source: String): String? {
        val colonMatch = Regex("""\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b""").find(source)
        if (colonMatch != null) {
            return colonMatch.value
        }
        val hourMinuteMatch = Regex("""([01]?\\d|2[0-3])\\s*시(?:\\s*([0-5]?\\d)\\s*분?)?""").find(source)
        if (hourMinuteMatch != null) {
            val hour = hourMinuteMatch.groupValues[1].padStart(2, '0')
            val minute = hourMinuteMatch.groupValues[2].ifBlank { "00" }.padStart(2, '0')
            return "$hour:$minute"
        }
        return null
    }

    private fun extractSummary(source: String): String {
        if (source.isBlank()) return ""
        val explicit = Regex("""제목(?:은|:)?\\s*(.+)""").find(source)?.groupValues?.getOrNull(1)?.trim()
        if (!explicit.isNullOrBlank()) {
            return explicit.take(40)
        }
        return source
            .replace(Regex("""\\b([01]?\\d|2[0-3]):([0-5]\\d)\\b"""), "")
            .replace(Regex("""([01]?\\d|2[0-3])\\s*시(?:\\s*([0-5]?\\d)\\s*분?)?"""), "")
            .trim()
            .take(40)
    }

    private fun extractCategoryId(source: String): String {
        val category = CategoryDefaults.categories.firstOrNull { category ->
            source.contains(category.displayName, ignoreCase = true) ||
                source.contains(category.id, ignoreCase = true)
        }
        return category?.id ?: CategoryDefaults.categories.first().id
    }

    private fun extractPlaceText(source: String): String? {
        val match = Regex("""(?:장소|위치)(?:는|:)?\\s*([^,\\n]+)""").find(source)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractLabels(source: String): List<String> {
        val match = Regex("""(?:라벨|태그)(?:은|:)?\\s*([^\\n]+)""").find(source)
        return match?.groupValues
            ?.getOrNull(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
    }
}

