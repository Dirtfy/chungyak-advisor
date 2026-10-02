package com.chungyak.advisor.data

/**
 * 경쟁률 조회 시점 규칙 (쿼터 절약). 날짜는 ISO yyyy-MM-dd 문자열 비교.
 * - 접수 시작(1순위, 없으면 모집공고일) 전: 조회 안 함 → '접수 전'.
 * - 접수 시작 ~ 당첨자 발표일 전: 2·기타지역 접수가 이어지므로 [REFETCH_MS] 간격으로 재조회.
 * - 발표일 이후 한 번 더 받으면 확정(cmpetFinal) → 이후 호출 없음.
 */
object CompetitionPolicy {

    const val REFETCH_MS = 3 * 60 * 60 * 1000L

    fun receiptStart(n: Notice): String = n.rank1Start.ifBlank { n.noticeDate }

    fun beforeReceipt(n: Notice, today: String): Boolean {
        val start = receiptStart(n)
        return start.isNotBlank() && today < start
    }

    fun shouldFetch(n: Notice, today: String, now: Long): Boolean =
        !n.cmpetFinal && !beforeReceipt(n, today) &&
            (n.cmpetFetchedAt == 0L || now - n.cmpetFetchedAt >= REFETCH_MS)

    /** 이번 응답으로 확정할지: 발표일(없으면 1순위 종료일) 당일 이후면 확정. */
    fun isFinal(n: Notice, today: String): Boolean {
        val end = n.resultDate.ifBlank { n.rank1End }
        return end.isNotBlank() && today >= end
    }
}
