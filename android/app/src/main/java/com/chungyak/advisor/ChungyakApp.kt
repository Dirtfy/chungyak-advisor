package com.chungyak.advisor

import android.app.Application
import com.chungyak.advisor.notify.Notifier
import com.chungyak.advisor.work.Scheduler

class ChungyakApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifier.ensureChannel(this)
        // Idempotent (KEEP policy) — safe to call on every launch.
        Scheduler.schedulePeriodic(this)
    }
}
