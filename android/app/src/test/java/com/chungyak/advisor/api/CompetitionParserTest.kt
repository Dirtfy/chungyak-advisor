package com.chungyak.advisor.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompetitionParserTest {

    private fun row(vararg kv: Pair<String, String>) =
        CompetitionParser.row("N1") { k -> kv.toMap()[k].orEmpty() }

    @Test fun parsesOfficialFields() {
        val c = row(
            "MODEL_NO" to "01", "HOUSE_TY" to "084.9818 ", "SUPLY_HSHLDCO" to "10",
            "SUBSCRPT_RANK_CODE" to "1", "RESIDE_SECD" to "01", "RESIDE_SENM" to "해당지역",
            "REQ_CNT" to "1,234", "CMPET_RATE" to "123.4",
        )
        assertEquals("01", c.modelNo)
        assertEquals("084.9818", c.houseType)
        assertEquals(1, c.rank)
        assertEquals("01", c.resideCode)
        assertEquals(10, c.units)
        assertEquals(1234, c.requests)
        assertEquals(123.4, c.rate, 1e-9)
        assertFalse(c.shortfall)
    }

    @Test fun shortfallTriangle_usesRequestsOverUnits() {
        val c = row("SUPLY_HSHLDCO" to "10", "REQ_CNT" to "7", "CMPET_RATE" to "(△3)")
        assertTrue(c.shortfall)
        assertEquals(0.7, c.rate, 1e-9)
        assertEquals("(△3)", c.rateText)
    }

    @Test fun dashRate_isNotCounted() {
        assertEquals(0.0 to false, CompetitionParser.rate("-", 28, 169))
        assertEquals(0.0 to false, CompetitionParser.rate("-", 28, 0))
        assertEquals(0.0 to false, CompetitionParser.rate("", 0, 0))
        assertEquals(12.0 to false, CompetitionParser.rate("12:1", 1, 12))
    }

    @Test fun summary_prefersRank1Local() {
        val local = row("SUPLY_HSHLDCO" to "10", "REQ_CNT" to "100", "CMPET_RATE" to "10",
            "SUBSCRPT_RANK_CODE" to "1", "RESIDE_SECD" to "01", "MODEL_NO" to "01")
        val local2 = row("SUPLY_HSHLDCO" to "30", "REQ_CNT" to "60", "CMPET_RATE" to "2",
            "SUBSCRPT_RANK_CODE" to "1", "RESIDE_SECD" to "01", "MODEL_NO" to "02")
        val other = row("SUPLY_HSHLDCO" to "10", "REQ_CNT" to "900", "CMPET_RATE" to "90",
            "SUBSCRPT_RANK_CODE" to "1", "RESIDE_SECD" to "02", "MODEL_NO" to "01")
        val (max, avg) = CompetitionParser.summary(listOf(local, local2, other))
        assertEquals(10.0, max, 1e-9)
        assertEquals(4.0, avg, 1e-9) // (100+60)/(10+30)
        assertEquals(90.0, CompetitionParser.summary(listOf(other)).first, 1e-9)
        assertEquals(0.0 to 0.0, CompetitionParser.summary(emptyList()))
    }

    /** 실응답(2026-10-03, 숭의역 노르웨이숲 더 스카이 2026000448) 그대로. */
    @Test fun realResponse_2026000448() {
        val raw = listOf(
            listOf("01", "069.7032 ", "1", "01", "해당지역", "7", "9", "1.29"),
            listOf("01", "069.7032 ", "1", "02", "기타지역", "7", "2", "-"),
            listOf("01", "069.7032 ", "2", "01", "해당지역", "7", "5", "-"),
            listOf("01", "069.7032 ", "2", "02", "기타지역", "7", "10", "-"),
            listOf("02", "084.9818 ", "1", "01", "해당지역", "22", "5", "(△17)"),
            listOf("02", "084.9818 ", "1", "02", "기타지역", "22", "2", "(△15)"),
            listOf("02", "084.9818 ", "2", "01", "해당지역", "22", "2", "(△13)"),
            listOf("02", "084.9818 ", "2", "02", "기타지역", "22", "4", "(△9)"),
        )
        val keys = listOf("MODEL_NO", "HOUSE_TY", "SUBSCRPT_RANK_CODE", "RESIDE_SECD",
            "RESIDE_SENM", "SUPLY_HSHLDCO", "REQ_CNT", "CMPET_RATE")
        val rows = raw.map { v -> CompetitionParser.row("N1") { k -> v[keys.indexOf(k)] } }
        assertEquals(1.29, rows[0].rate, 1e-9)
        assertEquals(0.0, rows[3].rate, 1e-9) // "-" → 미산정 (10/7 아님)
        assertFalse(rows[3].shortfall)
        assertTrue(rows[4].shortfall)
        assertEquals(5.0 / 22, rows[4].rate, 1e-9)
        val (max, avg) = CompetitionParser.summary(rows)
        assertEquals(1.29, max, 1e-9)
        assertEquals(14.0 / 29, avg, 1e-9) // 1순위 해당지역: (9+5)/(7+22) → 미달 표시
    }
}
