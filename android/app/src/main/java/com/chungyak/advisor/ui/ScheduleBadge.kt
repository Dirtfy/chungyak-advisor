package com.chungyak.advisor.ui

import com.chungyak.advisor.data.Notice
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 목록 카드 오른쪽 위 일정 배지(리디자인, docs/11). 날짜는 "yyyy-MM-dd". */
object ScheduleBadge {

    enum class Tone { UPCOMING, OPEN, WAITING, CLOSED }

    data class Badge(val text: String, val tone: Tone)

    /** 접수 전 "접수 D-n" → 접수 중(시작일~마감일) → 발표 전 "발표 D-n" → "발표 완료"(발표일부터). 날짜 없으면 null. */
    fun of(n: Notice, today: String): Badge? {
        val t = parse(today) ?: return null
        val start = parse(n.rank1Start)
        val end = parse(n.rank1End) ?: start
        val result = parse(n.resultDate)
        return when {
            start != null && t < start -> Badge("접수 ${dday(t, start)}", Tone.UPCOMING)
            start != null && end != null && t <= end -> Badge("접수 중", Tone.OPEN)
            result != null && t < result -> Badge("발표 ${dday(t, result)}", Tone.WAITING)
            result != null -> Badge("발표 완료", Tone.CLOSED)
            else -> null
        }
    }

    private fun dday(from: LocalDate, to: LocalDate): String =
        "D-" + ChronoUnit.DAYS.between(from, to)

    private fun parse(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
}
