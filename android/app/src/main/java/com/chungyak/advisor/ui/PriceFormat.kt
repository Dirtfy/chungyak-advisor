package com.chungyak.advisor.ui

import java.util.Locale

/**
 * 만원 단위 원자료(청약홈 LTTOT_TOP_AMOUNT 등)를 한국식 금액 문자열로.
 * 0 이하는 "정보 없음".
 */
object PriceFormat {

    const val NONE = "정보 없음"
    private const val SQM_PER_PYEONG = 3.305785

    /** 91000 → "9억 1,000만원", 120000 → "12억", 8500 → "8,500만원". */
    fun full(manwon: Int): String {
        if (manwon <= 0) return NONE
        val eok = manwon / 10000
        val rest = manwon % 10000
        return when {
            eok == 0 -> "${comma(rest)}만원"
            rest == 0 -> "${eok}억"
            else -> "${eok}억 ${comma(rest)}만원"
        }
    }

    /** 목록용 짧은 표기: 52000 → "5.2억", 8500 → "8,500만원". */
    fun short(manwon: Int): String {
        if (manwon <= 0) return NONE
        if (manwon < 10000) return "${comma(manwon)}만원"
        val s = String.format(Locale.US, "%.1f", manwon / 10000.0).removeSuffix(".0")
        return "${s}억"
    }

    /** 최저~최고 범위. 같으면 한 값, 둘 다 없으면 "정보 없음". */
    fun range(minManwon: Int, maxManwon: Int): String = when {
        maxManwon <= 0 -> NONE
        minManwon in 1 until maxManwon -> "${short(minManwon)}~${short(maxManwon)}"
        else -> short(maxManwon)
    }

    /** ㎡ → 평 (소수 1자리). */
    fun pyeong(sqm: Double): Double = if (sqm <= 0) 0.0 else sqm / SQM_PER_PYEONG

    /** 공급면적 기준 평당가(만원, 반올림). 면적/금액 없으면 0. */
    fun perPyeongManwon(priceManwon: Int, supplySqm: Double): Int {
        val p = pyeong(supplySqm)
        return if (priceManwon <= 0 || p <= 0) 0 else Math.round(priceManwon / p).toInt()
    }

    /** "81.70㎡ (24.7평)". */
    fun area(sqm: Double): String =
        if (sqm <= 0) NONE
        else String.format(Locale.US, "%.2f㎡ (%.1f평)", sqm, pyeong(sqm))

    private fun comma(n: Int): String = String.format(Locale.US, "%,d", n)
}
