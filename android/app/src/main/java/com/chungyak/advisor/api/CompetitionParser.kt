package com.chungyak.advisor.api

import com.chungyak.advisor.data.Competition

/**
 * Parses getAPTLttotPblancCmpet rows (fields per the official OAS
 * `ApplyhomeInfoCmpetRtSvc/v1`): MODEL_NO, HOUSE_TY, SUPLY_HSHLDCO,
 * SUBSCRPT_RANK_CODE, RESIDE_SECD, RESIDE_SENM, REQ_CNT, CMPET_RATE.
 * Pure Kotlin (row access via [get]) so it is unit-testable without org.json.
 */
object CompetitionParser {

    /** 1순위 해당지역 code. */
    const val RESIDE_LOCAL = "01"

    fun row(noticeId: String, get: (String) -> String): Competition {
        val units = int(get("SUPLY_HSHLDCO"))
        val requests = int(get("REQ_CNT"))
        val text = get("CMPET_RATE").trim()
        val (rate, shortfall) = rate(text, units, requests)
        return Competition(
            noticeId = noticeId,
            modelNo = get("MODEL_NO").trim(),
            houseType = get("HOUSE_TY").trim(),
            rank = int(get("SUBSCRPT_RANK_CODE")),
            resideCode = get("RESIDE_SECD").trim(),
            resideName = get("RESIDE_SENM").trim(),
            units = units,
            requests = requests,
            rate = rate,
            rateText = text,
            shortfall = shortfall,
        )
    }

    /**
     * CMPET_RATE → (n:1 값, 미달 여부). 청약홈은 미달을 "(△3)"처럼 부족 세대수로
     * 표기하므로 그때는 접수/공급으로 계산. 숫자가 아니면("-", "") 접수/공급으로
     * 계산할 수 있으면 계산, 아니면 0.
     */
    fun rate(text: String, units: Int, requests: Int): Pair<Double, Boolean> {
        val computed = if (units > 0) requests.toDouble() / units else 0.0
        if (text.contains('△')) return computed to true
        val v = text.removeSuffix(":1").replace(",", "").trim().toDoubleOrNull()
        val r = v ?: computed
        return r to (units > 0 && requests < units)
    }

    /**
     * 공고 대표값 (최고, 평균). 1순위 해당지역 행이 있으면 그것만, 없으면 전체로 계산.
     * 평균 = Σ접수 ÷ Σ공급. 데이터 없으면 (0, 0).
     */
    fun summary(rows: List<Competition>): Pair<Double, Double> {
        val base = rows.filter { it.rank == 1 && it.resideCode == RESIDE_LOCAL }.ifEmpty { rows }
        if (base.isEmpty()) return 0.0 to 0.0
        val max = base.maxOf { it.rate }
        val units = base.sumOf { it.units }
        val avg = if (units > 0) base.sumOf { it.requests }.toDouble() / units else 0.0
        return max to avg
    }

    private fun int(v: String): Int = v.filter { it.isDigit() }.toIntOrNull() ?: 0
}
