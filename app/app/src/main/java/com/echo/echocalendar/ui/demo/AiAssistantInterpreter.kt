package com.echo.echocalendar.ui.demo

import com.echo.echocalendar.data.local.CategoryDefaults
import java.time.LocalDate
import java.time.ZoneId

object AiAssistantInterpreter {
    fun suggestInput(transcript: String, selectedDate: LocalDate): AiInputSuggestion {
        val normalized = transcript.trim()
        val intent = extractCrudIntent(normalized)
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
            intent = intent,
            summary = summary,
            timeText = timeText,
            repeatYearly = detectYearlyRecurring(normalized),
            categoryId = categoryId,
            placeText = placeText,
            body = normalized,
            labelsText = labelsText,
            missingRequired = missingRequired
        )
    }

    fun suggestSearchQuery(transcript: String): AiSearchSuggestion {
        val wantsAllRecords = containsAllRecordsIntent(transcript)
        val dateRange = extractDateRange(transcript)
        val sortOrder = extractSortOrder(transcript)
        val labelNames = extractLabels(transcript)
        val categorySource = transcript
            .replace(Regex("""(?:라벨|태그)(?:은|:)?\s*[^\n]+"""), " ")
            .replace(Regex("""#([\p{L}\p{N}_-]+)"""), " ")
        val categoryIds = extractSearchCategoryIds(categorySource)
        val cleaned = transcript
            .replace(Regex("""(?:라벨|태그)(?:은|:)?\s*[^\n]+"""), " ")
            .replace(Regex("""\d{4}[./-]\d{1,2}[./-]\d{1,2}\s*(?:부터|에서)\s*\d{4}[./-]\d{1,2}[./-]\d{1,2}\s*까지"""), " ")
            .replace(Regex("""\d{1,2}일\s*(?:부터|에서)\s*\d{1,2}일\s*까지"""), " ")
            .replace(Regex("""(?:여태까지|지금까지|전체|전부|모든)\s*(?:기록|일정)?"""), " ")
            .replace(Regex("""(?:최신순|최근순|오래된순|오름차순|내림차순)(?:으로|로)?"""), " ")
            .replace("검색", "")
            .replace("찾아줘", "")
            .replace("찾아 줘", "")
            .replace("보여줘", "")
            .replace("보여 줘", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
        val query = if (wantsAllRecords) {
            "*"
        } else if (cleaned.isNotBlank()) {
            cleaned
        } else if (labelNames.isNotEmpty()) {
            ""
        } else {
            transcript.trim()
        }
        val strategy = inferSearchStrategy(
            forcedAll = wantsAllRecords,
            query = query,
            dateFrom = dateRange.first,
            dateTo = dateRange.second,
            categoryIds = categoryIds,
            labels = labelNames
        )
        return AiSearchSuggestion(
            strategy = strategy,
            query = query,
            dateFrom = dateRange.first,
            dateTo = dateRange.second,
            sortOrder = sortOrder,
            categoryIds = categoryIds,
            labelNames = labelNames
        )
    }

    fun refineField(
        field: DraftField,
        transcript: String,
        currentValue: String,
        selectedDate: LocalDate
    ): AiRefineSuggestion {
        val inputSuggestion = suggestInput(transcript, selectedDate)
        val value = when (field) {
            DraftField.Summary -> inputSuggestion.summary.ifBlank { currentValue }
            DraftField.Time -> inputSuggestion.timeText.ifBlank { currentValue }
            DraftField.Category -> inputSuggestion.categoryId.ifBlank { currentValue }
            DraftField.Place -> inputSuggestion.placeText.ifBlank { currentValue }
            DraftField.Labels -> inputSuggestion.labelsText.ifBlank { currentValue }
            DraftField.Body -> inputSuggestion.body.ifBlank { currentValue }
        }
        return AiRefineSuggestion(field = field, value = value, missingRequired = inputSuggestion.missingRequired)
    }

    private fun extractTimeText(source: String): String? {
        val colonMatch = Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b""").find(source)
        if (colonMatch != null) {
            return colonMatch.value
        }
        val hourMinuteMatch = Regex("""([01]?\d|2[0-3])\s*시(?:\s*([0-5]?\d)\s*분?)?""").find(source)
        if (hourMinuteMatch != null) {
            val hour = hourMinuteMatch.groupValues[1].padStart(2, '0')
            val minute = hourMinuteMatch.groupValues[2].ifBlank { "00" }.padStart(2, '0')
            return "$hour:$minute"
        }
        return null
    }

    private fun extractSummary(source: String): String {
        if (source.isBlank()) return ""
        val explicit = Regex("""제목(?:은|:)?\s*(.+)""").find(source)?.groupValues?.getOrNull(1)?.trim()
        if (!explicit.isNullOrBlank()) {
            return explicit.take(40)
        }
        return source
            .replace(Regex("""(삭제|지워|지워줘|취소|수정|바꿔|변경|고쳐)(해줘|해 줘)?"""), " ")
            .replace(Regex("""\b([01]?\d|2[0-3]):([0-5]\d)\b"""), "")
            .replace(Regex("""([01]?\d|2[0-3])\s*시(?:\s*([0-5]?\d)\s*분?)?"""), "")
            .trim()
            .take(40)
    }

    private fun extractCrudIntent(source: String): AiCrudIntent {
        val normalized = source.lowercase()
        return when {
            normalized.contains("삭제") ||
                normalized.contains("지워") ||
                normalized.contains("취소") -> AiCrudIntent.Delete
            normalized.contains("수정") ||
                normalized.contains("바꿔") ||
                normalized.contains("변경") ||
                normalized.contains("고쳐") -> AiCrudIntent.Update
            else -> AiCrudIntent.Create
        }
    }

    private fun detectYearlyRecurring(source: String): Boolean? {
        val normalized = source.lowercase()
        val yearlyTokens = listOf("생일", "매년", "해마다", "매 해", "anniversary", "birthday")
        return if (yearlyTokens.any { normalized.contains(it) }) true else null
    }

    private fun extractCategoryId(source: String): String {
        val category = CategoryDefaults.categories.firstOrNull { category ->
            source.contains(category.displayName, ignoreCase = true) ||
                source.contains(category.id, ignoreCase = true)
        }
        return category?.id ?: "other"
    }

    private fun extractPlaceText(source: String): String? {
        val match = Regex("""(?:장소|위치)(?:는|:)?\s*([^,\n]+)""").find(source)
        return match?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractLabels(source: String): List<String> {
        val match = Regex("""(?:라벨|태그)(?:은|:)?\s*([^\n]+)""").find(source)
        val inlineLabels = match?.groupValues
            ?.getOrNull(1)
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            .orEmpty()
        val hashtagLabels = Regex("""#([\p{L}\p{N}_-]+)""")
            .findAll(source)
            .map { it.groupValues[1].trim() }
            .filter { it.isNotBlank() }
            .toList()
        return (inlineLabels + hashtagLabels).distinct()
    }

    private fun extractSearchCategoryIds(source: String): List<String> {
        return CategoryDefaults.categories
            .filter { category ->
                category.id !in setOf("record", "other") &&
                    (
                        source.contains(category.displayName, ignoreCase = true) ||
                            source.contains(category.id, ignoreCase = true)
                        )
            }
            .map { it.id }
            .distinct()
    }

    private fun extractDateRange(source: String): Pair<String?, String?> {
        val text = source.trim()
        if (containsAllRecordsIntent(text)) {
            return null to null
        }

        val explicit = Regex(
            """(\d{4}[./-]\d{1,2}[./-]\d{1,2})\s*(?:부터|에서)\s*(\d{4}[./-]\d{1,2}[./-]\d{1,2})\s*까지"""
        ).find(text)
        if (explicit != null) {
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val from = normalizeDateToken(explicit.groupValues[1], today) ?: return null to null
            val to = normalizeDateToken(explicit.groupValues[2], today) ?: return null to null
            return if (from <= to) Pair(from, to) else Pair(to, from)
        }

        val dayOnly = Regex("""(\d{1,2})일\s*(?:부터|에서)\s*(\d{1,2})일\s*까지""").find(text)
        if (dayOnly != null) {
            val today = LocalDate.now(ZoneId.of("Asia/Seoul"))
            val fromDay = dayOnly.groupValues[1].toIntOrNull()
            val toDay = dayOnly.groupValues[2].toIntOrNull()
            if (fromDay != null && toDay != null && fromDay in 1..31 && toDay in 1..31) {
                val a = today.withDayOfMonth(fromDay.coerceAtMost(today.lengthOfMonth()))
                val b = today.withDayOfMonth(toDay.coerceAtMost(today.lengthOfMonth()))
                return if (a <= b) Pair(a.toString(), b.toString()) else Pair(b.toString(), a.toString())
            }
        }
        return null to null
    }

    private fun containsAllRecordsIntent(source: String): Boolean {
        val normalized = source.replace(Regex("""\s+"""), "")
        val hasAllToken = listOf("여태까지", "지금까지", "전체", "전부", "모든").any { normalized.contains(it) }
        val hasRecordToken = listOf("기록", "일정", "이벤트").any { normalized.contains(it) }
        return hasAllToken && hasRecordToken
    }

    private fun normalizeDateToken(raw: String, today: LocalDate): String? {
        val token = raw.trim().replace('.', '-').replace('/', '-')
        val parts = token.split("-").filter { it.isNotBlank() }
        return when (parts.size) {
            3 -> {
                val year = parts[0].toIntOrNull() ?: return null
                val month = parts[1].toIntOrNull() ?: return null
                val day = parts[2].toIntOrNull() ?: return null
                runCatching { LocalDate.of(year, month, day).toString() }.getOrNull()
            }
            2 -> {
                val month = parts[0].toIntOrNull() ?: return null
                val day = parts[1].toIntOrNull() ?: return null
                runCatching { LocalDate.of(today.year, month, day).toString() }.getOrNull()
            }
            else -> null
        }
    }

    private fun extractSortOrder(source: String): String? {
        val normalized = source.lowercase()
        return when {
            listOf("오래된순", "예전순", "오름차순").any { normalized.contains(it) } -> "asc"
            listOf("최신순", "최근순", "내림차순").any { normalized.contains(it) } -> "desc"
            else -> null
        }
    }

    private fun inferSearchStrategy(
        forcedAll: Boolean,
        query: String,
        dateFrom: String?,
        dateTo: String?,
        categoryIds: List<String>,
        labels: List<String>
    ): AiSearchStrategy {
        if (forcedAll) return AiSearchStrategy.AllEvents
        val hasDateRange = dateFrom != null || dateTo != null
        val hasCategory = categoryIds.isNotEmpty()
        val hasLabel = labels.isNotEmpty()
        val hasQuery = query.isNotBlank() && query != "*" && !isGenericSearchQuery(query)
        val activeKinds = listOf(hasDateRange, hasCategory, hasLabel, hasQuery).count { it }
        if (activeKinds >= 2) return AiSearchStrategy.Combined
        return when {
            hasDateRange -> AiSearchStrategy.DateRange
            hasCategory -> AiSearchStrategy.Category
            hasLabel -> AiSearchStrategy.Label
            hasQuery -> AiSearchStrategy.Keyword
            else -> AiSearchStrategy.Combined
        }
    }

    private fun isGenericSearchQuery(query: String): Boolean {
        return query.trim() in setOf("기록", "일정", "이벤트", "기록들")
    }
}
