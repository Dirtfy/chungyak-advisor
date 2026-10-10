package com.chungyak.advisor.match

import com.chungyak.advisor.data.Notice

/**
 * 관심 지역(v0.13.0~): 시·도 아래 시·군·구 단위 선택과 공고 주소 → 시·군·구 판별. 순수 Kotlin(단위테스트 대상).
 *
 * 선택 값([Profile.interestRegions])은 문자열 집합이다.
 *  - "경기"        = 경기 전체(지금 있는 31개 시·군 + 주소에서 시·군을 못 읽은 공고 + 앞으로 생길 시·군).
 *  - "경기 수원시" = 그 시·군만.
 * 한 시·도의 시·군·구를 모두 고르면 "경기" 하나로 합친다([normalize]). 그래서 v0.12.0까지 저장된 "경기"는
 * 바꿀 것 없이 그대로 '경기 전체 = 31개 시·군 모두 선택'으로 읽힌다(마이그레이션 = 항등).
 *
 * 공고의 시·군·구는 공급위치(HSSPLY_ADRES)에서 읽는다. 시·도는 공급지역(SUBSCRPT_AREA_CODE_NM)을 먼저 본다.
 * 시·군·구를 못 읽은 공고는 버리지 않는다 — 그 시·도에서 하나라도 골랐으면 포함하고 [Match.note]로 표시한다.
 *
 * 행정구역 기준일 [AREAS_DATE]: 인천은 2026-07-01 개편(중구·동구 → 제물포구·영종구, 서구 → 서해구·검단구) 반영.
 * 개편 전 이름("인천광역시 중구 …")으로 적힌 공고는 동 이름으로 새 구를 고른다([legacyIncheon]).
 */
object Regions {

    const val AREAS_DATE = "2026-07-01"

    val SIDO = listOf("서울", "경기", "인천")

    /** 시·도별 시·군·구(행정안전부 행정구역 순서). */
    val SIGUNGU: Map<String, List<String>> = mapOf(
        "서울" to listOf(
            "종로구", "중구", "용산구", "성동구", "광진구", "동대문구", "중랑구", "성북구", "강북구", "도봉구",
            "노원구", "은평구", "서대문구", "마포구", "양천구", "강서구", "구로구", "금천구", "영등포구", "동작구",
            "관악구", "서초구", "강남구", "송파구", "강동구",
        ),
        "경기" to listOf(
            "수원시", "성남시", "의정부시", "안양시", "부천시", "광명시", "평택시", "동두천시", "안산시", "고양시",
            "과천시", "구리시", "남양주시", "오산시", "시흥시", "군포시", "의왕시", "하남시", "용인시", "파주시",
            "이천시", "안성시", "김포시", "화성시", "광주시", "양주시", "포천시", "여주시", "연천군", "가평군", "양평군",
        ),
        "인천" to listOf(
            "제물포구", "영종구", "미추홀구", "연수구", "남동구", "부평구", "계양구", "서해구", "검단구", "강화군", "옹진군",
        ),
    )

    /** 화면 문구: 시·도마다 하위 단위 이름. */
    fun unitName(sido: String) = when (sido) {
        "경기" -> "시·군"
        "인천" -> "구·군"
        else -> "구"
    }

    private val SIDO_ALIASES = mapOf(
        "서울" to setOf("서울특별시", "서울시", "서울"),
        "경기" to setOf("경기도", "경기"),
        "인천" to setOf("인천광역시", "인천시", "인천"),
    )

    // 개편 전 인천 구 이름 → 새 구. 영종도(옛 중구)·검단(옛 서구) 동 이름으로 가른다.
    private val YEONGJONG_DONG = listOf("영종", "운서", "운남", "운북", "중산", "을왕", "남북동", "덕교", "무의", "용유")
    private val GEOMDAN_DONG = listOf("검단", "마전", "당하", "원당", "불로", "대곡", "오류", "왕길", "금곡")

    /** 한 공고의 위치. [sigungu]가 비어 있으면 시·군·구 미확인. [note] = 판별 근거가 애매할 때 남기는 표시. */
    data class Place(val sido: String, val sigungu: List<String>, val note: String? = null)

    fun locate(n: Notice): Place = locate(n.areaName, n.address)

    fun locate(areaName: String, address: String): Place {
        val toks = tokens(address)
        val sido = SIDO.firstOrNull { areaName.contains(it) }
            ?: toks.firstNotNullOfOrNull { t -> sidoOf(t) }
            ?: return Place("", emptyList())
        val names = SIGUNGU.getValue(sido)
        val found = LinkedHashSet<String>()
        var note: String? = null
        // 주소가 여러 시·도에 걸치면(예: "인천광역시 계양구 … 서울특별시 강서구 …") 공고 시·도 구간만 본다.
        var current = sido
        for (t in toks) {
            val other = sidoOf(t)
            if (other != null) current = other
            if (other != null || current != sido) continue
            val hit = names.firstOrNull { t == it || t == it.dropLast(1) + "특례시" }
            val legacy = if (hit == null && sido == "인천") legacyIncheon(t, address) else null
            if (hit != null) found += hit
            if (legacy != null) { found += legacy.first; note = legacy.second }
        }
        return Place(sido, found.toList(), note)
    }

    private fun sidoOf(token: String): String? = SIDO.firstOrNull { token in SIDO_ALIASES.getValue(it) }

    private fun legacyIncheon(token: String, address: String): Pair<String, String>? = when (token) {
        "중구" -> (if (YEONGJONG_DONG.any { address.contains(it) }) "영종구" else "제물포구").let { it to "옛 행정구역 '중구' 주소 → $it(으)로 판단" }
        "동구" -> "제물포구" to "옛 행정구역 '동구' 주소 → 제물포구로 판단"
        "서구" -> (if (GEOMDAN_DONG.any { address.contains(it) }) "검단구" else "서해구").let { it to "옛 행정구역 '서구' 주소 → $it(으)로 판단" }
        else -> null
    }

    /** 괄호 안(사업지구명 등)을 빼고 공백·쉼표로 자른 낱말. */
    internal fun tokens(address: String): List<String> =
        address.replace(Regex("\\([^)]*\\)?"), " ").split(Regex("[\\s,·/]+")).filter { it.isNotBlank() }

    /** 목록·상세 배지: "경기 수원시". 시·군·구를 못 읽으면 공급지역 그대로. */
    fun label(n: Notice): String {
        val p = locate(n)
        return if (p.sido.isEmpty() || p.sigungu.isEmpty()) n.areaName.ifBlank { p.sido } else "${p.sido} ${p.sigungu.first()}"
    }

    // ── 선택 집합 다루기 ──

    fun key(sido: String, sigungu: String) = "$sido $sigungu"

    /** [sido]에서 고른 시·군·구. 시·도 전체를 골랐으면 전부. */
    fun picked(sel: Set<String>, sido: String): Set<String> =
        if (sido in sel) SIGUNGU.getValue(sido).toSet()
        else sel.filter { it.startsWith("$sido ") }.map { it.removePrefix("$sido ") }.toSet()

    fun isAll(sel: Set<String>, sido: String) = sido in sel

    /** 'OO 전체' 체크: 전체였으면 그 시·도를 모두 해제, 아니면 전체로. */
    fun toggleAll(sel: Set<String>, sido: String): Set<String> {
        val rest = sel.filterNot { it == sido || it.startsWith("$sido ") }.toSet()
        return if (sido in sel) rest else rest + sido
    }

    /** 시·군·구 하나 켜기/끄기. 전체 상태에서 하나를 끄면 나머지가 개별 선택으로 풀린다. */
    fun toggle(sel: Set<String>, sido: String, sigungu: String): Set<String> {
        val now = picked(sel, sido).let { if (sigungu in it) it - sigungu else it + sigungu }
        val rest = sel.filterNot { it == sido || it.startsWith("$sido ") }.toSet()
        return normalize(rest + now.map { key(sido, it) })
    }

    /** 한 시·도의 시·군·구를 모두 골랐으면 시·도 하나로 합친다. 모르는 값(예: 옛 버전의 "기타")은 그대로 둔다. */
    fun normalize(sel: Set<String>): Set<String> {
        var out = sel
        for (sido in SIDO) {
            if (sido in out) {
                out = out.filterNot { it.startsWith("$sido ") }.toSet()
            } else if (picked(out, sido).containsAll(SIGUNGU.getValue(sido))) {
                out = out.filterNot { it.startsWith("$sido ") }.toSet() + sido
            }
        }
        return out
    }

    /** 고른 시·도(일부만 골라도 포함). 옛 버전 앱이 읽는 interestSido 값 — 내려 설치해도 공고가 빠지지 않게 넓게. */
    fun parents(sel: Set<String>): Set<String> = sel.map { it.substringBefore(' ') }.toSet()

    /** 요약 문구: "경기 수원시·화성시, 서울 전체". */
    fun summary(sel: Set<String>): String = SIDO.mapNotNull { sido ->
        val p = picked(sel, sido)
        when {
            sido in sel -> "$sido 전체"
            p.isEmpty() -> null
            else -> "$sido " + SIGUNGU.getValue(sido).filter { it in p }.joinToString("·")
        }
    }.joinToString(", ").ifBlank { "전체" }

    // ── 공고 매칭 ──

    /** [ok] = 관심 지역에 든다. [note] = 포함은 했지만 시·군·구를 확실히 못 가린 경우의 표시(상세 화면 메모). */
    data class Match(val ok: Boolean, val note: String? = null)

    fun match(sel: Set<String>, n: Notice): Match {
        if (sel.isEmpty()) return Match(true)
        val place = locate(n)
        if (place.sido.isEmpty()) {
            // 시·도도 못 읽음: 예전처럼 공급지역·주소 글자로만 비교(옛 값 포함).
            val ok = parents(sel).any { n.areaName.contains(it) || n.address.contains(it) }
            return Match(ok, if (ok) "공고 시·도 미확인 — 공급지역 글자로 관심 지역 판단" else null)
        }
        if (place.sido in sel) return Match(true, place.note)
        val picked = picked(sel, place.sido)
        if (picked.isEmpty()) return Match(false)
        if (place.sigungu.isEmpty()) {
            return Match(true, "${place.sido} ${unitName(place.sido)} 미확인 — 공급위치에서 ${unitName(place.sido)}를 읽지 못해 ${place.sido} 관심 공고로 포함(주소 확인)")
        }
        return if (place.sigungu.any { it in picked }) Match(true, place.note) else Match(false)
    }
}
