package com.chungyak.advisor.map

import android.content.Context
import android.location.Geocoder
import com.chungyak.advisor.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** 좌표. [exact] = 지번까지 찾음, false = [query](동·읍·면 등) 중심의 대략 위치. */
data class GeoPoint(val lat: Double, val lon: Double, val exact: Boolean, val query: String)

/** 지오코딩 결과. [Offline] = 네트워크 문제로 아직 모름(캐시하지 않음 → 다음에 재시도). */
sealed class GeoResult {
    data class Found(val point: GeoPoint) : GeoResult()
    data object NotFound : GeoResult()
    data object Offline : GeoResult()
}

/**
 * 공급위치 주소 → 좌표. API 키가 필요 없는 것만 쓴다(오너 지시: 키를 앱·저장소에 넣지 않음).
 *  1) Android [Geocoder] — 기기 내장(대개 Google), 키 없음. 지번 단위로 잘 찾는다.
 *  2) OpenStreetMap Nominatim — 기기에 Geocoder가 없거나 못 찾을 때. 이용 정책 준수:
 *     초당 1건 이하, 앱 식별 User-Agent, 결과 캐시, 사용자가 상세 화면을 열 때만(일괄 조회 없음).
 * 결과(찾음/못 찾음)는 기기 안 prefs([FILE])에 캐시한다. 주소는 공고의 공개 정보라 개인정보가 아니다.
 */
class GeocodeRepository(context: Context) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun cached(address: String): GeoResult? = decode(prefs.getString(address, null), System.currentTimeMillis())

    suspend fun resolve(address: String): GeoResult = withContext(Dispatchers.IO) {
        if (address.isBlank()) return@withContext GeoResult.NotFound
        cached(address)?.let { return@withContext it }
        val r = lookup(AddressQuery.candidates(address))
        if (r !is GeoResult.Offline) prefs.edit().putString(address, encode(r, System.currentTimeMillis())).apply()
        r
    }

    private suspend fun lookup(cands: List<AddressQuery.Candidate>): GeoResult {
        for (c in cands) {
            val hit = runCatching { platform(c.query) }.getOrNull()?.takeIf { inKorea(it.first, it.second) }
                // 네트워크 실패면 남은 검색어도 다 실패하므로 바로 중단(오프라인에서 오래 붙잡지 않음).
                ?: (nominatim(c.query) ?: return GeoResult.Offline).getOrNull()
            if (hit != null && inKorea(hit.first, hit.second)) {
                return GeoResult.Found(GeoPoint(hit.first, hit.second, c.exact, c.query))
            }
        }
        return GeoResult.NotFound
    }

    @Suppress("DEPRECATION") // 동기 API: IO 스레드에서만 호출. 콜백 API는 33+ 전용.
    private fun platform(q: String): Pair<Double, Double>? {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(app, Locale.KOREA).getFromLocationName(q, 1)?.firstOrNull() ?: return null
        return a.latitude to a.longitude
    }

    /** null = 네트워크 실패, Result(null) = 응답은 왔는데 결과 없음. */
    private suspend fun nominatim(q: String): Result<Pair<Double, Double>?>? = nominatimLock.withLock {
        val wait = lastNominatimAt + NOMINATIM_INTERVAL_MS - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        lastNominatimAt = System.currentTimeMillis()
        try {
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=kr&accept-language=ko&q=" +
                    URLEncoder.encode(q, "UTF-8")
            )
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            try {
                if (conn.responseCode != 200) return@withLock Result.success(null)
                Result.success(parseNominatim(conn.inputStream.bufferedReader().use { it.readText() }))
            } finally {
                conn.disconnect()
            }
        } catch (e: java.io.IOException) {
            null
        }
    }

    companion object {
        const val FILE = "geocode_cache"
        /** 못 찾은 주소는 하루 뒤 다시 시도(지도 DB 갱신·신설 지명 반영). */
        const val NOT_FOUND_TTL_MS = 24L * 60 * 60 * 1000
        private const val NOMINATIM_INTERVAL_MS = 1100L
        private val USER_AGENT = "ChungyakRadar/${BuildConfig.VERSION_NAME} (Android; github.com/Dirtfy/chungyak-advisor)"
        private val nominatimLock = Mutex()
        @Volatile private var lastNominatimAt = 0L

        /** 대한민국 대략 범위. 엉뚱한 나라·0,0 결과를 걸러 낸다. */
        fun inKorea(lat: Double, lon: Double) = lat in 33.0..38.7 && lon in 124.5..131.9

        fun parseNominatim(body: String): Pair<Double, Double>? {
            val arr = JSONArray(body)
            if (arr.length() == 0) return null
            val o = arr.getJSONObject(0)
            val lat = o.optString("lat").toDoubleOrNull() ?: return null
            val lon = o.optString("lon").toDoubleOrNull() ?: return null
            return lat to lon
        }

        /** 캐시 값: "ok|lat|lon|exact|query" 또는 "none|저장시각". */
        fun encode(r: GeoResult, now: Long): String = when (r) {
            is GeoResult.Found -> with(r.point) { "ok|$lat|$lon|$exact|$query" }
            else -> "none|$now"
        }

        fun decode(v: String?, now: Long): GeoResult? {
            val p = v?.split('|', limit = 5) ?: return null
            return when (p.firstOrNull()) {
                "ok" -> runCatching {
                    GeoResult.Found(GeoPoint(p[1].toDouble(), p[2].toDouble(), p[3].toBoolean(), p[4]))
                }.getOrNull()
                "none" -> if (now - (p.getOrNull(1)?.toLongOrNull() ?: 0) < NOT_FOUND_TTL_MS) GeoResult.NotFound else null
                else -> null
            }
        }
    }
}
