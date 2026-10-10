package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 자금 판정(v0.11.0~, docs/15). 기본 가정: 연 4%, 30년, 원리금균등, 계약금 10%. */
class AffordabilityTest {

    /** 현금 3억, 대출 한도 5억, 월 상환 상한 200만원 → 월 200만원으로 갚을 수 있는 원금 약 4.19억이 실제 대출. */
    private val p = Profile(cashManwon = 30_000, loanLimitManwon = 50_000, monthlyCapManwon = 200)

    private fun model(no: String, price: Int) = HouseModel("N", no, "0$no.0000A", 0.0, 10, price)

    @Test fun principalFor_annuityAndEqualPrincipal() {
        assertEquals(41_892, Affordability.principalFor(200, 4.0, 30, Repayment.ANNUITY))
        assertEquals(32_727, Affordability.principalFor(200, 4.0, 30, Repayment.EQUAL_PRINCIPAL)) // 첫 달 기준
        assertEquals(12_000, Affordability.principalFor(100, 0.0, 10, Repayment.ANNUITY))      // 무이자 = 월액 × 개월
        assertEquals(0, Affordability.principalFor(0, 4.0, 30, Repayment.ANNUITY))
    }

    @Test fun monthlyPayment_matchesFormula() {
        assertEquals(143.22, Affordability.monthlyPayment(30_000, 4.0, 30, Repayment.ANNUITY), 0.01)
        assertEquals(183.33, Affordability.monthlyPayment(30_000, 4.0, 30, Repayment.EQUAL_PRINCIPAL), 0.01)
        assertEquals(0.0, Affordability.monthlyPayment(0, 4.0, 30, Repayment.ANNUITY), 0.0)
    }

    @Test fun budget_usesSmallerOfLimitAndCap() {
        val b = Affordability.budget(p)!!
        assertEquals(50_000, b.loanByLimit)
        assertEquals(41_892, b.loanByCap)
        assertEquals(41_892, b.usableLoan)
        assertEquals(71_892, b.total)
        // 한도가 더 작으면 한도
        assertEquals(20_000, Affordability.budget(p.copy(loanLimitManwon = 20_000))!!.usableLoan)
        // 한쪽만 넣으면 그쪽, 둘 다 없으면 현금만
        assertEquals(41_892, Affordability.budget(p.copy(loanLimitManwon = -1))!!.usableLoan)
        assertEquals(50_000, Affordability.budget(p.copy(monthlyCapManwon = -1))!!.usableLoan)
        assertEquals(0, Affordability.budget(p.copy(loanLimitManwon = -1, monthlyCapManwon = -1))!!.usableLoan)
    }

    @Test fun noCash_noBudget() {
        assertNull(Affordability.budget(Profile(loanLimitManwon = 50_000)))
        assertNull(Affordability.evaluate(Profile(), notice("A", priceMin = 50_000, priceMax = 60_000), emptyList()))
    }

    @Test fun check_okTightNo() {
        val b = Affordability.budget(p)!!
        val ok = Affordability.check(b, p, 60_000)!!
        assertEquals(Affordability.Verdict.OK, ok.verdict)
        assertEquals(30_000, ok.loanNeeded)
        assertEquals(143, ok.monthly)
        assertEquals(6_000, ok.downPayment)
        assertEquals(11_892, ok.margin)

        // 살 수는 있지만 남는 돈(1,892만원)이 분양가의 5% 미만 → 빠듯
        val tight = Affordability.check(b, p, 70_000)!!
        assertEquals(Affordability.Verdict.TIGHT, tight.verdict)
        assertEquals(0, tight.shortfall)

        val no = Affordability.check(b, p, 80_000)!!
        assertEquals(Affordability.Verdict.NO, no.verdict)
        assertEquals(50_000 - 41_892, no.shortfall)

        assertNull(Affordability.check(b, p, 0))
    }

    @Test fun check_downPaymentNotCoveredByCash_isTight() {
        val q = Profile(cashManwon = 3_000, loanLimitManwon = 100_000)
        val c = Affordability.check(Affordability.budget(q)!!, q, 50_000)!!
        assertEquals(Affordability.Verdict.TIGHT, c.verdict)
        assertEquals(5_000, c.downPayment)
        assertEquals(2_000, c.downShort)
        // 계약금 20%로 바꾸면 모자란 현금도 늘어난다
        val q20 = q.copy(downPaymentPct = 20)
        assertEquals(7_000, Affordability.check(Affordability.budget(q20)!!, q20, 50_000)!!.downShort)
    }

    @Test fun evaluate_perModel_bestAndWorst() {
        val r = Affordability.evaluate(p, notice("N"), listOf(model("1", 80_000), model("2", 60_000), model("3", 70_000), model("4", 0)))!!
        assertEquals(3, r.checks.size)
        assertEquals(60_000, r.best.price)
        assertEquals(Affordability.Verdict.OK, r.best.verdict)
        assertEquals(80_000, r.worst.price)
        assertEquals(Affordability.Verdict.NO, r.worst.verdict)
        assertEquals(2, r.okCount)
    }

    @Test fun evaluate_withoutModels_usesNoticeRange() {
        val r = Affordability.evaluate(p, notice("N", priceMin = 60_000, priceMax = 80_000), emptyList())!!
        assertEquals(listOf(60_000, 80_000), r.checks.map { it.price })
        assertNull(Affordability.evaluate(p, notice("N"), emptyList())) // 분양가 정보 없음
    }

    @Test fun cashOnly_canStillBeOk() {
        val q = Profile(cashManwon = 100_000)
        val c = Affordability.check(Affordability.budget(q)!!, q, 60_000)!!
        assertEquals(Affordability.Verdict.OK, c.verdict)
        assertEquals(0, c.loanNeeded)
        assertEquals(0, c.monthly)
    }
}
