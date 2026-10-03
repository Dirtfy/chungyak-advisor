package com.chungyak.advisor.update

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.random.Random

/**
 * 인앱 업데이트 경로(다운로드 → 저장 → FileProvider URI) 검증.
 * v0.4.0~0.4.3은 cacheDir/updates 폴더를 만들지 않아 첫 다운로드가 항상 ENOENT로 실패했다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class UpdatePathTest {

    private val ctx: Context = ApplicationProvider.getApplicationContext()
    private val apk = Random(1).nextBytes(300_000)
    private lateinit var server: ServerSocket
    private lateinit var base: String

    @Before fun start() {
        File(ctx.cacheDir, "updates").deleteRecursively()
        server = ServerSocket(0)
        base = "http://127.0.0.1:${server.localPort}"
        // 최소 HTTP 서버: GitHub처럼 에셋 URL(/download)이 다른 경로(/blob)로 302 리다이렉트.
        thread(isDaemon = true) {
            while (!server.isClosed) {
                val s = runCatching { server.accept() }.getOrNull() ?: break
                s.use {
                    val path = it.getInputStream().bufferedReader().readLine().split(' ')[1]
                    val out = it.getOutputStream()
                    if (path == "/download") {
                        out.write("HTTP/1.1 302 Found\r\nLocation: $base/blob\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    } else {
                        out.write("HTTP/1.1 200 OK\r\nContent-Length: ${apk.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        out.write(apk)
                    }
                    out.flush()
                }
            }
        }
    }

    @After fun stop() {
        server.close()
    }

    @Test fun download_createsMissingUpdatesDir_followsRedirect() {
        val dest = File(File(ctx.cacheDir, "updates"), "chungyak-radar-9.9.9.apk")
        assertFalse(dest.parentFile!!.exists()) // 새로 설치한 앱의 상태
        var last = 0f
        UpdateClient.download("$base/download", apk.size.toLong(), dest) { last = it }
        assertArrayEquals(apk, dest.readBytes())
        assertEquals(1f, last)
        assertFalse(File(dest.parentFile, dest.name + ".part").exists())
    }

    @Test fun downloadedApk_isSharedViaFileProvider() {
        val dest = File(File(ctx.cacheDir, "updates"), "chungyak-radar-9.9.9.apk")
        UpdateClient.download("$base/download", apk.size.toLong(), dest) {}
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", dest)
        assertEquals("content", uri.scheme)
        assertEquals("${ctx.packageName}.updates", uri.authority)
    }

    /** 실제 GitHub 공개 릴리스로 조회 → APK 다운로드까지(네트워크 필요, LIVE_GITHUB=1 일 때만). */
    @Test fun live_latestRelease_downloads() {
        assumeTrue(System.getenv("LIVE_GITHUB") == "1")
        val info = UpdateClient.latest()
        assertNotNull("releases/latest 조회 실패", info)
        info!!
        assertTrue(info.apkUrl.endsWith(".apk"))
        val dest = File(File(ctx.cacheDir, "updates"), "chungyak-radar-${info.version}.apk")
        UpdateClient.download(info.apkUrl, info.apkSize, dest) {}
        assertEquals(info.apkSize, dest.length())
        println("LIVE: ${info.tag} ${info.apkUrl} ${dest.length()} bytes")
    }
}
