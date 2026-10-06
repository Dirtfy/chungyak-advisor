package com.chungyak.advisor.ui

import com.chungyak.advisor.data.Notice

/**
 * 공고 목록 검색(v0.10.0~, docs/14). 기기에 수집된 공고 안에서만 찾는다(서버 호출 없음).
 * 대상 칸: 단지명 · 공급 위치(주소) · 지역(서울/경기/인천) · 사업주체(시행사) · 시공사.
 * 공백으로 나눈 낱말이 모두 들어 있으면 맞음(부분 일치, 대소문자·띄어쓰기 무시).
 * 예: "래미안 강남" → 이름에 '래미안', 주소에 '강남'이 있는 공고. "원베일리" → "래미안 원베일리".
 */
object NoticeSearch {

    const val FIELDS_LABEL = "단지명·주소·지역·사업주체·시공사"

    fun matches(n: Notice, query: String): Boolean {
        val words = query.trim().split(Regex("\\s+")).map(::norm).filter { it.isNotEmpty() }
        if (words.isEmpty()) return true
        // 칸끼리 이어 붙지 않게 줄바꿈으로 구분(띄어쓰기는 칸 안에서만 무시).
        val hay = listOf(n.name, n.address, n.areaName, n.builder, n.contractor).joinToString("\n") { norm(it) }
        return words.all { hay.contains(it) }
    }

    fun filter(notices: List<Notice>, query: String): List<Notice> =
        if (query.isBlank()) notices else notices.filter { matches(it, query) }

    private fun norm(s: String) = s.filterNot { it.isWhitespace() }.lowercase()
}
