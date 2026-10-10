package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.match.Affordability.Limit
import com.chungyak.advisor.match.Affordability.Verdict
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 자금 판정(v0.11.0~, docs/15·16). 기본 가정: 연 4%, 30년, 원리금균등, 혼합형, 계약금 10%.
 * 기본 프로필은 무주택 생애최초, 기본 공고(테스트 notice())는 경기 비규제 → LTV 70%, 주담대 6억 상한.
 */
class AffordabilityTest {

    /** 현금 3억, 대출 한도 5억, 월 상환 상한 200만원 → 월 200만원으로 갚을 수 있는 원금 약 4.19억. */
    private val p = Profile(cashManwon = 30_000, loanLimitManwon = 50_000, monthlyCapManwon = 200)
    private val reg = LoanRules.Area(metro = true, regulated = true, source = "")

    private fun model(no: String, price: Int) = HouseModel("N", no, "0$no.0000A", 0.0, 10, price)

    private fun check(q: Profile, price: Int, a: LoanRules.Area = Affordability.DEFAULT_AREA) =
        Affordability.check(Affordability.budget(q, a)!!, q, price, a)!!

    @Test fun principalFor_byRepayment() {
        assertEquals(41_892, Affordability.principalFor(200.0, 4.0, 30, 0, Repayment.ANNUITY))
        assertEquals(32_727, Affordability.principalFor(200.0, 4.0, 30, 0, Repayment.EQUAL_PRINCIPAL)) // 첫 달 기준
        assertEquals(12_000, Affordability.principalFor(100.0, 0.0, 10, 0, Repayment.ANNUITY))        // 무이자 = 월액 × 개월
        assertEquals(0, Affordability.principalFor(0.0, 4.0, 30, 0, Repayment.ANNUITY))
    }

    @Test fun budget_personalLimits() {
        val b = Affordability.budget(p)!!
        assertEquals(50_000, b.loanByLimit)
        assertEquals(41_892, b.loanByCap)
        assertNull(b.loanByDsr) // 연소득 미입력
        assertEquals(41_892, b.personalMax)
        assertNull(Affordability.budget(Profile(cashManwon = 0))!!.personalMax)
        // 수도권은 만기 30년 이내 → 40년으로 넣어도 30년으로 계산
        assertEquals(41_892, Affordability.budget(p.copy(loanYears = 40))!!.loanByCap)
    }

    @Test fun dsr_principalByIncomeDebtAndRateType() {
        val q = Profile(cashManwon = 0, incomeManwon = 10_000)
        val a = Affordability.DEFAULT_AREA
        assertEquals(53_290, Affordability.dsrPrincipal(q, a))                       // 4% + 2.4%p(혼합형)
        assertEquals(39_967, Affordability.dsrPrincipal(q.copy(debtAnnualManwon = 1_000), a))
        assertEquals(0, Affordability.dsrPrincipal(q.copy(debtAnnualManwon = 4_000), a)) // 여유 0
        assertEquals(50_102, Affordability.dsrPrincipal(q.copy(rateType = RateType.VARIABLE), a))
        assertEquals(60_704, Affordability.dsrPrincipal(q.copy(rateType = RateType.PERIODIC), a))
        assertEquals(69_820, Affordability.dsrPrincipal(q.copy(rateType = RateType.FIXED), a))
        assertEquals(61_141, Affordability.dsrPrincipal(q.copy(repayment = Repayment.EQUAL_PRINCIPAL), a))
        assertEquals(56_433, Affordability.dsrPrincipal(q.copy(repayment = Repayment.GRADUATED), a))
        assertEquals(24_390, Affordability.dsrPrincipal(q.copy(repayment = Repayment.BULLET), a)) // 원금 ÷ 10년
        assertNull(Affordability.dsrPrincipal(q.copy(incomeManwon = -1), a))
    }

    @Test fun noCash_noBudget() {
        assertNull(Affordability.budget(Profile(loanLimitManwon = 50_000)))
        assertNull(Affordability.evaluate(Profile(), notice("A", priceMin = 50_000, priceMax = 60_000), emptyList()))
    }

    @Test fun check_okTightNo() {
        val ok = check(p, 60_000)
        assertEquals(Verdict.OK, ok.verdict)
        assertEquals(30_000, ok.loanNeeded)
        assertEquals(143, ok.monthly)
        assertEquals(21_561, ok.totalInterest)
        assertEquals(6_000, ok.downPayment)
        assertEquals(11_892, ok.margin)
        assertEquals(Limit.MONTHLY_CAP, ok.loan.binding)
        assertEquals(42_000, ok.loan.byLtv)

        // 살 수는 있지만 남는 돈(1,892만원)이 분양가의 5% 미만 → 빠듯
        val tight = check(p, 70_000)
        assertEquals(Verdict.TIGHT, tight.verdict)
        assertEquals(0, tight.shortfall)

        val no = check(p, 80_000)
        assertEquals(Verdict.NO, no.verdict)
        assertEquals(50_000 - 41_892, no.shortfall)

        assertNull(Affordability.check(Affordability.budget(p)!!, p, 0))
    }

    @Test fun ltvBinds_regulatedHomeless() {
        val c = check(Profile(cashManwon = 50_000, everOwned = true), 100_000, reg)
        assertEquals(Limit.LTV, c.loan.binding)
        assertEquals(40, c.loan.ltv.pct)
        assertEquals(40_000, c.loan.usable)
        assertEquals(Verdict.NO, c.verdict)
        assertEquals(10_000, c.shortfall)
        // 중도금도 LTV 40%까지 → 중도금 60% 중 20%는 현금
        assertEquals(40_000, c.midLoan)
        assertEquals(30_000, c.cashDuringBuild)
    }

    @Test fun priceCapBinds_firstTimeRegulated() {
        val first = Profile(cashManwon = 50_000)
        val c = check(first, 100_000, reg)
        assertEquals(Limit.PRICE_CAP, c.loan.binding)
        assertEquals(70_000, c.loan.byLtv)
        assertEquals(60_000, c.loan.usable)
        assertEquals(Verdict.OK, c.verdict)
        assertEquals(60_000, check(first, 150_000, reg).loan.usable)
        assertEquals(40_000, check(first, 150_001, reg).loan.usable)
        assertEquals(40_000, check(first, 250_000, reg).loan.usable)
        assertEquals(20_000, check(first, 250_001, reg).loan.usable)
    }

    @Test fun dsrBinds() {
        val q = Profile(cashManwon = 50_000, incomeManwon = 10_000)
        val c = check(q, 100_000)
        assertEquals(Limit.DSR, c.loan.binding)
        assertEquals(53_290, c.loan.usable)
        assertEquals(Verdict.TIGHT, c.verdict)               // 여유 3,290만원 < 5%
        assertEquals(2.4, c.loan.stressPct, 1e-9)
        val withDebt = check(q.copy(debtAnnualManwon = 1_000), 100_000)
        assertEquals(Verdict.NO, withDebt.verdict)
        assertEquals(10_033, withDebt.shortfall)
        // 서민·실수요자 LTV 60%(4.8억)보다 DSR(4.7961억)이 작으면 DSR
        val s = check(Profile(cashManwon = 50_000, everOwned = true, householdHead = true, incomeManwon = 9_000), 80_000, reg)
        assertEquals(60, s.loan.ltv.pct)
        assertEquals(Limit.DSR, s.loan.binding)
        assertEquals(47_961, s.loan.usable)
    }

    @Test fun userLimitBinds() {
        val c = check(Profile(cashManwon = 50_000, loanLimitManwon = 20_000), 60_000)
        assertEquals(Limit.USER_LIMIT, c.loan.binding)
        assertEquals(20_000, c.loan.usable)
    }

    @Test fun homesOwned_banned() {
        val multi = check(Profile(cashManwon = 50_000, homesOwned = 2), 60_000)
        assertEquals(Limit.BANNED, multi.loan.binding)
        assertEquals(0, multi.loan.usable)
        assertEquals(Verdict.NO, multi.verdict)
        assertEquals(Limit.BANNED, check(Profile(cashManwon = 50_000, homesOwned = 1, everOwned = true), 60_000).loan.binding)
        // 처분조건부 1주택은 무주택과 같게: 규제지역 40%
        val selling = check(Profile(cashManwon = 50_000, homesOwned = 1, everOwned = true, sellingHome = true), 60_000, reg)
        assertEquals(Limit.LTV, selling.loan.binding)
        assertEquals(24_000, selling.loan.usable)
        assertEquals(Verdict.OK, selling.verdict)
        // 현금만으로 사면 주택 수와 상관없이 가능
        assertEquals(Verdict.OK, check(Profile(cashManwon = 100_000, homesOwned = 2), 60_000).verdict)
    }

    @Test fun check_cashDuringBuildShort_isTight() {
        // 계약금 40%면 중도금 60%(대출)까지 합쳐 입주 전 현금 2억이 필요한데 현금은 1.8억
        val q = Profile(cashManwon = 18_000, downPaymentPct = 40)
        val c = check(q, 50_000)
        assertEquals(Verdict.TIGHT, c.verdict)
        assertEquals(20_000, c.downPayment)
        assertEquals(30_000, c.midLoan)
        assertEquals(20_000, c.cashDuringBuild)
        assertEquals(2_000, c.downShort)
    }

    @Test fun evaluate_usesNoticeArea() {
        val q = Profile(cashManwon = 50_000, everOwned = true)
        val plain = Affordability.evaluate(q, notice("N", priceMax = 100_000), emptyList())!!
        assertEquals(60_000, plain.best.loan.usable)           // 비규제 70% → 6억 상한
        val regulated = Affordability.evaluate(q, notice("N", priceMax = 100_000, adjustment = true), emptyList())!!
        assertEquals(40_000, regulated.best.loan.usable)       // 규제 40%
        val seoul = Affordability.evaluate(q, notice("N", priceMax = 100_000, areaName = "서울", address = "서울특별시 강동구"), emptyList())!!
        assertEquals(true, seoul.best.area.regulated)
    }

    @Test fun evaluate_perModel_bestAndWorst() {
        val r = Affordability.evaluate(p, notice("N"), listOf(model("1", 80_000), model("2", 60_000), model("3", 70_000), model("4", 0)))!!
        assertEquals(3, r.checks.size)
        assertEquals(60_000, r.best.price)
        assertEquals(Verdict.OK, r.best.verdict)
        assertEquals(80_000, r.worst.price)
        assertEquals(Verdict.NO, r.worst.verdict)
        assertEquals(2, r.okCount)
    }

    @Test fun evaluate_withoutModels_usesNoticeRange() {
        val r = Affordability.evaluate(p, notice("N", priceMin = 60_000, priceMax = 80_000), emptyList())!!
        assertEquals(listOf(60_000, 80_000), r.checks.map { it.price })
        assertNull(Affordability.evaluate(p, notice("N"), emptyList())) // 분양가 정보 없음
    }

    @Test fun cashOnly_canStillBeOk() {
        val c = check(Profile(cashManwon = 100_000), 60_000)
        assertEquals(Verdict.OK, c.verdict)
        assertEquals(0, c.loanNeeded)
        assertEquals(0, c.monthly)
        assertEquals(0, c.totalInterest)
    }
}
