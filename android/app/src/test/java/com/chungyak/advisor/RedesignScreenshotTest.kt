package com.chungyak.advisor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Inbox
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.ui.theme.ChungyakTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 리디자인 1단계(docs/11) 목록 카드·상세 요약 카드 스크린샷 → app/build/screenshots/. 실제 Composable 렌더링(Robolectric). */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class RedesignScreenshotTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val a = notice("1", rank1Start = "2026-10-10", rank1End = "2026-10-11", resultDate = "2026-10-20", priceMin = 52000, priceMax = 89000)
        .copy(name = "힐스테이트 숭의 더 퍼스트", areaName = "인천", address = "인천광역시 미추홀구 숭의동 350-1번지 일원", totalUnits = 412, adjustmentArea = true)
    private val b = notice("2", rank1Start = "2026-09-20", rank1End = "2026-09-21", resultDate = "2026-10-08", priceMin = 98000, priceMax = 154000, cmpetMax = 48.2, cmpetAvg = 12.7, cmpetFinal = true)
        .copy(name = "래미안 원펜타스", areaName = "서울", address = "서울특별시 서초구 반포동 1-1", totalUnits = 292, speculationArea = true)
    private val ok = Eligibility.Result(Eligibility.Verdict.ELIGIBLE, listOf(Eligibility.Track("일반공급 1순위", Eligibility.Verdict.ELIGIBLE, emptyList())))

    private fun shot(name: String, fontScale: Float, content: @androidx.compose.runtime.Composable () -> Unit) {
        rule.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale)) {
                ChungyakTheme(darkTheme = false) {
                    Column(
                        Modifier.width(360.dp).background(MaterialTheme.colorScheme.background).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) { content() }
                }
            }
        }
        rule.waitForIdle()
        val root = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        File("build/screenshots").apply { mkdirs() }.resolve(name).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun listCards() = shot("redesign_list_360dp_x1.0.png", 1.0f) {
        NoticeRow(a, "2026-10-04", "접수 전", ok) {}
        NoticeRow(b, "2026-10-04", "최고 48.20:1 · 평균 12.70:1", null) {}
    }

    @Test fun listCardsLargeFont() = shot("redesign_list_360dp_x1.3.png", 1.3f) {
        NoticeRow(a, "2026-10-04", "접수 전", ok) {}
    }

    @Test fun detailSummary() = shot("redesign_detail_summary_360dp_x1.0.png", 1.0f) {
        DetailSummaryCard(a, "2026-10-04", "접수 전")
    }

    @Test fun emptyStates() = shot("redesign_empty_360dp_x1.0.png", 1.0f) {
        com.chungyak.advisor.ui.EmptyState(
            androidx.compose.material.icons.Icons.Outlined.Inbox, "아직 수집된 공고가 없어요",
            "위의 새로고침(지금 확인)을 누르면 바로 조회합니다.", action = "지금 확인",
        )
        com.chungyak.advisor.ui.EmptyState(
            androidx.compose.material.icons.Icons.Outlined.ErrorOutline, "확인 중 오류",
            "네트워크 상태를 확인하세요.",
            tint = MaterialTheme.colorScheme.error, container = MaterialTheme.colorScheme.errorContainer,
        )
    }
}
