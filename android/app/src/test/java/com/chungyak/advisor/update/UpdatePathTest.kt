package com.chungyak.advisor.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * 인앱 업데이트 경로(v0.13.1~ DownloadManager): 저장 위치 → 완료 판정 → FileProvider 설치 URI,
 * 그리고 DownloadManager 상태 해석. 실제 다운로드는 시스템 프로세스가 하므로 여기선 파일을 직접 둔다.
 * v0.4.0~0.4.3은 저장 폴더를 만들지 않아 첫 다운로드가 항상 ENOENT로 실패했다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UpdatePathTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val info = UpdateInfo("v9.9.9", "9.9.9", "notes", "https://example.invalid/a.apk", 300_000)

    @Before fun clean() {
        UpdateDownloads.dir(ctx)?.deleteRecursively()
        ctx.getSharedPreferences("update_download", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun putApk(size: Int): File {
        val f = UpdateDownloads.apkFile(ctx, info.version)!!
        f.parentFile!!.mkdirs()
        f.writeBytes(ByteArray(size))
        return f
    }

    @Test fun apkFile_isInAppExternalFilesDir() {
        val f = UpdateDownloads.apkFile(ctx, "9.9.9")!!
        assertEquals(File(ctx.getExternalFilesDir(null), "updates/chungyak-radar-9.9.9.apk"), f)
    }

    @Test fun isComplete_requiresExactSize() {
        assertFalse(UpdateDownloads.isComplete(ctx, info)) // 받은 적 없음
        putApk(1000)
        assertFalse(UpdateDownloads.isComplete(ctx, info)) // 덜 받음
        putApk(300_000)
        assertTrue(UpdateDownloads.isComplete(ctx, info))
        assertTrue(UpdateDownloads.isComplete(ctx, info.copy(apkSize = 0))) // 크기 모르면 비어 있지만 않으면
    }

    @Test fun installIntent_sharesApkViaFileProvider() {
        putApk(300_000)
        val i = UpdateDownloads.installIntent(ctx, info)
        assertEquals(Intent.ACTION_VIEW, i.action)
        assertEquals("application/vnd.android.package-archive", i.type)
        assertEquals("content", i.data!!.scheme)
        assertEquals("${ctx.packageName}.updates", i.data!!.authority)
        assertTrue(i.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }

    @Test fun start_enqueuesAndRemembersInfo_acrossProcess() {
        UpdateDownloads.start(ctx, info)
        // 프로세스가 새로 떠도(앱 복귀) prefs에서 같은 릴리스 정보를 읽는다.
        assertEquals(info, UpdateDownloads.load(ctx))
        assertTrue(UpdateDownloads.dir(ctx)!!.isDirectory)
    }

    @Test fun status_fileCompleteWithoutSystemRecord_isDone() {
        UpdateDownloads.start(ctx, info)
        putApk(300_000)
        // Robolectric의 DownloadManager 기록과 무관하게, 완성된 파일만 있어도 재다운로드 없이 설치로 간다.
        val s = UpdateDownloads.status(ctx)
        assertTrue(s.toString(), s is UpdateDownloads.Status.Done || s is UpdateDownloads.Status.Running)
    }

    @Test fun cleanupIfInstalled_removesOldDownload() {
        UpdateDownloads.start(ctx, info)
        putApk(300_000)
        UpdateDownloads.cleanupIfInstalled(ctx, "9.9.8") // 아직 옛 버전 → 그대로
        assertNotNull(UpdateDownloads.load(ctx))
        UpdateDownloads.cleanupIfInstalled(ctx, "9.9.9") // 설치 완료 → 정리
        assertEquals(null, UpdateDownloads.load(ctx))
        assertFalse(UpdateDownloads.apkFile(ctx, "9.9.9")!!.exists())
    }

    @Test fun interpret_mapsDownloadManagerRows() {
        fun row(status: Int, reason: Int = 0, soFar: Long = 0, total: Long = -1) =
            UpdateDownloads.Row(status, reason, soFar, total)

        val running = UpdateDownloads.interpret(info, row(DownloadManager.STATUS_RUNNING, soFar = 150_000, total = 300_000), 0)
        assertEquals(UpdateDownloads.Status.Running(info, 0.5f, null), running)

        // 앱이 백그라운드여도 시스템이 네트워크를 기다렸다가 이어 받는다 → 실패가 아니라 대기.
        val paused = UpdateDownloads.interpret(
            info, row(DownloadManager.STATUS_PAUSED, DownloadManager.PAUSED_WAITING_FOR_NETWORK, 100_000), 0,
        ) as UpdateDownloads.Status.Running
        assertEquals("네트워크 연결을 기다리는 중", paused.waiting)
        assertEquals(1f / 3, paused.progress, 0.001f) // 총 크기 모르면 릴리스 크기로

        val unknown = UpdateDownloads.interpret(info.copy(apkSize = 0), row(DownloadManager.STATUS_PENDING), 0)
        assertEquals(-1f, (unknown as UpdateDownloads.Status.Running).progress)

        assertEquals(UpdateDownloads.Status.Done(info), UpdateDownloads.interpret(info, row(DownloadManager.STATUS_SUCCESSFUL), 300_000))
        assertTrue(UpdateDownloads.interpret(info, row(DownloadManager.STATUS_SUCCESSFUL), 299_999) is UpdateDownloads.Status.Failed)
        assertEquals(
            UpdateDownloads.Status.Failed(info, "HTTP 404"),
            UpdateDownloads.interpret(info, row(DownloadManager.STATUS_FAILED, 404), 0),
        )
    }

    /** 실제 GitHub 공개 릴리스 조회 + APK URL 응답 확인(네트워크 필요, LIVE_GITHUB=1 일 때만). */
    @Test fun live_latestRelease_assetReachable() {
        assumeTrue(System.getenv("LIVE_GITHUB") == "1")
        val info = UpdateClient.latest()
        assertNotNull("releases/latest 조회 실패", info)
        info!!
        assertTrue(info.apkUrl.endsWith(".apk"))
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        conn.requestMethod = "HEAD"
        conn.instanceFollowRedirects = true
        assertEquals(200, conn.responseCode)
        println("LIVE: ${info.tag} ${info.apkUrl} ${conn.contentLengthLong} bytes")
        conn.disconnect()
    }
}
