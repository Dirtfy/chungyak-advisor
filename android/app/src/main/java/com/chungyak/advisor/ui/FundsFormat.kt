package com.chungyak.advisor.ui

import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.Affordability.Limit
import com.chungyak.advisor.match.LoanRules
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.Repayment
import java.util.Locale

/**
 * 자금 판정(Affordability) 문구. 목록 한 줄·상세·알림이 같은 문구를 쓴다.
 * 순수 Kotlin — 화면 없이 단위 테스트한다(FundsFormatTest).
 */
object FundsFormat {

    /** 앱 안 규정 안내(상세 카드·내 조건). */
    const val RULES_NOTE = "기준일 ${LoanRules.RULES_DATE} 규정(LTV·DSR·주담대 상한)으로 계산한 추정이며, 실제 대출 심사와 다를 수 있습니다."

    /** 목록·알림 한 줄: "가능 · 대출 3.2억 · 월 약 153만원", "일부 가능 · 분양가 8.5억 이하 주택형", "불가 · 최저가 기준 1.2억 부족(LTV 40%)". */
    fun summary(r: Affordability.NoticeResult): String {
        val best = r.best
        return when {
            best.verdict == Affordability.Verdict.NO ->
                "불가 · ${if (r.checks.size > 1) "최저가 기준 " else ""}${amount(best.shortfall, short = true)} 부족(${limitShort(best.loan)})"
            r.worst.verdict == best.verdict -> "${best.verdict.label} · ${detail(best, short = true)}"
            else -> {
                val upTo = r.checks.filter { it.verdict != Affordability.Verdict.NO }.maxOf { it.price }
                if (r.worst.verdict == Affordability.Verdict.NO) "일부 가능 · 분양가 ${PriceFormat.short(upTo)} 이하 주택형"
                else "${best.verdict.label}~${r.worst.verdict.label} · ${detail(best, short = true)}"
            }
        }
    }

    /** 판정 하나의 근거: 필요한 대출·월 상환액, 빠듯한 이유, 부족액. [short]면 금액을 "3.2억"처럼 줄인다(목록·알림). */
    fun detail(c: Affordability.Check, short: Boolean = false): String {
        fun amt(m: Int) = amount(m, short)
        val loan = if (c.loanNeeded == 0) "현금으로 충분" else "대출 ${amt(c.loanNeeded)} · 월 약 ${monthly(c.monthly)}"
        return when (c.verdict) {
            Affordability.Verdict.OK -> loan
            Affordability.Verdict.TIGHT ->
                if (c.downShort > 0) "입주 전 현금 ${amt(c.downShort)} 부족 · $loan"
                else "$loan · 여유 ${amt(c.margin)}"
            Affordability.Verdict.NO -> "${amt(c.shortfall)} 부족 · 필요 대출 ${amt(c.loanNeeded)}(월 약 ${monthly(c.monthly)})"
        }
    }

    /** 대출액을 정한 제약 — 짧게(목록). */
    fun limitShort(l: Affordability.Loan): String = when (l.binding) {
        Limit.BANNED -> "대출 불가"
        Limit.LTV -> "LTV ${l.ltv.pct}%"
        Limit.PRICE_CAP -> "주담대 ${amount(l.byPriceCap ?: 0, short = true)} 상한"
        Limit.DSR -> "DSR ${LoanRules.DSR_LIMIT_PCT}%"
        Limit.USER_LIMIT -> "입력 한도"
        Limit.MONTHLY_CAP -> "월 상환 상한"
    }

    /** 대출액을 정한 제약 — 문장(상세): "DSR 40%에 걸림(스트레스 금리 +2.4%p 반영)". */
    fun limitText(l: Affordability.Loan, p: Profile): String = when (l.binding) {
        Limit.BANNED -> "대출 불가 — ${l.ltv.label}"
        Limit.LTV -> "LTV ${l.ltv.pct}%(${l.ltv.label})에 걸림"
        Limit.PRICE_CAP -> "${l.priceCapLabel}에 걸림"
        Limit.DSR -> "DSR ${LoanRules.DSR_LIMIT_PCT}%에 걸림(스트레스 금리 +${pct(l.stressPct)}%p 반영)"
        Limit.USER_LIMIT -> "입력한 대출 한도에 걸림"
        Limit.MONTHLY_CAP -> "월 상환액 상한 ${monthly(p.monthlyCapManwon)}에 걸림"
    }

    /** "대출 최대 4억 2,000만원 — LTV 70%(생애최초)에 걸림". */
    fun loanLine(c: Affordability.Check, p: Profile): String =
        if (c.loan.binding == Limit.BANNED) limitText(c.loan, p)
        else "대출 최대 ${amount(c.loan.usable)} — ${limitText(c.loan, p)}"

    /** 각 한도 금액 나열(상세 펼침): "LTV 70% 4억 2,000만원 · 6억 상한 · DSR 5억 1,000만원 · …". */
    fun limits(c: Affordability.Check): String {
        val l = c.loan
        return listOfNotNull(
            "LTV ${l.ltv.pct}% ${amount(l.byLtv)}",
            l.byPriceCap?.let { "상한 ${amount(it)}" },
            l.byDsr?.let { "DSR ${amount(it)}" } ?: "DSR 미반영(연소득 미입력)",
            l.byLimit?.let { "입력 한도 ${amount(it)}" },
            l.byMonthly?.let { "월 상환 상한 ${amount(it)}" },
        ).joinToString(" · ")
    }

    /** 필요한 대출의 상환 모양과 총이자. 대출이 없으면 "". */
    fun repaymentLine(c: Affordability.Check, p: Profile): String {
        if (c.loanNeeded == 0) return ""
        val s = c.schedule
        fun m(x: Double) = monthly(Math.round(x).toInt())
        val shape = when (p.repayment) {
            Repayment.ANNUITY -> "매달 ${m(s.monthly)}"
            Repayment.EQUAL_PRINCIPAL -> "첫 달 ${m(s.monthly)} → 마지막 달 ${m(s.lastMonthly)}"
            Repayment.GRADUATED -> "첫 달 ${m(s.amortStart)}부터 해마다 ${pct(LoanRules.GRADUATED_STEP_PCT)}%씩 늘어 마지막 달 ${m(s.lastMonthly)}"
            Repayment.BULLET -> "매달 이자 ${m(s.monthly)}, 만기에 원금 ${amount(c.loanNeeded)} 한 번에"
        }
        val grace = if (p.graceYears > 0 && p.repayment != Repayment.BULLET) "거치 ${p.graceYears}년 동안 이자만 월 ${m(s.firstMonthly)}, 이후 " else ""
        return "${p.repayment.label} ${c.loan.years}년: $grace$shape · 총이자 약 ${amount(c.totalInterest)}"
    }

    /** 입주 전 현금: "입주 전 현금 1.6억 = 계약금 1억 + 중도금 중 대출 안 되는 몫 6,000만원(중도금대출 2.4억)". */
    fun buildLine(c: Affordability.Check): String {
        val own = c.cashDuringBuild - c.downPayment
        val mid = if (own > 0) " + 중도금 중 대출 안 되는 몫 ${amount(own)}" else ""
        return "입주 전 현금 ${amount(c.cashDuringBuild)} = 계약금 ${amount(c.downPayment)}$mid(중도금대출 ${amount(c.midLoan)}, DSR 미적용)"
    }

    /** "규제지역 · 조정대상지역(공고)". */
    fun areaLine(a: LoanRules.Area): String = if (a.regulated) "규제지역 · ${a.source}" else a.source

    /** 내 조건 화면 요약: "현금 3억 + 대출 최대 4억 1,892만원(개인 한도)". */
    fun budget(b: Affordability.Budget): String =
        b.personalMax?.let { "현금 ${amount(b.cash)} + 대출 최대 ${amount(it)}(개인 한도)" }
            ?: "현금 ${amount(b.cash)} · 대출은 공고마다 LTV·주담대 상한으로 계산"

    /** 개인 한도가 어떻게 정해졌는지. 공고마다 LTV·금액 상한이 더 걸린다. */
    fun loanBasis(b: Affordability.Budget, p: Profile): String {
        val parts = listOfNotNull(
            b.loanByDsr?.let {
                "DSR ${LoanRules.DSR_LIMIT_PCT}% ${amount(it)}(연소득 ${amount(p.incomeManwon)}, 스트레스 +${pct(LoanRules.stress(p.rateType, Affordability.DEFAULT_AREA))}%p)"
            },
            b.loanByLimit?.let { "입력 한도 ${amount(it)}" },
            b.loanByCap?.let { "월 ${monthly(p.monthlyCapManwon)}으로 갚을 수 있는 원금 ${amount(it)}" },
        )
        val head = if (parts.isEmpty()) "개인 한도 없음." else "개인 한도 = 가장 작은 값: ${parts.joinToString(", ")}."
        val dsr = if (b.loanByDsr == null) " 연소득을 넣으면 DSR ${LoanRules.DSR_LIMIT_PCT}% 한도도 계산합니다." else ""
        return "$head 공고마다 LTV·주담대 상한(규제지역 여부·분양가·주택 수)이 더 걸립니다.$dsr"
    }

    /** 계산 가정: "연 4% · 30년 · 원리금균등 · 혼합형(5년 고정) · 계약금 10%". */
    fun assumptions(p: Profile): String =
        "연 ${pct(p.loanRatePct)}% · ${p.loanYears}년 · ${p.repayment.label}" +
            (if (p.graceYears > 0 && p.repayment != Repayment.BULLET) " · 거치 ${p.graceYears}년" else "") +
            " · ${p.rateType.label} · 계약금 ${p.downPaymentPct}%"

    /** 0 이하도 "0원"으로(PriceFormat은 0을 "정보 없음"으로 쓴다). [short]면 "3.2억". */
    fun amount(manwon: Int, short: Boolean = false): String = when {
        manwon <= 0 -> "0원"
        short -> PriceFormat.short(manwon)
        else -> PriceFormat.full(manwon)
    }

    fun monthly(manwon: Int): String = String.format(Locale.US, "%,d만원", manwon.coerceAtLeast(0))

    private fun pct(x: Double) = String.format(Locale.US, "%.2f", x).trimEnd('0').trimEnd('.')
}
