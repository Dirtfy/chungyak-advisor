package com.chungyak.advisor.ui

import com.chungyak.advisor.notice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 공고 목록 검색(v0.10.0~): 단지명·주소·지역·사업주체·시공사 부분 일치. */
class NoticeSearchTest {

    private val raemian = notice("A").copy(
        name = "래미안 원베일리", areaName = "서울", address = "서울특별시 서초구 반포동 1-1",
        builder = "신반포3차경남아파트재건축정비사업조합", contractor = "삼성물산(주)",
    )
    private val suwon = notice("B").copy(
        name = "힐스테이트 광교", areaName = "경기", address = "경기도 수원시 영통구 이의동",
        builder = "경기주택도시공사", contractor = "현대건설",
    )
    private val all = listOf(raemian, suwon)

    private fun ids(q: String) = NoticeSearch.filter(all, q).map { it.id }

    @Test fun blank_showsAll() {
        assertEquals(listOf("A", "B"), ids(""))
        assertEquals(listOf("A", "B"), ids("   "))
    }

    @Test fun partialMatch_eachField() {
        assertEquals(listOf("A"), ids("원베일"))        // 단지명 일부
        assertEquals(listOf("B"), ids("수원"))          // 주소(시·군·구)
        assertEquals(listOf("B"), ids("영통구"))
        assertEquals(listOf("A"), ids("서울"))          // 지역
        assertEquals(listOf("B"), ids("주택도시공사"))  // 사업주체
        assertEquals(listOf("A"), ids("삼성물산"))      // 시공사
        assertEquals(listOf<String>(), ids("자이"))
    }

    @Test fun spacesIgnored_andAllWordsRequired() {
        assertEquals(listOf("A"), ids("래미안원베일리"))  // 띄어쓰기 없이 입력
        assertEquals(listOf("A"), ids(" 래미안  서초 ")) // 낱말 모두 포함(이름 + 주소)
        assertEquals(listOf<String>(), ids("래미안 수원"))
        assertEquals(listOf("B"), ids("힐스테이트 현대"))
    }

    @Test fun caseInsensitive_andOrderKept() {
        val sk = notice("C").copy(name = "SK VIEW 동탄", address = "경기도 화성시")
        assertTrue(NoticeSearch.matches(sk, "sk view"))
        assertTrue(NoticeSearch.matches(sk, "skview"))
        // 필드 경계를 넘어 이어 붙은 글자로는 맞지 않는다(이름 끝 + 주소 앞).
        assertFalse(NoticeSearch.matches(sk, "동탄경기"))
        // 결과는 입력 순서(=정렬 순서) 그대로
        assertEquals(listOf("B", "A"), NoticeSearch.filter(listOf(suwon, raemian), "동").map { it.id })
    }
}
