package com.chungyak.advisor.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 상환 방식별 계산(v0.12.0~, docs/16). 기준: 3억, 연 4%, 30년. 금액은 만원. */
class LoanScheduleTest {

    private fun of(type: Repayment, grace: Int = 0, principal: Double = 30_000.0, rate: Double = 4.0, years: Int = 30) =
        LoanSchedule.of(principal, rate, years, grace, type)

    @Test fun annuity_samePaymentEveryMonth() {
        val s = of(Repayment.ANNUITY)
        assertEquals(143.22, s.monthly, 0.01)
        assertEquals(s.monthly, s.firstMonthly, 1e-9)
        assertEquals(s.monthly, s.lastMonthly, 1e-9)
        assertEquals(21_560.85, s.totalInterest, 0.1)
        assertEquals(s.monthly * 12, s.dsrAnnual, 1e-6) // DSR = 실제 연 상환액
    }

    @Test fun equalPrincipal_firstMonthLargest_lessInterest() {
        val s = of(Repayment.EQUAL_PRINCIPAL)
        assertEquals(183.33, s.monthly, 0.01)        // 원금 83.33 + 첫 달 이자 100
        assertEquals(83.61, s.lastMonthly, 0.01)
        assertEquals(18_050.0, s.totalInterest, 0.1) // 원금 × 월이율 × (n+1)/2
        assertEquals((30_000 + 18_050.0) / 30, s.dsrAnnual, 0.01) // 실제 상환액의 연평균
        assertTrue(s.totalInterest < of(Repayment.ANNUITY).totalInterest)
    }

    @Test fun graduated_growsYearly_moreInterest() {
        val s = of(Repayment.GRADUATED)
        assertEquals(126.62, s.firstMonthly, 0.01)
        assertEquals(170.52, s.lastMonthly, 0.01)
        assertEquals(133.08, s.monthly, 0.01)        // 대표 = 초기 10년 평균
        assertEquals(s.monthly * 12, s.dsrAnnual, 1e-6)
        assertEquals(23_095.18, s.totalInterest, 0.1)
        assertTrue(s.firstMonthly < of(Repayment.ANNUITY).monthly)
        assertTrue(s.totalInterest > of(Repayment.ANNUITY).totalInterest)
    }

    @Test fun graduated_matchesGovernmentExample() {
        // 정부 예시(2022): 3억·40년·4.6% — 원리금균등 약 137만원, 체증식 첫 달 약 20만원 적고 총이자 약 3,800만원 많음.
        val g = of(Repayment.GRADUATED, rate = 4.6, years = 40)
        val a = of(Repayment.ANNUITY, rate = 4.6, years = 40)
        assertEquals(136.8, a.monthly, 0.1)
        assertTrue(a.monthly - g.firstMonthly in 15.0..25.0)
        assertTrue(g.totalInterest - a.totalInterest in 3_500.0..4_500.0)
    }

    @Test fun graduated_shortTerm_dsrUsesFirstFiveYears() {
        val s = of(Repayment.GRADUATED, years = 10)
        assertEquals(297.16, s.monthly, 0.01)
        assertEquals(s.monthly * 12, s.dsrAnnual, 1e-6)
    }

    @Test fun bullet_interestOnly_dsrCountsPrincipalOverTenYears() {
        val s = of(Repayment.BULLET)
        assertEquals(100.0, s.monthly, 1e-9)
        assertEquals(30_100.0, s.lastMonthly, 1e-9)  // 만기에 원금
        assertEquals(36_000.0, s.totalInterest, 1e-6)
        assertEquals(30_000.0 / 10 + 1_200, s.dsrAnnual, 1e-6) // 원금 ÷ 10년(최대) + 연 이자
        assertEquals(30_000.0 / 5 + 1_200, of(Repayment.BULLET, years = 5).dsrAnnual, 1e-6)
        assertEquals(100.0, of(Repayment.BULLET, grace = 1).firstMonthly, 1e-9) // 거치 무시
    }

    @Test fun grace_interestOnlyThenAmortizeOverRest() {
        val a = of(Repayment.ANNUITY, grace = 1)
        assertEquals(100.0, a.firstMonthly, 1e-9)    // 거치 중 이자만
        assertEquals(145.79, a.amortStart, 0.01)     // 남은 29년에 원리금균등
        assertEquals(21_935.63, a.totalInterest, 0.1)
        assertEquals(a.amortStart * 12, a.dsrAnnual, 1e-6) // 거치기간은 DSR 기간에서 뺀다
        val e = of(Repayment.EQUAL_PRINCIPAL, grace = 1)
        assertEquals(186.21, e.monthly, 0.01)
        assertEquals(18_650.0, e.totalInterest, 0.1)
        // 거치가 만기 이상이면 만기 − 1년으로 줄인다
        assertEquals(of(Repayment.ANNUITY, grace = 29).amortStart, of(Repayment.ANNUITY, grace = 40).amortStart, 1e-9)
    }

    @Test fun zeroRateAndZeroPrincipal() {
        val z = LoanSchedule.of(12_000.0, 0.0, 10, 0, Repayment.ANNUITY)
        assertEquals(100.0, z.monthly, 1e-9)
        assertEquals(0.0, z.totalInterest, 1e-9)
        assertEquals(100.0, LoanSchedule.of(12_000.0, 0.0, 10, 0, Repayment.GRADUATED).monthly, 5.0)
        assertEquals(LoanSchedule.ZERO, of(Repayment.ANNUITY, principal = 0.0))
    }
}
