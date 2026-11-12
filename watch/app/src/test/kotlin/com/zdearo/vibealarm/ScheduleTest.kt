package com.zdearo.vibealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleTest {
    private val zone = ZoneId.of("UTC")
    private fun ms(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).atZone(zone).toInstant().toEpochMilli()

    private fun alarm(id: String, h: Int, m: Int, days: Set<Int>, enabled: Boolean = true, at: Long = 0) =
        Alarm(id, h, m, days, "", enabled, 1, 40, 10, 10, at)

    // 2026-11-14 é sábado.
    private val saturday10 = ms(2026, 11, 14, 10, 0)
    private val weekdays = setOf(1, 2, 3, 4, 5)

    @Test fun weekdayAlarmOnSaturdayFiresMonday() {
        val n = Schedule.next(listOf(alarm("a", 6, 45, weekdays)), saturday10, 0, zone)!!
        assertEquals(ms(2026, 11, 16, 6, 45), n.at)
    }

    @Test fun laterTodayCountsBeforeTomorrow() {
        val n = Schedule.next(listOf(alarm("a", 11, 0, setOf(6))), saturday10, 0, zone)!!
        assertEquals(ms(2026, 11, 14, 11, 0), n.at)
    }

    @Test fun sameMinuteAlreadyPassedMovesToNextWeek() {
        val n = Schedule.next(listOf(alarm("a", 10, 0, setOf(6))), saturday10, 0, zone)!!
        assertEquals(ms(2026, 11, 21, 10, 0), n.at)
    }

    @Test fun skipUntilExcludesThatOccurrence() {
        val a = alarm("a", 6, 45, weekdays)
        val first = Schedule.next(listOf(a), saturday10, 0, zone)!!
        val second = Schedule.next(listOf(a), saturday10, first.at, zone)!!
        assertEquals(ms(2026, 11, 17, 6, 45), second.at)
    }

    @Test fun earliestAlarmWinsAndDisabledIsIgnored() {
        val list = listOf(alarm("late", 9, 0, weekdays), alarm("early", 6, 0, weekdays), alarm("off", 5, 0, weekdays, enabled = false))
        assertEquals("early", Schedule.next(list, saturday10, 0, zone)!!.alarm.id)
    }

    @Test fun onceAlarmUsesAbsoluteTimeAndExpires() {
        val at = ms(2026, 11, 15, 7, 15)
        val once = alarm("o", 7, 15, emptySet(), at = at)
        assertEquals(at, Schedule.next(listOf(once), saturday10, 0, zone)!!.at)
        assertNull(Schedule.next(listOf(once), at + 1, 0, zone))
    }

    @Test fun parsingClampsOutOfRangeValues() {
        val json = """[{"id":"x","hour":99,"minute":-5,"days":[0,3,9],"enabled":true,"rampSeconds":1,"walkSeconds":500,"timeoutMin":0}]"""
        val a = Alarm.listFromJson(json).single()
        assertEquals(23, a.hour)
        assertEquals(0, a.minute)
        assertEquals(setOf(3), a.days)
        assertTrue(a.rampSeconds == 15 && a.walkSeconds == 120 && a.timeoutMin == 1)
    }

    @Test fun oldListsWithStepsAreConvertedToWalkSeconds() {
        val old = """[{"id":"x","hour":6,"minute":0,"days":[],"enabled":true,"steps":10}]"""
        assertEquals(20, Alarm.listFromJson(old).single().walkSeconds)
        val zero = """[{"id":"x","hour":6,"minute":0,"days":[],"enabled":true,"steps":0}]"""
        assertEquals(5, Alarm.listFromJson(zero).single().walkSeconds)
    }
}
