package com.chungyak.advisor.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AColor
import android.graphics.Paint
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.chungyak.advisor.ui.theme.ChungyakTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 상세 화면 위치 카드의 배치 검증(v0.6.1). 에뮬레이터가 없어 Robolectric 위에서 실제 Composable을 그린다.
 * - 지도 아래 설명 문구·버튼, 위의 주소가 지도 영역과 겹치지 않는다(360dp 폭, 글꼴 1.0/1.3배).
 * - 지도 안에는 작은 OSM 표기만 있고, 지도가 자기 영역 밖에 그린 것은 잘린다(v0.6.0 글자 가림 원인).
 * 스크린샷은 app/build/screenshots/ 에 남긴다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LocationCardLayoutTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val address = "인천광역시 미추홀구 숭의동 350-1번지 일원"
    private val exact = GeoResult.Found(GeoPoint(37.465, 126.646, exact = true, query = "숭의동 350-1"))
    private val approx = GeoResult.Found(GeoPoint(37.465, 126.646, exact = false, query = "인천광역시 미추홀구 숭의동"))

    private class Case(val label: String, val state: GeoResult?, val online: Boolean = true)

    private val cases = listOf(
        Case("exact", exact),
        Case("approx", approx),
        Case("notfound", GeoResult.NotFound),
        Case("geocode_offline", GeoResult.Offline),
        Case("device_offline", approx, online = false),
        Case("loading", null),
    )

    // setContent는 테스트당 한 번만 되므로, 그릴 내용을 상태로 바꿔 끼운다.
    private var screen by mutableStateOf<(@Composable () -> Unit)?>(null)
    private var started = false

    private fun put(content: @Composable () -> Unit) {
        screen = content
        if (!started) { started = true; rule.setContent { screen?.invoke() } }
        rule.waitForIdle()
    }

    private fun show(widthDp: Int, fontScale: Float, case: Case, map: @Composable (GeoPoint) -> Unit = { FakeMap(overflow = false) }) {
        put {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                ChungyakTheme(darkTheme = false) {
                    Box(Modifier.width(widthDp.dp).padding(horizontal = 16.dp)) {
                        LocationCardContent(address, case.state, case.online, onOpen = {}, onRetry = {}, map = map)
                    }
                }
            }
        }
    }

    private fun bounds(tag: String): Rect = rule.onNodeWithTag(tag, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    private fun exists(tag: String) = rule.onAllNodes(androidx.compose.ui.test.hasTestTag(tag), useUnmergedTree = true)
        .fetchSemanticsNodes().isNotEmpty()

    private fun assertNoOverlap(a: Rect, b: Rect, what: String) =
        assertFalse("$what overlap: $a vs $b", a.overlaps(b) && a.intersect(b).let { it.width > 0.5f && it.height > 0.5f })

    private fun checkLayout(widthDp: Int, fontScale: Float) {
        for (c in cases) {
            show(widthDp, fontScale, c)
            val tag = "${c.label} @${widthDp}dp x$fontScale"
            val hasMap = exists(TAG_MAP)
            assertTrue("$tag: map only when Found+online", hasMap == (c.online && c.state is GeoResult.Found))
            val open = bounds(TAG_OPEN)
            if (exists(TAG_RETRY)) assertNoOverlap(open, bounds(TAG_RETRY), "$tag open/retry")
            if (hasMap) {
                val map = bounds(TAG_MAP)
                assertNoOverlap(map, bounds(TAG_ADDRESS), "$tag map/address")
                assertNoOverlap(map, bounds(TAG_BUTTONS), "$tag map/buttons")
                assertNoOverlap(map, open, "$tag map/open")
                if (exists(TAG_NOTE)) assertNoOverlap(map, bounds(TAG_NOTE), "$tag map/note")
                // 버튼·설명은 지도 아래
                assertTrue("$tag buttons below map", bounds(TAG_BUTTONS).top >= map.bottom)
                // OSM 표기: 지도 안 오른쪽 아래 구석, 작게(가운데 마커를 가리지 않음)
                val attr = bounds(TAG_ATTRIBUTION)
                assertTrue("$tag attribution inside map", map.contains(attr.topLeft) && attr.right <= map.right + 0.5f && attr.bottom <= map.bottom + 0.5f)
                assertTrue("$tag attribution small", attr.width < map.width * 0.45f && attr.height < map.height * 0.2f)
                assertFalse("$tag attribution clear of marker", attr.contains(map.center))
            }
            if (exists(TAG_NOTE)) assertNoOverlap(bounds(TAG_NOTE), bounds(TAG_BUTTONS), "$tag note/buttons")
        }
    }

    @Test fun noOverlap_360dp_normalFont() = checkLayout(360, 1.0f)
    @Test fun noOverlap_360dp_largeFont() = checkLayout(360, 1.3f)
    @Test fun noOverlap_411dp_largeFont() = checkLayout(411, 1.3f)

    /** 지도가 자기 영역 밖까지 그려도(osmdroid 가장자리 타일) 주소·문구·버튼 위에는 아무것도 칠해지지 않는다. */
    @Test
    fun mapDrawingIsClipped() {
        show(360, 1.3f, Case("approx", approx)) { FakeMap(overflow = true) }
        val bmp = capture()
        val px = rule.activity.resources.displayMetrics.density
        for (tag in listOf(TAG_ADDRESS, TAG_NOTE, TAG_BUTTONS)) {
            val r = bounds(tag)
            assertFalse("map paint leaked onto $tag", hasRed(bmp, r))
        }
        // 대조: 같은 지도를 자르지 않고 놓으면 밖으로 번진다 → 위 검사가 실제로 원인을 잡는 검사임을 확인.
        put {
            Column(Modifier.width(360.dp)) {
                Text("위 글자", Modifier.fillMaxWidth().testTag("above"))
                Box(Modifier.fillMaxWidth().height(200.dp)) { FakeMap(overflow = true) }
                Text("아래 글자", Modifier.fillMaxWidth().testTag("below"))
            }
        }
        val leaked = capture()
        assertTrue("control: unclipped map should leak (px=$px)", hasRed(leaked, bounds("above")) || hasRed(leaked, bounds("below")))
    }

    /** 리포트용 스크린샷(실제 Composable 렌더링; 지도 칸은 실제 osmdroid MapView — 테스트 환경이라 타일 없이 마커만 보일 수 있음). */
    @Test
    fun screenshots() {
        val out = File("build/screenshots").apply { mkdirs() }
        for (scale in listOf(1.0f, 1.3f)) for (c in cases.take(4)) {
            show(360, scale, c, map = { MapPreview("테스트 단지", it) })
            Thread.sleep(300)
            rule.waitForIdle()
            val bmp = capture()
            File(out, "location_${c.label}_360dp_x$scale.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun capture(): Bitmap {
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        return bmp
    }

    private fun hasRed(bmp: Bitmap, r: Rect): Boolean {
        for (y in r.top.toInt().coerceAtLeast(0) until r.bottom.toInt().coerceAtMost(bmp.height) step 2)
            for (x in r.left.toInt().coerceAtLeast(0) until r.right.toInt().coerceAtMost(bmp.width) step 2)
                if (bmp.getPixel(x, y) == AColor.RED) return true
        return false
    }
}

/** 지도 대역: overflow면 osmdroid 가장자리 타일처럼 자기 영역 밖(위아래 300px)까지 빨갛게 칠한다. */
@Composable
private fun FakeMap(overflow: Boolean) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx: Context ->
            object : View(ctx) {
                private val paint = Paint().apply { color = if (overflow) AColor.RED else AColor.LTGRAY }
                override fun onDraw(canvas: Canvas) {
                    val pad = if (overflow) 300f else 0f
                    canvas.drawRect(-pad, -pad, width + pad, height + pad, paint)
                }
            }
        },
    )
}
