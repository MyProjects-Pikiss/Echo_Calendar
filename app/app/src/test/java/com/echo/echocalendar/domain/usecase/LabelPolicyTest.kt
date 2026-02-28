package com.echo.echocalendar.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LabelPolicyTest {
    @Test
    fun harmonizeEventLabels_reusesExistingWhenSimilar() {
        val labels = harmonizeEventLabels(
            rawLabels = listOf("병원검진", "건강"),
            existingLabelNames = listOf("병원 검진", "가족")
        )

        assertEquals("병원 검진", labels.first())
        assertTrue("건강" in labels)
    }

    @Test
    fun harmonizeEventLabels_limitsToFive() {
        val labels = harmonizeEventLabels(
            rawLabels = listOf("a", "b", "c", "d", "e", "f"),
            existingLabelNames = emptyList()
        )

        assertEquals(5, labels.size)
    }

    @Test
    fun harmonizeSearchLabels_prefersExistingLabels() {
        val labels = harmonizeSearchLabels(
            rawLabels = listOf("정기검진"),
            existingLabelNames = listOf("정기 검진", "회의")
        )

        assertEquals(listOf("정기 검진"), labels)
    }
}
