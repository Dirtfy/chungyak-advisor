package com.chungyak.advisor.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 실제 청약홈 공급위치 문자열(2026-10-03 수도권 공고)로 검색어 정리를 검증. */
class AddressQueryTest {

    private fun queries(a: String) = AddressQuery.candidates(a).map { it.query }

    @Test fun lotNumber_exactFirst_thenWider() {
        val c = AddressQuery.candidates("경기도 수원시 권선구 서둔동 212-1번지 일원")
        assertEquals("경기도 수원시 권선구 서둔동 212-1", c[0].query)
        assertTrue(c[0].exact)
        assertEquals("경기도 수원시 권선구 서둔동", c[1].query)
        assertFalse(c[1].exact)
        assertEquals(listOf("경기도 수원시 권선구 서둔동 212-1", "경기도 수원시 권선구 서둔동", "경기도 수원시 권선구", "경기도 수원시"), c.map { it.query })
    }

    @Test fun mountainLot_kept() {
        assertEquals(
            "경기도 용인시 처인구 양지읍 양지리 산105-8",
            queries("경기도 용인시 처인구 양지읍 양지리 산105-8번지 일원")[0],
        )
        assertEquals("경기도 성남시 분당구 정자동 90", queries("경기도 성남시 분당구 정자동 90번지")[0])
    }

    @Test fun parenthesesAndDistrictName_dropped() {
        assertEquals("경기도 평택시 고덕동 고덕면", queries("경기도 평택시 고덕동 고덕면 일원(평택고덕국제화계획지구 A65BL)")[0])
        val gm = AddressQuery.candidates("경기도 광명시 소하동 광명 구름산지구 도시개발사업지구 A6BL")
        assertEquals("경기도 광명시 소하동", gm[0].query)
        assertFalse(gm[0].exact)
    }

    @Test fun multiDongList_firstOnly() {
        val a = "인천광역시 계양구 귤현동, 동양동, 박촌동, 병방동, 상야동, 경기도 부천시 대장동, 서울특별시 강서구 오곡동 일원 인천계양 테크노밸리 공공주택지구 내 A17블록"
        assertEquals("인천광역시 계양구 귤현동", queries(a)[0])
    }

    @Test fun newSpecialCity_alsoTriesLegacyName() {
        val q = queries("경기도 화성특례시 만세구 향남읍 하길리 404-2번지 일원")
        assertEquals("경기도 화성특례시 만세구 향남읍 하길리 404-2", q[0])
        assertEquals("경기도 화성시 향남읍 하길리 404-2", q[1])
        assertTrue("경기도 화성시 향남읍" in q)
    }

    @Test fun neverProvinceOnly_orEmpty() {
        assertTrue(queries("").isEmpty())
        assertTrue(queries("서울특별시").isEmpty())
        assertTrue(queries("미정").isEmpty())
        assertTrue(queries("경기도 양평군 양서면 용담리 212-3번지 일원").none { !it.contains(' ') })
    }

    @Test fun mapAppQuery_keepsLotNumber() {
        assertEquals("경기도 시흥시 은행동 289-31", AddressQuery.forMapApp("경기도 시흥시 은행동 289-31번지 일원"))
        assertEquals("미정", AddressQuery.forMapApp(" 미정 "))
        assertEquals("서울특별시 강남구 테헤란로 123", AddressQuery.forMapApp("서울특별시 강남구 테헤란로 123"))
    }
}
