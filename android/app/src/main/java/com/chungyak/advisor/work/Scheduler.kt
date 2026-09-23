package com.chungyak.advisor.work

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/** Schedules the periodic 공고 check and the manual one-shot check. */
object Scheduler {

    private const val PERIODIC = "chungyak_periodic_check"
    private const val ONESHOT = "chungyak_manual_check"

    /**
     * Register the repeating check. Interval is 6h by default — well above the
     * 15-min OS floor, gentle on battery and the API quota, and more than fast
     * enough for 청약 접수 windows measured in days. Requires connectivity.
     */
    fun schedulePeriodic(context: Context, intervalHours: Long = 6) {
        val request = PeriodicWorkRequestBuilder<CheckWorker>(intervalHours, TimeUnit.HOURS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** Force an immediate check (the "지금 확인" button). */
    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<CheckWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, request)
    }
}
