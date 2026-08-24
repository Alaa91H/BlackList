package com.blacklist.app.util

import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import java.util.Calendar

object ScheduleEvaluator {

    /**
     * Returns the first matching enabled rule for current time, or null if none matches.
     * Used by CallScreeningService to decide blocking mode during scheduled windows.
     */
    fun matchingRule(rules: List<ScheduleRuleEntity>, nowMillis: Long = System.currentTimeMillis()): ScheduleRuleEntity? {
        if (rules.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = nowMillis }
        val dayBit = dayToBit(cal.get(Calendar.DAY_OF_WEEK))
        val minutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        for (rule in rules) {
            if (!rule.isEnabled) continue
            if ((rule.daysOfWeek and dayBit) == 0) continue
            if (isInTimeWindow(minutes, rule.startMinutes, rule.endMinutes)) {
                return rule
            }
        }
        return null
    }

    fun isInTimeWindow(currentMin: Int, startMin: Int, endMin: Int): Boolean {
        return if (startMin <= endMin) {
            currentMin in startMin..endMin
        } else {
            // Overnight span e.g. 22:00-06:00
            currentMin >= startMin || currentMin <= endMin
        }
    }

    private fun dayToBit(calendarDay: Int): Int = when (calendarDay) {
        Calendar.MONDAY -> ScheduleRuleEntity.MON
        Calendar.TUESDAY -> ScheduleRuleEntity.TUE
        Calendar.WEDNESDAY -> ScheduleRuleEntity.WED
        Calendar.THURSDAY -> ScheduleRuleEntity.THU
        Calendar.FRIDAY -> ScheduleRuleEntity.FRI
        Calendar.SATURDAY -> ScheduleRuleEntity.SAT
        Calendar.SUNDAY -> ScheduleRuleEntity.SUN
        else -> 0
    }

    fun formatMinutes(min: Int): String {
        val h = min / 60
        val m = min % 60
        return String.format("%02d:%02d", h, m)
    }

    fun formatDays(bitmask: Int): String {
        if (bitmask == ScheduleRuleEntity.ALL_DAYS) return "Every day"
        if (bitmask == ScheduleRuleEntity.WEEKDAYS) return "Weekdays"
        if (bitmask == ScheduleRuleEntity.WEEKEND) return "Weekend"
        val names = mutableListOf<String>()
        if (bitmask and ScheduleRuleEntity.MON != 0) names += "Mon"
        if (bitmask and ScheduleRuleEntity.TUE != 0) names += "Tue"
        if (bitmask and ScheduleRuleEntity.WED != 0) names += "Wed"
        if (bitmask and ScheduleRuleEntity.THU != 0) names += "Thu"
        if (bitmask and ScheduleRuleEntity.FRI != 0) names += "Fri"
        if (bitmask and ScheduleRuleEntity.SAT != 0) names += "Sat"
        if (bitmask and ScheduleRuleEntity.SUN != 0) names += "Sun"
        return names.joinToString(", ")
    }
}
