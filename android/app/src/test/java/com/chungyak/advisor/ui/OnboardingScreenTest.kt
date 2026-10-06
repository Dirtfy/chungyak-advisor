package com.chungyak.advisor.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.Profile
import com.chungyak.advisor.ui.theme.ChungyakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 온보딩 화면 흐름(Robolectric Compose): 소개 → 단계 저장 → 검증 → 나중에 하기 / 완료. */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "w360dp-h1400dp-xxhdpi")
class OnboardingScreenTest {

    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private val saved = mutableListOf<Profile>()
    private val closed = mutableListOf<Boolean>()

    private fun show(initial: Profile = Profile()) = rule.setContent {
        ChungyakTheme(darkTheme = false) {
            OnboardingScreen(
                initial = initial, initialMode = NotifyMode.ELIGIBLE_OR_CHECK, scheduleAlerts = true, hasKey = false,
                onScheduleAlerts = {}, onSaveStep = { p, _ -> saved += p }, onClose = { closed += it },
            )
        }
    }

    @Test fun later_onIntro_closesWithoutSaving() {
        show()
        rule.onNodeWithText("나중에 하기").performClick()
        assertEquals(listOf(false), closed)
        assertTrue(saved.isEmpty())
    }

    @Test fun residenceRequired_thenSavedOnNext() {
        show()
        rule.onNodeWithText("시작하기").performClick()
        rule.onNodeWithText("내 조건 입력 1/8").assertIsDisplayed()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("거주 시·도를 골라 주세요.").assertIsDisplayed()
        assertTrue(saved.isEmpty())

        rule.onNodeWithText("경기").performClick()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("내 조건 입력 2/8").assertIsDisplayed()
        assertEquals("경기", saved.single().sido)
    }

    @Test fun skipAll_reachesDone_andCloses() {
        show(Profile(sido = "서울"))
        rule.onNodeWithText("시작하기").performClick()
        repeat(Onboarding.steps.size) { rule.onNodeWithText("건너뛰기").performClick() }
        rule.onNodeWithText("내 조건을 저장했어요").assertIsDisplayed()
        assertTrue(saved.isEmpty()) // 건너뛰기만 했으면 저장하지 않는다
        rule.onNodeWithText("서비스키 입력하러 가기").performClick()
        assertEquals(listOf(true), closed)
    }

    @Test fun previous_keepsEnteredValues() {
        show()
        rule.onNodeWithText("시작하기").performClick()
        rule.onNodeWithText("인천").performClick()
        rule.onNodeWithText("다음").performClick()
        rule.onNodeWithText("이전").performClick()
        rule.onNodeWithText("내 조건 입력 1/8").assertIsDisplayed()
        rule.onNodeWithText("다음").performClick() // 인천이 남아 있어 통과
        assertEquals(listOf("인천", "인천"), saved.map { it.sido })
    }
}
