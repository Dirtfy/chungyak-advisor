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

    companion object {
        private const val KEY_SERVICE_KEY = "service_key"
        private const val KEY_LOOKBACK = "lookback_days"
        private const val KEY_REGIONS = "regions"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_RESULT = "last_result"
        private const val KEY_SORT = "sort_order"
        private const val KEY_CMPET_UNAUTH = "cmpet_unauthorized"

        /** 수도권 default: Seoul, Gyeonggi, Incheon. */
        val DEFAULT_REGIONS = setOf("서울", "경기", "인천")
    }
}
