package com.adskipper.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromMatchType(matchType: MatchType): String = matchType.value

    @TypeConverter
    fun toMatchType(value: String): MatchType = MatchType.fromValue(value)

    @TypeConverter
    fun fromActionType(actionType: ActionType): String = actionType.value

    @TypeConverter
    fun toActionType(value: String): ActionType = ActionType.fromValue(value)
}