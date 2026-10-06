package com.chungyak.advisor.notify

import com.chungyak.advisor.notice
import com.chungyak.advisor.notify.ScheduleAlerts.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScheduleAlertsTest {

    private val today = LocalDate.parse("2026-10-06")
    private val n = notice("A", rank1Start = "2026-10-07", rank1End = "2026-10-08", resultDate = "2026-10-15")

    @Test fun dayBefore_evening_only() {
        assertTrue(ScheduleAlerts.due(listOf(n), today, 17, emptySet()).isEmpty())
        val a = ScheduleAlerts.due(listOf(n), today, 18, emptySet()).single()
        assertEquals(Kind.RECEIPT, a.kind)
        assertTrue(a.tomorrow)
        assertEquals("내일 접수 ", a.prefix)
    }

    @Test fun missedDayBefore_firesSameDayMorning_once() {
        val day = LocalDate.parse("2026-10-07")
        assertTrue(ScheduleAlerts.due(listOf(n), day, 7, emptySet()).isEmpty())
        val a = ScheduleAlerts.due(listOf(n), day, 8, emptySet()).single()
        assertEquals(false, a.tomorrow)
        assertTrue(ScheduleAlerts.due(listOf(n), day, 9, setOf(a.key)).isEmpty())
    }

    @Test fun sentDayBefore_noRepeatOnTheDay() {
        val eve = ScheduleAlerts.due(listOf(n), today, 19, emptySet()).single()
        assertTrue(ScheduleAlerts.due(listOf(n), today.plusDays(1), 9, setOf(eve.key)).isEmpty())
    }

    @Test fun resultDate_and_quietHours() {
        val eve = LocalDate.parse("2026-10-14")
        assertEquals(Kind.RESULT, ScheduleAlerts.due(listOf(n), eve, 20, emptySet()).single().kind)
        assertTrue(ScheduleAlerts.due(listOf(n), eve, 22, emptySet()).isEmpty())
        assertTrue(ScheduleAlerts.due(listOf(n), eve.plusDays(1), 3, emptySet()).isEmpty())
    }

    @Test fun blankOrPastDates_ignored() {
        assertTrue(ScheduleAlerts.due(listOf(notice("B")), today, 19, emptySet()).isEmpty())
        assertTrue(ScheduleAlerts.due(listOf(n), LocalDate.parse("2026-10-20"), 19, emptySet()).isEmpty())
    }

    @Test fun prune_dropsOldKeys() {
        val old = ScheduleAlerts.key("A", Kind.RECEIPT, LocalDate.parse("2026-09-01"))
        val recent = ScheduleAlerts.key("A", Kind.RESULT, LocalDate.parse("2026-10-05"))
        assertEquals(setOf(recent), ScheduleAlerts.prune(setOf(old, recent, "garbage"), today))
    }
}
