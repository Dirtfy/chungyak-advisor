package com.chungyak.advisor.data

import android.content.Context

/**
 * App settings persisted in SharedPreferences (no extra dependency).
 *
 * The data.go.kr service key is entered by each user for their own account —
 * the app ships no shared key. This keeps a standalone, server-less app within
 * the API's per-account traffic quota and avoids embedding a secret in the APK.
 */
class Settings(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("chungyak_prefs", Context.MODE_PRIVATE)

    var serviceKey: String
        get() = prefs.getString(KEY_SERVICE_KEY, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_SERVICE_KEY, v.trim()).apply()

    /** Look-back window (days) for RCRIT_PBLANC_DE when polling. */
    var lookbackDays: Int
        get() = prefs.getInt(KEY_LOOKBACK, 30)
        set(v) = prefs.edit().putInt(KEY_LOOKBACK, v).apply()

    /** Selected 수도권 regions to keep (by SUBSCRPT_AREA_CODE_NM). */
    var regions: Set<String>
        get() = prefs.getStringSet(KEY_REGIONS, DEFAULT_REGIONS) ?: DEFAULT_REGIONS
        set(v) = prefs.edit().putStringSet(KEY_REGIONS, v).apply()

    var lastCheckMillis: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(v) = prefs.edit().putLong(KEY_LAST_CHECK, v).apply()

    var lastResult: String
        get() = prefs.getString(KEY_LAST_RESULT, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_LAST_RESULT, v).apply()

    val hasKey: Boolean get() = serviceKey.isNotBlank()

    /** 목록 정렬 (SortOrder.name). 다음 실행에도 유지. */
    var sortOrder: String
        get() = prefs.getString(KEY_SORT, "").orEmpty()
        set(v) = prefs.edit().putString(KEY_SORT, v).apply()

    /** 마지막 경쟁률 호출이 401(경쟁률 서비스 미신청)이었는지. 성공하면 false로 복귀. */
    var cmpetUnauthorized: Boolean
        get() = prefs.getBoolean(KEY_CMPET_UNAUTH, false)
        set(v) = prefs.edit().putBoolean(KEY_CMPET_UNAUTH, v).apply()

    /** 첫 수집을 '기준선'으로 처리했는지(재설치 직후 기존 공고가 한꺼번에 알림으로 쏟아지지 않게). */
    var baselineDone: Boolean
        get() = prefs.getBoolean(KEY_BASELINE, false)
        set(v) = prefs.edit().putBoolean(KEY_BASELINE, v).apply()

    /** 청약 일정 알림(접수 시작일·당첨 발표일) 켜짐 여부. 기본 켬(v0.7.0~). */
    var scheduleAlerts: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULE_ALERTS, true)
        set(v) = prefs.edit().putBoolean(KEY_SCHEDULE_ALERTS, v).apply()

    /** 이미 보낸 일정 알림 키(ScheduleAlerts.key). 중복 알림 방지. */
    var scheduleSent: Set<String>
        get() = prefs.getStringSet(KEY_SCHEDULE_SENT, emptySet())?.toSet() ?: emptySet()
        set(v) = prefs.edit().putStringSet(KEY_SCHEDULE_SENT, HashSet(v)).apply()

    /**
     * 설정 스키마 마이그레이션. 키 이름/형식을 바꿀 때는 [PREFS_VERSION]을 올리고 아래에 단계를
     * 추가한다(기존 단계는 지우지 않는다). 앱 시작 시 1회 호출. 업데이트 시 설정 보존이 목적.
     */
    fun migrate() {
        var v = prefs.getInt(KEY_PREFS_VERSION, 0)
        if (v >= PREFS_VERSION) return
        val e = prefs.edit()
        if (v < 1) {
            // 0 → 1 (v0.4.1): 기존 키 그대로 유지. 이미 수집 이력이 있는 기존 사용자는 기준선 완료로 간주.
            if (prefs.getLong(KEY_LAST_CHECK, 0L) > 0L) e.putBoolean(KEY_BASELINE, true)
            v = 1
        }
        e.putInt(KEY_PREFS_VERSION, v).apply()
    }

    companion object {
        const val PREFS_VERSION = 1
        private const val KEY_PREFS_VERSION = "prefs_version"
        private const val KEY_BASELINE = "baseline_done"

        private const val KEY_SERVICE_KEY = "service_key"
        private const val KEY_LOOKBACK = "lookback_days"
        private const val KEY_REGIONS = "regions"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_SORT = "sort_order"
        private const val KEY_CMPET_UNAUTH = "cmpet_unauthorized"
        private const val KEY_SCHEDULE_ALERTS = "schedule_alerts"
        private const val KEY_SCHEDULE_SENT = "schedule_sent"

        /** 수도권 default: Seoul, Gyeonggi, Incheon. */
        val DEFAULT_REGIONS = setOf("서울", "경기", "인천")
    }
}
