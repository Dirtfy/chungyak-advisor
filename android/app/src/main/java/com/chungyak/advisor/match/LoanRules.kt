package com.chungyak.advisor.match

import com.chungyak.advisor.data.Notice

/** 대출 금리 유형 — 스트레스 DSR에서 가산금리를 얼마나 붙일지([LoanRules.stressRatio])가 다르다. */
enum class RateType(val label: String) {
    VARIABLE("변동형"),
    MIXED("혼합형(5년 고정)"),
    PERIODIC("주기형(5년)"),
    FIXED("만기 고정");

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: MIXED
    }
}

/**
 * 주택 구입 대출 규정 숫자 모음(v0.12.0~, docs/16). 규정이 바뀌면 이 파일만 고치고 [RULES_DATE]를 올린다.
 * 출처·해석은 docs/16_대출_규정.md. 금액은 만원, 비율은 %.
 * 은행 심사 그대로가 아니라 공개된 규정으로 계산한 추정이다(은행 내규·감정가·신용 등은 반영 못 함).
 */
object LoanRules {

    /** 이 표가 반영한 규정의 기준일. 앱 안내 문구에 그대로 나온다. */
    const val RULES_DATE = "2026-10-10"

    // ── LTV(주택담보인정비율, 담보가치 = 분양가로 봄) ── 금융위 10·15(2025) 대출수요 관리 방안, 6·27(2025) 가계부채 관리 방안
    const val LTV_REGULATED_HOMELESS = 40    // 규제지역 무주택(처분조건부 1주택 포함)
    const val LTV_REGULATED_SEOMIN = 60      // 규제지역 서민·실수요자
    const val LTV_FIRST_TIME = 70            // 생애최초(수도권·규제지역, 6개월 내 전입 의무)
    const val LTV_NON_REGULATED = 70         // 비규제지역 무주택
    const val LTV_FIRST_TIME_LOCAL = 80      // 지방 비규제 생애최초(참고 — 앱 공고는 수도권뿐)
    const val LTV_MULTI_LOCAL = 60           // 지방 비규제 다주택(참고)

    /** 서민·실수요자: 부부합산 연소득 이하 + 주택가격 이하 + 무주택세대주. */
    const val SEOMIN_INCOME = 9_000
    const val SEOMIN_PRICE = 80_000

    /** 규제지역(투기과열·조정) 구입 주담대 최대 금액: (시가 이하 to 한도) 순서대로, 넘으면 [PRICE_CAP_TOP]. 10·15. */
    val PRICE_CAP_TIERS = listOf(150_000 to 60_000, 250_000 to 40_000)
    const val PRICE_CAP_TOP = 20_000
    /** 수도권 비규제 구입 주담대 최대 금액. 6·27. */
    const val PRICE_CAP_METRO = 60_000

    // ── DSR(총부채원리금상환비율) ── 은행업감독규정 별표6, 스트레스 DSR 3단계(2025.7.1~), 10·15 하한 상향
    const val DSR_LIMIT_PCT = 40             // 은행권(2금융권 50%는 반영 안 함)
    const val STRESS_METRO = 3.0             // 수도권·규제지역 주담대 스트레스 금리(%p, 하한 3.0 — 2025.10.16~)
    const val STRESS_LOCAL = 0.75            // 지방 주담대(2단계 유지: 기본 1.5 × 50%, 참고)

    /** 금리 유형별 스트레스 금리 반영 비율. 변동 100%, 5년 혼합 80%, 5년 주기 40%, 만기 고정 0%. */
    fun stressRatio(t: RateType): Double = when (t) {
        RateType.VARIABLE -> 1.0
        RateType.MIXED -> 0.8
        RateType.PERIODIC -> 0.4
        RateType.FIXED -> 0.0
    }

    /** DSR에서 만기일시상환 원금은 대출총액 ÷ 대출기간(최대 이 햇수). */
    const val BULLET_DSR_MAX_YEARS = 10
    /** DSR에서 체증식은 초기 이 햇수(상환기간 10년 이하면 [GRADUATED_DSR_YEARS_SHORT]) 원리금의 연평균. */
    const val GRADUATED_DSR_YEARS = 10
    const val GRADUATED_DSR_YEARS_SHORT = 5

    // ── 상품 조건 ──
    const val MAX_YEARS_METRO = 30           // 수도권·규제지역 주담대 만기 30년 이내(6·27)
    const val MAX_GRACE_YEARS = 1            // 주담대 거치기간 보통 1년 이내(가계부채 가이드라인)
    /**
     * 체증식: 월 상환액이 해마다 이만큼(%) 늘어난다고 본다(근사). 정부 예시(3억·40년·4.6%: 첫 달 약 20만원·첫해 228만원 덜 내고,
     * 총이자 약 3,800만원 더 냄)에 가장 가깝게 고른 값 — 1.0%면 첫 달 18.7만원·첫해 217만원 덜, 총이자 3,962만원 더.
     */
    const val GRADUATED_STEP_PCT = 1.0

    // ── 분양 대금 흐름 ── 계약금(현금) → 중도금(집단대출, DSR 미적용) → 입주 때 잔금대출로 전환(위 규정 전부 적용)
    const val MID_PAYMENT_PCT = 60           // 일반적인 중도금 비율(분양가의 60%)

    /** 앱이 다루는 수도권 시·도. */
    private val METRO = listOf("서울", "경기", "인천")

    /**
     * 2026-07-01 기준 규제지역(투기과열지구·조정대상지역). 서울은 25개 구 전부.
     * (시, 구 — null이면 시 전체). 동탄구·기흥구·구리시는 2026-06-30 추가 지정(7/1 시행).
     */
    private val REGULATED_GYEONGGI: List<Pair<String, String?>> = listOf(
        "과천시" to null, "광명시" to null, "성남시" to null, "의왕시" to null, "하남시" to null, "구리시" to null,
        "수원시" to "영통구", "수원시" to "장안구", "수원시" to "팔달구", "안양시" to "동안구",
        "용인시" to "수지구", "용인시" to "기흥구", "화성시" to "동탄", // 동탄구(2026 분구) — 옛 주소의 '동탄'도 포함
    )

    /** 공고 지역 구분. [source]는 화면에 보일 근거. */
    data class Area(val metro: Boolean, val regulated: Boolean, val source: String)

    /**
     * 공고의 지역 구분. 청약홈 공고의 투기과열지구·조정대상지역 표시가 있으면 그걸로, 없으면 주소를 위 목록과 맞춰 본다
     * (지정 뒤 나온 공고·표시가 빠진 공고 대비 — 하나라도 해당하면 규제지역).
     */
    fun area(n: Notice): Area {
        val metro = n.areaName in METRO || METRO.any { n.address.startsWith(it) }
        return when {
            n.speculationArea -> Area(metro, true, "투기과열지구(공고)")
            n.adjustmentArea -> Area(metro, true, "조정대상지역(공고)")
            inRegulatedList(n.areaName, n.address) -> Area(metro, true, "규제지역(${RULES_DATE} 지정 목록)")
            else -> Area(metro, false, if (metro) "수도권 비규제지역" else "비규제지역")
        }
    }

    fun inRegulatedList(areaName: String, address: String): Boolean {
        if (areaName == "서울" || address.startsWith("서울")) return true
        if (areaName != "경기" && !address.startsWith("경기")) return false
        return REGULATED_GYEONGGI.any { (city, gu) -> address.contains(city) && (gu == null || address.contains(gu)) }
    }

    /** 적용 LTV. [pct] = 0이면 그 조건으로는 구입 주담대 불가. */
    data class Ltv(val pct: Int, val label: String)

    /** 생애최초: 세대 무주택 + 세대원 누구도 집을 가진 적 없음(내 조건 '세대·주택'). */
    fun firstTime(p: Profile) = p.homesOwned == 0 && !p.everOwned

    fun ltv(p: Profile, price: Int, a: Area): Ltv {
        val strict = a.metro || a.regulated
        return when {
            p.homesOwned >= 2 ->
                if (strict) Ltv(0, "다주택자 수도권·규제지역 구입 주담대 불가") else Ltv(LTV_MULTI_LOCAL, "다주택")
            p.homesOwned == 1 && !p.sellingHome ->
                if (strict) Ltv(0, "1주택자 수도권·규제지역 추가 구입 주담대 불가(기존 집 처분조건부만 가능)")
                else Ltv(LTV_NON_REGULATED, "1주택")
            firstTime(p) ->
                if (strict) Ltv(LTV_FIRST_TIME, "생애최초") else Ltv(LTV_FIRST_TIME_LOCAL, "생애최초")
            a.regulated && seomin(p, price) -> Ltv(LTV_REGULATED_SEOMIN, "규제지역 서민·실수요자")
            a.regulated -> Ltv(LTV_REGULATED_HOMELESS, if (p.homesOwned == 1) "규제지역 처분조건부 1주택" else "규제지역 무주택")
            else -> Ltv(LTV_NON_REGULATED, if (p.homesOwned == 1) "비규제지역 처분조건부 1주택" else "비규제지역 무주택")
        }
    }

    /** 서민·실수요자(규제지역 LTV 60%): 무주택세대주 + 부부합산 연소득 9천만원 이하 + 주택가격 8억 이하. 소득 미입력이면 아님. */
    fun seomin(p: Profile, price: Int) =
        p.homesOwned == 0 && p.householdHead && p.incomeManwon in 0..SEOMIN_INCOME && price <= SEOMIN_PRICE

    /** 구입 주담대 최대 금액(만원)과 근거. 지방 비규제는 금액 상한 없음(null). */
    fun priceCap(price: Int, a: Area): Pair<Int, String>? = when {
        a.regulated -> {
            val tier = PRICE_CAP_TIERS.firstOrNull { price <= it.first }
            if (tier != null) tier.second to "시가 ${tier.first / 10_000}억 이하 주담대 ${tier.second / 10_000}억 상한"
            else PRICE_CAP_TOP to "시가 ${PRICE_CAP_TIERS.last().first / 10_000}억 초과 주담대 ${PRICE_CAP_TOP / 10_000}억 상한"
        }
        a.metro -> PRICE_CAP_METRO to "수도권 주담대 ${PRICE_CAP_METRO / 10_000}억 상한"
        else -> null
    }

    /** DSR 심사용 스트레스 가산금리(%p). */
    fun stress(t: RateType, a: Area): Double = (if (a.metro || a.regulated) STRESS_METRO else STRESS_LOCAL) * stressRatio(t)

    /** 실제 계산에 쓰는 만기. 수도권·규제지역은 30년 이내. */
    fun years(p: Profile, a: Area): Int = if (a.metro || a.regulated) p.loanYears.coerceAtMost(MAX_YEARS_METRO) else p.loanYears
}
