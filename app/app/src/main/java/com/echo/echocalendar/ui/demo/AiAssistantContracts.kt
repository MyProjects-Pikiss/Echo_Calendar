package com.echo.echocalendar.ui.demo

import java.time.LocalDate

enum class AiMode(val value: String) {
    Input("input"),
    Search("search"),
    Refine("refine"),
    Modify("modify")
}

enum class DraftField(val value: String) {
    Summary("summary"),
    Time("time"),
    Category("category"),
    Place("place"),
    Labels("labels"),
    Body("body")
}

enum class AiCrudIntent(val value: String) {
    Create("create"),
    Update("update"),
    Delete("delete")
}

data class AiInputSuggestion(
    val date: LocalDate,
    val intent: AiCrudIntent,
    val summary: String,
    val timeText: String,
    val repeatYearly: Boolean?,
    val categoryId: String,
    val placeText: String,
    val body: String,
    val labelsText: String,
    val missingRequired: List<String>
)

data class AiSearchSuggestion(
    val query: String,
    val dateFrom: String? = null,
    val dateTo: String? = null,
    val categoryIds: List<String> = emptyList(),
    val labelNames: List<String> = emptyList()
)

data class AiRefineSuggestion(
    val field: DraftField,
    val value: String,
    val missingRequired: List<String>
)

data class AiModifyPatch(
    val summary: String? = null,
    val timeText: String? = null,
    val categoryId: String? = null,
    val placeText: String? = null,
    val body: String? = null,
    val labelsText: String? = null
)

data class UsageMySummary(
    val username: String,
    val requestCount: Int,
    val totalTokens: Int,
    val avgTokensPerRequest: Double,
    val successRate: Double
)
