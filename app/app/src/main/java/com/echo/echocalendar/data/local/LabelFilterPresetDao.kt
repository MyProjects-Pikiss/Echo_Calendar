package com.echo.echocalendar.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LabelFilterPresetDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPreset(preset: LabelFilterPresetEntity): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRules(rules: List<LabelFilterPresetRuleEntity>)

    @Query("SELECT * FROM LabelFilterPreset WHERE id = :presetId LIMIT 1")
    suspend fun getPresetById(presetId: Long): LabelFilterPresetEntity?

    @Query("SELECT * FROM LabelFilterPreset WHERE name = :name LIMIT 1")
    suspend fun getPresetByName(name: String): LabelFilterPresetEntity?

    @Query("SELECT * FROM LabelFilterPreset ORDER BY name ASC")
    suspend fun getAllPresets(): List<LabelFilterPresetEntity>

    @Query("SELECT * FROM LabelFilterPresetRule")
    suspend fun getAllRules(): List<LabelFilterPresetRuleEntity>

    @Query("SELECT * FROM LabelFilterPresetRule WHERE presetId = :presetId")
    suspend fun getRulesForPreset(presetId: Long): List<LabelFilterPresetRuleEntity>

    @Query(
        "UPDATE LabelFilterPreset SET name = :name, includeMode = :includeMode, updatedAt = :updatedAt " +
            "WHERE id = :presetId"
    )
    suspend fun updatePreset(presetId: Long, name: String, includeMode: String, updatedAt: Long)

    @Query("DELETE FROM LabelFilterPresetRule WHERE presetId = :presetId")
    suspend fun deleteRulesForPreset(presetId: Long)

    @Query("DELETE FROM LabelFilterPreset WHERE id = :presetId")
    suspend fun deletePresetById(presetId: Long)

    @Query("SELECT DISTINCT eventId FROM EventLabel WHERE labelId IN (:labelIds)")
    suspend fun getEventIdsMatchingAnyLabelIds(labelIds: List<Long>): List<String>

    @Query(
        "SELECT eventId FROM EventLabel WHERE labelId IN (:labelIds) " +
            "GROUP BY eventId HAVING COUNT(DISTINCT labelId) = :requiredCount"
    )
    suspend fun getEventIdsContainingAllLabelIds(labelIds: List<Long>, requiredCount: Int): List<String>

    @Transaction
    suspend fun createPreset(
        name: String,
        includeMode: String,
        includeLabelIds: List<Long>,
        excludeLabelIds: List<Long>,
        now: Long
    ): Long? {
        if (getPresetByName(name) != null) return null
        val presetId = insertPreset(
            LabelFilterPresetEntity(
                name = name,
                includeMode = includeMode,
                createdAt = now,
                updatedAt = now
            )
        )
        if (presetId == -1L) return null
        replaceRules(presetId, includeLabelIds, excludeLabelIds)
        return presetId
    }

    @Transaction
    suspend fun editPreset(
        presetId: Long,
        name: String,
        includeMode: String,
        includeLabelIds: List<Long>,
        excludeLabelIds: List<Long>,
        updatedAt: Long
    ): Boolean {
        val current = getPresetById(presetId) ?: return false
        val existingName = getPresetByName(name)
        if (existingName != null && existingName.id != presetId) return false
        updatePreset(
            presetId = presetId,
            name = name,
            includeMode = includeMode,
            updatedAt = updatedAt
        )
        replaceRules(presetId, includeLabelIds, excludeLabelIds)
        return current.id == presetId
    }

    private suspend fun replaceRules(
        presetId: Long,
        includeLabelIds: List<Long>,
        excludeLabelIds: List<Long>
    ) {
        deleteRulesForPreset(presetId)
        val rules = includeLabelIds.distinct().map { labelId ->
            LabelFilterPresetRuleEntity(
                presetId = presetId,
                labelId = labelId,
                role = LABEL_FILTER_RULE_INCLUDE
            )
        } + excludeLabelIds.distinct().map { labelId ->
            LabelFilterPresetRuleEntity(
                presetId = presetId,
                labelId = labelId,
                role = LABEL_FILTER_RULE_EXCLUDE
            )
        }
        if (rules.isNotEmpty()) {
            insertRules(rules)
        }
    }
}
