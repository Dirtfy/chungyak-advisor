package com.chungyak.advisor.map

/**
 * 청약홈 공급위치(HSSPLY_ADRES)를 지오코딩용 검색어로 정리한다.
 *
 * 실제 값 예(2026-10 청약홈):
 *  - "경기도 수원시 권선구 서둔동 212-1번지 일원"
 *  - "경기도 용인시 처인구 양지읍 양지리 산105-8번지 일원"
 *  - "경기도 평택시 고덕동 고덕면 일원(평택고덕국제화계획지구 A65BL)"
 *  - "경기도 광명시 소하동 광명 구름산지구 도시개발사업지구 A6BL"
 *  - "인천광역시 계양구 귤현동, 동양동, 박촌동, ... 서울특별시 강서구 오곡동 일원 인천계양 테크노밸리 ..."
 *  - "경기도 화성특례시 만세구 향남읍 하길리 404-2번지 일원" (2026 신설 행정구 — 지도 DB에 없을 수 있음)
 *
 * 그대로는 지오코더가 못 찾으므로 [candidates]가 정확한 것부터 넓은 것 순으로 검색어를 만든다.
 */
object AddressQuery {

    /** 검색어 하나. [exact] = 지번까지 포함(공급위치 자체), false = 동·읍·면 등 주변 중심(대략). */
    data class Candidate(val query: String, val exact: Boolean)

    private val ADMIN = Regex(".+(특별시|광역시|특별자치시|특별자치도|도|시|군|구|읍|면|동|리|\\d가|로|길)$")
    private val LOT = Regex("^(산\\s?)?\\d+(-\\d+)?$")

    /** 행정구역 + 지번까지만 남긴 토큰. 괄호·"일원"·"외 N필지"·"번지"·사업지구명 제거, 여러 동 나열은 첫 동만. */
    fun tokens(address: String): List<String> {
        var s = address.replace(Regex("\\([^)]*\\)?"), " ")
        s = s.substringBefore(',')
        s = s.replace(Regex("\\s외\\s*\\d+\\s*필지.*"), " ")
        s = s.replace(Regex("일원.*"), " ")
        s = s.replace("번지", " ")
        s = s.replace(Regex("(^|\\s)산\\s+(\\d)"), "$1산$2")
        val out = ArrayList<String>()
        for (t in s.trim().split(Regex("\\s+"))) {
            when {
                t.isEmpty() -> continue
                LOT.matches(t) -> { if (out.isNotEmpty()) out.add(t); break }
                ADMIN.matches(t) -> out.add(t)
                else -> break // "광명 구름산지구 ..." 같은 사업지구명부터는 버린다.
            }
        }
        return out
    }

    /**
     * 정확한 것부터: 지번 포함 → 리/동 → 읍·면 → 시·군·구. 각 단계마다 신설 "특례시 + 행정구"를
     * 옛 이름(○○시, 구 생략)으로 바꾼 것도 시도한다. 시·도 하나만 남는 검색어는 만들지 않는다(너무 넓음).
     */
    fun candidates(address: String): List<Candidate> {
        var toks = tokens(address)
        val out = LinkedHashMap<String, Candidate>()
        fun add(t: List<String>, exact: Boolean) {
            if (t.size < 2) return
            val q = t.joinToString(" ")
            if (q !in out) out[q] = Candidate(q, exact)
        }
        while (toks.size >= 2) {
            val exact = LOT.matches(toks.last())
            add(toks, exact)
            add(legacy(toks), exact)
            toks = toks.dropLast(1)
        }
        return out.values.toList()
    }

    /** "화성특례시 만세구" → "화성시": 2026 신설 이름을 지도 DB가 아는 옛 이름으로. */
    private fun legacy(t: List<String>): List<String> {
        val out = ArrayList<String>()
        for ((i, s) in t.withIndex()) {
            if (s.endsWith("구") && i > 0 && t[i - 1].endsWith("특례시")) continue
            out.add(if (s.endsWith("특례시")) s.removeSuffix("특례시") + "시" else s)
        }
        return out
    }

    /** 지도 앱(geo: 인텐트)에 넘길 주소: 괄호·"일원" 등만 걷어 내고 지번은 남긴다. 정리 결과가 비면 원문. */
    fun forMapApp(address: String): String =
        tokens(address).joinToString(" ").ifBlank { address.trim() }
}
