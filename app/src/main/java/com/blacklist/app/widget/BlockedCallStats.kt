package com.blacklist.app.widget

import java.util.Calendar
import java.util.TimeZone

internal object BlockedCallStats {
    fun startOfDayMillis(nowMillis: Long, timeZone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(timeZone).run {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            timeInMillis
        }
}
