package com.chungyak.advisor.ui

import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Test

class NoticeSortTest {

    private val today = "2026-10-02"
    private fun ids(order: SortOrder, vararg n: com.chungyak.advisor.data.Notice) =
        NoticeSort.sort(n.toList(), order, today).map { it.id }

    @Test fun noticeDate_newestFirst() {
        assertEquals(listOf("b", "a"), ids(SortOrder.NOTICE_DESC,
            notice("a", noticeDate = "2026-09-01"), notice("b", noticeDate = "2026-09-20")))
    }

    @Test fun receipt_upcomingNearestFirst_thenClosed_thenUnknown() {
        val order = ids(SortOrder.RECEIPT_NEAR,
            notice("closedOld", rank1Start = "2026-09-10", rank1End = "2026-09-10"),
            notice("unknown"),
            notice("later", rank1Start = "2026-10-20", rank1End = "2026-10-20"),
            notice("ongoing", rank1Start = "2026-10-01", rank1End = "2026-10-02"),
            notice("closedNew", rank1Start = "2026-09-28", rank1End = "2026-09-28"),
            notice("soon", rank1Start = "2026-10-05", rank1End = "2026-10-05"),
        )
        assertEquals(listOf("ongoing", "soon", "later", "closedNew", "closedOld", "unknown"), order)
    }

    @Test fun price_usesLowest_andMissingLast() {
        val a = notice("a", priceMin = 50000, priceMax = 90000)
        val b = notice("b", priceMin = 0, priceMax = 60000) // 최저 없음 → 최고가로
        val c = notice("c", priceMin = 70000, priceMax = 70000)
        val none = notice("none")
        assertEquals(listOf("a", "b", "c", "none"), ids(SortOrder.PRICE_ASC, none, c, b, a))
        assertEquals(listOf("c", "b", "a", "none"), ids(SortOrder.PRICE_DESC, none, a, b, c))
    }

    @Test fun competition_highestFirst_missingLast() {
        assertEquals(listOf("hi", "lo", "none"), ids(SortOrder.CMPET_DESC,
            notice("none"), notice("lo", cmpetMax = 0.8), notice("hi", cmpetMax = 45.2)))
    }

    @Test fun sortOrder_persistedNameRoundTrip() {
        assertEquals(SortOrder.PRICE_DESC, SortOrder.of("PRICE_DESC"))
        assertEquals(SortOrder.NOTICE_DESC, SortOrder.of(""))
        assertEquals(SortOrder.NOTICE_DESC, SortOrder.of("bogus"))
    }
}
