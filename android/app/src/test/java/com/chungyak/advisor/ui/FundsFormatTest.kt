package com.chungyak.advisor.ui

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.LoanRules
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.RateType
import com.chungyak.advisor.match.Repayment
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FundsFormatTest {

    private val p = Profile(cashManwon = 30_000, loanLimitManwon = 50_000, monthlyCapManwon = 200)
    private val reg = LoanRules.Area(metro = true, regulated = true, source = "조정대상지역(공고)")

    private fun model(no: String, price: Int) = HouseModel("N", no, "0$no.0000A", 0.0, 10, price)

    private fun check(q: Profile, price: Int, a: LoanRules.Area = Affordability.DEFAULT_AREA) =
        Affordability.check(Affordability.budget(q, a)!!, q, price, a)!!

    @Test fun summary_allOk() {
        val r = Affordability.evaluate(p, notice("N", priceMin = 60_000, priceMax = 60_000), emptyList())!!
        assertEquals("가능 · 대출 3억 · 월 약 143만원", FundsFormat.summary(r))
    }

    @Test fun summary_partly() {
        val r = Affordability.evaluate(p, notice("N"), listOf(model("1", 60_000), model("2", 70_000), model("3", 80_000)))!!
        assertEquals("일부 가능 · 분양가 7억 이하 주택형", FundsFormat.summary(r))
    }

    @Test fun summary_no_showsBindingLimit() {
        val q = Profile(cashManwon = 5_000) // 생애최초 LTV 70%
        assertEquals("불가 · 1.3억 부족(LTV 70%)", FundsFormat.summary(Affordability.evaluate(q, notice("N", priceMax = 60_000), emptyList())!!))
        assertEquals(
            "불가 · 최저가 기준 1억 부족(LTV 70%)",
            FundsFormat.summary(Affordability.evaluate(q, notice("N", priceMin = 50_000, priceMax = 60_000), emptyList())!!),
        )
        val multi = Profile(cashManwon = 50_000, homesOwned = 2)
        assertEquals("불가 · 1억 부족(대출 불가)", FundsFormat.summary(Affordability.evaluate(multi, notice("N", priceMax = 60_000), emptyList())!!))
    }

    @Test fun detail_tightAndNo() {
        assertEquals("대출 4억 · 월 약 191만원 · 여유 1,892만원", FundsFormat.detail(check(p, 70_000)))
        assertEquals("8,108만원 부족 · 필요 대출 5억(월 약 239만원)", FundsFormat.detail(check(p, 80_000)))
    }

    @Test fun loanLine_namesTheBindingLimit() {
        assertEquals("대출 최대 4억 1,892만원 — 월 상환액 상한 200만원에 걸림", FundsFormat.loanLine(check(p, 60_000), p))
        val dsr = Profile(cashManwon = 50_000, incomeManwon = 10_000)
        assertEquals("대출 최대 5억 3,290만원 — DSR 40%에 걸림(스트레스 금리 +2.4%p 반영)", FundsFormat.loanLine(check(dsr, 100_000), dsr))
        val homeless = Profile(cashManwon = 50_000, everOwned = true)
        assertEquals("대출 최대 4억 — LTV 40%(규제지역 무주택)에 걸림", FundsFormat.loanLine(check(homeless, 100_000, reg), homeless))
        val first = Profile(cashManwon = 50_000)
        assertEquals("대출 최대 6억 — 시가 15억 이하 주담대 6억 상한에 걸림", FundsFormat.loanLine(check(first, 100_000, reg), first))
        val limit = Profile(cashManwon = 50_000, loanLimitManwon = 20_000)
        assertEquals("대출 최대 2억 — 입력한 대출 한도에 걸림", FundsFormat.loanLine(check(limit, 60_000), limit))
        val multi = Profile(cashManwon = 50_000, homesOwned = 2)
        assertEquals("대출 불가 — 다주택자 수도권·규제지역 구입 주담대 불가", FundsFormat.loanLine(check(multi, 60_000), multi))
    }

    @Test fun limits_listsEveryCap() {
        assertEquals(
            "LTV 70% 4억 2,000만원 · 상한 6억 · DSR 미반영(연소득 미입력) · 입력 한도 5억 · 월 상환 상한 4억 1,892만원",
            FundsFormat.limits(check(p, 60_000)),
        )
    }

    @Test fun repaymentLine_perMethod() {
        assertEquals("원리금균등 30년: 매달 143만원 · 총이자 약 2억 1,561만원", FundsFormat.repaymentLine(check(p, 60_000), p))
        val ep = p.copy(repayment = Repayment.EQUAL_PRINCIPAL)
        assertEquals("원금균등 30년: 첫 달 183만원 → 마지막 달 84만원 · 총이자 약 1억 8,050만원", FundsFormat.repaymentLine(check(ep, 60_000), ep))
        val bullet = p.copy(repayment = Repayment.BULLET)
        assertEquals("만기일시 30년: 매달 이자 100만원, 만기에 원금 3억 한 번에 · 총이자 약 3억 6,000만원", FundsFormat.repaymentLine(check(bullet, 60_000), bullet))
        val grad = p.copy(repayment = Repayment.GRADUATED)
        assertEquals("체증식 30년: 첫 달 127만원부터 해마다 1%씩 늘어 마지막 달 171만원 · 총이자 약 2억 3,095만원", FundsFormat.repaymentLine(check(grad, 60_000), grad))
        val grace = p.copy(graceYears = 1)
        assertEquals("원리금균등 30년: 거치 1년 동안 이자만 월 100만원, 이후 매달 146만원 · 총이자 약 2억 1,936만원", FundsFormat.repaymentLine(check(grace, 60_000), grace))
        assertEquals("", FundsFormat.repaymentLine(check(Profile(cashManwon = 100_000), 60_000), p))
    }

    @Test fun buildLine_midLoanLimitedByLtv() {
        assertEquals("입주 전 현금 6,000만원 = 계약금 6,000만원(중도금대출 3억 6,000만원, DSR 미적용)", FundsFormat.buildLine(check(p, 60_000)))
        assertEquals(
            "입주 전 현금 3억 = 계약금 1억 + 중도금 중 대출 안 되는 몫 2억(중도금대출 4억, DSR 미적용)",
            FundsFormat.buildLine(check(Profile(cashManwon = 50_000, everOwned = true), 100_000, reg)),
        )
    }

    @Test fun budgetAndAssumptions() {
        val b = Affordability.budget(p)!!
        assertEquals("현금 3억 + 대출 최대 4억 1,892만원(개인 한도)", FundsFormat.budget(b))
        assertEquals("현금 3억 · 대출은 공고마다 LTV·주담대 상한으로 계산", FundsFormat.budget(Affordability.budget(Profile(cashManwon = 30_000))!!))
        assertEquals(
            "개인 한도 = 가장 작은 값: 입력 한도 5억, 월 200만원으로 갚을 수 있는 원금 4억 1,892만원. " +
                "공고마다 LTV·주담대 상한(규제지역 여부·분양가·주택 수)이 더 걸립니다. 연소득을 넣으면 DSR 40% 한도도 계산합니다.",
            FundsFormat.loanBasis(b, p),
        )
        val withIncome = p.copy(incomeManwon = 10_000)
        assertTrue(FundsFormat.loanBasis(Affordability.budget(withIncome)!!, withIncome).contains("DSR 40% 5억 3,290만원(연소득 1억, 스트레스 +2.4%p)"))
        assertEquals("연 4% · 30년 · 원리금균등 · 혼합형(5년 고정) · 계약금 10%", FundsFormat.assumptions(p))
        assertEquals(
            "연 3.5% · 40년 · 원금균등 · 거치 1년 · 만기 고정 · 계약금 20%",
            FundsFormat.assumptions(
                p.copy(loanRatePct = 3.5, loanYears = 40, repayment = Repayment.EQUAL_PRINCIPAL, downPaymentPct = 20, graceYears = 1, rateType = RateType.FIXED),
            ),
        )
        assertTrue(FundsFormat.RULES_NOTE.startsWith("기준일 ${LoanRules.RULES_DATE}"))
        assertEquals("규제지역 · 조정대상지역(공고)", FundsFormat.areaLine(reg))
    }
}
