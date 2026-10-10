package com.chungyak.advisor.match

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import kotlin.math.ceil

/**
 * 자금 판정(v0.11.0~, docs/15·16): "내 자금으로 이 분양을 살 수 있나".
 * 쓸 수 있는 대출 = min(LTV 한도, 주담대 금액 상한, DSR 한도, 입력한 대출 한도, 월 상환액 상한으로 갚을 수 있는 원금)
 * (v0.12.0~ 규정 숫자는 LoanRules). 분양가 = 계약금(현금) + 중도금(집단대출) + 잔금, 입주 때 중도금대출을 잔금대출로
 * 바꾸므로 최종 대출은 잔금대출 규정으로 정해진다. 은행 심사 그대로가 아닌 공개 규정 기반 추정이다. 금액은 모두 만원.
 * 순수 Kotlin — 화면 없이 단위 테스트한다(AffordabilityTest).
 */
object Affordability {

    /** 분양가를 내고도 구매 가능 자금이 이 비율(%)만큼 남지 않으면 '빠듯' — 취득세·등기·이사 등 부대비용 몫. */
    const val TIGHT_MARGIN_PCT = 5

    /** 공고 없이(내 조건 화면 등) 계산할 때의 지역: 앱 공고는 모두 수도권. */
    val DEFAULT_AREA = LoanRules.Area(metro = true, regulated = false, source = "수도권")

    enum class Verdict(val label: String, val rank: Int) {
        OK("가능", 2),
        TIGHT("빠듯", 1),
        NO("불가", 0),
    }

    /** 대출액을 정한 제약(가장 작은 한도). 같은 금액이면 앞의 것을 근거로 보인다. */
    enum class Limit {
        BANNED,      // 주택 수 때문에 구입 주담대 불가(LTV 0%)
        LTV,
        PRICE_CAP,   // 주담대 금액 상한(6억/4억/2억)
        DSR,
        USER_LIMIT,  // 사용자가 넣은 대출 한도
        MONTHLY_CAP, // 월 상환액 상한으로 갚을 수 있는 원금
    }

    /**
     * 공고와 무관한 개인 한도(null = 미입력): 입력한 대출 한도, 월 상환액 상한으로 갚을 수 있는 원금, DSR 한도.
     * 공고마다 LTV·금액 상한이 여기에 더 걸린다([loan]).
     */
    data class Budget(
        val cash: Int,
        val loanByLimit: Int?,
        val loanByCap: Int?,
        val loanByDsr: Int?,
    ) {
        val personalMax: Int? get() = listOfNotNull(loanByLimit, loanByCap, loanByDsr).minOrNull()
    }

    /** 분양가 하나에 쓸 수 있는 대출. 금액 항목이 null이면 그 제약은 없음(미입력·해당 없음). */
    data class Loan(
        val ltv: LoanRules.Ltv,
        val byLtv: Int,
        val byPriceCap: Int?,
        val priceCapLabel: String,
        val byDsr: Int?,
        val byLimit: Int?,
        val byMonthly: Int?,
        val usable: Int,
        val binding: Limit,
        val stressPct: Double,   // DSR 심사 가산금리(%p)
        val years: Int,          // 실제 계산 만기(수도권 30년 이내)
    )

    /** 주택형(분양가) 하나에 대한 판정. */
    data class Check(
        val price: Int,
        val verdict: Verdict,
        val loanNeeded: Int,       // 분양가 − 현금 (0 이상)
        val monthly: Int,          // 필요한 대출의 대표 월 상환액(만원, 반올림 — LoanSchedule.monthly). 대출이 없으면 0
        val shortfall: Int,        // 불가일 때 모자란 금액
        val downPayment: Int,      // 계약금(분양가 × 계약금 비율)
        val downShort: Int,        // 입주 전 현금(계약금 + 중도금 중 대출 안 되는 몫)에서 모자란 금액
        val margin: Int,           // 현금 + 쓸 수 있는 대출 − 분양가(음수면 부족)
        val loan: Loan,
        val totalInterest: Int,    // 필요한 대출의 총이자(만원)
        val midLoan: Int,          // 중도금 집단대출(분양가 × min(중도금 비율, LTV))
        val cashDuringBuild: Int,  // 입주 전까지 현금으로 낼 돈
        val schedule: LoanSchedule,
        val area: LoanRules.Area,
    )

    /** 공고 하나(주택형 여러 개)에 대한 판정. [best]는 가장 유리한 주택형, [worst]는 가장 불리한 주택형. */
    data class NoticeResult(val checks: List<Check>, val best: Check, val worst: Check) {
        val okCount: Int get() = checks.count { it.verdict != Verdict.NO }
    }

    /** 자금을 입력했는가(보유 현금은 필수). 아니면 판정을 숨기고 입력을 유도한다. */
    fun isSet(p: Profile): Boolean = p.cashManwon >= 0

    fun budget(p: Profile, a: LoanRules.Area = DEFAULT_AREA): Budget? {
        if (!isSet(p)) return null
        val years = LoanRules.years(p, a)
        val byLimit = p.loanLimitManwon.takeIf { it >= 0 }
        val byCap = p.monthlyCapManwon.takeIf { it >= 0 }?.let { principalFor(it.toDouble(), p.loanRatePct, years, p.graceYears, p.repayment) }
        return Budget(p.cashManwon, byLimit, byCap, dsrPrincipal(p, a))
    }

    /** 대표 월 상환액 [monthlyManwon]으로 감당 가능한 대출 원금(만원, 내림). 상환액은 원금에 비례하므로 1억 기준으로 나눈다. */
    fun principalFor(monthlyManwon: Double, ratePct: Double, years: Int, graceYears: Int, type: Repayment): Int {
        if (monthlyManwon <= 0 || years <= 0) return 0
        val unit = LoanSchedule.of(UNIT, ratePct, years, graceYears, type).monthly
        return if (unit <= 0) 0 else (monthlyManwon / unit * UNIT).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }

    /**
     * DSR 한도 원금: (연소득 × 40% − 기존 대출 연 상환액)을, 스트레스 금리(금리 + 가산)로 계산한 연 상환액으로 갚을 수 있는 원금.
     * 연소득 미입력이면 null(DSR 미반영).
     */
    fun dsrPrincipal(p: Profile, a: LoanRules.Area): Int? {
        if (p.incomeManwon < 0) return null
        val room = p.incomeManwon.toDouble() * LoanRules.DSR_LIMIT_PCT / 100 - p.debtAnnualManwon.coerceAtLeast(0)
        if (room <= 0) return 0
        val unit = LoanSchedule.of(UNIT, p.loanRatePct + LoanRules.stress(p.rateType, a), LoanRules.years(p, a), p.graceYears, p.repayment).dsrAnnual
        return if (unit <= 0) 0 else (room / unit * UNIT).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }

    /** 분양가 [price]에 쓸 수 있는 대출 = 모든 제약 중 최솟값. */
    fun loan(b: Budget, p: Profile, price: Int, a: LoanRules.Area): Loan {
        val ltv = LoanRules.ltv(p, price, a)
        val byLtv = (price.toLong() * ltv.pct / 100).toInt()
        val cap = LoanRules.priceCap(price, a)
        val candidates = listOfNotNull(
            (if (ltv.pct == 0) Limit.BANNED else Limit.LTV) to byLtv,
            cap?.let { Limit.PRICE_CAP to it.first },
            b.loanByDsr?.let { Limit.DSR to it },
            b.loanByLimit?.let { Limit.USER_LIMIT to it },
            b.loanByCap?.let { Limit.MONTHLY_CAP to it },
        )
        val (binding, usable) = candidates.minBy { it.second }
        return Loan(
            ltv = ltv, byLtv = byLtv, byPriceCap = cap?.first, priceCapLabel = cap?.second.orEmpty(),
            byDsr = b.loanByDsr, byLimit = b.loanByLimit, byMonthly = b.loanByCap,
            usable = usable, binding = binding, stressPct = LoanRules.stress(p.rateType, a), years = LoanRules.years(p, a),
        )
    }

    /**
     * 분양가 [price] 하나를 판정한다(0 이하 = 분양가 정보 없음 → null).
     * - 불가: 분양가 − 현금 > 쓸 수 있는 대출
     * - 빠듯: 살 수는 있지만 입주 전 현금(계약금 + 중도금 중 집단대출이 안 되는 몫)이 모자라거나, 남는 돈이 분양가의 [TIGHT_MARGIN_PCT]% 미만
     * - 가능: 그 밖
     */
    fun check(b: Budget, p: Profile, price: Int, a: LoanRules.Area = DEFAULT_AREA): Check? {
        if (price <= 0) return null
        val loan = loan(b, p, price, a)
        val loanNeeded = (price - b.cash).coerceAtLeast(0)
        val schedule = LoanSchedule.of(loanNeeded.toDouble(), p.loanRatePct, loan.years, p.graceYears, p.repayment)
        val downPct = p.downPaymentPct.coerceIn(0, 100)
        val down = ceil(price.toDouble() * downPct / 100).toInt()
        val midPct = LoanRules.MID_PAYMENT_PCT.coerceAtMost(100 - downPct)
        val midLoan = (price.toLong() * midPct.coerceAtMost(loan.ltv.pct) / 100).toInt()
        val cashDuringBuild = down + (price.toLong() * midPct / 100).toInt() - midLoan
        val downShort = (cashDuringBuild - b.cash).coerceAtLeast(0)
        val margin = b.cash + loan.usable - price
        val verdict = when {
            loanNeeded > loan.usable -> Verdict.NO
            downShort > 0 || margin.toLong() * 100 < price.toLong() * TIGHT_MARGIN_PCT -> Verdict.TIGHT
            else -> Verdict.OK
        }
        val shortfall = if (verdict == Verdict.NO) loanNeeded - loan.usable else 0
        return Check(
            price, verdict, loanNeeded, Math.round(schedule.monthly).toInt(), shortfall, down, downShort, margin,
            loan, Math.round(schedule.totalInterest).toInt(), midLoan, cashDuringBuild, schedule, a,
        )
    }

    /**
     * 공고 판정. 주택형별 분양가가 있으면 그걸로, 아직 없으면 공고의 최저·최고 분양가로 판정한다.
     * 지역(수도권·규제지역)은 공고에서 판별(LoanRules.area). 자금 미입력이거나 분양가 정보가 하나도 없으면 null.
     */
    fun evaluate(p: Profile, n: Notice, models: List<HouseModel>): NoticeResult? {
        val a = LoanRules.area(n)
        val b = budget(p, a) ?: return null
        val prices = models.map { it.priceManwon }.filter { it > 0 }
            .ifEmpty { listOf(n.priceMinManwon, n.priceMaxManwon).filter { it > 0 } }
            .distinct()
        val checks = prices.mapNotNull { check(b, p, it, a) }
        if (checks.isEmpty()) return null
        val order = compareByDescending<Check> { it.verdict.rank }.thenBy { it.price }
        val sorted = checks.sortedWith(order)
        return NoticeResult(checks, sorted.first(), sorted.last())
    }

    private const val UNIT = 10_000.0
}
