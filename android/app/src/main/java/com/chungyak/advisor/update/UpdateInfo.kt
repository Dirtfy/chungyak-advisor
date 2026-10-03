package com.chungyak.advisor.update

/** 최신 릴리스 정보 (GitHub Releases `releases/latest`). */
data class UpdateInfo(
    val tag: String,          // tag_name, 예: "v0.4.0"
    val version: String,      // "0.4.0"
    val notes: String,        // 릴리스 노트 요약
    val apkUrl: String,       // .apk 에셋 browser_download_url
    val apkSize: Long,        // 바이트, 0 = 모름
    val pageUrl: String = UpdateClient.RELEASES_PAGE, // 릴리스 웹 페이지(수동 다운로드용)
)

/** 버전 비교·노트 요약 — 순수 Kotlin(단위테스트 대상). */
object Versions {

    /** "v0.4.0" / "0.4" / "v1.2.3-beta" → [0,4,0] / [0,4,0] / [1,2,3]. 숫자 없으면 null. */
    fun parse(tag: String): List<Int>? {
        val core = tag.trim().removePrefix("v").removePrefix("V").substringBefore('-').substringBefore('+')
        val parts = core.split('.').map { it.toIntOrNull() ?: return null }
        if (parts.isEmpty() || parts.size > 3) return null
        return parts + List(3 - parts.size) { 0 }
    }

    /** [latest]가 [installed]보다 새 버전인가. 파싱 실패면 false(조용히 무시). */
    fun isNewer(latest: String, installed: String): Boolean {
        val a = parse(latest) ?: return false
        val b = parse(installed) ?: return false
        for (i in 0 until 3) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }

    /** 릴리스 노트 요약: 마크다운 기호 정리, 빈 줄 제거, 최대 [maxLines]줄/[maxChars]자. */
    fun summarize(body: String, maxLines: Int = 6, maxChars: Int = 400): String {
        val lines = body.lines()
            .map { it.trim().trimStart('#', '>', ' ').replace("**", "").replace("`", "") }
            .filter { it.isNotBlank() && !it.startsWith("SHA-256", ignoreCase = true) }
        var out = lines.take(maxLines).joinToString("\n")
        if (out.length > maxChars) out = out.take(maxChars - 1).trimEnd() + "…"
        else if (lines.size > maxLines) out += "\n…"
        return out
    }
}
