package com.chungyak.advisor.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.chungyak.advisor.api.ApplyHomeClient
import com.chungyak.advisor.api.CompetitionFetch
import com.chungyak.advisor.api.CompetitionParser
import com.chungyak.advisor.api.FetchResult
import com.chungyak.advisor.data.AppDatabase
import com.chungyak.advisor.data.CompetitionPolicy
import com.chungyak.advisor.data.Notice
import com.chungyak.advisor.data.Settings
import com.chungyak.advisor.match.Eligibility
import com.chungyak.advisor.match.NotifyMode
import com.chungyak.advisor.match.ProfileStore
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
                // 첫 수집(새 설치·재설치 직후)은 기준선: 이미 올라와 있는 공고를 한꺼번에 알리지 않는다.
                val baseline = !settings.baselineDone && existing.isEmpty()
                settings.baselineDone = true
                if (fresh.isNotEmpty()) {
                    dao.insertAll(if (baseline) fresh.map { it.copy(notified = true) } else fresh)
                }
                // 이미 있는 공고도 주택 구분·규제지역은 최신 값으로(v0.4.4 이하 수집분은 houseDtl이 비어 있음).
                result.notices.filter { it.id in existing }.forEach {
                    dao.updateMeta(it.id, it.houseDtl, it.speculationArea, it.adjustmentArea)
                }
                // 분양가는 목록에 없어 주택형별 상세를 공고별 2차 호출로 받아 캐시한다.
                // 한 번 받으면 다시 부르지 않고, 실패분만 다음 폴링에서 재시도(쿼터·지연 절약).
                val priced = cacheModels(settings.serviceKey)
                // 경쟁률은 별도 서비스: 401이어도 다른 기능엔 영향 없이 안내만 남긴다.
                val cmpet = cacheCompetition(settings)
                var matchNote = ""
                if (fresh.isNotEmpty() && !baseline) {
                    // 개인화: 프로필이 있으면 내 조건에 맞는 공고만 알린다(판정은 기기 안에서만).
                    val store = ProfileStore(applicationContext)
                    val profile = store.profile
                    val mode = store.notifyMode
                    val stored = dao.byIds(fresh.map { it.id }).associateBy { it.id }
                    val models = AppDatabase.get(applicationContext).houseModelDao()
                        .forNotices(fresh.map { it.id }).groupBy { it.noticeId }
                    val candidates = fresh.map { stored[it.id] ?: priced[it.id] ?: it }
                    val results = if (profile.isSet)
                        candidates.associate { it.id to Eligibility.evaluate(profile, it, models[it.id].orEmpty()) }
                    else emptyMap()
                    val toNotify = candidates.filter { Eligibility.shouldNotify(profile, mode, results[it.id]) }
                    Notifier.notifyNew(applicationContext, toNotify, results)
                    dao.markNotified(fresh.map { it.id })
                    if (profile.isSet && mode != NotifyMode.ALL) matchNote = " · 맞춤 알림 ${toNotify.size}건"
                }
                // 새로 받은 공고의 오늘·내일 일정도 바로 확인(1시간 주기 확인을 기다리지 않음).
                runCatching { ScheduleAlertWorker.runAlerts(applicationContext, settings) }
                settings.lastResult =
                    "$stamp · 조회 ${result.notices.size}건 · 신규 ${fresh.size}건" +
                        (if (baseline && fresh.isNotEmpty()) "(첫 수집: 알림 생략)" else "") + matchNote +
                        (if (priced.isNotEmpty()) " · 분양가 ${priced.size}건" else "") +
                        when {
                            settings.cmpetUnauthorized -> " · 경쟁률: data.go.kr 활용신청 필요"
                            cmpet > 0 -> " · 경쟁률 ${cmpet}건"
                            else -> ""
                        }
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

    /**
     * 접수가 시작된 미확정 공고의 경쟁률을 받아 캐시(회당 [CMPET_PER_RUN]건, [CompetitionPolicy]
     * 간격). 401이면 즉시 중단하고 [Settings.cmpetUnauthorized]를 켠다. 반환: 갱신한 공고 수.
     */
    private suspend fun cacheCompetition(settings: Settings): Int {
        val db = AppDatabase.get(applicationContext)
        val dao = db.noticeDao()
        val now = System.currentTimeMillis()
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date(now))
        val due = dao.withoutFinalCompetition()
            .filter { CompetitionPolicy.shouldFetch(it, today, now) }
            .take(CMPET_PER_RUN)
        var updated = 0
        for (n in due) {
            when (val r = ApplyHomeClient.fetchCompetition(settings.serviceKey, n.id, n.houseManageNo, n.pblancNo)) {
                is CompetitionFetch.Unauthorized -> {
                    settings.cmpetUnauthorized = true
                    return updated
                }
                is CompetitionFetch.Failed -> continue
                is CompetitionFetch.Ok -> {
                    settings.cmpetUnauthorized = false
                    db.competitionDao().replaceFor(n.id, r.rows)
                    val (max, avg) = CompetitionParser.summary(r.rows)
                    dao.setCompetition(n.id, max, avg, now, CompetitionPolicy.isFinal(n, today))
                    updated++
                }
            }
        }
        return updated
    }

    private companion object {
        const val MODELS_PER_RUN = 20
        const val CMPET_PER_RUN = 20
    }
}
