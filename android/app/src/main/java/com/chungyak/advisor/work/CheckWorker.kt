package com.chungyak.advisor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chungyak.advisor.api.ApplyHomeClient
import com.chungyak.advisor.api.FetchResult
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.notify.Notifier
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Polls the 청약홈 API, stores newly seen 수도권 일반공급 공고, and posts a
 * local notification for each new one. Runs both on the WorkManager periodic
 * schedule and on the manual "지금 확인" action.
 *
 * Android background note: WorkManager's minimum periodic interval is 15 min and
 * the OS batches/defers work under Doze — so this is best-effort, not exact.
 * For a 청약 알림 that is acceptable: 공고 접수 windows are days long, and we
 * only need to catch a 공고 within hours of it appearing, not at a precise time.
 */
class CheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val settings = Settings(applicationContext)
        settings.lastCheckMillis = System.currentTimeMillis()
        val stamp = SimpleDateFormat("MM-dd HH:mm", Locale.KOREA).format(Date())

        if (!settings.hasKey) {
            settings.lastResult = "$stamp · 서비스키 미설정"
            return Result.success()
        }

        val result = ApplyHomeClient.fetch(
            serviceKey = settings.serviceKey,
            lookbackDays = settings.lookbackDays,
            regions = settings.regions,
        )

        return when (result) {
            is FetchResult.Error -> {
                settings.lastResult = "$stamp · 오류: ${result.message}"
                Result.retry()
            }
            is FetchResult.Ok -> {
                val dao = AppDatabase.get(applicationContext).noticeDao()
                val existing = dao.allIds().toHashSet()
                // 분양가는 목록에 없어 신규 공고에 한해 주택형별 상세를 2차 호출로 결합.
                val fresh = result.notices.filter { it.id !in existing }.map { n ->
                    val price = ApplyHomeClient.fetchPrice(
                        settings.serviceKey, n.houseManageNo, n.pblancNo,
                    )
                    if (price != null) {
                        n.copy(priceMinManwon = price.minManwon, priceMaxManwon = price.maxManwon)
                    } else n
                }
                if (fresh.isNotEmpty()) {
                    dao.insertAll(fresh)
                    Notifier.notifyNew(applicationContext, fresh)
                    dao.markNotified(fresh.map { it.id })
                }
                settings.lastResult =
                    "$stamp · 조회 ${result.notices.size}건 · 신규 ${fresh.size}건"
                Result.success()
            }
        }
    }
}
