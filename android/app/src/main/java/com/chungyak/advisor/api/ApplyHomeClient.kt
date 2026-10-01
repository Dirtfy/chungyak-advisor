package com.chungyak.advisor.api

import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** Result of one poll of the 청약홈 분양정보 API. */
sealed class FetchResult {
    data class Ok(val notices: List<Notice>) : FetchResult()
    data class Error(val message: String) : FetchResult()
}

/**
 * Minimal client for 한국부동산원 청약홈 "분양정보 조회 서비스" on data.go.kr
 * (odcloud stage 37000). Only the APT general-supply endpoint is used for the
 * MVP; other house types (무순위/오피스텔/임의공급) are documented for later.
 *
 * Spec: https://infuser.odcloud.kr/api/stages/37000/api-docs
 * Endpoint: GET {BASE}/getAPTLttotPblancDetail
 *   ?page&perPage&returnType=JSON&cond[RCRIT_PBLANC_DE::GTE]=yyyy-MM-dd&serviceKey
 */
object ApplyHomeClient {

    private const val BASE = "https://api.odcloud.kr/api/ApplyhomeInfoDetailSvc/v1"
    private const val ENDPOINT = "getAPTLttotPblancDetail"
    // 주택형별 상세(면적·세대수·분양가). 분양가는 LTTOT_TOP_AMOUNT(만원)에 있고
    // 목록 엔드포인트(getAPTLttotPblancDetail)에는 없다 → 공고별 2차 호출로 결합.
    private const val MDL_ENDPOINT = "getAPTLttotPblancMdl"
    private const val PER_PAGE = 100
    private const val MAX_PAGES = 10

    /**
     * Fetch APT 일반공급 공고 whose 모집공고일 is within [lookbackDays], keeping
     * only those whose region name contains one of [regions]. Never throws;
     * failures come back as [FetchResult.Error] for the worker to record.
     */
    fun fetch(serviceKey: String, lookbackDays: Int, regions: Set<String>): FetchResult {
        if (serviceKey.isBlank()) return FetchResult.Error("서비스키가 설정되지 않았습니다.")
        val since = sinceDate(lookbackDays)
        val out = ArrayList<Notice>()
        try {
            var page = 1
            while (page <= MAX_PAGES) {
                val json = request(serviceKey, page, since)
                    ?: return FetchResult.Error("응답을 읽지 못했습니다 (page $page).")
                // API-level error envelope (e.g. bad/expired key) has no "data".
                if (!json.has("data")) {
                    val msg = json.optString("msg").ifBlank { json.optString("message") }
                    return FetchResult.Error(
                        if (msg.isNotBlank()) msg else "인증 오류 또는 알 수 없는 응답입니다. 서비스키를 확인하세요."
                    )
                }
                val data = json.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val row = data.getJSONObject(i)
                    if (matchesRegion(row, regions)) out.add(toNotice(row))
                }
                if (data.length() < PER_PAGE) break
                page++
            }
        } catch (e: Exception) {
            return FetchResult.Error(e.message ?: "네트워크 오류")
        }
        return FetchResult.Ok(out)
    }

    /**
     * Fetch every 주택형 of one 공고 via the 주택형별 상세 endpoint
     * (`getAPTLttotPblancMdl`): HOUSE_TY, SUPLY_AR(㎡), SUPLY_HSHLDCO,
     * LTTOT_TOP_AMOUNT(분양최고금액, 만원). Returns null on error so the caller
     * retries on a later poll; an empty list means the API had no rows.
     *
     * LTTOT_TOP_AMOUNT was checked equal to the 청약홈 공고 page's 공급금액(최고가).
     */
    fun fetchModels(serviceKey: String, noticeId: String, houseManageNo: String, pblancNo: String): List<HouseModel>? {
        if (serviceKey.isBlank() || houseManageNo.isBlank() || pblancNo.isBlank()) return null
        val h = URLEncoder.encode("cond[HOUSE_MANAGE_NO::EQ]", "UTF-8")
        val p = URLEncoder.encode("cond[PBLANC_NO::EQ]", "UTF-8")
        val out = ArrayList<HouseModel>()
        try {
            var page = 1
            while (page <= 3) {
                val url = "$BASE/$MDL_ENDPOINT?page=$page&perPage=$PER_PAGE&returnType=JSON" +
                    "&$h=${URLEncoder.encode(houseManageNo, "UTF-8")}" +
                    "&$p=${URLEncoder.encode(pblancNo, "UTF-8")}" +
                    "&serviceKey=${keyParam(serviceKey)}"
                val json = getJson(url) ?: return null
                if (!json.has("data")) return null
                val data = json.getJSONArray("data")
                for (i in 0 until data.length()) {
                    val r = data.getJSONObject(i)
                    out.add(
                        HouseModel(
                            noticeId = noticeId,
                            modelNo = r.optString("MODEL_NO").ifBlank { "${out.size + 1}" },
                            houseType = r.optString("HOUSE_TY").trim(),
                            supplyArea = r.optString("SUPLY_AR").trim().toDoubleOrNull() ?: 0.0,
                            units = manwon(r.optString("SUPLY_HSHLDCO")) ?: 0,
                            priceManwon = manwon(r.optString("LTTOT_TOP_AMOUNT")) ?: 0,
                        )
                    )
                }
                if (data.length() < PER_PAGE) break
                page++
            }
        } catch (e: Exception) {
            return null
        }
        return out
    }

    private fun request(serviceKey: String, page: Int, since: String): JSONObject? {
        val cond = URLEncoder.encode("cond[RCRIT_PBLANC_DE::GTE]", "UTF-8")
        val url = "$BASE/$ENDPOINT?page=$page&perPage=$PER_PAGE&returnType=JSON" +
            "&$cond=$since&serviceKey=${keyParam(serviceKey)}"
        return getJson(url)
    }

    /**
     * Portal shows both a Decoding (raw) and an Encoding (percent-encoded) key;
     * accept either by only encoding when it is not already encoded.
     */
    private fun keyParam(serviceKey: String): String =
        if (Regex("%[0-9A-Fa-f]{2}").containsMatchIn(serviceKey)) serviceKey
        else URLEncoder.encode(serviceKey, "UTF-8")

    private fun getJson(urlStr: String): JSONObject? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: return null
            val trimmed = body.trimStart()
            // Non-JSON (XML error page) when the key is unregistered/expired.
            if (!trimmed.startsWith("{")) {
                JSONObject().put("msg", "HTTP $code: 서비스키가 유효하지 않을 수 있습니다.")
            } else {
                JSONObject(trimmed)
            }
        } finally {
            conn.disconnect()
        }
    }

    /** "85,600" / "85600" → 85600 (만원). "공고문 참조" 등 숫자 없으면 null. */
    private fun manwon(v: String): Int? {
        val digits = v.filter { it.isDigit() }
        return if (digits.isEmpty()) null else digits.toIntOrNull()
    }

    private fun matchesRegion(row: JSONObject, regions: Set<String>): Boolean {
        val area = row.optString("SUBSCRPT_AREA_CODE_NM")
        val addr = row.optString("HSSPLY_ADRES")
        return regions.any { area.contains(it) || addr.contains(it) }
    }

    private fun toNotice(r: JSONObject): Notice {
        val house = r.optString("HOUSE_MANAGE_NO")
        val pblanc = r.optString("PBLANC_NO")
        return Notice(
            id = "$house:$pblanc",
            houseManageNo = house,
            pblancNo = pblanc,
            name = r.optString("HOUSE_NM"),
            areaName = r.optString("SUBSCRPT_AREA_CODE_NM"),
            address = r.optString("HSSPLY_ADRES"),
            totalUnits = r.optString("TOT_SUPLY_HSHLDCO").toIntOrNull() ?: 0,
            noticeDate = iso(r.optString("RCRIT_PBLANC_DE")),
            rank1Start = iso(r.optString("GNRL_RNK1_CRSPAREA_RCPTDE")),
            rank1End = iso(r.optString("GNRL_RNK1_CRSPAREA_ENDDE")),
            resultDate = iso(r.optString("PRZWNER_PRESNATN_DE")),
            houseKind = r.optString("HOUSE_SECD_NM").ifBlank { r.optString("HOUSE_DTL_SECD_NM") },
            speculationArea = r.optString("SPECLT_RDN_EARTH_AT") == "Y",
            adjustmentArea = r.optString("MDAT_TRGET_AREA_SECD").isNotBlank() &&
                r.optString("MDAT_TRGET_AREA_SECD") != "3",
            url = r.optString("PBLANC_URL"),
            homepage = r.optString("HMPG_ADRES"),
            firstSeen = System.currentTimeMillis(),
            notified = false,
        )
    }

    /** Normalise "2026-03-04" or "20260304" to ISO yyyy-MM-dd; else return as-is. */
    private fun iso(v: String): String {
        val t = v.trim()
        return when {
            Regex("^\\d{4}-\\d{2}-\\d{2}").containsMatchIn(t) -> t.substring(0, 10)
            Regex("^\\d{8}$").matches(t) -> "${t.substring(0, 4)}-${t.substring(4, 6)}-${t.substring(6, 8)}"
            else -> t
        }
    }

    private fun sinceDate(lookbackDays: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -lookbackDays)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }
}
