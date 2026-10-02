package com.chungyak.advisor.data

import android.content.Context
import androidx.room.withTransaction
import com.chungyak.advisor.BuildConfig
import org.json.JSONArray
import org.json.JSONObject

/**
 * 파일 백업/복원. 앱을 지웠다 다시 깔아야 하는 경우(서명 키 변경 등)에 설정·서비스키·수집 공고·
 * 분양가/경쟁률 캐시를 그대로 옮기기 위한 것. 형식은 사람이 읽을 수 있는 JSON.
 * 형식을 바꾸면 [FORMAT_VERSION]을 올리고 [import]에서 이전 형식도 계속 읽는다.
 */
object Backup {

    const val FORMAT = "chungyak-radar-backup"
    const val FORMAT_VERSION = 1

    data class Summary(val notices: Int, val models: Int, val competitions: Int)

    suspend fun export(context: Context): String {
        val s = Settings(context)
        val db = AppDatabase.get(context)
        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("appVersion", BuildConfig.VERSION_NAME)
            .put("exportedAt", System.currentTimeMillis())
            .put(
                "settings", JSONObject()
                    .put("serviceKey", s.serviceKey)
                    .put("lookbackDays", s.lookbackDays)
                    .put("regions", JSONArray(s.regions.sorted()))
                    .put("sortOrder", s.sortOrder)
            )
            .put("notices", JSONArray(db.noticeDao().all().map(::noticeJson)))
            .put("houseModels", JSONArray(db.houseModelDao().all().map(::modelJson)))
            .put("competitions", JSONArray(db.competitionDao().all().map(::cmpetJson)))
            .toString(1)
    }

    /** 백업을 현재 앱에 합친다(같은 공고는 백업 값으로 교체). 형식이 다르면 IllegalArgumentException. */
    suspend fun import(context: Context, text: String): Summary {
        val j = JSONObject(text)
        require(j.optString("format") == FORMAT) { "청약 레이더 백업 파일이 아닙니다." }
        require(j.optInt("formatVersion") in 1..FORMAT_VERSION) { "더 새 버전 앱에서 만든 백업입니다. 앱을 업데이트하세요." }

        val notices = j.optJSONArray("notices").objects().map(::notice)
        val models = j.optJSONArray("houseModels").objects().map(::model)
        val cmpets = j.optJSONArray("competitions").objects().map(::cmpet)
        val db = AppDatabase.get(context)
        db.withTransaction {
            db.noticeDao().upsertAll(notices)
            db.houseModelDao().insertAll(models)
            db.competitionDao().insertAll(cmpets)
        }

        j.optJSONObject("settings")?.let { st ->
            val s = Settings(context)
            st.optString("serviceKey").takeIf { it.isNotBlank() }?.let { s.serviceKey = it }
            st.optInt("lookbackDays", 0).takeIf { it > 0 }?.let { s.lookbackDays = it }
            st.optJSONArray("regions")?.let { a ->
                s.regions = (0 until a.length()).map { a.getString(it) }.toSet()
            }
            st.optString("sortOrder").takeIf { it.isNotBlank() }?.let { s.sortOrder = it }
            // 공고를 옮겨왔으니 다음 수집에서 이미 본 공고를 다시 알리지 않는다.
            if (notices.isNotEmpty()) s.baselineDone = true
        }
        return Summary(notices.size, models.size, cmpets.size)
    }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun noticeJson(n: Notice) = JSONObject()
        .put("id", n.id).put("houseManageNo", n.houseManageNo).put("pblancNo", n.pblancNo)
        .put("name", n.name).put("areaName", n.areaName).put("address", n.address)
        .put("totalUnits", n.totalUnits).put("noticeDate", n.noticeDate)
        .put("rank1Start", n.rank1Start).put("rank1End", n.rank1End).put("resultDate", n.resultDate)
        .put("houseKind", n.houseKind).put("speculationArea", n.speculationArea)
        .put("adjustmentArea", n.adjustmentArea).put("url", n.url).put("homepage", n.homepage)
        .put("priceMinManwon", n.priceMinManwon).put("priceMaxManwon", n.priceMaxManwon)
        .put("modelsFetchedAt", n.modelsFetchedAt).put("cmpetMaxRate", n.cmpetMaxRate)
        .put("cmpetAvgRate", n.cmpetAvgRate).put("cmpetFetchedAt", n.cmpetFetchedAt)
        .put("cmpetFinal", n.cmpetFinal).put("firstSeen", n.firstSeen).put("notified", n.notified)

    private fun notice(o: JSONObject) = Notice(
        id = o.getString("id"), houseManageNo = o.optString("houseManageNo"),
        pblancNo = o.optString("pblancNo"), name = o.optString("name"),
        areaName = o.optString("areaName"), address = o.optString("address"),
        totalUnits = o.optInt("totalUnits"), noticeDate = o.optString("noticeDate"),
        rank1Start = o.optString("rank1Start"), rank1End = o.optString("rank1End"),
        resultDate = o.optString("resultDate"), houseKind = o.optString("houseKind"),
        speculationArea = o.optBoolean("speculationArea"), adjustmentArea = o.optBoolean("adjustmentArea"),
        url = o.optString("url"), homepage = o.optString("homepage"),
        priceMinManwon = o.optInt("priceMinManwon"), priceMaxManwon = o.optInt("priceMaxManwon"),
        modelsFetchedAt = o.optLong("modelsFetchedAt"), cmpetMaxRate = o.optDouble("cmpetMaxRate", 0.0),
        cmpetAvgRate = o.optDouble("cmpetAvgRate", 0.0), cmpetFetchedAt = o.optLong("cmpetFetchedAt"),
        cmpetFinal = o.optBoolean("cmpetFinal"), firstSeen = o.optLong("firstSeen"),
        notified = o.optBoolean("notified", true),
    )

    private fun modelJson(m: HouseModel) = JSONObject()
        .put("noticeId", m.noticeId).put("modelNo", m.modelNo).put("houseType", m.houseType)
        .put("supplyArea", m.supplyArea).put("units", m.units).put("priceManwon", m.priceManwon)

    private fun model(o: JSONObject) = HouseModel(
        noticeId = o.getString("noticeId"), modelNo = o.getString("modelNo"),
        houseType = o.optString("houseType"), supplyArea = o.optDouble("supplyArea", 0.0),
        units = o.optInt("units"), priceManwon = o.optInt("priceManwon"),
    )

    private fun cmpetJson(c: Competition) = JSONObject()
        .put("noticeId", c.noticeId).put("modelNo", c.modelNo).put("houseType", c.houseType)
        .put("rank", c.rank).put("resideCode", c.resideCode).put("resideName", c.resideName)
        .put("units", c.units).put("requests", c.requests).put("rate", c.rate)
        .put("rateText", c.rateText).put("shortfall", c.shortfall)

    private fun cmpet(o: JSONObject) = Competition(
        noticeId = o.getString("noticeId"), modelNo = o.getString("modelNo"),
        houseType = o.optString("houseType"), rank = o.optInt("rank"),
        resideCode = o.optString("resideCode"), resideName = o.optString("resideName"),
        units = o.optInt("units"), requests = o.optInt("requests"), rate = o.optDouble("rate", 0.0),
        rateText = o.optString("rateText"), shortfall = o.optBoolean("shortfall"),
    )
}
