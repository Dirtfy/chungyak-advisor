package com.chungyak.advisor.update

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * GitHub Releases 기반 사이드로드 업데이트(Play In-App Update 불가).
 * 비인증 공개 API라 레이트리밋(60회/시간/IP)·네트워크 실패는 null로 조용히 무시한다.
 */
object UpdateClient {

    /**
     * 순서대로 시도해 처음 응답하는 저장소를 쓴다. 본 저장소가 비공개면 404이므로,
     * APK만 올리는 공개 저장소(-releases)로도 동작하게 둔다.
     */
    val REPOS = listOf("Dirtfy/chungyak-advisor", "Dirtfy/chungyak-advisor-releases")

    /** 팝업 경로가 막혔을 때 브라우저로 직접 받는 곳. */
    const val RELEASES_PAGE = "https://github.com/Dirtfy/chungyak-advisor/releases/latest"

    fun latest(): UpdateInfo? {
        for (repo in REPOS) {
            val json = runCatching { getJson("https://api.github.com/repos/$repo/releases/latest") }
                .getOrNull() ?: continue
            return parse(json)
        }
        return null
    }

    private fun parse(j: JSONObject): UpdateInfo? {
        val tag = j.optString("tag_name").ifBlank { return null }
        val version = Versions.parse(tag)?.joinToString(".") ?: return null
        val assets = j.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                return UpdateInfo(
                    tag = tag,
                    version = version,
                    notes = Versions.summarize(j.optString("body").ifBlank { j.optString("name") }),
                    apkUrl = a.optString("browser_download_url"),
                    apkSize = a.optLong("size"),
                    pageUrl = j.optString("html_url").ifBlank { RELEASES_PAGE },
                )
            }
        }
        return null
    }

    private fun getJson(url: String): JSONObject? {
        val conn = open(url, 10_000)
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        return try {
            if (conn.responseCode != 200) null
            else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    // APK 다운로드는 UpdateDownloads(시스템 DownloadManager)가 한다 — 앱을 나가도 이어지게.
    private fun open(url: String, readTimeout: Int) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            this.readTimeout = readTimeout
            setRequestProperty("User-Agent", "chungyak-radar")
        }
}
