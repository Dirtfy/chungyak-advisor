package com.chungyak.advisor.ui

import com.chungyak.advisor.data.CompetitionPolicy
import com.chungyak.advisor.data.Notice
import java.util.Locale

/** 경쟁률 표시 문자열. */
object CompetitionFormat {

    const val BEFORE = "접수 전"
    const val PENDING = "집계 중"
    const val NONE = "정보 없음"
    const val NEED_APPLY = "data.go.kr 활용신청 필요"

    /** 12.345 → "12.35:1", 0.5 → "0.50:1", 123.4 → "123.4:1". 0 이하 → "-". */
    fun rate(v: Double): String = when {
        v <= 0.0 -> "-"
        v >= 100 -> String.format(Locale.US, "%.1f:1", v)
        else -> String.format(Locale.US, "%.2f:1", v)
    }

    /**
     * 목록용 한 줄 (1순위 해당지역 기준). 우선순위: 값 있음 → 접수 전 → 활용신청 필요 →
     * 확정인데 없음(정보 없음) → 집계 중.
     */
    fun summary(n: Notice, today: String, unauthorized: Boolean): String = when {
        n.cmpetMaxRate > 0 ->
            "최고 ${rate(n.cmpetMaxRate)} · 평균 ${rate(n.cmpetAvgRate)}" +
                (if (n.cmpetAvgRate > 0 && n.cmpetAvgRate < 1) " (미달)" else "")
        CompetitionPolicy.beforeReceipt(n, today) -> BEFORE
        unauthorized -> NEED_APPLY
        n.cmpetFinal -> NONE
        else -> PENDING
    }
}
