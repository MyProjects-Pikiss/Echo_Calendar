package com.echo.echocalendar.data.local

object CategoryDefaults {
    val categories: List<CategoryEntity> = listOf(
        CategoryEntity(id = "medical", displayName = "의료", sortOrder = 1),
        CategoryEntity(id = "finance", displayName = "금융", sortOrder = 2),
        CategoryEntity(id = "spending", displayName = "소비", sortOrder = 3),
        CategoryEntity(id = "administration", displayName = "행정", sortOrder = 4),
        CategoryEntity(id = "work", displayName = "업무", sortOrder = 5),
        CategoryEntity(id = "learning", displayName = "학습", sortOrder = 6),
        CategoryEntity(id = "relationships", displayName = "개인 관계", sortOrder = 7),
        CategoryEntity(id = "gathering-event", displayName = "모임·행사", sortOrder = 8),
        CategoryEntity(id = "life", displayName = "생활", sortOrder = 9),
        CategoryEntity(id = "repair-maintenance", displayName = "수리·정비", sortOrder = 10),
        CategoryEntity(id = "delivery", displayName = "배송", sortOrder = 11),
        CategoryEntity(id = "hobby-leisure", displayName = "취미·여가", sortOrder = 12),
        CategoryEntity(id = "dining", displayName = "외식·식사", sortOrder = 13),
        CategoryEntity(id = "record", displayName = "기록", sortOrder = 14),
        CategoryEntity(id = "other", displayName = "기타", sortOrder = 15)
    )

    val categoryIds: Set<String> = categories.map { it.id }.toSet()

    fun requireValidCategoryId(categoryId: String) {
        require(categoryId in categoryIds) { "Unknown categoryId: $categoryId" }
    }
}
