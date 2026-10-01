package com.chungyak.advisor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chungyak.advisor.api.ApplyHomeClient
import com.chungyak.advisor.api.FetchResult
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.Notice
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
                val fresh = result.notices.filter { it.id !in existing }
                if (fresh.isNotEmpty()) dao.insertAll(fresh)
                // 분양가는 목록에 없어 주택형별 상세를 공고별 2차 호출로 받아 캐시한다.
                // 한 번 받으면 다시 부르지 않고, 실패분만 다음 폴링에서 재시도(쿼터·지연 절약).
                val priced = cacheModels(settings.serviceKey)
                if (fresh.isNotEmpty()) {
                    Notifier.notifyNew(applicationContext, fresh.map { priced[it.id] ?: it })
                    dao.markNotified(fresh.map { it.id })
                }
                settings.lastResult =
                    "$stamp · 조회 ${result.notices.size}건 · 신규 ${fresh.size}건" +
                        (if (priced.isNotEmpty()) " · 분양가 ${priced.size}건" else "")
                Result.success()
            }
        }
    }

    /**
     * 주택형별 상세가 아직 없는 공고(최신 우선, 회당 [MODELS_PER_RUN]건)에 대해
     * getAPTLttotPblancMdl을 호출해 [HouseModel]로 저장하고 공고의 분양가 범위를 채운다.
     * 반환: 이번에 상세를 받은 공고(id → 갱신본).
     */
    private suspend fun cacheModels(serviceKey: String): Map<String, Notice> {
        val db = AppDatabase.get(applicationContext)
        val dao = db.noticeDao()
        val out = HashMap<String, Notice>()
        for (n in dao.withoutModels(MODELS_PER_RUN)) {
            val models = ApplyHomeClient.fetchModels(serviceKey, n.id, n.houseManageNo, n.pblancNo)
                ?: continue
            db.houseModelDao().insertAll(models)
            val prices = models.map { it.priceManwon }.filter { it > 0 }
            val min = prices.minOrNull() ?: 0
            val max = prices.maxOrNull() ?: 0
            val now = System.currentTimeMillis()
            dao.setPrice(n.id, min, max, now)
            out[n.id] = n.copy(priceMinManwon = min, priceMaxManwon = max, modelsFetchedAt = now)
        }
        return out
    }

    private companion object {
        const val MODELS_PER_RUN = 20
    }
}
