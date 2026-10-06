package com.chungyak.advisor.match

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 주택공급에 관한 규칙 별표 1 점수표 경계값 + 기산일 규칙. */
class GajeomTest {

    private val asOf = LocalDate.parse("2026-10-06")

    @Test fun homelessTable() {
        assertEquals(2, Gajeom.homelessScore(0))
        assertEquals(2, Gajeom.homelessScore(11))
        assertEquals(4, Gajeom.homelessScore(12))
        assertEquals(30, Gajeom.homelessScore(14 * 12 + 11))
        assertEquals(32, Gajeom.homelessScore(15 * 12))
        assertEquals(32, Gajeom.homelessScore(40 * 12))
    }

    @Test fun dependentsTable() {
        assertEquals(5, Gajeom.dependentsScore(0))
        assertEquals(10, Gajeom.dependentsScore(1))
        assertEquals(30, Gajeom.dependentsScore(5))
        assertEquals(35, Gajeom.dependentsScore(6))
        assertEquals(35, Gajeom.dependentsScore(9))
    }

    @Test fun accountTable() {
        assertEquals(1, Gajeom.accountScore(5))
        assertEquals(2, Gajeom.accountScore(6))
        assertEquals(2, Gajeom.accountScore(11))
        assertEquals(3, Gajeom.accountScore(12))
        assertEquals(4, Gajeom.accountScore(24))
        assertEquals(16, Gajeom.accountScore(14 * 12 + 11))
        assertEquals(17, Gajeom.accountScore(15 * 12))
    }

    @Test fun homeless_from30thBirthday() {
        // 1990-01-15생 → 만 30세 2020-01-15 → 2026-10-06까지 6년 8개월 → 14점
        val p = Profile(sido = "서울", birthDate = "1990-01-15")
        val part = Gajeom.homeless(p, asOf)
        assertEquals(14, part.score)
        assertTrue(part.basis, part.basis.contains("2020-01-15"))
    }

    @Test fun homeless_marriedBefore30_fromMarriage() {
        // 1995-05-01생(만 30세 2025-05-01), 2021-03 혼인 → 2021-03-01부터 5년 7개월 → 12점
        val p = Profile(sido = "서울", birthDate = "1995-05-01", married = true, marriageYm = "2021-03")
        assertEquals(12, Gajeom.homeless(p, asOf).score)
    }

    @Test fun homeless_under30Single_zero_owner_zero() {
        assertEquals(0, Gajeom.homeless(Profile(sido = "서울", birthDate = "2000-01-01"), asOf).score)
        assertEquals(0, Gajeom.homeless(Profile(sido = "서울", birthDate = "1980-01-01", homesOwned = 1), asOf).score)
    }

    @Test fun homeless_formerOwner_fromDisposal_orUnknown() {
        val p = Profile(sido = "서울", birthDate = "1980-01-01", everOwned = true)
        assertNull(Gajeom.homeless(p, asOf).score)
        // 2024-02-01 처분 → 2년 8개월 → 6점
        assertEquals(6, Gajeom.homeless(p.copy(homelessSince = "2024-02-01"), asOf).score)
        // 처분일이 만 30세 전이면 만 30세부터: 2010-01-01 → 16년 9개월 → 32점
        assertEquals(32, Gajeom.homeless(p.copy(homelessSince = "2005-06-01"), asOf).score)
    }

    @Test fun dependents_estimatedFromSpouseAndChildren() {
        val part = Gajeom.dependents(Profile(sido = "서울", married = true, children = 2))
        assertEquals(20, part.score)
        assertTrue(part.estimated)
        assertEquals(10, Gajeom.dependents(Profile(sido = "서울", married = true, children = 2, dependents = 1)).score)
    }

    @Test fun total_andMissing() {
        val full = Profile(
            sido = "서울", birthDate = "1985-01-01", married = true, children = 2, dependents = 3,
            account = AccountType.COMPREHENSIVE, accountOpened = "2010-01-01",
        )
        // 무주택: 만 30세 2015-01-01 → 11년 9개월 → 24점, 부양 3명 20점, 통장 16년 9개월 17점 = 61
        val g = Gajeom.calc(full, asOf)
        assertEquals(61, g.total)
        assertEquals("가점 61점/84", g.summary)

        val partial = Gajeom.calc(Profile(sido = "서울", account = AccountType.COMPREHENSIVE), asOf)
        assertNull(partial.total)
        assertEquals(listOf("무주택기간", "청약통장 가입기간"), partial.missing)
    }

    @Test fun noAccount_zero() {
        assertEquals(0, Gajeom.account(Profile(sido = "서울"), asOf).score)
    }
}
