package com.adskipper.data

import com.google.gson.annotations.SerializedName

// JSON export/import models with version info for future compatibility
data class RuleExport(
    @SerializedName("version")
    val version: Int = 1,
    @SerializedName("exportTime")
    val exportTime: Long = System.currentTimeMillis(),
    @SerializedName("appVersion")
    val appVersion: String = "",
    @SerializedName("rules")
    val rules: List<RuleJsonModel>
)

data class RuleJsonModel(
    @SerializedName("name")
    val name: String,
    @SerializedName("description")
    val description: String = "",
    @SerializedName("packageName")
    val packageName: String,
    @SerializedName("enabled")
    val enabled: Boolean = true,
    @SerializedName("priority")
    val priority: Int = 0,
    @SerializedName("useText")
    val useText: Boolean = false,
    @SerializedName("targetText")
    val targetText: String = "",
    @SerializedName("textMatchType")
    val textMatchType: String = "contains",
    @SerializedName("useId")
    val useId: Boolean = false,
    @SerializedName("targetId")
    val targetId: String = "",
    @SerializedName("idMatchType")
    val idMatchType: String = "contains",
    @SerializedName("useClassName")
    val useClassName: Boolean = false,
    @SerializedName("targetClassName")
    val targetClassName: String = "",
    @SerializedName("classMatchType")
    val classMatchType: String = "contains",
    @SerializedName("useBounds")
    val useBounds: Boolean = false,
    @SerializedName("targetBounds")
    val targetBounds: String = "",
    @SerializedName("actionType")
    val actionType: String = "click",
    @SerializedName("customClickX")
    val customClickX: Int = -1,
    @SerializedName("customClickY")
    val customClickY: Int = -1,
    @SerializedName("delayMs")
    val delayMs: Long = 0,
    @SerializedName("clickParent")
    val clickParent: Boolean = false,
    @SerializedName("useAfterText")
    val useAfterText: Boolean = false,
    @SerializedName("targetAfterText")
    val targetAfterText: String = "",
    @SerializedName("afterTextMatchType")
    val afterTextMatchType: String = "contains",
    @SerializedName("useContentDesc")
    val useContentDesc: Boolean = false,
    @SerializedName("targetContentDesc")
    val targetContentDesc: String = "",
    @SerializedName("contentDescMatchType")
    val contentDescMatchType: String = "contains",
    @SerializedName("swipeDirection")
    val swipeDirection: String = "none"
)

// Extension functions to convert between Entity and JSON model
fun RuleEntity.toJsonModel(): RuleJsonModel {
    return RuleJsonModel(
        name = name,
        description = description,
        packageName = packageName,
        enabled = enabled,
        priority = priority,
        useText = useText,
        targetText = targetText,
        textMatchType = textMatchType,
        useId = useId,
        targetId = targetId,
        idMatchType = idMatchType,
        useClassName = useClassName,
        targetClassName = targetClassName,
        classMatchType = classMatchType,
        useBounds = useBounds,
        targetBounds = targetBounds,
        actionType = actionType,
        customClickX = customClickX,
        customClickY = customClickY,
        delayMs = delayMs,
        clickParent = clickParent,
        useAfterText = useAfterText,
        targetAfterText = targetAfterText,
        afterTextMatchType = afterTextMatchType,
        useContentDesc = useContentDesc,
        targetContentDesc = targetContentDesc,
        contentDescMatchType = contentDescMatchType,
        swipeDirection = swipeDirection
    )
}

fun RuleJsonModel.toEntity(): RuleEntity {
    return RuleEntity(
        id = 0, // Auto-generate
        name = name,
        description = description,
        packageName = packageName,
        enabled = enabled,
        priority = priority,
        useText = useText,
        targetText = targetText,
        textMatchType = textMatchType,
        useId = useId,
        targetId = targetId,
        idMatchType = idMatchType,
        useClassName = useClassName,
        targetClassName = targetClassName,
        classMatchType = classMatchType,
        useBounds = useBounds,
        targetBounds = targetBounds,
        actionType = actionType,
        customClickX = customClickX,
        customClickY = customClickY,
        delayMs = delayMs,
        clickParent = clickParent,
        useAfterText = useAfterText,
        targetAfterText = targetAfterText,
        afterTextMatchType = afterTextMatchType,
        useContentDesc = useContentDesc,
        targetContentDesc = targetContentDesc,
        contentDescMatchType = contentDescMatchType,
        swipeDirection = swipeDirection
    )
}