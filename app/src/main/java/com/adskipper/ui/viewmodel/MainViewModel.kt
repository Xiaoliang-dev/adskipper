package com.adskipper.ui.viewmodel

import android.app.Application
import android.content.*
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adskipper.data.RuleEntity
import com.adskipper.data.RuleManager
import com.adskipper.service.AdSkipAccessibilityService
import com.adskipper.service.FloatingWindowService
import com.adskipper.util.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
    }

    private val context = application.applicationContext
    private val ruleManager = RuleManager.getInstance(context)
    private val settingsManager = SettingsManager(context)
    private val jsonExporter = JsonExporter(context)
    private val jsonImporter = JsonImporter(context)

    val settings = settingsManager.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), SettingsManager.AppSettings())

    val rules = ruleManager.allRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    val enabledRules = ruleManager.enabledRules
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), emptyList())

    private val _serviceRunning = MutableStateFlow(false)
    val serviceRunning: StateFlow<Boolean> = _serviceRunning.asStateFlow()

    private val _stats = MutableStateFlow(StatsData())
    val stats: StateFlow<StatsData> = _stats.asStateFlow()

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val serviceStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AdSkipAccessibilityService.ACTION_SERVICE_STATUS) {
                val running = intent.getBooleanExtra(
                    AdSkipAccessibilityService.EXTRA_SERVICE_RUNNING, false
                )
                _serviceRunning.value = running
            }
        }
    }

    init {
        // Register service status receiver
        val filter = IntentFilter(AdSkipAccessibilityService.ACTION_SERVICE_STATUS)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceStatusReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(serviceStatusReceiver, filter)
        }

        // Check initial service status
        _serviceRunning.value = AdSkipAccessibilityService.isRunning

        // Load stats
        loadStats()

        // Mark first launch complete
        viewModelScope.launch {
            settingsManager.setFirstLaunch(false)
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            try {
                val total = ruleManager.getTotalTriggerCount()
                val today = ruleManager.getTodayTriggerCount()
                val ruleCount = ruleManager.getRuleCount()
                val enabledCount = ruleManager.getEnabledRuleCount()
                _stats.value = StatsData(
                    totalSkipped = total,
                    todaySkipped = today,
                    ruleCount = ruleCount,
                    enabledRuleCount = enabledCount
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error loading stats", e)
            }
        }
    }

    fun refreshServiceStatus() {
        _serviceRunning.value = AdSkipAccessibilityService.isRunning
    }

    fun isAccessibilityServiceEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val serviceName = "${context.packageName}/${AdSkipAccessibilityService::class.java.canonicalName}"
        return enabledServices.contains(serviceName)
    }

    fun addRule(rule: RuleEntity) {
        viewModelScope.launch {
            try {
                ruleManager.addRule(rule)
                showSnackbar("规则已添加")
                loadStats()
            } catch (e: Exception) {
                Log.e(TAG, "Error adding rule", e)
                showSnackbar("添加失败: ${e.message}")
            }
        }
    }

    fun updateRule(rule: RuleEntity) {
        viewModelScope.launch {
            try {
                ruleManager.updateRule(rule)
                showSnackbar("规则已更新")
            } catch (e: Exception) {
                Log.e(TAG, "Error updating rule", e)
                showSnackbar("更新失败: ${e.message}")
            }
        }
    }

    fun deleteRule(rule: RuleEntity) {
        viewModelScope.launch {
            try {
                ruleManager.deleteRule(rule)
                showSnackbar("规则已删除")
                loadStats()
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting rule", e)
                showSnackbar("删除失败: ${e.message}")
            }
        }
    }

    fun toggleRuleEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            try {
                ruleManager.toggleRuleEnabled(id, enabled)
                showSnackbar(if (enabled) "规则已启用" else "规则已禁用")
            } catch (e: Exception) {
                Log.e(TAG, "Error toggling rule", e)
            }
        }
    }

    fun exportRulesToFile(uri: android.net.Uri?) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentRules = rules.value
                val result = jsonExporter.exportToFile(currentRules, uri)
                result.fold(
                    onSuccess = { path ->
                        showSnackbar("已导出到: $path")
                    },
                    onFailure = { e ->
                        showSnackbar("导出失败: ${e.message}")
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun shareRules() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val currentRules = rules.value
                val result = jsonExporter.exportToCache(currentRules)
                result.fold(
                    onSuccess = { file ->
                        jsonExporter.shareExportFile(file)
                    },
                    onFailure = { e ->
                        showSnackbar("分享失败: ${e.message}")
                    }
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importRulesFromFile(uri: android.net.Uri, merge: Boolean = true) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = jsonImporter.importFromFile(uri)
                if (result.success) {
                    // Actually import the rules
                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        val json = inputStream.readBytes().toString(Charsets.UTF_8)
                        val entities = jsonImporter.extractRulesFromJson(json)
                        if (entities.isNotEmpty()) {
                            ruleManager.importRules(entities, merge)
                            showSnackbar("成功导入 ${entities.size} 条规则")
                            loadStats()
                        } else {
                            showSnackbar("没有可导入的有效规则")
                        }
                    }
                } else {
                    showSnackbar(result.message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import error", e)
                showSnackbar("导入失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun importFromClipboard() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val result = jsonImporter.importFromClipboard()
                if (result.success) {
                    showSnackbar(result.message)
                    // Need to get the actual clipboard content and import
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clipData = clipboard.primaryClip
                    if (clipData != null && clipData.itemCount > 0) {
                        val item = clipData.getItemAt(0)
                        val json = item.text?.toString() ?: ""
                        if (json.isNotBlank()) {
                            val entities = jsonImporter.extractRulesFromJson(json)
                            if (entities.isNotEmpty()) {
                                ruleManager.importRules(entities, true)
                                showSnackbar("成功导入 ${entities.size} 条规则")
                                loadStats()
                            }
                        }
                    }
                } else {
                    showSnackbar(result.message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Clipboard import error", e)
                showSnackbar("导入失败: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun copyRulesToClipboard() {
        viewModelScope.launch {
            try {
                val currentRules = rules.value
                val export = com.adskipper.data.RuleExport(
                    appVersion = context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "",
                    rules = currentRules.map { com.adskipper.data.toJsonModel(it) }
                )
                val gson = com.google.gson.GsonBuilder().setPrettyPrinting().create()
                val json = gson.toJson(export)
                jsonImporter.copyToClipboard(json)
                showSnackbar("规则已复制到剪贴板")
            } catch (e: Exception) {
                Log.e(TAG, "Copy to clipboard error", e)
                showSnackbar("复制失败: ${e.message}")
            }
        }
    }

    fun deleteAllRules() {
        viewModelScope.launch {
            try {
                ruleManager.deleteAllRules()
                showSnackbar("所有规则已清除")
                loadStats()
            } catch (e: Exception) {
                Log.e(TAG, "Error clearing rules", e)
                showSnackbar("清除失败: ${e.message}")
            }
        }
    }

    fun resetStats() {
        // Stats are derived from triggerCount in rules, so we reset those
        viewModelScope.launch {
            try {
                val currentRules = rules.value
                currentRules.forEach { rule ->
                    ruleManager.updateRule(rule.copy(triggerCount = 0, lastTriggeredAt = null))
                }
                showSnackbar("统计数据已重置")
                loadStats()
            } catch (e: Exception) {
                Log.e(TAG, "Error resetting stats", e)
                showSnackbar("重置失败: ${e.message}")
            }
        }
    }

    fun updateSettings(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "Error updating settings", e)
            }
        }
    }

    fun setShowToast(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setShowToast(enabled)
        }
    }

    fun setVibrate(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setVibrate(enabled)
        }
    }

    fun setSkipDelay(delay: Long) {
        viewModelScope.launch {
            settingsManager.setSkipDelay(delay)
        }
    }

    private fun showSnackbar(message: String) {
        viewModelScope.launch {
            _snackbarMessage.emit(message)
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            context.unregisterReceiver(serviceStatusReceiver)
        } catch (e: Exception) {
            // Receiver may not be registered
        }
    }

    data class StatsData(
        val totalSkipped: Long = 0,
        val todaySkipped: Long = 0,
        val ruleCount: Int = 0,
        val enabledRuleCount: Int = 0
    )
}