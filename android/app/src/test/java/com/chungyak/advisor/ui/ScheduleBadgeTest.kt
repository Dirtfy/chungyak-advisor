package com.chungyak.advisor.ui

import com.chungyak.advisor.notice
import com.chungyak.advisor.ui.ScheduleBadge.Tone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleBadgeTest {

    private val n = notice("a", rank1Start = "2026-10-10", rank1End = "2026-10-11", resultDate = "2026-10-20")

    private fun at(today: String) = ScheduleBadge.of(n, today)

    @Test fun beforeReceipt() = assertEquals(ScheduleBadge.Badge("접수 D-6", Tone.UPCOMING), at("2026-10-04"))
    @Test fun dayOfReceipt() = assertEquals("접수 중", at("2026-10-10")?.text)
    @Test fun lastDayOfReceipt() = assertEquals(Tone.OPEN, at("2026-10-11")?.tone)
    @Test fun waitingResult() = assertEquals(ScheduleBadge.Badge("발표 D-1", Tone.WAITING), at("2026-10-19"))
    @Test fun resultDone() = assertEquals(Tone.CLOSED, at("2026-10-20")?.tone)
    @Test fun noDates() = assertNull(ScheduleBadge.of(notice("b"), "2026-10-04"))
}
