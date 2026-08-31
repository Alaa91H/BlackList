package com.blacklist.app.util

import com.blacklist.app.data.local.entity.BlacklistRuleEntity
import com.blacklist.app.data.local.entity.ScheduleRuleEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ScheduleEvaluatorTest {
    @Test
    fun `overnight window includes both sides of midnight`() {
        assertTrue(ScheduleEvaluator.isInTimeWindow(23 * 60, 22 * 60, 6 * 60))
        assertTrue(ScheduleEvaluator.isInTimeWindow(2 * 60, 22 * 60, 6 * 60))
        assertFalse(ScheduleEvaluator.isInTimeWindow(12 * 60, 22 * 60, 6 * 60))
    }

    @Test
    fun `after midnight portion belongs to the day on which an overnight rule started`() {
        val mondayNight = ScheduleRuleEntity(
            startMinutes = 22 * 60,
            endMinutes = 6 * 60,
            daysOfWeek = ScheduleRuleEntity.MON,
            mode = ScheduleRuleEntity.MODE_ALL
        )
        val tuesdayAtTwo = calendarMillis(2026, Calendar.JANUARY, 6, 2)
        assertEquals(mondayNight, ScheduleEvaluator.matchingRule(listOf(mondayNight), tuesdayAtTwo))
    }

    @Test
    fun `same rule does not extend into the following afternoon`() {
        val mondayNight = ScheduleRuleEntity(
            startMinutes = 22 * 60,
            endMinutes = 6 * 60,
            daysOfWeek = ScheduleRuleEntity.MON,
            mode = ScheduleRuleEntity.MODE_ALL
        )
        val tuesdayAtNoon = calendarMillis(2026, Calendar.JANUARY, 6, 12)
        assertEquals(null, ScheduleEvaluator.matchingRule(listOf(mondayNight), tuesdayAtNoon))
    }

    @Test
    fun `disabled blacklist schedule keeps rule active`() {
        val rule = BlacklistRuleEntity(
            ruleType = BlacklistRuleEntity.TYPE_EXACT,
            pattern = "+491234567",
            scheduleEnabled = false
        )
        assertTrue(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 2, 12)))
    }

    @Test
    fun `regular blacklist window matches selected weekdays and boundaries`() {
        val rule = scheduledBlacklistRule(9 * 60, 17 * 60, ScheduleRuleEntity.WEEKDAYS)
        assertTrue(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 2, 9)))
        assertTrue(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 6, 16, 59)))
        assertFalse(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 7, 12)))
        assertFalse(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 3, 17, 1)))
    }

    @Test
    fun `overnight blacklist window continues into following day only`() {
        val rule = scheduledBlacklistRule(22 * 60, 6 * 60, ScheduleRuleEntity.MON)
        assertTrue(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 2, 23, 30)))
        assertTrue(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 3, 5, 59)))
        assertFalse(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 3, 6, 1)))
        assertFalse(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 3, 23)))
    }

    @Test
    fun `invalid blacklist schedule is inactive`() {
        val rule = scheduledBlacklistRule(-1, 1500, 0)
        assertFalse(ScheduleEvaluator.isRuleActive(rule, calendarMillis(2026, Calendar.MARCH, 2, 12)))
    }

    @Test
    fun `format helpers use stable human readable values`() {
        assertEquals("09:05", ScheduleEvaluator.formatMinutes(545))
        assertEquals("Weekdays", ScheduleEvaluator.formatDays(ScheduleRuleEntity.WEEKDAYS))
        assertEquals("Mon, Sun", ScheduleEvaluator.formatDays(ScheduleRuleEntity.MON or ScheduleRuleEntity.SUN))
    }

    private fun scheduledBlacklistRule(start: Int, end: Int, days: Int) = BlacklistRuleEntity(
        ruleType = BlacklistRuleEntity.TYPE_EXACT,
        pattern = "+491234567",
        scheduleEnabled = true,
        scheduleStartMinutes = start,
        scheduleEndMinutes = end,
        scheduleDaysOfWeek = days
    )

    private fun calendarMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0): Long =
        Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis
}
