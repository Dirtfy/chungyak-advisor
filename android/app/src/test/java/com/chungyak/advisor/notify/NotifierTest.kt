package com.chungyak.advisor.notify

import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import com.chungyak.advisor.notice
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** 밴드(Galaxy Wearable) 미러링 가능한 형태로 게시되는지 + 채널 마이그레이션. */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class NotifierTest {

    private val ctx: Application = ApplicationProvider.getApplicationContext()
    private val mgr = ctx.getSystemService(NotificationManager::class.java)

    @Test fun channel_isHighWithVibration_andLegacyRemoved() {
        mgr.createNotificationChannel(NotificationChannel(Notifier.CHANNEL_LEGACY, "old", NotificationManager.IMPORTANCE_HIGH))
        Notifier.ensureChannel(ctx)
        val ch = mgr.getNotificationChannel(Notifier.CHANNEL_NEW)
        assertEquals(NotificationManager.IMPORTANCE_HIGH, ch.importance)
        assertTrue(ch.shouldVibrate())
        assertArrayEquals(Notifier.VIBRATION, ch.vibrationPattern)
        assertNull(mgr.getNotificationChannel(Notifier.CHANNEL_LEGACY))
    }

    @Test fun legacyChannelTurnedOff_staysOff() {
        mgr.createNotificationChannel(NotificationChannel(Notifier.CHANNEL_LEGACY, "old", NotificationManager.IMPORTANCE_NONE))
        Notifier.ensureChannel(ctx)
        assertEquals(NotificationManager.IMPORTANCE_NONE, mgr.getNotificationChannel(Notifier.CHANNEL_NEW).importance)
    }

    @Test fun posted_isPlainMirrorableAlert() {
        val long = "아주아주긴단지이름이있는신규분양아파트단지테스트"
        Notifier.notifyNew(ctx, listOf(notice("A", rank1Start = "2026-10-12").copy(name = long)))
        val posted = shadowOf(mgr).allNotifications
        assertEquals(1, posted.size)
        val n = posted[0]
        assertEquals(Notifier.CHANNEL_NEW, n.channelId)
        assertEquals(0, n.flags and Notification.FLAG_ONGOING_EVENT)
        assertEquals(0, n.flags and Notification.FLAG_FOREGROUND_SERVICE)
        assertFalse(NotificationCompat.getLocalOnly(n))
        assertEquals(NotificationCompat.CATEGORY_RECOMMENDATION, n.category)
        assertNull(n.fullScreenIntent)
        assertNull(n.group)
        val title = n.extras.getString(Notification.EXTRA_TITLE)!!
        assertTrue(title, title.length <= 24 && title.endsWith("…"))
        assertEquals("경기 · 1순위 2026-10-12", n.extras.getString(Notification.EXTRA_TEXT))
    }

    @Test fun moreThanFive_postsFivePlusSummary() {
        Notifier.notifyNew(ctx, (1..7).map { notice("N$it") })
        val posted = shadowOf(mgr).allNotifications
        assertEquals(6, posted.size)
        assertTrue(posted.all { it.channelId == Notifier.CHANNEL_NEW && !NotificationCompat.getLocalOnly(it) })
    }
}
