package com.echo.echocalendar.domain.usecase

import java.util.Locale

internal const val MAX_LABELS_PER_EVENT = 5
private const val LABEL_MATCH_THRESHOLD = 0.72

internal fun harmonizeEventLabels(
    rawLabels: List<String>,
    existingLabelNames: List<String>,
    maxCount: Int = MAX_LABELS_PER_EVENT
): List<String> {
    if (maxCount <= 0) return emptyList()
    val evolvingExisting = existingLabelNames.toMutableList()
    val result = mutableListOf<String>()
    val seenKeys = mutableSetOf<String>()

    rawLabels.forEach { raw ->
        if (result.size >= maxCount) return@forEach
        val generic = toGenericLabel(raw)
        if (generic.isBlank()) return@forEach
        val matched = findBestExistingLabel(generic, evolvingExisting)
        val selected = matched ?: generic
        val key = normalizeLabelKey(selected)
        if (key.isBlank() || !seenKeys.add(key)) return@forEach
        result += selected
        if (matched == null) {
            evolvingExisting += selected
        }
    }
    return result
}

internal fun harmonizeSearchLabels(
    rawLabels: List<String>,
    existingLabelNames: List<String>,
    maxCount: Int = MAX_LABELS_PER_EVENT
): List<String> {
    if (maxCount <= 0) return emptyList()
    val result = mutableListOf<String>()
    val seenKeys = mutableSetOf<String>()

    rawLabels.forEach { raw ->
        if (result.size >= maxCount) return@forEach
        val cleaned = toGenericLabel(raw)
        if (cleaned.isBlank()) return@forEach
        val selected = findBestExistingLabel(cleaned, existingLabelNames) ?: cleaned
        val key = normalizeLabelKey(selected)
        if (key.isBlank() || !seenKeys.add(key)) return@forEach
        result += selected
    }
    return result
}

private fun findBestExistingLabel(candidate: String, existingLabelNames: List<String>): String? {
    val trimmed = candidate.trim()
    if (trimmed.isBlank()) return null
    existingLabelNames.firstOrNull { it.equals(trimmed, ignoreCase = true) }?.let { return it }

    val candidateKey = normalizeLabelKey(trimmed)
    if (candidateKey.isBlank()) return null

    var bestLabel: String? = null
    var bestScore = 0.0
    existingLabelNames.forEach { existing ->
        val score = similarityScore(candidateKey, normalizeLabelKey(existing))
        if (score > bestScore) {
            bestScore = score
            bestLabel = existing
        }
    }
    return bestLabel?.takeIf { bestScore >= LABEL_MATCH_THRESHOLD }
}

private fun similarityScore(a: String, b: String): Double {
    if (a.isBlank() || b.isBlank()) return 0.0
    if (a == b) return 1.0
    if (a.contains(b) || b.contains(a)) {
        val ratio = minOf(a.length, b.length).toDouble() / maxOf(a.length, b.length).toDouble()
        return 0.8 + (0.2 * ratio)
    }
    val aBigrams = bigramSet(a)
    val bBigrams = bigramSet(b)
    if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0.0
    val intersection = aBigrams.intersect(bBigrams).size.toDouble()
    val union = aBigrams.union(bBigrams).size.toDouble()
    if (union == 0.0) return 0.0
    return intersection / union
}

private fun bigramSet(value: String): Set<String> {
    if (value.length < 2) return emptySet()
    return buildSet {
        for (index in 0 until value.length - 1) {
            add(value.substring(index, index + 2))
        }
    }
}

private fun toGenericLabel(raw: String): String {
    val cleaned = raw
        .trim()
        .removePrefix("#")
        .replace(Regex("""[^\p{L}\p{N}\s_-]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
    if (cleaned.isBlank()) return ""

    val compact = if (cleaned.length > 20) cleaned.take(20).trim() else cleaned
    val words = compact.split(' ').filter { it.isNotBlank() }
    return when {
        words.isEmpty() -> ""
        words.size >= 3 -> words.take(2).joinToString(" ")
        else -> compact
    }
}

private fun normalizeLabelKey(label: String): String {
    return label
        .trim()
        .lowercase(Locale.ROOT)
        .replace(Regex("""[^\p{L}\p{N}]"""), "")
}
