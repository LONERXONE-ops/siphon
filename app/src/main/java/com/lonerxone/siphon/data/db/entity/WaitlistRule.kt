package com.lonerxone.siphon.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class RuleType {
    ANY_WIFI,
    SPECIFIC_WIFI
}

@Entity(tableName = "waitlist_rules")
data class WaitlistRule(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val ruleType: RuleType = RuleType.ANY_WIFI,
    val allowMetered: Boolean = false,
    val requireCharging: Boolean = false,
    val windowStartMinutes: Int? = null, // e.g. 23 * 60 = 1380 (11 PM)
    val windowEndMinutes: Int? = null     // e.g. 6 * 60 = 360 (6 AM)
)

@Entity(tableName = "saved_networks")
data class SavedNetwork(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val ssid: String,
    val label: String
)
