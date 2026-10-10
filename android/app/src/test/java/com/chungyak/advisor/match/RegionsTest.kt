package com.chungyak.advisor.match

import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 관심 지역 시·군·구 판별·선택·매칭(v0.13.0). 주소 예는 실제 청약홈 공급위치(HSSPLY_ADRES) 형식. */
class RegionsTest {

    private fun at(area: String, addr: String) = Regions.locate(area, addr)

    @Test fun tables_haveExpectedCounts() {
        assertEquals(25, Regions.SIGUNGU.getValue("서울").size)
        assertEquals(31, Regions.SIGUNGU.getValue("경기").size)
        assertEquals(11, Regions.SIGUNGU.getValue("인천").size) // 2026-07-01 개편: 2군 9구
        Regions.SIGUNGU.values.forEach { assertEquals(it.size, it.toSet().size) }
    }

    @Test fun gyeonggi_cityFromAddress() {
        assertEquals(listOf("수원시"), at("경기", "경기도 수원시 권선구 서둔동 212-1번지 일원").sigungu)
        assertEquals(listOf("용인시"), at("경기", "경기도 용인시 처인구 양지읍 양지리 산105-8번지 일원").sigungu)
        assertEquals(listOf("평택시"), at("경기", "경기도 평택시 고덕동 고덕면 일원(평택고덕국제화계획지구 A65BL)").sigungu)
        assertEquals(listOf("광명시"), at("경기", "경기도 광명시 소하동 광명 구름산지구 도시개발사업지구 A6BL").sigungu)
        assertEquals(listOf("양평군"), at("경기", "경기도 양평군 양평읍 양근리 일원").sigungu)
        assertEquals(listOf("광주시"), at("경기", "경기도 광주시 오포읍 신현리 일원").sigungu)
    }

    @Test fun specialCityName_mapsToCity() {
        assertEquals(listOf("화성시"), at("경기", "경기도 화성특례시 만세구 향남읍 하길리 404-2번지 일원").sigungu)
        assertEquals(listOf("수원시"), at("경기", "경기도 수원특례시 영통구 이의동").sigungu)
    }

    @Test fun noSidoPrefix_andMissingAreaName() {
        assertEquals(listOf("하남시"), at("경기", "하남시 교산동 일원").sigungu)
        val p = at("", "경기도 김포시 고촌읍")
        assertEquals("경기", p.sido)
        assertEquals(listOf("김포시"), p.sigungu)
    }

    @Test fun parenthesisContent_ignored() {
        // 괄호 안 사업지구명에 다른 시 이름이 있어도 시·군으로 읽지 않는다.
        assertEquals(listOf("하남시"), at("경기", "경기도 하남시 천현동 일원(구리시 경계 A1BL)").sigungu)
    }

    @Test fun seoulAndIncheon_gu() {
        assertEquals(listOf("강남구"), at("서울", "서울특별시 강남구 개포동 189").sigungu)
        assertEquals(listOf("중구"), at("서울", "서울특별시 중구 신당동").sigungu)
        assertEquals(listOf("검단구"), at("인천", "인천광역시 검단구 마전동 일원").sigungu)
    }

    @Test fun multiSidoAddress_onlyNoticeSido() {
        val p = at("인천", "인천광역시 계양구 귤현동, 동양동, 박촌동 일원 서울특별시 강서구 오곡동 일원 인천계양 테크노밸리")
        assertEquals(listOf("계양구"), p.sigungu)
    }

    @Test fun incheonLegacyNames_mapToNewGu() {
        at("인천", "인천광역시 중구 중산동 1885-1 일원").let { assertEquals(listOf("영종구"), it.sigungu); assertNotNull(it.note) }
        assertEquals(listOf("제물포구"), at("인천", "인천광역시 중구 신흥동3가 일원").sigungu)
        assertEquals(listOf("제물포구"), at("인천", "인천광역시 동구 송림동 일원").sigungu)
        assertEquals(listOf("검단구"), at("인천", "인천광역시 서구 당하동 일원").sigungu)
        assertEquals(listOf("서해구"), at("인천", "인천광역시 서구 청라동 일원").sigungu)
    }

    @Test fun unparseable_cityEmpty() {
        val p = at("경기", "경기도 일원 (택지개발지구)")
        assertEquals("경기", p.sido)
        assertTrue(p.sigungu.isEmpty())
    }

    // ── 선택 ──

    @Test fun toggleAll_andIndividual() {
        var sel = Regions.toggleAll(emptySet(), "경기")
        assertEquals(setOf("경기"), sel)
        assertEquals(31, Regions.picked(sel, "경기").size)
        sel = Regions.toggle(sel, "경기", "수원시") // 전체에서 하나 끄기 → 30개 개별 선택
        assertFalse(Regions.isAll(sel, "경기"))
        assertEquals(30, Regions.picked(sel, "경기").size)
        assertFalse("경기 수원시" in sel)
        sel = Regions.toggle(sel, "경기", "수원시") // 다시 켜면 전체로 합쳐짐
        assertEquals(setOf("경기"), sel)
        sel = Regions.toggleAll(sel, "경기")
        assertEquals(emptySet<String>(), sel)
    }

    @Test fun toggleAll_fromPartial_selectsAll() {
        val sel = Regions.toggleAll(setOf("경기 수원시", "서울"), "경기")
        assertEquals(setOf("경기", "서울"), sel)
    }

    @Test fun normalize_andSummary() {
        val every = Regions.SIGUNGU.getValue("인천").map { Regions.key("인천", it) }.toSet()
        assertEquals(setOf("인천"), Regions.normalize(every))
        assertEquals(setOf("경기", "기타"), Regions.normalize(setOf("경기", "경기 수원시", "기타")))
        assertEquals("서울 전체, 경기 수원시·화성시", Regions.summary(setOf("경기 화성시", "경기 수원시", "서울")))
        assertEquals("전체", Regions.summary(emptySet()))
        assertEquals(setOf("경기", "서울"), Regions.parents(setOf("경기 수원시", "서울")))
    }

    // ── 매칭 ──

    private val suwon = notice("A", areaName = "경기", address = "경기도 수원시 권선구 서둔동 일원")
    private val hwaseong = notice("B", areaName = "경기", address = "경기도 화성특례시 만세구 향남읍")
    private val unknownGg = notice("C", areaName = "경기", address = "경기도 일원 (공공주택지구)")
    private val seoul = notice("D", areaName = "서울", address = "서울특별시 강남구 개포동")

    @Test fun match_city() {
        val sel = setOf("경기 수원시")
        assertTrue(Regions.match(sel, suwon).ok)
        assertFalse(Regions.match(sel, hwaseong).ok)
        assertFalse(Regions.match(sel, seoul).ok)
        assertNull(Regions.match(sel, suwon).note)
    }

    @Test fun match_unparseableCity_keptUnderParentAndMarked() {
        val m = Regions.match(setOf("경기 수원시"), unknownGg)
        assertTrue(m.ok)
        assertNotNull(m.note)
        assertFalse(Regions.match(setOf("서울 강남구"), unknownGg).ok) // 경기를 하나도 안 골랐으면 제외
    }

    @Test fun match_emptySelection_isAll() {
        listOf(suwon, hwaseong, unknownGg, seoul).forEach { assertTrue(Regions.match(emptySet(), it).ok) }
    }

    /** 마이그레이션: v0.12.0 이하에 저장된 "경기"는 경기 31개 시·군 공고와 시·군 미확인 공고를 모두 받는다. */
    @Test fun legacyProvince_matchesEveryCity() {
        val old = setOf("경기")
        Regions.SIGUNGU.getValue("경기").forEach { city ->
            val n = notice(city, areaName = "경기", address = "경기도 $city 어느동 일원")
            assertEquals(listOf(city), Regions.locate(n).sigungu)
            assertTrue(city, Regions.match(old, n).ok)
        }
        assertTrue(Regions.match(old, unknownGg).ok)
        assertFalse(Regions.match(old, seoul).ok)
    }

    @Test fun label_forBadge() {
        assertEquals("경기 수원시", Regions.label(suwon))
        assertEquals("경기", Regions.label(unknownGg))
    }
}
