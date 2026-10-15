package com.chungyak.advisor.update

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chungyak.advisor.update.UpdateDownloads.ApkCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 설치 화면 열기(v0.13.3~): 받은 APK를 넘기기 전에 검사하고, 같은 파일로 설치 화면을 겹쳐 열지 않는다.
 * v0.13.2까지는 크기만 봤고, "업데이트 준비됨" 알림이 앱을 거치지 않고 설치 화면을 직접 열었다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class UpdateInstallTest {

    private val app: Application = ApplicationProvider.getApplicationContext()
    private val ours = "com.chungyak.advisor"
    private val size = 9_000_000L

    private fun check(
        onDisk: Long = size,
        expected: Long = size,
        pkg: String? = ours,
        code: Long = 1304,
        installed: Long = 1303,
    ) = UpdateDownloads.checkApk(onDisk, expected, pkg, code, ours, installed)

    @Before fun setUp() {
        UpdateViewModel.Session.installerPath = null
        UpdateViewModel.Session.installerAt = 0L
        UpdateDownloads.dir(app)?.deleteRecursively()
        app.getSharedPreferences("update_download", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun completeNewerApkOfThisApp_ok() {
        assertEquals(ApkCheck.OK, check())
        assertEquals(ApkCheck.OK, check(onDisk = 123, expected = 0)) // 크기 모름 → 비교 안 함
    }

    @Test fun missingOrTruncatedFile_rejected() {
        assertEquals(ApkCheck.MISSING, check(onDisk = 0))
        assertEquals(ApkCheck.INCOMPLETE, check(onDisk = size - 4096))
        assertEquals(ApkCheck.INCOMPLETE, check(onDisk = size + 1))
    }

    @Test fun notAnApk_wrongApp_orNotNewer_rejected() {
        // 예: 오류 HTML 페이지가 APK 이름으로 저장됨(크기 모름)
        assertEquals(ApkCheck.NOT_AN_APK, check(onDisk = 9_000, expected = 0, pkg = null))
        assertEquals(ApkCheck.WRONG_APP, check(pkg = "com.example.other"))
        assertEquals(ApkCheck.NOT_NEWER, check(code = 1303))
        assertEquals(ApkCheck.NOT_NEWER, check(code = 1302))
    }

    @Test fun installerOpensOnce_whenTwoPathsAskTogether() {
        val apk = "/x/updates/chungyak-radar-0.13.3.apk"
        assertTrue(UpdateDownloads.shouldOpenInstaller(null, 0L, apk, 10_000L))
        // 알림 탭과 진행률 읽기가 몇 ms 차이로
        assertFalse(UpdateDownloads.shouldOpenInstaller(apk, 10_000L, apk, 10_050L))
    }

    @Test fun installerOpensAgain_laterOrForAnotherFile() {
        val apk = "/x/updates/chungyak-radar-0.13.3.apk"
        val later = 10_000L + UpdateDownloads.INSTALLER_REPEAT_MS
        assertTrue(UpdateDownloads.shouldOpenInstaller(apk, 10_000L, apk, later))
        assertTrue(UpdateDownloads.shouldOpenInstaller(apk, 10_000L, "/x/updates/chungyak-radar-0.13.4.apk", 10_050L))
    }

    @Test fun brokenDownload_isDiscarded_andInstallerNotOpened() {
        val bytes = "<html>not an apk</html>".toByteArray()
        val info = UpdateInfo("v9.9.9", "9.9.9", "notes", "https://example.invalid/a.apk", bytes.size.toLong())
        val f = UpdateDownloads.apkFile(app, info.version)!!
        f.parentFile!!.mkdirs()
        f.writeBytes(bytes) // 크기는 맞지만 APK가 아님
        assertTrue(UpdateDownloads.isComplete(app, info))

        val vm = UpdateViewModel(app)
        vm.install(info)

        assertEquals(UpdateState.Failed(info, UpdateViewModel.BROKEN_APK), vm.state.value)
        assertFalse(f.exists()) // [다시 시도]는 처음부터 새로 받는다
        assertFalse(UpdateDownloads.isComplete(app, info))
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }

    @Test fun notificationTap_withNothingDownloaded_opensNothing() {
        val vm = UpdateViewModel(app)
        vm.installDownloaded()
        assertEquals(UpdateState.Hidden, vm.state.value)
        assertEquals(null, shadowOf(app).nextStartedActivity)
    }
}
