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

    companion object {
        private const val KEY_SERVICE_KEY = "service_key"
        private const val KEY_LOOKBACK = "lookback_days"
        private const val KEY_REGIONS = "regions"
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_LAST_RESULT = "last_result"

        /** 수도권 default: Seoul, Gyeonggi, Incheon. */
        val DEFAULT_REGIONS = setOf("서울", "경기", "인천")
    }
}
