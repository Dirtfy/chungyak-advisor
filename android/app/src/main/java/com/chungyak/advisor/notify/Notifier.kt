package com.chungyak.advisor.notify

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

/** Posts on-device local notifications for newly detected 공고. */
object Notifier {

    const val CHANNEL_NEW = "new_notices"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_NEW) == null) {
            val ch = NotificationChannel(
                CHANNEL_NEW,
                context.getString(R.string.notif_channel_new),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.notif_channel_new_desc) }
            mgr.createNotificationChannel(ch)
        }
    }

    /**
     * Notify about [newNotices]. One notification per 공고 (up to a small cap),
     * plus a summary line. Safe to call when the POST_NOTIFICATIONS permission
     * is absent — it simply no-ops.
     */
    fun notifyNew(context: Context, newNotices: List<Notice>) {
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
            val notif = NotificationCompat.Builder(context, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("신규 청약: ${n.name}")
                .setContentText("${n.areaName} · ${n.totalUnits}세대 · $schedule")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        "${n.areaName} ${n.address}\n${n.totalUnits}세대 · $schedule"
                    )
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            try {
                nm.notify(n.id.hashCode(), notif)
            } catch (_: SecurityException) {
                // Permission revoked between the check and here — ignore.
            }
        }

        if (newNotices.size > 5) {
            val summary = NotificationCompat.Builder(context, CHANNEL_NEW)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("신규 청약 공고 ${newNotices.size}건")
                .setContentText("수도권 일반공급 신규 공고가 도착했습니다.")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
            try {
                nm.notify(1, summary)
            } catch (_: SecurityException) {
            }
        }
    }
}
