package com.chungyak.advisor.update

import android.app.ActivityManager
import android.app.DownloadManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.chungyak.advisor.MainActivity
import com.chungyak.advisor.R
import java.io.File

/**
 * APK 다운로드를 시스템 DownloadManager에 맡긴다(v0.13.1~).
 *
 * v0.13.0까지는 UpdateViewModel의 코루틴이 HttpURLConnection으로 직접 받았다. 앱을 나가면
 * 프로세스가 백그라운드 네트워크 제한(Doze/앱 대기·Android 14 캐시 프로세스 동결)에 걸려
 * 읽기가 멈추고, 60초 readTimeout 뒤 "timeout"·호스트 확인 실패 같은 네트워크 오류로 끝났다.
 * 프로세스가 정리되면 받던 파일(.part)도 사라졌다. DownloadManager는 시스템 프로세스에서 받으므로
 * 앱이 백그라운드이거나 종료돼도 이어지고, 네트워크가 끊기면 기다렸다가 다시 받는다.
 *
 * 진행 중인 다운로드 id와 릴리스 정보는 prefs에 둬서, 앱을 다시 열면(프로세스가 새로 떠도) 이어서 보여 준다.
 */
object UpdateDownloads {

    const val CHANNEL = "app_update"
    private const val PREFS = "update_download"
    private const val NOTIF_ID = 0x5550
    private const val APK_MIME = "application/vnd.android.package-archive"

    /** 다운로드 상태. [Running.progress]는 0..1, 크기 모르면 -1. */
    sealed class Status {
        data object None : Status()
        data class Running(val info: UpdateInfo, val progress: Float, val waiting: String?) : Status()
        data class Done(val info: UpdateInfo) : Status()
        data class Failed(val info: UpdateInfo, val message: String) : Status()
    }

    /** 앱 전용 외부 저장소(권한 불필요). DownloadManager는 앱 내부 cacheDir에 쓸 수 없다. */
    fun dir(ctx: Context): File? = ctx.getExternalFilesDir(null)?.let { File(it, "updates") }

    fun apkFile(ctx: Context, version: String): File? = dir(ctx)?.let { File(it, "chungyak-radar-$version.apk") }

    /** 이미 다 받은 APK가 있나(크기 일치). */
    fun isComplete(ctx: Context, info: UpdateInfo): Boolean =
        apkFile(ctx, info.version)?.let { sizeOk(it.takeIf(File::isFile)?.length() ?: 0L, info.apkSize) } == true

    fun sizeOk(length: Long, expected: Long) = length > 0 && (expected <= 0 || length == expected)

    /** [info] 다운로드를 시작한다. 이전 다운로드·파일은 정리. 실패 시 예외. */
    fun start(ctx: Context, info: UpdateInfo) {
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val p = prefs(ctx)
        p.getLong("id", -1L).takeIf { it >= 0 }?.let { dm.remove(it) }
        File(ctx.cacheDir, "updates").deleteRecursively() // v0.13.0 이하가 직접 받던 위치
        val dir = dir(ctx) ?: error("저장소를 쓸 수 없습니다")
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() } // 이전 버전 APK. 같은 이름이 있으면 DownloadManager가 -1을 붙인다.
        val name = "chungyak-radar-${info.version}.apk"
        val req = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setDestinationInExternalFilesDir(ctx, null, "updates/$name")
            .setTitle("청약 레이더 v${info.version}")
            .setDescription("업데이트 다운로드")
            .setMimeType(APK_MIME)
            .addRequestHeader("User-Agent", "chungyak-radar")
            // 진행 중에만 시스템 알림(앱 알림 권한과 무관). 완료 알림은 우리가 띄운다.
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
        val id = dm.enqueue(req)
        save(ctx, id, info)
        cancelNotification(ctx)
    }

    /** 저장된 다운로드의 현재 상태. 받은 적 없거나 기록이 사라졌으면 None. */
    fun status(ctx: Context): Status {
        val p = prefs(ctx)
        val id = p.getLong("id", -1L)
        val info = load(ctx) ?: return Status.None
        if (id < 0) return Status.None
        val dm = ctx.getSystemService(DownloadManager::class.java)
        val row = dm.query(DownloadManager.Query().setFilterById(id))?.use { c ->
            if (!c.moveToFirst()) null
            else {
                fun long(col: String, def: Long) = c.getColumnIndex(col).takeIf { it >= 0 }?.let(c::getLong) ?: def
                Row(
                    long(DownloadManager.COLUMN_STATUS, DownloadManager.STATUS_PENDING.toLong()).toInt(),
                    long(DownloadManager.COLUMN_REASON, 0).toInt(),
                    long(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR, 0),
                    long(DownloadManager.COLUMN_TOTAL_SIZE_BYTES, -1),
                )
            }
        }
        val fileLen = apkFile(ctx, info.version)?.takeIf(File::isFile)?.length() ?: 0L
        // 시스템 기록이 지워졌어도(다운로드 앱 정리 등) 파일이 온전하면 완료로 본다.
        if (row == null) return if (sizeOk(fileLen, info.apkSize)) Status.Done(info) else Status.None
        return interpret(info, row, fileLen)
    }

    data class Row(val status: Int, val reason: Int, val soFar: Long, val total: Long)

    /** DownloadManager 행 → 상태. 순수 함수(단위테스트 대상). */
    fun interpret(info: UpdateInfo, r: Row, fileLen: Long): Status = when (r.status) {
        DownloadManager.STATUS_SUCCESSFUL ->
            if (sizeOk(fileLen, info.apkSize)) Status.Done(info) else Status.Failed(info, "파일 크기가 맞지 않습니다")
        DownloadManager.STATUS_FAILED -> Status.Failed(info, failReason(r.reason))
        else -> {
            val total = r.total.takeIf { it > 0 } ?: info.apkSize
            val progress = if (total > 0) (r.soFar.toFloat() / total).coerceIn(0f, 1f) else -1f
            val waiting = if (r.status == DownloadManager.STATUS_PAUSED) pauseReason(r.reason) else null
            Status.Running(info, progress, waiting)
        }
    }

    private fun pauseReason(reason: Int) = when (reason) {
        DownloadManager.PAUSED_WAITING_FOR_NETWORK -> "네트워크 연결을 기다리는 중"
        DownloadManager.PAUSED_QUEUED_FOR_WIFI -> "Wi-Fi 연결을 기다리는 중"
        DownloadManager.PAUSED_WAITING_TO_RETRY -> "잠시 후 다시 시도하는 중"
        else -> "일시정지됨"
    }

    private fun failReason(reason: Int) = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "저장 공간이 부족합니다"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "저장소를 찾을 수 없습니다"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_CANNOT_RESUME -> "네트워크 오류로 중단됐습니다"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "서버 응답 오류"
        in 400..599 -> "HTTP $reason"
        else -> "다운로드 실패($reason)"
    }

    /** 이 다운로드 기록이 우리 것인가(완료 브로드캐스트 확인용). */
    fun isOurs(ctx: Context, id: Long) = id >= 0 && prefs(ctx).getLong("id", -1L) == id

    /** 시스템 설치 화면 인텐트. */
    fun installIntent(ctx: Context, info: UpdateInfo): Intent {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.updates", apkFile(ctx, info.version)!!)
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /** 앱 화면이 사용자 앞에 있나. */
    fun isForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    /**
     * "업데이트 준비됨" 알림. 탭하면 설치 화면(설치 권한이 아직 없으면 앱을 열어 권한 안내).
     * 알림 권한이 없으면 조용히 건너뛴다 — 앱을 열면 팝업으로 설치를 안내한다.
     */
    fun notifyReady(ctx: Context, info: UpdateInfo) {
        val nm = NotificationManagerCompat.from(ctx)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(ctx)
        val intent = if (ctx.packageManager.canRequestPackageInstalls()) installIntent(ctx, info)
        else Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(
            ctx, NOTIF_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("업데이트 준비됨 v${info.version}")
            .setContentText("탭하면 설치합니다")
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        try {
            nm.notify(NOTIF_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancelNotification(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(NOTIF_ID)

    private fun ensureChannel(ctx: Context) {
        val mgr = ctx.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL) != null) return
        mgr.createNotificationChannel(
            NotificationChannel(CHANNEL, "앱 업데이트", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "새 버전을 다 받았을 때 설치 안내"
            }
        )
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun save(ctx: Context, id: Long, info: UpdateInfo) {
        prefs(ctx).edit()
            .putLong("id", id)
            .putString("tag", info.tag)
            .putString("version", info.version)
            .putString("notes", info.notes)
            .putString("apkUrl", info.apkUrl)
            .putLong("apkSize", info.apkSize)
            .putString("pageUrl", info.pageUrl)
            .apply()
    }

    fun load(ctx: Context): UpdateInfo? {
        val p = prefs(ctx)
        val version = p.getString("version", null) ?: return null
        return UpdateInfo(
            tag = p.getString("tag", null) ?: "v$version",
            version = version,
            notes = p.getString("notes", null).orEmpty(),
            apkUrl = p.getString("apkUrl", null).orEmpty(),
            apkSize = p.getLong("apkSize", 0L),
            pageUrl = p.getString("pageUrl", null) ?: UpdateClient.RELEASES_PAGE,
        )
    }

    /** 설치된 버전 이하의 기록·파일은 지운다(업데이트 설치 후 정리). */
    fun cleanupIfInstalled(ctx: Context, installedVersion: String) {
        val info = load(ctx) ?: return
        if (Versions.isNewer(info.version, installedVersion)) return
        val dm = ctx.getSystemService(DownloadManager::class.java)
        prefs(ctx).getLong("id", -1L).takeIf { it >= 0 }?.let { runCatching { dm.remove(it) } }
        dir(ctx)?.listFiles()?.forEach { it.delete() }
        prefs(ctx).edit().clear().apply()
        cancelNotification(ctx)
    }
}
