package com.blacklist.app.util

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
        val tuesdayAtTwo = Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(2026, Calendar.JANUARY, 6, 2, 0, 0) // Tuesday
        }.timeInMillis

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
        val tuesdayAtNoon = Calendar.getInstance(TimeZone.getDefault()).apply {
            clear()
            set(2026, Calendar.JANUARY, 6, 12, 0, 0)
        }.timeInMillis

        assertEquals(null, ScheduleEvaluator.matchingRule(listOf(mondayNight), tuesdayAtNoon))
    }
}
