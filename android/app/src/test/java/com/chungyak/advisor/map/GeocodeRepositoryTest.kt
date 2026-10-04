package com.chungyak.advisor.map

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class GeocodeRepositoryTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()

    @Test fun cache_roundTrip() {
        val p = GeoPoint(37.27228, 126.98695, false, "경기도 수원시 권선구 서둔동")
        assertEquals(GeoResult.Found(p), GeocodeRepository.decode(GeocodeRepository.encode(GeoResult.Found(p), 0), 0))
        val none = GeocodeRepository.encode(GeoResult.NotFound, 1000)
        assertEquals(GeoResult.NotFound, GeocodeRepository.decode(none, 2000))
        // 못 찾음은 하루 뒤 만료 → 다시 조회.
        assertNull(GeocodeRepository.decode(none, 1000 + GeocodeRepository.NOT_FOUND_TTL_MS))
        assertNull(GeocodeRepository.decode("garbage", 0))
        assertNull(GeocodeRepository.decode("ok|x|y|true|q", 0))
    }

    @Test fun nominatimParse() {
        assertEquals(37.6 to 127.0, GeocodeRepository.parseNominatim("""[{"lat":"37.6","lon":"127.0","display_name":"x"}]"""))
        assertNull(GeocodeRepository.parseNominatim("[]"))
    }

    @Test fun koreaBounds() {
        assertTrue(GeocodeRepository.inKorea(37.5665, 126.978))
        assertFalse(GeocodeRepository.inKorea(0.0, 0.0))
        assertFalse(GeocodeRepository.inKorea(35.68, 139.69)) // 도쿄
    }

    @Test fun blankAddress_notFound_noCrash() = runBlocking {
        assertEquals(GeoResult.NotFound, GeocodeRepository(ctx).resolve("  "))
    }

    @Test fun geoIntent_addressQuery() {
        val i = mapAppIntent("경기도 여주시 홍문동 332-2번지 일원")
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("geo", i.data!!.scheme)
        assertEquals("q=경기도 여주시 홍문동 332-2", i.data!!.query)
    }

    /**
     * 실제 Nominatim으로 앱의 지오코딩 경로 전체(AddressQuery → GeocodeRepository)를 실행.
     * 네트워크·외부 서비스라 LIVE_GEOCODE=1 일 때만(Robolectric Geocoder는 결과 없음 → Nominatim 경로).
     * 기대 좌표: 각 주소의 동·읍·면 중심(OSM). 마커가 그 동 안(±3km)에 찍히는지 본다.
     */
    @Test fun live_realAddresses() = runBlocking {
        assumeTrue(System.getenv("LIVE_GEOCODE") == "1")
        ctx.getSharedPreferences(GeocodeRepository.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        val repo = GeocodeRepository(ctx)
        val cases = listOf(
            Triple("경기도 수원시 권선구 서둔동 212-1번지 일원", 37.272, 126.987),
            Triple("인천광역시 미추홀구 숭의동 350-1번지 일원", 37.463, 126.650),
            Triple("경기도 화성특례시 만세구 향남읍 하길리 404-2번지 일원", 37.129, 126.933),
            Triple("경기도 광명시 소하동 광명 구름산지구 도시개발사업지구 A6BL", 37.443, 126.882),
            Triple("인천광역시 계양구 귤현동, 동양동, 박촌동, 병방동, 상야동, 경기도 부천시 대장동, 서울특별시 강서구 오곡동 일원 인천계양 테크노밸리 공공주택지구 내 A17블록", 37.567, 126.744),
            Triple("경기도 성남시 분당구 정자동 90번지", 37.366, 127.119),
        )
        for ((addr, lat, lon) in cases) {
            val r = repo.resolve(addr)
            println("LIVE_GEO $addr -> $r")
            assertTrue("$addr: $r", r is GeoResult.Found)
            val p = (r as GeoResult.Found).point
            assertTrue("$addr: $p", kotlin.math.abs(p.lat - lat) < 0.03 && kotlin.math.abs(p.lon - lon) < 0.03)
            // 두 번째는 캐시(네트워크 없이 같은 값).
            assertEquals(r, repo.cached(addr))
        }
        assertEquals(GeoResult.NotFound, repo.resolve("미정"))
    }
}
