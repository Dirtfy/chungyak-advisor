package com.chungyak.advisor.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionsTest {

    @Test fun parse() {
        assertEquals(listOf(0, 4, 0), Versions.parse("v0.4.0"))
        assertEquals(listOf(1, 2, 0), Versions.parse("1.2"))
        assertEquals(listOf(1, 2, 3), Versions.parse("v1.2.3-beta"))
        assertNull(Versions.parse("latest"))
        assertNull(Versions.parse(""))
    }

    @Test fun isNewer() {
        assertTrue(Versions.isNewer("v0.4.0", "0.3.0"))
        assertTrue(Versions.isNewer("v0.10.0", "0.9.9")) // 문자열 비교가 아닌 숫자 비교
        assertTrue(Versions.isNewer("1.0.0", "0.99.99"))
        assertFalse(Versions.isNewer("v0.4.0", "0.4.0"))
        assertFalse(Versions.isNewer("v0.3.0", "0.4.0"))
        assertFalse(Versions.isNewer("garbage", "0.4.0"))
    }

    @Test fun summarize() {
        val body = "## 변경\n\n**경쟁률** 추가\n- 정렬\n\n디버그 APK. SHA-256: abc"
        assertEquals("변경\n경쟁률 추가\n- 정렬\n디버그 APK. SHA-256: abc".lines().take(4).joinToString("\n"),
            Versions.summarize(body))
        assertEquals("a\nb\n…", Versions.summarize("a\nb\nc", maxLines = 2))
        assertEquals(10, Versions.summarize("x".repeat(50), maxChars = 10).length)
    }
}
