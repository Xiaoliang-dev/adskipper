package com.adskipper.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class RuleManager private constructor(context: Context) {

    private val database = RuleDatabase.getDatabase(context)
    private val ruleDao = database.ruleDao()

    val allRules: Flow<List<RuleEntity>> = ruleDao.getAllRules()
    val enabledRules: Flow<List<RuleEntity>> = ruleDao.getEnabledRules()

    suspend fun getRulesForPackage(packageName: String): List<RuleEntity> {
        return ruleDao.getRulesForPackage(packageName)
    }

    suspend fun getRuleById(id: Long): RuleEntity? {
        return ruleDao.getRuleById(id)
    }

    suspend fun addRule(rule: RuleEntity): Long {
        return ruleDao.insertRule(rule)
    }

    suspend fun updateRule(rule: RuleEntity) {
        ruleDao.updateRule(rule)
    }

    suspend fun deleteRule(rule: RuleEntity) {
        ruleDao.deleteRule(rule)
    }

    suspend fun deleteRuleById(id: Long) {
        ruleDao.deleteRuleById(id)
    }

    suspend fun toggleRuleEnabled(id: Long, enabled: Boolean) {
        ruleDao.setRuleEnabled(id, enabled)
    }

    suspend fun recordRuleTrigger(id: Long) {
        ruleDao.recordTrigger(id)
    }

    suspend fun getRuleCount(): Int {
        return ruleDao.getRuleCount()
    }

    suspend fun getEnabledRuleCount(): Int {
        return ruleDao.getEnabledRuleCount()
    }

    suspend fun getTotalTriggerCount(): Long {
        return ruleDao.getTotalTriggerCount() ?: 0L
    }

    suspend fun getTodayTriggerCount(): Long {
        val todayStart = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        return ruleDao.getTriggerCountSince(todayStart) ?: 0L
    }

    suspend fun deleteAllRules() {
        ruleDao.deleteAllRules()
    }

    suspend fun importRules(rules: List<RuleEntity>, merge: Boolean = true) {
        withContext(Dispatchers.IO) {
            if (!merge) {
                ruleDao.deleteAllRules()
            }
            // Reset IDs to allow auto-generation
            val rulesToInsert = rules.map { it.copy(id = 0) }
            ruleDao.insertRules(rulesToInsert)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: RuleManager? = null

        fun getInstance(context: Context): RuleManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RuleManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
}