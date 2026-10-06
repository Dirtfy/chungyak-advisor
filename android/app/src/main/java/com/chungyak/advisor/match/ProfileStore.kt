package com.chungyak.advisor.match

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 프로필·알림 범위 저장소. 개인정보라 별도 prefs 파일([FILE])에 두고, 서버·백업 파일·로그 어디로도
 * 보내지 않는다. Android 자동 백업(구글 드라이브)에서도 제외한다(res/xml/backup_rules.xml,
 * data_extraction_rules.xml). 앱 백업 JSON(Backup)에도 넣지 않는다.
 */
class ProfileStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    var profile: Profile
        get() = prefs.getString(KEY_PROFILE, null)?.let { runCatching { decode(JSONObject(it)) }.getOrNull() } ?: Profile()
        set(v) = prefs.edit().putString(KEY_PROFILE, encode(v).toString()).apply()

    var notifyMode: NotifyMode
        get() = NotifyMode.of(prefs.getString(KEY_MODE, null))
        set(v) = prefs.edit().putString(KEY_MODE, v.name).apply()

    fun clear() = prefs.edit().clear().apply()

    companion object {
        const val FILE = "chungyak_profile"
        private const val KEY_PROFILE = "profile_v1"
        private const val KEY_MODE = "notify_mode"

        fun encode(p: Profile) = JSONObject()
            .put("sido", p.sido).put("sigungu", p.sigungu).put("residenceMonths", p.residenceMonths)
            .put("householdHead", p.householdHead).put("homesOwned", p.homesOwned)
            .put("everOwned", p.everOwned).put("wonWithin5y", p.wonWithin5y).put("usedSpecial", p.usedSpecial)
            .put("married", p.married).put("marriageYm", p.marriageYm).put("children", p.children)
            .put("hasNewborn", p.hasNewborn).put("supportsParent", p.supportsParent)
            .put("account", p.account.name).put("accountMonths", p.accountMonths)
            .put("accountOpened", p.accountOpened)
            .put("payments", p.payments).put("depositManwon", p.depositManwon)
            .put("householdSize", p.householdSize).put("incomePct", p.incomePct)
            .put("dualIncome", p.dualIncome).put("realEstateManwon", p.realEstateManwon)
            .put("taxYears5", p.taxYears5).put("interestSido", JSONArray(p.interestSido.toList()))
            .put("maxPriceManwon", p.maxPriceManwon).put("minAreaM2", p.minAreaM2).put("maxAreaM2", p.maxAreaM2)
            .put("birthDate", p.birthDate).put("homelessSince", p.homelessSince).put("dependents", p.dependents)

        fun decode(o: JSONObject) = Profile(
            sido = o.optString("sido"), sigungu = o.optString("sigungu"),
            residenceMonths = o.optInt("residenceMonths", -1), householdHead = o.optBoolean("householdHead"),
            homesOwned = o.optInt("homesOwned", 0), everOwned = o.optBoolean("everOwned"),
            wonWithin5y = o.optBoolean("wonWithin5y"), usedSpecial = o.optBoolean("usedSpecial"),
            married = o.optBoolean("married"), marriageYm = o.optString("marriageYm"),
            children = o.optInt("children", 0), hasNewborn = o.optBoolean("hasNewborn"),
            supportsParent = o.optBoolean("supportsParent"), account = AccountType.of(o.optString("account")),
            accountMonths = o.optInt("accountMonths", -1), accountOpened = o.optString("accountOpened"), payments = o.optInt("payments", -1),
            depositManwon = o.optInt("depositManwon", -1), householdSize = o.optInt("householdSize", -1),
            incomePct = o.optInt("incomePct", -1), dualIncome = o.optBoolean("dualIncome"),
            realEstateManwon = o.optInt("realEstateManwon", -1), taxYears5 = o.optBoolean("taxYears5"),
            interestSido = o.optJSONArray("interestSido")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet(),
            maxPriceManwon = o.optInt("maxPriceManwon", -1), minAreaM2 = o.optInt("minAreaM2", -1),
            maxAreaM2 = o.optInt("maxAreaM2", -1),
            birthDate = o.optString("birthDate"), homelessSince = o.optString("homelessSince"),
            dependents = o.optInt("dependents", -1),
        )
    }
}
