package com.chungyak.advisor.update

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 업데이트 팝업 닫기(v0.13.2~): 바깥 탭·뒤로 가기 = 이번만 닫기, [나중에]만 그 버전을 다시 묻지 않음.
 * 그리고 앱 복귀(ON_START)마다 확인하되 30분 간격으로 중복 요청을 막는지.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateDismissTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val info = UpdateInfo("v9.9.9", "9.9.9", "notes", "https://example.invalid/a.apk", 300_000)
    private lateinit var vm: UpdateViewModel

    @Before fun setUp() {
        UpdateViewModel.Session.dismissed = null
        UpdateViewModel.Session.lastCheck = 0L
        UpdateDownloads.dir(app)?.deleteRecursively()
        vm = UpdateViewModel(app)
    }

    @Test fun outsideTap_closesOnlyThisTime() {
        vm.offer(info, manual = false)
        assertEquals(UpdateState.Available(info), vm.state.value)
        vm.dismiss() // 바깥 탭·뒤로 가기
        assertEquals(UpdateState.Hidden, vm.state.value)
        assertEquals(null, UpdateViewModel.Session.dismissed)
        vm.offer(info, manual = false) // 다음 자동 확인
        assertEquals(UpdateState.Available(info), vm.state.value)
    }

    @Test fun laterButton_skipsThatVersionOnAutoCheck() {
        vm.offer(info, manual = false)
        vm.later()
        assertEquals("9.9.9", UpdateViewModel.Session.dismissed)
        vm.offer(info, manual = false)
        assertEquals(UpdateState.Hidden, vm.state.value)
        // 설정의 [업데이트 확인]은 미룬 버전도 보여 준다.
        vm.offer(info, manual = true)
        assertEquals(UpdateState.Available(info), vm.state.value)
        // 더 새 버전은 다시 묻는다.
        vm.dismiss()
        val next = info.copy(tag = "v9.9.10", version = "9.9.10")
        vm.offer(next, manual = false)
        assertEquals(UpdateState.Available(next), vm.state.value)
    }

    @Test fun foregroundCheck_everyReturn_butThrottled() {
        val min = 60_000L
        assertTrue(UpdateViewModel.autoCheckDue(now = 1_000_000_000L, lastCheck = 0L)) // 첫 복귀
        assertFalse(UpdateViewModel.autoCheckDue(now = 10 * min, lastCheck = 0L + 1 * min)) // 9분 뒤 복귀 → 건너뜀
        assertTrue(UpdateViewModel.autoCheckDue(now = 31 * min, lastCheck = 1 * min)) // 30분 지나 복귀 → 확인
    }
}
