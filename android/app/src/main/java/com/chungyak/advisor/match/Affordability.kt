package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import kotlin.math.ceil
import kotlin.math.pow

/** 대출 상환 방식. 원금균등은 첫 달(가장 많이 내는 달) 상환액 기준으로 계산한다. */
enum class Repayment(val label: String) {
    ANNUITY("원리금균등"),
    EQUAL_PRINCIPAL("원금균등");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ANNUITY
    }
}

/**
 * 자금 판정(v0.11.0~, docs/15): "내 자금으로 이 분양을 살 수 있나".
 * 사용자가 넣은 현금·대출 한도·월 상환액 상한으로 구매 가능 자금을 추정하고 주택형별 분양가(최고가)와 비교한다.
 * 은행 대출 심사(LTV·DSR·소득 심사)가 아니라 입력값 기반 추정이다. 금액은 모두 만원.
 * 순수 Kotlin — 화면 없이 단위 테스트한다(AffordabilityTest).
 */
object Affordability {

    /** 분양가를 내고도 구매 가능 자금이 이 비율(%)만큼 남지 않으면 '빠듯' — 취득세·등기·이사 등 부대비용 몫. */
    const val TIGHT_MARGIN_PCT = 5

    enum class Verdict(val label: String, val rank: Int) {
        OK("가능", 2),
        TIGHT("빠듯", 1),
        NO("불가", 0),
    }

    /**
     * 구매 가능 자금. [loanByLimit]·[loanByCap]이 null이면 그 항목 미입력.
     * 실제 쓸 수 있는 대출 [usableLoan] = min(대출 한도, 월 상환 상한으로 감당 가능한 원금). 둘 다 없으면 0(현금만).
     */
    data class Budget(
        val cash: Int,
        val loanByLimit: Int?,
        val loanByCap: Int?,
        val usableLoan: Int,
    ) {
        val total: Int get() = cash + usableLoan
    }

    /** 주택형(분양가) 하나에 대한 판정. */
    data class Check(
        val price: Int,
        val verdict: Verdict,
        val loanNeeded: Int,       // 분양가 − 현금 (0 이상)
        val monthly: Int,          // 필요한 대출의 예상 월 상환액(만원, 반올림). 대출이 없으면 0
        val shortfall: Int,        // 불가일 때 모자란 금액
        val downPayment: Int,      // 계약금(분양가 × 계약금 비율)
        val downShort: Int,        // 계약금 중 현금으로 모자란 금액
        val margin: Int,           // 구매 가능 자금 − 분양가(음수면 부족)
    )

    /** 공고 하나(주택형 여러 개)에 대한 판정. [best]는 가장 유리한 주택형, [worst]는 가장 불리한 주택형. */
    data class NoticeResult(val checks: List<Check>, val best: Check, val worst: Check) {
        val okCount: Int get() = checks.count { it.verdict != Verdict.NO }
    }

    /** 자금을 입력했는가(보유 현금은 필수). 아니면 판정을 숨기고 입력을 유도한다. */
    fun isSet(p: Profile): Boolean = p.cashManwon >= 0

    fun budget(p: Profile): Budget? {
        if (!isSet(p)) return null
        val byLimit = p.loanLimitManwon.takeIf { it >= 0 }
        val byCap = p.monthlyCapManwon.takeIf { it >= 0 }?.let { principalFor(it, p.loanRatePct, p.loanYears, p.repayment) }
        val usable = listOfNotNull(byLimit, byCap).minOrNull() ?: 0
        return Budget(p.cashManwon, byLimit, byCap, usable)
    }

    /** 월 상환액 [monthlyManwon]으로 감당 가능한 대출 원금(만원, 내림). */
    fun principalFor(monthlyManwon: Int, ratePct: Double, years: Int, type: Repayment): Int {
        if (monthlyManwon <= 0 || years <= 0) return 0
        val n = years * 12
        val r = ratePct.coerceAtLeast(0.0) / 100 / 12
        val principal = when (type) {
            Repayment.ANNUITY -> if (r == 0.0) monthlyManwon.toDouble() * n else monthlyManwon * (1 - (1 + r).pow(-n)) / r
            Repayment.EQUAL_PRINCIPAL -> monthlyManwon / (1.0 / n + r)
        }
        return principal.coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }

    /** 대출 원금 [principalManwon]의 월 상환액(만원). 원금균등은 첫 달 기준. */
    fun monthlyPayment(principalManwon: Int, ratePct: Double, years: Int, type: Repayment): Double {
        if (principalManwon <= 0 || years <= 0) return 0.0
        val n = years * 12
        val r = ratePct.coerceAtLeast(0.0) / 100 / 12
        return when (type) {
            Repayment.ANNUITY -> if (r == 0.0) principalManwon.toDouble() / n else principalManwon * r / (1 - (1 + r).pow(-n))
            Repayment.EQUAL_PRINCIPAL -> principalManwon.toDouble() / n + principalManwon * r
        }
    }

    /**
     * 분양가 [price] 하나를 판정한다(0 이하 = 분양가 정보 없음 → null).
     * - 불가: 분양가 − 현금 > 쓸 수 있는 대출
     * - 빠듯: 살 수는 있지만 계약금을 현금으로 못 내거나, 남는 돈이 분양가의 [TIGHT_MARGIN_PCT]% 미만
     * - 가능: 그 밖
     */
    fun check(b: Budget, p: Profile, price: Int): Check? {
        if (price <= 0) return null
        val loanNeeded = (price - b.cash).coerceAtLeast(0)
        val monthly = Math.round(monthlyPayment(loanNeeded, p.loanRatePct, p.loanYears, p.repayment)).toInt()
        val down = ceil(price.toDouble() * p.downPaymentPct.coerceIn(0, 100) / 100).toInt()
        val downShort = (down - b.cash).coerceAtLeast(0)
        val margin = b.total - price
        val verdict = when {
            loanNeeded > b.usableLoan -> Verdict.NO
            downShort > 0 || margin.toLong() * 100 < price.toLong() * TIGHT_MARGIN_PCT -> Verdict.TIGHT
            else -> Verdict.OK
        }
        val shortfall = if (verdict == Verdict.NO) loanNeeded - b.usableLoan else 0
        return Check(price, verdict, loanNeeded, monthly, shortfall, down, downShort, margin)
    }

    /**
     * 공고 판정. 주택형별 분양가가 있으면 그걸로, 아직 없으면 공고의 최저·최고 분양가로 판정한다.
     * 자금 미입력이거나 분양가 정보가 하나도 없으면 null.
     */
    fun evaluate(p: Profile, n: Notice, models: List<HouseModel>): NoticeResult? {
        val b = budget(p) ?: return null
        val prices = models.map { it.priceManwon }.filter { it > 0 }
            .ifEmpty { listOf(n.priceMinManwon, n.priceMaxManwon).filter { it > 0 } }
            .distinct()
        val checks = prices.mapNotNull { check(b, p, it) }
        if (checks.isEmpty()) return null
        val order = compareByDescending<Check> { it.verdict.rank }.thenBy { it.price }
        val sorted = checks.sortedWith(order)
        return NoticeResult(checks, sorted.first(), sorted.last())
    }
}
