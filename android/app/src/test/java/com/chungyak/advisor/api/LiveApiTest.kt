package com.chungyak.advisor.api

import com.chungyak.advisor.data.Notice
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 실제 odcloud API로 앱의 수집·경쟁률 경로(ApplyHomeClient + 파서)를 끝까지 호출.
 * 키는 저장소에 두지 않으므로 환경변수 ODCLOUD_SERVICE_KEY 가 있을 때만 실행(없으면 skip).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class LiveApiTest {

    private val key: String? = System.getenv("ODCLOUD_SERVICE_KEY")?.takeIf { it.isNotBlank() }

    @Test fun noticesAndCompetition_realResponses() {
        assumeTrue(key != null)
        val notices = (ApplyHomeClient.fetch(key!!, 90, setOf("서울", "경기", "인천")) as FetchResult.Ok).notices
        assertTrue("수도권 공고 없음", notices.isNotEmpty())
        val started = notices.filter { it.rank1Start.isNotBlank() && it.rank1Start < "2026-10-01" }
        var withRows = 0
        for (n: Notice in started.take(6)) {
            val r = ApplyHomeClient.fetchCompetition(key, n.id, n.houseManageNo, n.pblancNo)
            assertTrue("${n.id}: $r", r is CompetitionFetch.Ok)
            val rows = (r as CompetitionFetch.Ok).rows
            if (rows.isNotEmpty()) withRows++
            assertTrue(rows.all { it.noticeId == n.id && it.modelNo.isNotBlank() && it.rank in 1..2 })
            val (max, avg) = CompetitionParser.summary(rows)
            println("LIVE ${n.id} ${n.name} rows=${rows.size} max=$max avg=$avg")
        }
        assertTrue("경쟁률 행이 있는 공고 없음", withRows > 0)
    }
}
