package com.chungyak.advisor.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the periodic check after a device reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Scheduler.schedulePeriodic(context)
            Scheduler.scheduleAlerts(context, com.chungyak.advisor.data.Settings(context).scheduleAlerts)
        }
    }
}
