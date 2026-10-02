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

    @Test fun nonNumericRate_fallsBackToComputed() {
        assertEquals(2.5 to false, CompetitionParser.rate("-", 4, 10))
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
}
