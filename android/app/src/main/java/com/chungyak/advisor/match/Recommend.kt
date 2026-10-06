package com.chungyak.advisor.match

import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.ui.NoticeSort
import java.time.LocalDate
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 추천 점수(0~100)와 '추천순' 정렬 — 순수 Kotlin(단위테스트 대상). 산식은 docs/12_가점_추천순.md.
 *
 * 판정(40) + 가점(20) + 경쟁률(25) + 분양가 적합도(15). 정보가 없는 항목은 중간값을 준다.
 * 정렬은 접수 예정·진행 중 → 접수일 미정 → 접수 마감 순으로 묶고, 묶음 안에서 점수 높은 순.
 */
object Recommend {

    data class Part(val name: String, val score: Int, val max: Int, val basis: String)

    data class Score(val parts: List<Part>) {
        val total: Int get() = parts.sumOf { it.score }
    }

    const val VERDICT_MAX = 40
    const val GAJEOM_MAX = 20
    const val CMPET_MAX = 25
    const val PRICE_MAX = 15

    fun score(n: Notice, match: Eligibility.Result?, profile: Profile): Score {
        val set = profile.isSet
        val verdict = when {
            !set || match == null -> Part("판정", VERDICT_MAX / 2, VERDICT_MAX, "내 조건 미입력 — 중간값")
            match.filteredOut != null -> Part("판정", 0, VERDICT_MAX, "관심 조건 제외")
            match.verdict == Eligibility.Verdict.ELIGIBLE -> Part("판정", VERDICT_MAX, VERDICT_MAX, "신청 가능")
            match.verdict == Eligibility.Verdict.CHECK -> Part("판정", VERDICT_MAX / 2, VERDICT_MAX, "확인 필요")
            else -> Part("판정", 0, VERDICT_MAX, "불가")
        }

        val gajeom = if (!set) Part("가점", GAJEOM_MAX / 2, GAJEOM_MAX, "내 조건 미입력 — 중간값") else {
            val g = Gajeom.calc(profile, parse(n.noticeDate) ?: LocalDate.now())
            val pts = g.total ?: g.known
            Part("가점", (pts * GAJEOM_MAX / Gajeom.MAX.toDouble()).roundToInt(), GAJEOM_MAX, "${pts}점/${Gajeom.MAX} 비례")
        }

        // 경쟁률은 낮을수록 유리: 1:1 이하 만점, 100:1 이상 0점, 그 사이는 로그 비례. 평균이 없으면 최고값.
        val rate = if (n.cmpetAvgRate > 0) n.cmpetAvgRate else n.cmpetMaxRate
        val cmpet = if (rate <= 0) Part("경쟁률", (CMPET_MAX + 1) / 2, CMPET_MAX, "아직 없음 — 중간값") else {
            val s = cmpetScore(rate)
            Part("경쟁률", s, CMPET_MAX, "${"%.1f".format(rate)}:1")
        }

        val budget = profile.maxPriceManwon
        val low = if (n.priceMinManwon > 0) n.priceMinManwon else n.priceMaxManwon
        val high = if (n.priceMaxManwon > 0) n.priceMaxManwon else n.priceMinManwon
        val price = when {
            !set || budget <= 0 -> Part("분양가", 8, PRICE_MAX, "분양가 상한 미입력 — 중간값")
            low <= 0 -> Part("분양가", 7, PRICE_MAX, "분양가 확인 중 — 중간값")
            high <= budget -> Part("분양가", PRICE_MAX, PRICE_MAX, "전 주택형이 상한 이내")
            low <= budget -> Part("분양가", 8, PRICE_MAX, "일부 주택형만 상한 이내")
            else -> Part("분양가", 0, PRICE_MAX, "상한 초과")
        }
        return Score(listOf(verdict, gajeom, cmpet, price))
    }

    /** 경쟁률 → 0~[CMPET_MAX]. 25 × (1 − ln(rate)/ln(100)), rate는 1~100으로 자름. */
    fun cmpetScore(rate: Double): Int {
        val r = rate.coerceIn(1.0, 100.0)
        return (CMPET_MAX * (1 - ln(r) / ln(100.0))).roundToInt()
    }

    /** 접수 예정·진행 중(0) → 접수일 미정(1) → 접수 마감(2) 순으로 묶고, 점수 높은 순, 동점은 공고일 최신순. */
    fun sort(list: List<Notice>, scores: Map<String, Score>, today: String): List<Notice> =
        list.sortedWith(
            compareBy<Notice> { group(it, today) }
                .thenByDescending { scores[it.id]?.total ?: 0 }
                .thenByDescending { it.noticeDate }
                .thenByDescending { it.firstSeen }
        )

    private fun group(n: Notice, today: String): Int = when (NoticeSort.receiptGroup(n, today)) {
        0 -> 0
        2 -> 1
        else -> 2
    }

    private fun parse(s: String): LocalDate? = runCatching { LocalDate.parse(s.trim().take(10)) }.getOrNull()
}
