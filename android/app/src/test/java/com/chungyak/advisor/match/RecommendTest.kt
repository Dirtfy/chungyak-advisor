package com.chungyak.advisor.match

import com.chungyak.advisor.notice
import com.chungyak.advisor.ui.NoticeSort
import com.chungyak.advisor.ui.SortOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class RecommendTest {

    private val ok = Eligibility.Result(Eligibility.Verdict.ELIGIBLE, emptyList())
    private val check = Eligibility.Result(Eligibility.Verdict.CHECK, emptyList())
    private val no = Eligibility.Result(Eligibility.Verdict.INELIGIBLE, emptyList())

    @Test fun cmpetScore_logScale() {
        assertEquals(25, Recommend.cmpetScore(0.5))
        assertEquals(25, Recommend.cmpetScore(1.0))
        assertEquals(13, Recommend.cmpetScore(10.0))
        assertEquals(0, Recommend.cmpetScore(100.0))
        assertEquals(0, Recommend.cmpetScore(400.0))
    }

    @Test fun noProfile_neutralParts() {
        // 판정 20 + 가점 10 + 경쟁률 없음 13 + 분양가 8
        assertEquals(51, Recommend.score(notice("a"), null, Profile()).total)
    }

    @Test fun verdict_budget_cmpet() {
        val p = Profile(
            sido = "서울", birthDate = "1985-01-01", married = true, children = 2, dependents = 3,
            account = AccountType.COMPREHENSIVE, accountOpened = "2010-01-01", maxPriceManwon = 90000,
        )
        // 공고일 2026-09-01 기준 가점: 무주택 11년 8개월 24 + 부양 20 + 통장 17 = 61 → 20×61/84 = 14.5 → 15
        val n = notice("a", priceMin = 50000, priceMax = 80000, cmpetAvg = 1.0)
        val s = Recommend.score(n, ok, p)
        assertEquals(listOf(40, 15, 25, 15), s.parts.map { it.score })
        assertEquals(95, s.total)
        assertEquals(20, Recommend.score(n, check, p).parts[0].score)
        assertEquals(0, Recommend.score(n, no, p).parts[0].score)
        assertEquals(0, Recommend.score(n, ok.copy(filteredOut = "관심 지역 아님"), p).parts[0].score)
        assertEquals(8, Recommend.score(notice("b", priceMin = 80000, priceMax = 120000), ok, p).parts[3].score)
        assertEquals(0, Recommend.score(notice("c", priceMin = 100000, priceMax = 120000), ok, p).parts[3].score)
    }

    @Test fun sort_openFirst_thenScore() {
        val today = "2026-10-06"
        val openLow = notice("openLow", rank1Start = "2026-10-10")
        val openHigh = notice("openHigh", rank1Start = "2026-10-20")
        val closedTop = notice("closedTop", rank1Start = "2026-09-01", rank1End = "2026-09-02")
        val noDate = notice("noDate")
        val scores = mapOf(
            "openLow" to Recommend.score(openLow, no, Profile(sido = "서울")),
            "openHigh" to Recommend.score(openHigh, ok, Profile(sido = "서울")),
            "closedTop" to Recommend.score(closedTop, ok, Profile(sido = "서울")),
            "noDate" to Recommend.score(noDate, ok, Profile(sido = "서울")),
        )
        assertEquals(
            listOf("openHigh", "openLow", "noDate", "closedTop"),
            Recommend.sort(listOf(closedTop, noDate, openLow, openHigh), scores, today).map { it.id },
        )
    }

    @Test fun sortOrder_recommend_hasLabel_andFallbackComparator() {
        assertEquals(SortOrder.RECOMMEND, SortOrder.of("RECOMMEND"))
        assertEquals("추천순", SortOrder.RECOMMEND.label)
        NoticeSort.sort(listOf(notice("x"), notice("y")), SortOrder.RECOMMEND, "2026-10-06")
    }
}
