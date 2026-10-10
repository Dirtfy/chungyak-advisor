package com.chungyak.advisor.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.chungyak.advisor.MainActivity
import com.chungyak.advisor.R
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.match.Affordability
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.ui.FundsFormat
import com.chungyak.advisor.ui.PriceFormat

/**
 * Posts on-device local notifications for newly detected 공고.
 *
 * 스마트밴드(Galaxy Fit 등)는 Galaxy Wearable 앱이 폰 알림을 미러링해서 받는다. 그래서
 * 일반(이벤트성) 알림으로만 게시한다: ongoing 아님, IMPORTANCE_HIGH + 진동 패턴 채널,
 * setLocalOnly(false), 카테고리 지정, 밴드 화면에 맞게 짧은 제목/본문.
 */
object Notifier {

    /** v0.4.3~ 채널. 진동 패턴은 채널 생성 후 못 바꾸므로 새 ID로 옮겼다. */
    const val CHANNEL_NEW = "new_notices_v2"
    /** v0.4.2 이하 채널(진동 미지정). [ensureChannel]이 설정을 옮기고 삭제한다. */
    const val CHANNEL_LEGACY = "new_notices"

    val VIBRATION = longArrayOf(0, 400, 200, 400)

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_NEW) == null) {
            // 사용자가 예전 채널을 꺼 두었다면 그 선택을 유지한다.
            val legacy = mgr.getNotificationChannel(CHANNEL_LEGACY)
            val blocked = legacy?.importance == NotificationManager.IMPORTANCE_NONE
            val ch = NotificationChannel(
                CHANNEL_NEW,
                context.getString(R.string.notif_channel_new),
                if (blocked) NotificationManager.IMPORTANCE_NONE else NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_new_desc)
                enableVibration(true)
                vibrationPattern = VIBRATION
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(true)
            }
            mgr.createNotificationChannel(ch)
        }
        if (mgr.getNotificationChannel(CHANNEL_LEGACY) != null) mgr.deleteNotificationChannel(CHANNEL_LEGACY)
    }

    /** 공통 설정: 밴드로 미러링되는 일반 알림. */
    private fun base(context: Context, intent: PendingIntent) =
        NotificationCompat.Builder(context, CHANNEL_NEW)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVibrate(VIBRATION)
            .setDefaults(NotificationCompat.DEFAULT_SOUND or NotificationCompat.DEFAULT_LIGHTS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(false)
            .setLocalOnly(false)
            .setOnlyAlertOnce(false)
            .setAutoCancel(true)
            .setContentIntent(intent)

    /** 밴드 화면용 짧은 제목: 단지명이 길면 자른다. */
    fun shortTitle(name: String, max: Int = 18, prefix: String = "신규 청약 "): String =
        prefix + (if (name.length > max) name.take(max - 1) + "…" else name)

    /**
     * 청약 일정 알림([ScheduleAlerts]): 공고·일정마다 한 건. 새 공고 알림과 같은 채널·형태라 밴드에도
     * 그대로 전달된다. 제목은 "내일 접수 단지명…" 처럼 밴드 화면에 맞게 짧게.
     */
    fun notifySchedule(context: Context, alerts: List<ScheduleAlerts.Alert>) {
        if (alerts.isEmpty()) return
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alerts.take(10).forEach { a ->
            val n = a.notice
            val day = if (a.tomorrow) "내일" else "오늘"
            val what = "$day(${a.date}) ${a.kind.label}"
            val notif = base(context, contentIntent)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setContentTitle(shortTitle(n.name, prefix = a.prefix))
                .setContentText("${n.areaName} · $what")
                .setStyle(NotificationCompat.BigTextStyle().bigText("${n.name}\n${n.areaName} ${n.address}\n$what"))
                .build()
            try {
                nm.notify(a.key.hashCode(), notif)
            } catch (_: SecurityException) {
            }
        }
    }

    /**
     * Notify about [newNotices]. One notification per 공고 (up to a small cap),
     * plus a summary line. Safe to call when the POST_NOTIFICATIONS permission
     * is absent — it simply no-ops.
     */
    fun notifyNew(
        context: Context,
        newNotices: List<Notice>,
        matches: Map<String, Eligibility.Result> = emptyMap(),
        funds: Map<String, Affordability.NoticeResult> = emptyMap(),
    ) {
        if (newNotices.isEmpty()) return
        ensureChannel(context)
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return

        val contentIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        newNotices.take(5).forEach { n ->
            val schedule = if (n.rank1Start.isNotBlank())
                "1순위 ${n.rank1Start}" else "모집공고 ${n.noticeDate}"
            val price = if (n.priceMaxManwon > 0)
                " · 분양가 ${PriceFormat.range(n.priceMinManwon, n.priceMaxManwon)}" else ""
            // 밴드에는 제목+본문 한 줄이 보인다. 상세(주소·세대수·분양가)는 폰의 펼친 알림에.
            val m = matches[n.id]
            val mine = m?.let { "\n내 조건: ${it.verdict.label} — ${it.summary}" }.orEmpty() +
                funds[n.id]?.let { "\n내 자금: ${FundsFormat.summary(it)}" }.orEmpty()
            val notif = base(context, contentIntent)
                .setContentTitle(if (m != null) shortTitle(n.name, prefix = "맞춤 청약 ") else shortTitle(n.name))
                .setContentText("${n.areaName} · $schedule")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${n.name}\n${n.areaName} ${n.address}\n${n.totalUnits}세대 · $schedule$price$mine"
                    )
                )
                .build()
            try {
                nm.notify(n.id.hashCode(), notif)
            } catch (_: SecurityException) {
                // Permission revoked between the check and here — ignore.
            }
        }

        if (newNotices.size > 5) {
            val summary = base(context, contentIntent)
                .setContentTitle("신규 청약 ${newNotices.size}건")
                .setContentText("앱에서 전체 목록 확인")
                .build()
            try {
                nm.notify(1, summary)
            } catch (_: SecurityException) {
            }
        }
    }
}
