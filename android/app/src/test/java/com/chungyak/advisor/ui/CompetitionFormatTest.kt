package com.chungyak.advisor.ui

import com.chungyak.advisor.data.CompetitionPolicy
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionFormatTest {

    private val today = "2026-10-02"

    @Test fun rateFormat() {
        assertEquals("12.35:1", CompetitionFormat.rate(12.345))
        assertEquals("0.70:1", CompetitionFormat.rate(0.7))
        assertEquals("123.4:1", CompetitionFormat.rate(123.4))
        assertEquals("-", CompetitionFormat.rate(0.0))
    }

    @Test fun summaryStates() {
        val upcoming = notice("u", rank1Start = "2026-10-10", rank1End = "2026-10-10")
        val open = notice("o", rank1Start = "2026-09-29", rank1End = "2026-09-30", resultDate = "2026-10-08")
        assertEquals("최고 10.00:1 · 평균 4.00:1",
            CompetitionFormat.summary(open.copy(cmpetMaxRate = 10.0, cmpetAvgRate = 4.0), today, true))
        assertEquals("최고 1.20:1 · 평균 0.60:1 (미달)",
            CompetitionFormat.summary(open.copy(cmpetMaxRate = 1.2, cmpetAvgRate = 0.6), today, false))
        assertEquals(CompetitionFormat.BEFORE, CompetitionFormat.summary(upcoming, today, true))
        assertEquals(CompetitionFormat.NEED_APPLY, CompetitionFormat.summary(open, today, true))
        assertEquals(CompetitionFormat.PENDING, CompetitionFormat.summary(open, today, false))
        assertEquals(CompetitionFormat.NONE, CompetitionFormat.summary(open.copy(cmpetFinal = true), today, false))
    }

    @Test fun policy_fetchWindowAndFinal() {
        val now = 10 * CompetitionPolicy.REFETCH_MS
        val upcoming = notice("u", rank1Start = "2026-10-10")
        val open = notice("o", rank1Start = "2026-09-29", rank1End = "2026-09-30", resultDate = "2026-10-08")
        assertFalse(CompetitionPolicy.shouldFetch(upcoming, today, now))
        assertTrue(CompetitionPolicy.shouldFetch(open, today, now))
        // 방금 받았으면 간격 전까지 재조회 안 함, 확정이면 영영 안 함.
        assertFalse(CompetitionPolicy.shouldFetch(open.copy(cmpetFetchedAt = now - 1000), today, now))
        assertTrue(CompetitionPolicy.shouldFetch(open.copy(cmpetFetchedAt = now - CompetitionPolicy.REFETCH_MS), today, now))
        assertFalse(CompetitionPolicy.shouldFetch(open.copy(cmpetFinal = true), today, now))
        // 발표일 전엔 미확정, 발표일 당일부터 확정.
        assertFalse(CompetitionPolicy.isFinal(open, today))
        assertTrue(CompetitionPolicy.isFinal(open, "2026-10-08"))
        // 발표일 없으면 1순위 종료일 기준.
        assertTrue(CompetitionPolicy.isFinal(notice("x", rank1End = "2026-09-30"), today))
    }
}
