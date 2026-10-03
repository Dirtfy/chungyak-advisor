package com.chungyak.advisor.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 청약통장 가입기간(만 개월) 계산 — 월말·윤년 경계 포함. */
class AccountPeriodTest {

    private fun m(opened: String, asOf: String) = AccountPeriod.months(LocalDate.parse(opened), LocalDate.parse(asOf))

    @Test fun fullMonths_needSameDay() {
        assertEquals(0, m("2019-03-15", "2019-03-15"))
        assertEquals(0, m("2019-03-15", "2019-04-14"))
        assertEquals(1, m("2019-03-15", "2019-04-15"))
        assertEquals(89, m("2019-03-15", "2026-09-14"))
        assertEquals(90, m("2019-03-15", "2026-09-15"))
        assertEquals(91, m("2019-03-15", "2026-10-15"))
        assertEquals(90, m("2019-03-15", "2026-10-14"))
    }

    @Test fun yearBoundary() {
        assertEquals(1, m("2025-12-10", "2026-01-10"))
        assertEquals(0, m("2025-12-10", "2026-01-09"))
        assertEquals(24, m("2024-10-03", "2026-10-03"))
        assertEquals(23, m("2024-10-04", "2026-10-03"))
    }

    @Test fun monthEnd_noSameDay_fillsOnLastDay() {
        assertEquals(0, m("2019-01-31", "2019-02-27"))
        assertEquals(1, m("2019-01-31", "2019-02-28"))  // 2월엔 31일이 없다 → 말일에 1개월
        assertEquals(1, m("2019-01-31", "2019-03-30"))
        assertEquals(2, m("2019-01-31", "2019-03-31"))
        assertEquals(3, m("2019-01-31", "2019-04-30"))
        assertEquals(1, m("2019-03-31", "2019-04-30"))
    }

    @Test fun leapYear() {
        assertEquals(1, m("2024-01-31", "2024-02-29"))  // 윤년 2월 말일
        assertEquals(0, m("2024-01-31", "2024-02-28"))
        assertEquals(11, m("2020-02-29", "2021-02-27"))
        assertEquals(12, m("2020-02-29", "2021-02-28")) // 평년엔 2/29가 없다 → 2/28에 1년
        assertEquals(48, m("2020-02-29", "2024-02-29"))
        assertEquals(12, m("2023-02-28", "2024-02-28"))
    }

    @Test fun futureAsOf_isMinusOne() {
        assertEquals(-1, m("2026-10-04", "2026-10-03"))
    }

    @Test fun label() {
        assertEquals("7년 6개월 (90개월)", AccountPeriod.label(90))
        assertEquals("2년 (24개월)", AccountPeriod.label(24))
        assertEquals("5개월 (5개월)", AccountPeriod.label(5))
        assertEquals("0개월 (0개월)", AccountPeriod.label(0))
    }

    @Test fun parse() {
        assertEquals(LocalDate.of(2019, 3, 15), AccountPeriod.parse("2019-03-15"))
        assertNull(AccountPeriod.parse(""))
        assertNull(AccountPeriod.parse("2019-02-30"))
        assertNull(AccountPeriod.parse("2019/03/15"))
    }

    @Test fun profile_dateWinsOverManual_andGrowsWithTime() {
        val p = Profile(accountOpened = "2024-11-20", accountMonths = 3)
        assertEquals(22, p.accountMonthsAt(LocalDate.parse("2026-10-03")))
        assertEquals(24, p.accountMonthsAt(LocalDate.parse("2026-11-20")))  // 저장값으로 굳지 않고 늘어난다
        assertEquals(3, Profile(accountMonths = 3).accountMonthsAt(LocalDate.parse("2026-10-03")))
        assertEquals(-1, Profile().accountMonthsAt(LocalDate.parse("2026-10-03")))
    }
}
