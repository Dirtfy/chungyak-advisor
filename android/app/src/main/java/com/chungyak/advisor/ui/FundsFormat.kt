package com.chungyak.advisor.ui

import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.match.Repayment
import java.util.Locale

/**
 * 자금 판정(Affordability) 문구. 목록 한 줄·상세·알림이 같은 문구를 쓴다.
 * 순수 Kotlin — 화면 없이 단위 테스트한다(FundsFormatTest).
 */
object FundsFormat {

    /** 목록·알림 한 줄: "가능 · 대출 3.2억 · 월 약 153만원", "일부 가능 · 분양가 8.5억 이하 주택형", "불가 · 최저가 기준 1.2억 부족". */
    fun summary(r: Affordability.NoticeResult): String {
        val best = r.best
        return when {
            best.verdict == Affordability.Verdict.NO ->
                "불가 · ${if (r.checks.size > 1) "최저가 기준 " else ""}${amount(best.shortfall, short = true)} 부족"
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
                if (c.downShort > 0) "계약금 현금 ${amt(c.downShort)} 부족 · $loan"
                else "$loan · 여유 ${amt(c.margin)}"
            Affordability.Verdict.NO -> "${amt(c.shortfall)} 부족 · 필요 대출 ${amt(c.loanNeeded)}(월 약 ${monthly(c.monthly)})"
        }
    }

    /** "구매 가능 자금 9억 = 현금 3억 + 대출 6억". */
    fun budget(b: Affordability.Budget): String =
        "구매 가능 자금 ${amount(b.total)} = 현금 ${amount(b.cash)} + 대출 ${amount(b.usableLoan)}"

    /** 쓸 수 있는 대출이 어떻게 정해졌는지. */
    fun loanBasis(b: Affordability.Budget, p: Profile): String {
        val cap = b.loanByCap?.let { "월 ${monthly(p.monthlyCapManwon)}으로 갚을 수 있는 원금 ${amount(it)}" }
        val limit = b.loanByLimit?.let { "대출 한도 ${amount(it)}" }
        return when {
            cap != null && limit != null -> "대출 = 둘 중 작은 쪽: $limit, $cap"
            limit != null -> "대출 = $limit (월 상환액 상한 미입력)"
            cap != null -> "대출 = $cap (대출 한도 미입력)"
            else -> "대출 한도·월 상환액 상한을 넣지 않아 현금만으로 판정합니다."
        }
    }

    /** 계산 가정: "연 4% · 30년 · 원리금균등 · 계약금 10%". */
    fun assumptions(p: Profile): String =
        "연 ${String.format(Locale.US, "%.2f", p.loanRatePct).trimEnd('0').trimEnd('.')}% · ${p.loanYears}년 · " +
            "${p.repayment.label}${if (p.repayment == Repayment.EQUAL_PRINCIPAL) "(첫 달 기준)" else ""} · 계약금 ${p.downPaymentPct}%"

    /** 0 이하도 "0원"으로(PriceFormat은 0을 "정보 없음"으로 쓴다). [short]면 "3.2억". */
    fun amount(manwon: Int, short: Boolean = false): String = when {
        manwon <= 0 -> "0원"
        short -> PriceFormat.short(manwon)
        else -> PriceFormat.full(manwon)
    }

    fun monthly(manwon: Int): String = String.format(Locale.US, "%,d만원", manwon.coerceAtLeast(0))
}
