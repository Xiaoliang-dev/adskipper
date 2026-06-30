package com.adskipper.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverters

@Entity(tableName = "rules")
@TypeConverters(Converters::class)
data class RuleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val description: String = "",
    val packageName: String,
    val enabled: Boolean = true,
    val priority: Int = 0,
    val useText: Boolean = false,
    val targetText: String = "",
    val textMatchType: String = "contains",
    val useId: Boolean = false,
    val targetId: String = "",
    val idMatchType: String = "contains",
    val useClassName: Boolean = false,
    val targetClassName: String = "",
    val classMatchType: String = "contains",
    val useBounds: Boolean = false,
    val targetBounds: String = "",
    val actionType: String = "click",
    val customClickX: Int = -1,
    val customClickY: Int = -1,
    val delayMs: Long = 0,
    val clickParent: Boolean = false,
    val useAfterText: Boolean = false,
    val targetAfterText: String = "",
    val afterTextMatchType: String = "contains",
    val useContentDesc: Boolean = false,
    val targetContentDesc: String = "",
    val contentDescMatchType: String = "contains",
    val swipeDirection: String = "none",
    val createdAt: Long = System.currentTimeMillis(),
    val lastTriggeredAt: Long? = null,
    val triggerCount: Long = 0
)

enum class MatchType(val value: String) {
    EXACT("exact"),
    CONTAINS("contains"),
    STARTS_WITH("starts_with"),
    ENDS_WITH("ends_with"),
    REGEX("regex");

    companion object {
        fun fromValue(value: String): MatchType {
            return entries.find { it.value == value } ?: CONTAINS
        }
    }
}

enum class ActionType(val value: String) {
    CLICK("click"),
    CLICK_PARENT("click_parent"),
    SWIPE_LEFT("swipe_left"),
    SWIPE_RIGHT("swipe_right"),
    BACK("back"),
    CUSTOM_CLICK("custom_click");

    companion object {
        fun fromValue(value: String): ActionType {
            return entries.find { it.value == value } ?: CLICK
        }
    }
}