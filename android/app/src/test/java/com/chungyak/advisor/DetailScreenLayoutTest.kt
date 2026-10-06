package com.chungyak.advisor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.chungyak.advisor.data.Competition
import com.chungyak.advisor.data.HouseModel
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.map.FakeMap
import com.chungyak.advisor.map.GeoPoint
import com.chungyak.advisor.map.GeoResult
import com.chungyak.advisor.map.LocationCardContent
import com.chungyak.advisor.map.MapPreview
import com.chungyak.advisor.map.TAG_MAP
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.ui.theme.ChungyakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 상세 화면 전체(NoticeDetailBody)를 스크롤하면서 지도 그림이 지도 칸 밖으로 새지 않는지 검사한다(v0.6.2).
 * 오너 실기기 캡처(v0.6.0)의 두 공고 — 광명 시티프라디움 에듀하임 / 더샵 분당하이스트 — 를 그대로 재현한다.
 * 지도 칸에는 자기 영역 밖 300px까지 빨갛게 칠하는 가짜 지도를 넣는다(osmdroid 가장자리 타일 흉내).
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DetailScreenLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val gwangmyeong = notice("gm", noticeDate = "2026-09-18", rank1Start = "2026-09-30", rank1End = "2026-09-30", resultDate = "2026-10-12")
        .copy(name = "광명 시티프라디움 에듀하임", address = "경기도 광명시 소하동 광명 구름산지구 도시개발사업지구 A6BL", totalUnits = 426, speculationArea = true, adjustmentArea = true)
    private val bundang = notice("bd", noticeDate = "2026-09-11", rank1Start = "2026-09-22", rank1End = "2026-09-22", resultDate = "2026-10-02")
        .copy(name = "더샵 분당하이스트", address = "경기도 성남시 분당구 정자동 90번지", totalUnits = 143, speculationArea = true, adjustmentArea = true)
    // 좌표는 테스트용 고정값(앱은 지오코딩으로 찾음). 광명은 캡처처럼 '대략적 위치', 분당은 정확한 위치.
    private val gmGeo = GeoResult.Found(GeoPoint(37.4436, 126.8870, exact = false, query = "경기도 광명시 소하동"))
    private val bdGeo = GeoResult.Found(GeoPoint(37.3660, 127.1085, exact = true, query = "경기도 성남시 분당구 정자동 90"))

    private fun match(noticeDate: String) = Eligibility.Result(
        Eligibility.Verdict.INELIGIBLE,
        listOf(
            Eligibility.Track("일반공급 2순위만", Eligibility.Verdict.INELIGIBLE, listOf(
                "✗ 예치금 156만원 < 필요 200만원(경기 거주, 최소 면적 기준)", "✓ 통장 가입 75개월 (필요 24개월)", "✓ 세대주",
                "1순위 요건 미충족 → 2순위만 가능(1순위 마감 시 기회 없음)",
            )),
            Eligibility.Track("신혼부부 특공", Eligibility.Verdict.INELIGIBLE, listOf(
                "✗ 예치금 156만원 < 필요 200만원(경기 거주, 최소 면적 기준)", "✗ 혼인 중이 아님(혼인 7년 이내 부부 대상)",
                "? 소득 미입력(도시근로자 월평균소득 140% 이하 필요)",
            )),
        ),
        notes = listOf("통장 가입 6년 3개월 (75개월) — 가입일 2020-05-21, 공고일 $noticeDate 기준", "규제지역(투기과열지구·조정대상지역)"),
    )

    // 분양가·경쟁률 카드 모양 확인용 예시 값(실제 공고 값 아님).
    private fun models(id: String) = listOf(
        HouseModel(id, "1", "059.9800A", 84.5, 120, 72_000),
        HouseModel(id, "2", "084.9900A", 112.3, 210, 98_500),
    )
    private fun cmpets(id: String) = listOf(
        Competition(id, "1", "059.9800A", 1, "01", "해당지역", 60, 2_412, 40.2, "40.2", false),
        Competition(id, "2", "084.9900A", 1, "01", "해당지역", 105, 98, 0.93, "(△7)", true),
    )

    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var started = false

    private fun put(content: @Composable () -> Unit) {
        screen = content
        if (!started) { started = true; rule.setContent { screen?.invoke() } }
        rule.waitForIdle()
    }

    private fun show(n: Notice, geo: GeoResult, widthDp: Int, heightDp: Int, fontScale: Float, scroll: ScrollState,
                     map: @Composable (GeoPoint) -> Unit) {
        put {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                ChungyakTheme(darkTheme = false) {
                    Box(Modifier.width(widthDp.dp).height(heightDp.dp).background(MaterialTheme.colorScheme.background)) {
                        NoticeDetailBody(
                            n, "2026-10-04", "집계 중", models(n.id), cmpets(n.id), match(n.noticeDate),
                            gajeom = com.chungyak.advisor.match.Gajeom.calc(
                                com.chungyak.advisor.match.Profile(sido = "서울", birthDate = "1988-03-02", married = true, children = 1),
                                java.time.LocalDate.parse("2026-10-04"),
                            ),
                            scroll = scroll,
                            location = { LocationCardContent(n.address, geo, online = true, onOpen = {}, onRetry = {}, map = map) },
                        )
                    }
                }
            }
        }
    }

    private fun mapBounds(): Rect? = rule.onAllNodes(hasTestTag(TAG_MAP), useUnmergedTree = true)
        .fetchSemanticsNodes().firstOrNull()?.boundsInRoot

    private fun capture(): Bitmap {
        val root = rule.activity.window.decorView
        return Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888).also { root.draw(Canvas(it)) }
    }

    /** 지도 칸 밖에 칠해진 빨간 점 수(가짜 지도만 순수 빨강을 쓴다). */
    private fun leaked(bmp: Bitmap, map: Rect?): Int {
        var n = 0
        for (y in 0 until bmp.height step 2) for (x in 0 until bmp.width step 2) {
            if (bmp.getPixel(x, y) != AColor.RED) continue
            val inMap = map != null && x >= map.left - 1 && x <= map.right + 1 && y >= map.top - 1 && y <= map.bottom + 1
            if (!inMap) n++
        }
        return n
    }

    private fun scrollSweep(n: Notice, geo: GeoResult, widthDp: Int, fontScale: Float) {
        val scroll = ScrollState(0)
        show(n, geo, widthDp, 640, fontScale, scroll) { FakeMap(overflow = true) }
        val max = rule.runOnIdle { scroll.maxValue }
        assertTrue("${n.name}: body should scroll", max > 0)
        val steps = 24
        var lastMap: Rect? = null
        var sawMap = 0
        for (i in 0..steps) {
            val target = max * i / steps
            rule.runOnIdle { scroll.dispatchRawDelta((target - scroll.value).toFloat()) }
            rule.waitForIdle()
            val map = mapBounds()
            if (map != null) { lastMap = map; sawMap++ }
            val bmp = capture()
            val bad = leaked(bmp, map)
            bmp.recycle() // 화면 크기 비트맵 ~13MB × 25회 — 바로 해제해야 테스트 JVM이 메모리 부족으로 죽지 않는다.
            assertEquals("${n.name} @${widthDp}dp x$fontScale scroll=$target/$max: map paint outside its box", 0, bad)
        }
        assertTrue("${n.name}: map should be on screen at some scroll positions", sawMap > 0 && lastMap != null)
    }

    @Test fun gwangmyeong_scroll_360dp_largeFont() = scrollSweep(gwangmyeong, gmGeo, 360, 1.3f)
    @Test fun bundang_scroll_360dp_largeFont() = scrollSweep(bundang, bdGeo, 360, 1.3f)
    @Test fun gwangmyeong_scroll_411dp() = scrollSweep(gwangmyeong, gmGeo, 411, 1.0f)
    @Test fun bundang_scroll_411dp() = scrollSweep(bundang, bdGeo, 411, 1.0f)

    /** 두 공고의 지도 칸 폭·위치가 같다(v0.6.0 캡처에선 광명은 여백 있음, 분당은 카드 끝까지). */
    @Test
    fun mapWidthSameForBothNotices() {
        val widths = listOf(gwangmyeong to gmGeo, bundang to bdGeo).map { (n, g) ->
            show(n, g, 411, 2400, 1.0f, ScrollState(0)) { FakeMap(overflow = false) }
            mapBounds()!!.let { it.left to it.right }
        }
        assertEquals(widths[0].first, widths[1].first, 0.5f)
        assertEquals(widths[0].second, widths[1].second, 0.5f)
    }

    /** 리포트용: 두 공고 상세 화면 위쪽(요약·위치·판정)을 실제 osmdroid 지도로 → build/screenshots/. */
    @Test
    fun screenshots() {
        val out = File("build/screenshots").apply { mkdirs() }
        for ((n, g, label) in listOf(Triple(gwangmyeong, gmGeo, "gwangmyeong"), Triple(bundang, bdGeo, "bundang"))) {
            for ((w, scale) in listOf(411 to 1.3f, 360 to 1.3f)) {
                show(n, g, w, 891, scale, ScrollState(0)) { MapPreview(n.name, it) }
                repeat(6) { Thread.sleep(500); rule.waitForIdle() }
                val bmp = capture()
                File(out, "detail_${label}_${w}dp_x$scale.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bmp.recycle()
            }
        }
        // 아래쪽(분양가·경쟁률 카드, 예시 값)
        val scroll = ScrollState(0)
        show(gwangmyeong, gmGeo, 411, 891, 1.0f, scroll) { FakeMap(overflow = false) }
        rule.runOnIdle { scroll.dispatchRawDelta(scroll.maxValue.toFloat()) }
        rule.waitForIdle()
        File(out, "detail_lower_411dp_x1.0.png").outputStream().use { capture().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
