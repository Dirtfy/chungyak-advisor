package com.chungyak.advisor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.ProfileStore
import com.chungyak.advisor.notify.Notifier
import com.chungyak.advisor.notify.ScheduleAlerts
import java.time.LocalDate
import java.time.LocalTime

/**
 * 청약 일정 알림: 1시간마다(OS가 묶어서 늦출 수 있음) 저장된 공고를 보고 [ScheduleAlerts] 규칙대로
 * 로컬 알림을 보낸다. 네트워크 없이 기기 DB만 읽으므로 가볍다.
 * 대상은 새 공고 알림과 같은 기준(내 조건·알림 범위·관심 조건, [Eligibility.shouldNotify]).
 */
class ScheduleAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        runAlerts(applicationContext)
        return Result.success()
    }

    companion object {
        /** 공고 확인([CheckWorker]) 직후에도 부른다 — 새로 받은 공고의 오늘·내일 일정도 바로 반영. */
        suspend fun runAlerts(ctx: Context, settings: Settings = Settings(ctx)) {
            if (!settings.scheduleAlerts) return
            val today = LocalDate.now()
            val db = AppDatabase.get(ctx)
            val all = db.noticeDao().all()
            val store = ProfileStore(ctx)
            val profile = store.profile
            val mode = store.notifyMode
            val targets: List<Notice> = if (!profile.isSet) all else {
                val models = db.houseModelDao().forNotices(all.map { it.id }).groupBy { it.noticeId }
                all.filter {
                    val r = Eligibility.evaluate(profile, it, models[it.id].orEmpty(), today)
                    r.filteredOut == null && Eligibility.shouldNotify(profile, mode, r)
                }
            }
            val sent = ScheduleAlerts.prune(settings.scheduleSent, today)
            val due = ScheduleAlerts.due(targets, today, LocalTime.now().hour, sent)
            if (due.isNotEmpty()) Notifier.notifySchedule(ctx, due)
            settings.scheduleSent = sent + due.map { it.key }
        }
    }
}
