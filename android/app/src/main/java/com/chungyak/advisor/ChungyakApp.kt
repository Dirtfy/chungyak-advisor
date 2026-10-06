package com.chungyak.advisor

import android.app.Application
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.notify.Notifier
import com.chungyak.advisor.work.Scheduler

class ChungyakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // 업데이트 후 설정 스키마를 먼저 맞춘다(설정 보존).
        Settings(this).migrate()
        Notifier.ensureChannel(this)
        // Idempotent (KEEP policy) — safe to call on every launch.
        Scheduler.schedulePeriodic(this)
        Scheduler.scheduleAlerts(this, Settings(this).scheduleAlerts)
    }
}
