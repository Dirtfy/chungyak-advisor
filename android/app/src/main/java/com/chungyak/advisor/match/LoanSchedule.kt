package com.chungyak.advisor.match

import kotlin.math.pow

/** 대출 상환 방식(v0.12.0~ 체증식·만기일시 추가). */
enum class Repayment(val label: String) {
    ANNUITY("원리금균등"),
    EQUAL_PRINCIPAL("원금균등"),
    GRADUATED("체증식"),
    BULLET("만기일시");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ANNUITY
    }
}

/**
 * 상환 방식별 월 상환액·총이자·DSR 연 상환액(v0.12.0~, docs/16). 매달 잔액을 따라가며 계산한다. 금액은 만원(소수).
 * - 거치기간([graceYears]년): 그동안 이자만 내고, 원금은 남은 기간에 나눠 갚는다. 만기일시는 거치가 없다.
 * - 체증식: 월 상환액이 해마다 [LoanRules.GRADUATED_STEP_PCT]%씩(달마다 고르게) 늘어나도록 첫 달 금액을 정한다.
 * - DSR 연 상환액(금융위 산정 방식): 분할상환은 거치 뒤 실제 상환액의 연평균, 체증식은 초기 10년(상환 10년 이하면 5년) 연평균,
 *   만기일시는 원금 ÷ 대출기간(최대 10년) + 연 이자.
 * 순수 Kotlin — 단위 테스트(LoanScheduleTest).
 */
data class LoanSchedule(
    val firstMonthly: Double,  // 첫 달 상환액(거치 중이면 이자만)
    val amortStart: Double,    // 원금 상환을 시작하는 달의 상환액(거치가 없으면 첫 달과 같다)
    val monthly: Double,       // 대표 월 상환액: 원리금균등 = 매달, 원금균등 = 거치 뒤 첫 달(가장 큼), 체증식 = 초기 10년 평균, 만기일시 = 이자
    val lastMonthly: Double,   // 마지막 달 상환액(만기일시는 원금 포함)
    val totalInterest: Double,
    val dsrAnnual: Double,     // DSR 계산용 연 원리금 상환액
) {
    companion object {
        val ZERO = LoanSchedule(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

        fun of(principal: Double, ratePct: Double, years: Int, graceYears: Int, type: Repayment): LoanSchedule {
            if (principal <= 0 || years <= 0) return ZERO
            val r = ratePct.coerceAtLeast(0.0) / 100 / 12
            val n = years * 12
            if (type == Repayment.BULLET) {
                val interest = principal * r
                return LoanSchedule(
                    firstMonthly = interest, amortStart = interest, monthly = interest, lastMonthly = interest + principal,
                    totalInterest = interest * n,
                    dsrAnnual = principal / years.coerceAtMost(LoanRules.BULLET_DSR_MAX_YEARS) + interest * 12,
                )
            }
            val g = graceYears.coerceIn(0, years - 1) * 12
            val m = n - g // 원금을 갚는 개월 수
            val pays = DoubleArray(m)
            var balance = principal
            var interestSum = principal * r * g
            when (type) {
                Repayment.ANNUITY -> {
                    val a = if (r == 0.0) principal / m else principal * r / (1 - (1 + r).pow(-m))
                    for (k in 0 until m) { interestSum += balance * r; balance -= a - balance * r; pays[k] = a }
                }
                Repayment.EQUAL_PRINCIPAL -> {
                    val part = principal / m
                    for (k in 0 until m) { val i = balance * r; interestSum += i; pays[k] = part + i; balance -= part }
                }
                Repayment.GRADUATED -> {
                    val step = (1 + LoanRules.GRADUATED_STEP_PCT / 100).pow(1.0 / 12)
                    var pv = 0.0
                    for (k in 0 until m) pv += step.pow(k) / (1 + r).pow(k + 1)
                    val a0 = principal / pv
                    for (k in 0 until m) { val pay = a0 * step.pow(k); interestSum += balance * r; balance -= pay - balance * r; pays[k] = pay }
                }
                Repayment.BULLET -> error("handled above")
            }
            val amortAnnual = when (type) {
                Repayment.GRADUATED -> {
                    val months = (if (m <= 120) LoanRules.GRADUATED_DSR_YEARS_SHORT else LoanRules.GRADUATED_DSR_YEARS) * 12
                    pays.take(months.coerceAtMost(m)).average() * 12
                }
                else -> pays.sum() / m * 12
            }
            val monthly = when (type) {
                Repayment.GRADUATED -> amortAnnual / 12
                else -> pays[0]
            }
            return LoanSchedule(
                firstMonthly = if (g > 0) principal * r else pays[0],
                amortStart = pays[0],
                monthly = monthly,
                lastMonthly = pays[m - 1],
                totalInterest = interestSum,
                dsrAnnual = amortAnnual,
            )
        }
    }
}
