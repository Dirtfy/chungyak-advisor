package com.chungyak.advisor.ui

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.Repayment
import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Test

class FundsFormatTest {

    private val p = Profile(cashManwon = 30_000, loanLimitManwon = 50_000, monthlyCapManwon = 200)

    private fun model(no: String, price: Int) = HouseModel("N", no, "0$no.0000A", 0.0, 10, price)

    @Test fun summary_allOk() {
        val r = Affordability.evaluate(p, notice("N", priceMin = 60_000, priceMax = 60_000), emptyList())!!
        assertEquals("가능 · 대출 3억 · 월 약 143만원", FundsFormat.summary(r))
    }

    @Test fun summary_partly() {
        val r = Affordability.evaluate(p, notice("N"), listOf(model("1", 60_000), model("2", 70_000), model("3", 80_000)))!!
        assertEquals("일부 가능 · 분양가 7억 이하 주택형", FundsFormat.summary(r))
    }

    @Test fun summary_no() {
        val q = Profile(cashManwon = 5_000)
        assertEquals("불가 · 5.5억 부족", FundsFormat.summary(Affordability.evaluate(q, notice("N", priceMax = 60_000), emptyList())!!))
        assertEquals(
            "불가 · 최저가 기준 4.5억 부족",
            FundsFormat.summary(Affordability.evaluate(q, notice("N", priceMin = 50_000, priceMax = 60_000), emptyList())!!),
        )
    }

    @Test fun detail_tightAndNo() {
        val b = Affordability.budget(p)!!
        assertEquals("대출 4억 · 월 약 191만원 · 여유 1,892만원", FundsFormat.detail(Affordability.check(b, p, 70_000)!!))
        assertEquals("8,108만원 부족 · 필요 대출 5억(월 약 239만원)", FundsFormat.detail(Affordability.check(b, p, 80_000)!!))
    }

    @Test fun budgetAndAssumptions() {
        val b = Affordability.budget(p)!!
        assertEquals("구매 가능 자금 7억 1,892만원 = 현금 3억 + 대출 4억 1,892만원", FundsFormat.budget(b))
        assertEquals("대출 = 둘 중 작은 쪽: 대출 한도 5억, 월 200만원으로 갚을 수 있는 원금 4억 1,892만원", FundsFormat.loanBasis(b, p))
        assertEquals("연 4% · 30년 · 원리금균등 · 계약금 10%", FundsFormat.assumptions(p))
        assertEquals(
            "연 3.5% · 40년 · 원금균등(첫 달 기준) · 계약금 20%",
            FundsFormat.assumptions(p.copy(loanRatePct = 3.5, loanYears = 40, repayment = Repayment.EQUAL_PRINCIPAL, downPaymentPct = 20)),
        )
    }
}
