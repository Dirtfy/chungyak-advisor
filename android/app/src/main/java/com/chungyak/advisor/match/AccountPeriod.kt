package com.chungyak.advisor.match

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * 청약통장 가입기간 계산. 기준: 주택공급에 관한 규칙 — 가입기간은 가입일부터 입주자모집공고일까지,
 * 만 개월(해당 월의 같은 날이 와야 1개월). 같은 날이 없는 달(예: 1/31 가입 → 2월)은 그 달 말일에 채운다.
 */
object AccountPeriod {

    /** "yyyy-MM-dd" → 날짜. 형식이 틀리면 null. */
    fun parse(s: String): LocalDate? = try {
        if (s.isBlank()) null else LocalDate.parse(s.trim())
    } catch (e: DateTimeParseException) {
        null
    }

    /** [opened]부터 [asOf]까지 만 개월 수. [asOf]가 가입일보다 앞이면 -1. */
    fun months(opened: LocalDate, asOf: LocalDate): Int {
        if (asOf.isBefore(opened)) return -1
        var n = (asOf.year - opened.year) * 12 + (asOf.monthValue - opened.monthValue)
        // plusMonths는 같은 날이 없으면 그 달 말일로 맞춘다(1/31 + 1개월 = 2/28·29).
        if (opened.plusMonths(n.toLong()).isAfter(asOf)) n--
        return n
    }

    /** "7년 6개월 (91개월)" 형태. */
    fun label(months: Int): String {
        val y = months / 12
        val m = months % 12
        val ym = when {
            y == 0 -> "${m}개월"
            m == 0 -> "${y}년"
            else -> "${y}년 ${m}개월"
        }
        return "$ym (${months}개월)"
    }
}
