package com.echo.echocalendar.data.seed

import androidx.room.withTransaction
import com.echo.echocalendar.data.local.AppDatabase
import com.echo.echocalendar.data.local.CategoryDefaults
import com.echo.echocalendar.domain.usecase.SaveEventUseCase
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object DebugSeedData {
    suspend fun seedIfEmpty(database: AppDatabase, saveEventUseCase: SaveEventUseCase) {
        val existingEvents = database.eventDao().getAll()

        database.withTransaction {
            if (database.categoryDao().getAll().isEmpty()) {
                CategoryDefaults.categories.forEach { category ->
                    database.categoryDao().upsert(category)
                }
            }
        }

        val zoneId = ZoneId.of("Asia/Seoul")
        val today = LocalDate.now(zoneId)
        val samples = listOf(
            SampleEvent(
                categoryId = "medical",
                date = today.minusDays(1),
                time = LocalTime.of(10, 30),
                summary = "병원 방문",
                body = "감기 진료 및 약 처방",
                placeText = "서울 내과",
                labels = listOf("감기", "진료")
            ),
            SampleEvent(
                categoryId = "work",
                date = today.minusDays(2),
                time = LocalTime.of(14, 0),
                summary = "프로젝트 회의",
                body = "신규 기능 논의 및 일정 조정",
                placeText = "회의실 A",
                labels = listOf("회의", "업무")
            ),
            SampleEvent(
                categoryId = "finance",
                date = today.minusDays(3),
                time = LocalTime.of(9, 0),
                summary = "자동이체 결제",
                body = "카드 결제 내역 확인",
                placeText = null,
                labels = listOf("결제", "카드")
            ),
            SampleEvent(
                categoryId = "life",
                date = today.minusDays(4),
                time = LocalTime.of(19, 30),
                summary = "동네 마트 장보기",
                body = "식료품 및 생활용품 구매",
                placeText = "OO 마트",
                labels = listOf("생활", "구매")
            ),
            SampleEvent(
                categoryId = "learning",
                date = today.minusDays(5),
                time = LocalTime.of(20, 0),
                summary = "온라인 강의 수강",
                body = "Compose 기초 강의",
                placeText = null,
                labels = listOf("학습", "Compose")
            ),
            SampleEvent(
                categoryId = "work",
                date = today,
                time = LocalTime.of(16, 0),
                summary = "고객 미팅",
                body = "요구사항 정리",
                placeText = "카페",
                labels = listOf("미팅", "업무")
            )
        )

        if (existingEvents.isEmpty()) {
            samples.forEach { sample ->
                val occurredAt = sample.date.atTime(sample.time).atZone(zoneId).toInstant().toEpochMilli()
                saveEventUseCase(
                    categoryId = sample.categoryId,
                    occurredAt = occurredAt,
                    summary = sample.summary,
                    body = sample.body,
                    placeText = sample.placeText,
                    labels = sample.labels
                )
            }
        }

        val hasDemoEvents = existingEvents.any { it.summary.startsWith("데모 이벤트") }
        if (!hasDemoEvents) {
            val demoCategoryIds = listOf("work", "life", "learning", "finance", "medical")
            val demoEvents = buildList {
                repeat(20) { dayOffset ->
                    val date = today.plusDays(dayOffset.toLong())
                    val eventCount = (dayOffset % 3) + 1
                    repeat(eventCount) { eventIndex ->
                        val categoryId = demoCategoryIds[(dayOffset + eventIndex) % demoCategoryIds.size]
                        val time = LocalTime.of(9 + eventIndex * 3, 0)
                        add(
                            SampleEvent(
                                categoryId = categoryId,
                                date = date,
                                time = time,
                                summary = "데모 이벤트 ${dayOffset + 1}-${eventIndex + 1}",
                                body = "일자 ${date}에 등록된 ${eventIndex + 1}번째 이벤트",
                                placeText = "데모 장소",
                                labels = listOf("데모", "테스트")
                            )
                        )
                    }
                }
            }

            demoEvents.forEach { sample ->
                val occurredAt = sample.date.atTime(sample.time).atZone(zoneId).toInstant().toEpochMilli()
                saveEventUseCase(
                    categoryId = sample.categoryId,
                    occurredAt = occurredAt,
                    summary = sample.summary,
                    body = sample.body,
                    placeText = sample.placeText,
                    labels = sample.labels
                )
            }
        }
    }

    private data class SampleEvent(
        val categoryId: String,
        val date: LocalDate,
        val time: LocalTime,
        val summary: String,
        val body: String,
        val placeText: String?,
        val labels: List<String>
    )
}
