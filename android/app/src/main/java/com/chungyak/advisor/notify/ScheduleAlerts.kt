package com.chungyak.advisor.notify

import com.chungyak.advisor.data.Notice
import java.time.LocalDate

/**
 * 청약 일정 알림(접수 시작일·당첨 발표일) 계산 — 순수 Kotlin(단위테스트 대상).
 *
 * 규칙: 일정 전날 저녁([EVENING_HOUR]~[QUIET_HOUR]시)에 한 번 알린다. 전날을 놓쳤으면(폰이 꺼져
 * 있었거나 공고를 늦게 받은 경우) 당일 아침([MORNING_HOUR]시)부터 한 번 알린다. 밤([QUIET_HOUR]시~
 * 아침)에는 보내지 않는다. 같은 공고·같은 일정은 [key]로 한 번만 보낸다.
 */
object ScheduleAlerts {

    /** 전날 알림 시작 시각(시). */
    const val EVENING_HOUR = 18
    /** 당일 알림 시작 시각(시). */
    const val MORNING_HOUR = 8
    /** 이 시각부터 아침까지는 알리지 않는다. */
    const val QUIET_HOUR = 22

    enum class Kind(val label: String) { RECEIPT("1순위 접수"), RESULT("당첨 발표") }

    data class Alert(val notice: Notice, val kind: Kind, val date: LocalDate, val tomorrow: Boolean) {
        val key: String get() = key(notice.id, kind, date)
        /** 밴드용 짧은 머리말: "내일 접수 " / "오늘 발표 ". */
        val prefix: String get() = (if (tomorrow) "내일 " else "오늘 ") + if (kind == Kind.RECEIPT) "접수 " else "발표 "
    }

    fun key(id: String, kind: Kind, date: LocalDate) = "$id|${kind.name}|$date"

    /**
     * 지금 보내야 할 알림. [notices]는 이미 '관심·조건 일치'로 거른 목록, [sent]는 보낸 알림 키.
     */
    fun due(notices: List<Notice>, today: LocalDate, hour: Int, sent: Set<String>): List<Alert> {
        val out = ArrayList<Alert>()
        if (hour < MORNING_HOUR || hour >= QUIET_HOUR) return out
        for (n in notices) {
            for ((kind, raw) in listOf(Kind.RECEIPT to n.rank1Start, Kind.RESULT to n.resultDate)) {
                val date = parse(raw) ?: continue
                val alert = when {
                    date == today.plusDays(1) && hour >= EVENING_HOUR -> Alert(n, kind, date, tomorrow = true)
                    date == today -> Alert(n, kind, date, tomorrow = false)
                    else -> null
                } ?: continue
                if (alert.key !in sent) out += alert
            }
        }
        return out
    }

    /** 오래된 보낸-기록 정리: 일정이 [today]보다 7일 넘게 지난 키는 버린다. */
    fun prune(sent: Set<String>, today: LocalDate): Set<String> =
        sent.filterTo(HashSet()) { k -> parse(k.substringAfterLast('|'))?.let { !it.isBefore(today.minusDays(7)) } ?: false }

    private fun parse(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
}
