package com.chungyak.advisor.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.chungyak.advisor.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * data.go.kr 서비스키를 앱·저장소에 넣지 않는다(공개 저장소·배포 APK = 모두가 오너 키를 쓰게 됨).
 * 키는 사용자가 설정에 입력한 값만 쓴다.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class)
class NoEmbeddedKeyTest {

    /** data.go.kr 키 모양: Decoding(base64, = 패딩)·Encoding(%2B/%3D)·hex 64자. */
    private val keyShape = Regex("[A-Za-z0-9+/]{40,}={1,2}|[A-Za-z0-9%]{40,}%3D|\\b[0-9a-fA-F]{64}\\b")

    @Test fun freshInstall_hasNoKey() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        ctx.getSharedPreferences("chungyak_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        val s = Settings(ctx)
        assertEquals("", s.serviceKey)
        assertFalse(s.hasKey)
    }

    @Test fun userEnteredKey_isKept() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        Settings(ctx).serviceKey = "  user-key==  "
        assertEquals("user-key==", Settings(ctx).serviceKey)
        assertTrue(Settings(ctx).hasKey)
    }

    @Test fun buildConfig_hasNoKeyField() {
        BuildConfig::class.java.declaredFields.forEach { f ->
            assertFalse("BuildConfig.${f.name}", f.name.contains("KEY", ignoreCase = true))
            val v = runCatching { f.get(null)?.toString() }.getOrNull().orEmpty()
            assertFalse("BuildConfig.${f.name} looks like a key", keyShape.containsMatchIn(v))
        }
    }

    @Test fun sources_haveNoKeyLiteral() {
        val roots = listOf(File("src"), File("build.gradle.kts"), File("../gradle.properties"), File("../local.properties"))
        val files = roots.filter { it.exists() }.flatMap { r -> r.walkTopDown().filter { it.isFile } }
            .filter { it.extension in setOf("kt", "kts", "xml", "properties", "json") }
        assertTrue("scanned nothing (cwd=${File(".").absolutePath})", files.size > 20)
        val bad = files.filter { f ->
            keyShape.findAll(f.readText()).any { it.value != SIGNING_CERT }
        }
        assertEquals(emptyList<File>(), bad)
    }

    private companion object {
        /** 공개 서명 인증서 지문(비밀 아님). */
        const val SIGNING_CERT = "e169d6eb3d5513bcf02400c0a9e8f270a9e299bfcd9e7f56a96b30136f3c61ed"
    }
}
