package com.echo.echocalendar.data.seed

import androidx.room.withTransaction
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.CategoryDefaults

object DebugSeedData {
    suspend fun seedIfEmpty(database: AppDatabase) {
        database.withTransaction {
            if (database.categoryDao().getAll().isEmpty()) {
                CategoryDefaults.categories.forEach { category ->
                    database.categoryDao().upsert(category)
                }
            }
        }
        val seededSummaries = listOf(
            "병원 방문",
            "프로젝트 회의",
            "자동이체 결제",
            "동네 마트 장보기",
            "온라인 강의 수강",
            "고객 미팅"
        )
        database.eventDao().deleteSeededEvents("데모 이벤트%", seededSummaries)
    }
}
