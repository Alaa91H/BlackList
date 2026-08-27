package com.blacklist.app.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BlockedCallStatsTest {
    private val utc = TimeZone.getTimeZone("UTC")

    @Test
    fun `start of day keeps the calendar date and resets the clock`() {
        val input = millisAt(2026, Calendar.AUGUST, 27, 23, 59, 58, 987)

        val result = BlockedCallStats.startOfDayMillis(input, utc)

        assertEquals(millisAt(2026, Calendar.AUGUST, 27, 0, 0, 0, 0), result)
    }

    @Test
    fun `start of day uses the supplied time zone across date boundaries`() {
        val input = millisAt(2026, Calendar.AUGUST, 28, 0, 30, 0, 0)
        val berlin = TimeZone.getTimeZone("Europe/Berlin")

        val result = BlockedCallStats.startOfDayMillis(input, berlin)
        val calendar = Calendar.getInstance(berlin).apply { timeInMillis = result }

        assertEquals(0, calendar.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, calendar.get(Calendar.MINUTE))
        assertEquals(28, calendar.get(Calendar.DAY_OF_MONTH))
    }

    private fun millisAt(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int, millisecond: Int): Long =
        Calendar.getInstance(utc).run {
            clear()
            set(year, month, day, hour, minute, second)
            set(Calendar.MILLISECOND, millisecond)
            timeInMillis
        }
}
