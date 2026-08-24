package com.blacklist.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Advanced scheduling rule.
 * Example: Block ALL_EXCEPT_WHITELIST from 22:00 to 06:00 on Mon-Fri
 * daysOfWeek: bitmask Mon=1 Tue=2 Wed=4 Thu=8 Fri=16 Sat=32 Sun=64 (127 = all days)
 * startMinutes / endMinutes: minutes from midnight (0..1439)
 * Handles overnight spans (e.g. 22*60=1320 to 6*60=360)
 */
@Entity(tableName = "schedule_rules")
data class ScheduleRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val isEnabled: Boolean = true,
    val startMinutes: Int, // 0..1439
    val endMinutes: Int,
    val daysOfWeek: Int = 127, // bitmask
    val mode: String, // ALL, ALL_EXCEPT_WHITELIST, UNKNOWN_PRIVATE, BLACKLIST
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val MODE_ALL = "ALL"
        const val MODE_ALL_EXCEPT_WHITELIST = "ALL_EXCEPT_WHITELIST"
        const val MODE_UNKNOWN_PRIVATE = "UNKNOWN_PRIVATE"
        const val MODE_BLACKLIST = "BLACKLIST"

        const val MON = 1; const val TUE = 2; const val WED = 4
        const val THU = 8; const val FRI = 16; const val SAT = 32; const val SUN = 64
        const val ALL_DAYS = 127
        const val WEEKDAYS = 31
        const val WEEKEND = 96
    }
}
