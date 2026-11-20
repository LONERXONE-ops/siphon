package com.lonerxone.siphon.data.db

import androidx.room.TypeConverter
import com.lonerxone.siphon.data.db.entity.DownloadStatus
import com.lonerxone.siphon.data.db.entity.EngineType
import com.lonerxone.siphon.data.db.entity.RuleType

class Converters {

    @TypeConverter
    fun fromDownloadStatus(status: DownloadStatus): String = status.name

    @TypeConverter
    fun toDownloadStatus(value: String): DownloadStatus =
        try {
            DownloadStatus.valueOf(value)
        } catch (e: Exception) {
            DownloadStatus.WAITING
        }

    @TypeConverter
    fun fromEngineType(type: EngineType): String = type.name

    @TypeConverter
    fun toEngineType(value: String): EngineType =
        try {
            EngineType.valueOf(value)
        } catch (e: Exception) {
            EngineType.YT_DLP
        }

    @TypeConverter
    fun fromRuleType(type: RuleType): String = type.name

    @TypeConverter
    fun toRuleType(value: String): RuleType =
        try {
            RuleType.valueOf(value)
        } catch (e: Exception) {
            RuleType.ANY_WIFI
        }
}
