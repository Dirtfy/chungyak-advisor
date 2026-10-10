package com.chungyak.advisor.match

import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 규제지역 판별·LTV·금액 상한·스트레스 금리(v0.12.0~, docs/16). */
class LoanRulesTest {

    private val reg = LoanRules.Area(metro = true, regulated = true, source = "")
    private val metro = LoanRules.Area(metro = true, regulated = false, source = "")
    private val local = LoanRules.Area(metro = false, regulated = false, source = "")

    @Test fun area_fromNoticeFlags() {
        assertTrue(LoanRules.area(notice("a", speculation = true)).regulated)
        assertEquals("조정대상지역(공고)", LoanRules.area(notice("a", adjustment = true)).source)
        val plain = LoanRules.area(notice("a", address = "경기도 평택시 고덕동"))
        assertFalse(plain.regulated)
        assertTrue(plain.metro)
        assertEquals("수도권 비규제지역", plain.source)
    }

    @Test fun area_fromAddressList() {
        assertTrue(LoanRules.area(notice("a", areaName = "서울", address = "서울특별시 강서구 마곡동")).regulated)
        assertTrue(LoanRules.inRegulatedList("경기", "경기도 성남시 분당구 정자동"))
        assertTrue(LoanRules.inRegulatedList("경기", "경기도 화성시 동탄구 오산동"))
        assertTrue(LoanRules.inRegulatedList("경기", "경기도 용인시 기흥구 보정동"))
        assertTrue(LoanRules.inRegulatedList("경기", "경기도 구리시 인창동"))
        assertFalse(LoanRules.inRegulatedList("경기", "경기도 용인시 처인구 남사읍")) // 처인구는 비규제
        assertFalse(LoanRules.inRegulatedList("경기", "경기도 수원시 권선구 금곡동")) // 권선구는 비규제
        assertFalse(LoanRules.inRegulatedList("경기", "경기도 화성시 봉담읍"))
        assertFalse(LoanRules.inRegulatedList("인천", "인천광역시 연수구 송도동"))
    }

    @Test fun ltv_byHomesAndArea() {
        val homeless = Profile(everOwned = true)
        assertEquals(40, LoanRules.ltv(homeless, 100_000, reg).pct)
        assertEquals(70, LoanRules.ltv(homeless, 100_000, metro).pct)
        assertEquals(70, LoanRules.ltv(Profile(), 100_000, reg).pct)          // 생애최초
        assertEquals(80, LoanRules.ltv(Profile(), 100_000, local).pct)        // 지방 생애최초(참고)
        assertEquals(0, LoanRules.ltv(Profile(homesOwned = 2), 50_000, metro).pct)
        assertEquals(0, LoanRules.ltv(Profile(homesOwned = 1, everOwned = true), 50_000, metro).pct)
        val selling = Profile(homesOwned = 1, everOwned = true, sellingHome = true)
        assertEquals(40, LoanRules.ltv(selling, 50_000, reg).pct)
        assertEquals("규제지역 처분조건부 1주택", LoanRules.ltv(selling, 50_000, reg).label)
        assertEquals(70, LoanRules.ltv(selling, 50_000, metro).pct)
    }

    @Test fun ltv_seominBoundaries() {
        val s = Profile(everOwned = true, householdHead = true, incomeManwon = 9_000)
        assertEquals(60, LoanRules.ltv(s, 80_000, reg).pct)
        assertEquals(40, LoanRules.ltv(s.copy(incomeManwon = 9_001), 80_000, reg).pct)
        assertEquals(40, LoanRules.ltv(s, 80_001, reg).pct)
        assertEquals(40, LoanRules.ltv(s.copy(householdHead = false), 80_000, reg).pct)
        assertEquals(40, LoanRules.ltv(s.copy(incomeManwon = -1), 80_000, reg).pct) // 소득 미입력 → 아님
        assertEquals(70, LoanRules.ltv(s, 80_000, metro).pct)                      // 비규제는 70%
    }

    @Test fun priceCap_tiers() {
        assertEquals(60_000, LoanRules.priceCap(150_000, reg)!!.first)
        assertEquals(40_000, LoanRules.priceCap(150_001, reg)!!.first)
        assertEquals(40_000, LoanRules.priceCap(250_000, reg)!!.first)
        assertEquals(20_000, LoanRules.priceCap(250_001, reg)!!.first)
        assertEquals("시가 15억 이하 주담대 6억 상한", LoanRules.priceCap(100_000, reg)!!.second)
        assertEquals("시가 25억 초과 주담대 2억 상한", LoanRules.priceCap(300_000, reg)!!.second)
        assertEquals(60_000, LoanRules.priceCap(300_000, metro)!!.first)           // 수도권 비규제는 6억
        assertNull(LoanRules.priceCap(300_000, local))
    }

    @Test fun stressAndYears() {
        assertEquals(3.0, LoanRules.stress(RateType.VARIABLE, metro), 1e-9)
        assertEquals(2.4, LoanRules.stress(RateType.MIXED, reg), 1e-9)
        assertEquals(1.2, LoanRules.stress(RateType.PERIODIC, metro), 1e-9)
        assertEquals(0.0, LoanRules.stress(RateType.FIXED, metro), 1e-9)
        assertEquals(0.75, LoanRules.stress(RateType.VARIABLE, local), 1e-9)
        assertEquals(30, LoanRules.years(Profile(loanYears = 40), metro))
        assertEquals(40, LoanRules.years(Profile(loanYears = 40), local))
    }
}
