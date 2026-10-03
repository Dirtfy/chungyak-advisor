package com.chungyak.advisor.update

import org.json.JSONObject
import java.io.File
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

    /** [url]을 [dest]로 받으며 진행률(0..1, 크기 모르면 -1)을 알린다. 실패 시 예외. */
    fun download(url: String, expectedSize: Long, dest: File, onProgress: (Float) -> Unit) {
        // cacheDir/updates는 새 설치 시 없다. v0.4.0~0.4.3은 이걸 만들지 않아 다운로드가 항상 ENOENT로 실패했다.
        dest.parentFile?.mkdirs()
        val tmp = File(dest.parentFile, dest.name + ".part")
        val conn = open(url, 60_000)
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: expectedSize
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f)
                    }
                }
            }
        } catch (e: Exception) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
        if (tmp.length() == 0L || (expectedSize > 0 && tmp.length() != expectedSize)) {
            tmp.delete()
            error("파일 크기가 맞지 않습니다")
        }
        dest.delete()
        if (!tmp.renameTo(dest)) error("파일 저장 실패")
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

    // 에셋 URL은 github.com → objects.githubusercontent.com 으로 리다이렉트(둘 다 https라 자동 추적).
    private fun open(url: String, readTimeout: Int) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 10_000
            this.readTimeout = readTimeout
            setRequestProperty("User-Agent", "chungyak-radar")
        }
}
