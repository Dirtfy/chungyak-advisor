package com.chungyak.advisor.ui

import com.chungyak.advisor.data.Notice

/** 목록 정렬 옵션. [label]은 UI 표시용. 저장은 name으로 한다. */
enum class SortOrder(val label: String) {
    NOTICE_DESC("공고일 최신순"),
    RECEIPT_NEAR("접수일 가까운 순"),
    PRICE_ASC("분양가 낮은 순"),
    PRICE_DESC("분양가 높은 순"),
    CMPET_DESC("경쟁률 높은 순"),
    /** 내 조건 판정·가점·경쟁률·분양가로 매긴 추천 점수 순(match/Recommend.kt). */
    RECOMMEND("추천순");

    companion object {
        val DEFAULT = NOTICE_DESC
        fun of(name: String?): SortOrder = entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}

/**
 * 정렬 비교자. 값이 없는 공고(분양가 0, 경쟁률 0, 접수일 공란)는 항상 뒤로 보내고,
 * 동률은 공고일 최신순으로 정리한다. [today]는 ISO yyyy-MM-dd.
 */
object NoticeSort {

    private val byNoticeDesc: Comparator<Notice> =
        compareByDescending<Notice> { it.noticeDate }.thenByDescending { it.firstSeen }

    fun comparator(order: SortOrder, today: String): Comparator<Notice> = when (order) {
        SortOrder.NOTICE_DESC -> byNoticeDesc
        // 접수 마감 전(진행·예정)을 시작일 가까운 순으로 먼저, 마감된 공고는 최근 마감순으로 뒤에.
        SortOrder.RECEIPT_NEAR -> compareBy<Notice> { receiptGroup(it, today) }
            .thenComparator { a, b ->
                if (receiptGroup(a, today) == 0) a.rank1Start.compareTo(b.rank1Start)
                else b.rank1Start.compareTo(a.rank1Start)
            }
            .then(byNoticeDesc)
        SortOrder.PRICE_ASC -> compareBy<Notice> { lowPrice(it) == 0 }
            .thenBy { lowPrice(it) }.then(byNoticeDesc)
        SortOrder.PRICE_DESC -> compareBy<Notice> { lowPrice(it) == 0 }
            .thenByDescending { lowPrice(it) }.then(byNoticeDesc)
        SortOrder.CMPET_DESC -> compareBy<Notice> { it.cmpetMaxRate <= 0.0 }
            .thenByDescending { it.cmpetMaxRate }.then(byNoticeDesc)
        // 점수가 필요해 실제 정렬은 Recommend.sort(뷰모델). 점수 없이 부르면 접수일 순으로 대신한다.
        SortOrder.RECOMMEND -> comparator(SortOrder.RECEIPT_NEAR, today)
    }

    fun sort(list: List<Notice>, order: SortOrder, today: String): List<Notice> =
        list.sortedWith(comparator(order, today))

    /** 0 = 접수 예정/진행 중, 1 = 마감, 2 = 접수일 정보 없음. */
    internal fun receiptGroup(n: Notice, today: String): Int = when {
        n.rank1Start.isBlank() -> 2
        n.rank1End.ifBlank { n.rank1Start } >= today -> 0
        else -> 1
    }

    /** 분양가 정렬 기준 = 최저가(없으면 최고가). 0 = 정보 없음. */
    private fun lowPrice(n: Notice): Int =
        if (n.priceMinManwon > 0) n.priceMinManwon else n.priceMaxManwon
}
