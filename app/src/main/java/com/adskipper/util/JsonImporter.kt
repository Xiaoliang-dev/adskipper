package com.adskipper.util

import android.content.Context
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import com.adskipper.data.RuleEntity
import com.adskipper.data.RuleExport
import com.adskipper.data.RuleJsonModel
import com.adskipper.data.toEntity
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class JsonImporter(private val context: Context) {

    private val gson = Gson()

    data class ImportResult(
        val success: Boolean,
        val importedCount: Int = 0,
        val message: String = ""
    )

    suspend fun importFromFile(uri: Uri): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    inputStream.readBytes().toString(Charsets.UTF_8)
                } ?: return@withContext ImportResult(false, 0, "无法读取文件")

                parseAndValidate(json)
            } catch (e: Exception) {
                ImportResult(false, 0, "导入失败: ${e.message}")
            }
        }
    }

    suspend fun importFromClipboard(): ImportResult {
        return withContext(Dispatchers.IO) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clipData = clipboard.primaryClip
                if (clipData == null || clipData.itemCount == 0) {
                    return@withContext ImportResult(false, 0, "剪贴板为空")
                }

                val item = clipData.getItemAt(0)
                val json = item.text?.toString() ?: return@withContext ImportResult(false, 0, "剪贴板内容无效")

                parseAndValidate(json)
            } catch (e: Exception) {
                ImportResult(false, 0, "导入失败: ${e.message}")
            }
        }
    }

    suspend fun importFromJsonString(json: String): ImportResult {
        return withContext(Dispatchers.IO) {
            parseAndValidate(json)
        }
    }

    private fun parseAndValidate(json: String): ImportResult {
        return try {
            // Try to parse as RuleExport (with metadata)
            val export = try {
                gson.fromJson(json, RuleExport::class.java)
            } catch (e: JsonSyntaxException) {
                // Try to parse as raw array of rules
                null
            }

            val rules: List<RuleJsonModel> = if (export != null && export.rules != null) {
                export.rules
            } else {
                // Try parsing as array
                try {
                    gson.fromJson(json, Array<RuleJsonModel>::class.java)?.toList()
                } catch (e: JsonSyntaxException) {
                    null
                }
            } ?: return ImportResult(false, 0, "无效的JSON格式")

            if (rules.isEmpty()) {
                return ImportResult(false, 0, "没有找到可导入的规则")
            }

            // Validate each rule
            val validRules = rules.filter { validateRule(it) }

            if (validRules.isEmpty()) {
                return ImportResult(false, 0, "所有规则验证失败，请检查规则格式")
            }

            ImportResult(
                success = true,
                importedCount = validRules.size,
                message = "成功导入 ${validRules.size} 条规则"
            )
        } catch (e: Exception) {
            ImportResult(false, 0, "解析失败: ${e.message}")
        }
    }

    fun extractRulesFromJson(json: String): List<RuleEntity> {
        return try {
            val export = try {
                gson.fromJson(json, RuleExport::class.java)
            } catch (e: JsonSyntaxException) {
                null
            }

            val rules: List<RuleJsonModel> = if (export != null && export.rules != null) {
                export.rules
            } else {
                try {
                    gson.fromJson(json, Array<RuleJsonModel>::class.java)?.toList()
                } catch (e: JsonSyntaxException) {
                    null
                }
            } ?: emptyList()

            rules.filter { validateRule(it) }.map { it.toEntity() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun validateRule(rule: RuleJsonModel): Boolean {
        // Must have package name
        if (rule.packageName.isBlank()) return false

        // Must have name
        if (rule.name.isBlank()) return false

        // Must have at least one condition
        if (!rule.useText && !rule.useId && !rule.useClassName && !rule.useBounds && 
            !rule.useAfterText && !rule.useContentDesc) return false

        // Validate match types
        val validMatchTypes = setOf("exact", "contains", "starts_with", "ends_with", "regex")
        if (rule.textMatchType !in validMatchTypes) return false

        // Validate action type
        val validActions = setOf("click", "click_parent", "swipe_left", "swipe_right", "back", "custom_click")
        if (rule.actionType !in validActions) return false

        return true
    }

    fun copyToClipboard(json: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("AdSkipper Rules", json)
        clipboard.setPrimaryClip(clip)
    }
}