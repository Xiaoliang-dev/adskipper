package com.adskipper.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RuleDao {
    @Query("SELECT * FROM rules ORDER BY priority DESC, createdAt ASC")
    fun getAllRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE enabled = 1 ORDER BY priority DESC, createdAt ASC")
    fun getEnabledRules(): Flow<List<RuleEntity>>

    @Query("SELECT * FROM rules WHERE packageName = :packageName AND enabled = 1 ORDER BY priority DESC")
    suspend fun getRulesForPackage(packageName: String): List<RuleEntity>

    @Query("SELECT * FROM rules WHERE id = :id")
    suspend fun getRuleById(id: Long): RuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRule(rule: RuleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRules(rules: List<RuleEntity>): List<Long>

    @Update
    suspend fun updateRule(rule: RuleEntity)

    @Delete
    suspend fun deleteRule(rule: RuleEntity)

    @Query("DELETE FROM rules WHERE id = :id")
    suspend fun deleteRuleById(id: Long)

    @Query("DELETE FROM rules")
    suspend fun deleteAllRules()

    @Query("UPDATE rules SET enabled = :enabled WHERE id = :id")
    suspend fun setRuleEnabled(id: Long, enabled: Boolean)

    @Query("UPDATE rules SET triggerCount = triggerCount + 1, lastTriggeredAt = :timestamp WHERE id = :id")
    suspend fun recordTrigger(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM rules")
    suspend fun getRuleCount(): Int

    @Query("SELECT COUNT(*) FROM rules WHERE enabled = 1")
    suspend fun getEnabledRuleCount(): Int

    @Query("SELECT SUM(triggerCount) FROM rules")
    suspend fun getTotalTriggerCount(): Long?

    @Query("SELECT SUM(triggerCount) FROM rules WHERE lastTriggeredAt >= :since")
    suspend fun getTriggerCountSince(since: Long): Long?
}